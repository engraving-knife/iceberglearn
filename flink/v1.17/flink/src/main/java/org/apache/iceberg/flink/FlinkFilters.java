/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.flink;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.flink.table.expressions.CallExpression;
import org.apache.flink.table.expressions.FieldReferenceExpression;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.apache.flink.table.expressions.ValueLiteralExpression;
import org.apache.flink.table.functions.BuiltInFunctionDefinitions;
import org.apache.flink.table.functions.FunctionDefinition;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expression.Operation;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.NaNUtil;

/**
 * 文件级说明：将 Flink 表达式转换为 Iceberg 表达式的工具类。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Flink 谓词表达式（等于、不等、大于、小于、IS NULL、AND/OR/NOT、LIKE 等） 转换为 Iceberg 内部表达式 {@link
 *       Expression}，供文件层做谓词下推过滤。
 *   <li>对常量字面量做时间戳/日期等类型到 Iceberg 内部表示的换算。
 *   <li>对 LIKE 做模式匹配，仅支持转换为 STARTS_WITH 表达式。
 * </ul>
 *
 * <p>设计意图：Flink SQL WHERE 子句被解析为 ResolvedExpression 树， 需要转成 Iceberg Expression
 * 才能在文件层做数据跳过。本类集中负责该转换， 复杂表达式（BETWEEN、IN 等）由 Flink 自动展开为基础比较组合，本类无需特殊处理。
 *
 * <p>上下游关系：上游为 Flink 的 Planner 产出的 ResolvedExpression， 下游为 Iceberg 的 {@link Expressions}
 * 工厂和文件扫描任务过滤逻辑。
 */
public class FlinkFilters {
  /** 私有构造，工具类禁止实例化。 */
  private FlinkFilters() {}

  private static final Pattern STARTS_WITH_PATTERN = Pattern.compile("([^%]+)%");

  /** Flink 内置函数定义到 Iceberg Expression 操作符的映射表。 */
  private static final Map<FunctionDefinition, Operation> FILTERS =
      ImmutableMap.<FunctionDefinition, Operation>builder()
          .put(BuiltInFunctionDefinitions.EQUALS, Operation.EQ)
          .put(BuiltInFunctionDefinitions.NOT_EQUALS, Operation.NOT_EQ)
          .put(BuiltInFunctionDefinitions.GREATER_THAN, Operation.GT)
          .put(BuiltInFunctionDefinitions.GREATER_THAN_OR_EQUAL, Operation.GT_EQ)
          .put(BuiltInFunctionDefinitions.LESS_THAN, Operation.LT)
          .put(BuiltInFunctionDefinitions.LESS_THAN_OR_EQUAL, Operation.LT_EQ)
          .put(BuiltInFunctionDefinitions.IS_NULL, Operation.IS_NULL)
          .put(BuiltInFunctionDefinitions.IS_NOT_NULL, Operation.NOT_NULL)
          .put(BuiltInFunctionDefinitions.AND, Operation.AND)
          .put(BuiltInFunctionDefinitions.OR, Operation.OR)
          .put(BuiltInFunctionDefinitions.NOT, Operation.NOT)
          .put(BuiltInFunctionDefinitions.LIKE, Operation.STARTS_WITH)
          .buildOrThrow();

  /**
   * 将 Flink 表达式转换为 Iceberg 表达式。
   *
   * <p>说明：BETWEEN、NOT_BETWEEN、IN 等表达式会被 Flink 自动展开—— BETWEEN 展开为 (GT_EQ AND LT_EQ)；NOT_BETWEEN 展开为
   * (LT_EQ OR GT_EQ)； IN 展开为多个 OR。因此本方法不单独处理这些表达式。
   *
   * @param flinkExpression Flink 侧的已解析表达式
   * @return 转换后的 Iceberg 表达式；不可识别时返回 Optional.empty()
   */
  public static Optional<Expression> convert(
      org.apache.flink.table.expressions.Expression flinkExpression) {
    if (!(flinkExpression instanceof CallExpression)) {
      return Optional.empty();
    }

    CallExpression call = (CallExpression) flinkExpression;
    Operation op = FILTERS.get(call.getFunctionDefinition());
    if (op != null) {
      switch (op) {
        case IS_NULL:
          return onlyChildAs(call, FieldReferenceExpression.class)
              .map(FieldReferenceExpression::getName)
              .map(Expressions::isNull);

        case NOT_NULL:
          return onlyChildAs(call, FieldReferenceExpression.class)
              .map(FieldReferenceExpression::getName)
              .map(Expressions::notNull);

        case LT:
          return convertFieldAndLiteral(Expressions::lessThan, Expressions::greaterThan, call);

        case LT_EQ:
          return convertFieldAndLiteral(
              Expressions::lessThanOrEqual, Expressions::greaterThanOrEqual, call);

        case GT:
          return convertFieldAndLiteral(Expressions::greaterThan, Expressions::lessThan, call);

        case GT_EQ:
          return convertFieldAndLiteral(
              Expressions::greaterThanOrEqual, Expressions::lessThanOrEqual, call);

        case EQ:
          return convertFieldAndLiteral(
              (ref, lit) -> {
                if (NaNUtil.isNaN(lit)) {
                  return Expressions.isNaN(ref);
                } else {
                  return Expressions.equal(ref, lit);
                }
              },
              call);

        case NOT_EQ:
          return convertFieldAndLiteral(
              (ref, lit) -> {
                if (NaNUtil.isNaN(lit)) {
                  return Expressions.notNaN(ref);
                } else {
                  return Expressions.notEqual(ref, lit);
                }
              },
              call);

        case NOT:
          return onlyChildAs(call, CallExpression.class)
              .flatMap(FlinkFilters::convert)
              .map(Expressions::not);

        case AND:
          return convertLogicExpression(Expressions::and, call);

        case OR:
          return convertLogicExpression(Expressions::or, call);

        case STARTS_WITH:
          return convertLike(call);
      }
    }

    return Optional.empty();
  }

  /** 取 CallExpression 的唯一子节点，并按期望类型进行类型检查与转换。 */
  private static <T extends ResolvedExpression> Optional<T> onlyChildAs(
      CallExpression call, Class<T> expectedChildClass) {
    List<ResolvedExpression> children = call.getResolvedChildren();
    if (children.size() != 1) {
      return Optional.empty();
    }

    ResolvedExpression child = children.get(0);
    if (!expectedChildClass.isInstance(child)) {
      return Optional.empty();
    }

    return Optional.of(expectedChildClass.cast(child));
  }

  private static Optional<Expression> convertLike(CallExpression call) {
    List<ResolvedExpression> args = call.getResolvedChildren();
    if (args.size() != 2) {
      return Optional.empty();
    }

    org.apache.flink.table.expressions.Expression left = args.get(0);
    org.apache.flink.table.expressions.Expression right = args.get(1);

    if (left instanceof FieldReferenceExpression && right instanceof ValueLiteralExpression) {
      String name = ((FieldReferenceExpression) left).getName();
      return convertLiteral((ValueLiteralExpression) right)
          .flatMap(
              lit -> {
                if (lit instanceof String) {
                  String pattern = (String) lit;
                  Matcher matcher = STARTS_WITH_PATTERN.matcher(pattern);
                  // exclude special char of LIKE
                  // '_' is the wildcard of the SQL LIKE
                  if (!pattern.contains("_") && matcher.matches()) {
                    return Optional.of(Expressions.startsWith(name, matcher.group(1)));
                  }
                }

                return Optional.empty();
              });
    }

    return Optional.empty();
  }

  /** 将 AND/OR 这类二元逻辑表达式递归转换为 Iceberg 表达式。 */
  private static Optional<Expression> convertLogicExpression(
      BiFunction<Expression, Expression, Expression> function, CallExpression call) {
    List<ResolvedExpression> args = call.getResolvedChildren();
    if (args == null || args.size() != 2) {
      return Optional.empty();
    }

    Optional<Expression> left = convert(args.get(0));
    Optional<Expression> right = convert(args.get(1));
    if (left.isPresent() && right.isPresent()) {
      return Optional.of(function.apply(left.get(), right.get()));
    }

    return Optional.empty();
  }

  /**
   * 把 Flink 字面量转换为 Iceberg 内部表示。
   *
   * <p>逻辑：日期/时间/时间戳按 Iceberg 的微秒或天数表示换算； 其他类型保持原值。
   */
  private static Optional<Object> convertLiteral(ValueLiteralExpression expression) {
    Optional<?> value =
        expression.getValueAs(
            expression.getOutputDataType().getLogicalType().getDefaultConversion());
    return value.map(
        o -> {
          if (o instanceof LocalDateTime) {
            return DateTimeUtil.microsFromTimestamp((LocalDateTime) o);
          } else if (o instanceof Instant) {
            return DateTimeUtil.microsFromInstant((Instant) o);
          } else if (o instanceof LocalTime) {
            return DateTimeUtil.microsFromTime((LocalTime) o);
          } else if (o instanceof LocalDate) {
            return DateTimeUtil.daysFromDate((LocalDate) o);
          }

          return o;
        });
  }

  /** 字段-字面量二元表达式的简化重载，左右参数顺序相同时使用。 */
  private static Optional<Expression> convertFieldAndLiteral(
      BiFunction<String, Object, Expression> expr, CallExpression call) {
    return convertFieldAndLiteral(expr, expr, call);
  }

  /**
   * 字段-字面量二元表达式的通用转换。
   *
   * <p>逻辑：根据左/右是字段引用还是字面量，分别使用 convertLR 或 convertRL 构造 Iceberg 表达式，确保参数顺序正确（字段名在前、字面量在后）。
   */
  private static Optional<Expression> convertFieldAndLiteral(
      BiFunction<String, Object, Expression> convertLR,
      BiFunction<String, Object, Expression> convertRL,
      CallExpression call) {
    List<ResolvedExpression> args = call.getResolvedChildren();
    if (args.size() != 2) {
      return Optional.empty();
    }

    org.apache.flink.table.expressions.Expression left = args.get(0);
    org.apache.flink.table.expressions.Expression right = args.get(1);

    if (left instanceof FieldReferenceExpression && right instanceof ValueLiteralExpression) {
      String name = ((FieldReferenceExpression) left).getName();
      Optional<Object> lit = convertLiteral((ValueLiteralExpression) right);
      if (lit.isPresent()) {
        return Optional.of(convertLR.apply(name, lit.get()));
      }
    } else if (left instanceof ValueLiteralExpression
        && right instanceof FieldReferenceExpression) {
      Optional<Object> lit = convertLiteral((ValueLiteralExpression) left);
      String name = ((FieldReferenceExpression) right).getName();
      if (lit.isPresent()) {
        return Optional.of(convertRL.apply(name, lit.get()));
      }
    }

    return Optional.empty();
  }
}
