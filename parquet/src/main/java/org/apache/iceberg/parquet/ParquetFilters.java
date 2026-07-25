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
package org.apache.iceberg.parquet;

import java.nio.ByteBuffer;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expression.Operation;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.ExpressionVisitors.ExpressionVisitor;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.parquet.filter2.compat.FilterCompat;
import org.apache.parquet.filter2.predicate.FilterApi;
import org.apache.parquet.filter2.predicate.FilterPredicate;
import org.apache.parquet.filter2.predicate.Operators;
import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Iceberg 表达式到 Parquet FilterPredicate 的转换器。
 *
 * <p>所属模块：iceberg-parquet（过滤下推，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：将 Iceberg {@link Expression} 转换为 Parquet {@link FilterPredicate}， 使 Parquet
 * 读取器能在解码前过滤行组内数据，减少不必要的值解码。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问器模式：通过 {@link ConvertFilterToParquet}（继承 ExpressionVisitor）遍历表达式树， 将每个谓词转换为对应的 FilterApi
 *       调用。
 *   <li>常量折叠：and/or/not 中处理 AlwaysTrue/AlwaysFalse 占位符，简化最终谓词。
 *   <li>类型分派：按 Iceberg 类型 ID 选择 Parquet 列类型（intColumn/longColumn/binaryColumn 等）。
 * </ul>
 *
 * <p>上下游关系：被 Parquet 读取流程调用，将用户过滤条件下推到 Parquet 层； 依赖 FilterApi（Parquet 过滤
 * API）、ExpressionVisitors（表达式遍历）。
 */
class ParquetFilters {

  private ParquetFilters() {}

  /**
   * 将 Iceberg 表达式转换为 Parquet FilterCompat.Filter。
   *
   * <p>逻辑：通过 ConvertFilterToParquet 访问器遍历表达式得到 FilterPredicate； 若结果为 null 或 AlwaysTrue 则返回
   * NOOP（不过滤），否则包装为 FilterCompat。
   *
   * @param schema Iceberg schema
   * @param expr Iceberg 过滤表达式
   * @param caseSensitive 字段名匹配是否大小写敏感
   * @return Parquet 兼容的过滤器
   */
  static FilterCompat.Filter convert(Schema schema, Expression expr, boolean caseSensitive) {
    FilterPredicate pred =
        ExpressionVisitors.visit(expr, new ConvertFilterToParquet(schema, caseSensitive));
    // TODO: handle AlwaysFalse.INSTANCE
    if (pred != null && pred != AlwaysTrue.INSTANCE) {
      // FilterCompat will apply LogicalInverseRewriter
      return FilterCompat.get(pred);
    } else {
      return FilterCompat.NOOP;
    }
  }

  /**
   * 表达式转换访问器：将 Iceberg 谓词逐个转为 Parquet FilterPredicate。
   *
   * <p>设计要点：and/or/not 中做常量折叠（AlwaysTrue/AlwaysFalse）； predicate 方法按 Iceberg 类型选择 Parquet 列类型和比较操作。
   */
  private static class ConvertFilterToParquet extends ExpressionVisitor<FilterPredicate> {
    private final Schema schema;
    private final boolean caseSensitive;

    private ConvertFilterToParquet(Schema schema, boolean caseSensitive) {
      this.schema = schema;
      this.caseSensitive = caseSensitive;
    }

    @Override
    public FilterPredicate alwaysTrue() {
      return AlwaysTrue.INSTANCE;
    }

    @Override
    public FilterPredicate alwaysFalse() {
      return AlwaysFalse.INSTANCE;
    }

    @Override
    public FilterPredicate not(FilterPredicate child) {
      if (child == AlwaysTrue.INSTANCE) {
        return AlwaysFalse.INSTANCE;
      } else if (child == AlwaysFalse.INSTANCE) {
        return AlwaysTrue.INSTANCE;
      }
      return FilterApi.not(child);
    }

    @Override
    public FilterPredicate and(FilterPredicate left, FilterPredicate right) {
      if (left == AlwaysFalse.INSTANCE || right == AlwaysFalse.INSTANCE) {
        return AlwaysFalse.INSTANCE;
      } else if (left == AlwaysTrue.INSTANCE) {
        return right;
      } else if (right == AlwaysTrue.INSTANCE) {
        return left;
      }
      return FilterApi.and(left, right);
    }

    @Override
    public FilterPredicate or(FilterPredicate left, FilterPredicate right) {
      if (left == AlwaysTrue.INSTANCE || right == AlwaysTrue.INSTANCE) {
        return AlwaysTrue.INSTANCE;
      } else if (left == AlwaysFalse.INSTANCE) {
        return right;
      } else if (right == AlwaysFalse.INSTANCE) {
        return left;
      }
      return FilterApi.or(left, right);
    }

    protected Expression bind(UnboundPredicate<?> pred) {
      return pred.bind(schema.asStruct(), caseSensitive);
    }

    /**
     * 转换绑定谓词：按 Iceberg 类型选择 Parquet 列类型和 FilterApi 操作。
     *
     * <p>逻辑：从 ref 获取字段路径，按 typeId 选择 booleanColumn/intColumn/longColumn 等， 然后调用 {@link
     * #pred(Operation, COL, Comparable)} 生成具体 FilterPredicate。
     *
     * @param pred 绑定后的谓词
     * @return Parquet FilterPredicate
     * @throws UnsupportedOperationException 若谓词不可转换
     */
    @Override
    public <T> FilterPredicate predicate(BoundPredicate<T> pred) {
      if (!(pred.term() instanceof BoundReference)) {
        throw new UnsupportedOperationException(
            "Cannot convert non-reference to Parquet filter: " + pred.term());
      }

      Operation op = pred.op();
      BoundReference<T> ref = (BoundReference<T>) pred.term();
      String path = schema.idToAlias(ref.fieldId());
      Literal<T> lit;
      if (pred.isUnaryPredicate()) {
        lit = null;
      } else if (pred.isLiteralPredicate()) {
        lit = pred.asLiteralPredicate().literal();
      } else {
        throw new UnsupportedOperationException("Cannot convert to Parquet filter: " + pred);
      }

      switch (ref.type().typeId()) {
        case BOOLEAN:
          Operators.BooleanColumn col = FilterApi.booleanColumn(path);
          switch (op) {
            case EQ:
              return FilterApi.eq(col, getParquetPrimitive(lit));
            case NOT_EQ:
              return FilterApi.notEq(col, getParquetPrimitive(lit));
          }
          break;
        case INTEGER:
        case DATE:
          return pred(op, FilterApi.intColumn(path), getParquetPrimitive(lit));
        case LONG:
        case TIME:
        case TIMESTAMP:
          return pred(op, FilterApi.longColumn(path), getParquetPrimitive(lit));
        case FLOAT:
          return pred(op, FilterApi.floatColumn(path), getParquetPrimitive(lit));
        case DOUBLE:
          return pred(op, FilterApi.doubleColumn(path), getParquetPrimitive(lit));
        case STRING:
        case UUID:
        case FIXED:
        case BINARY:
        case DECIMAL:
          return pred(op, FilterApi.binaryColumn(path), getParquetPrimitive(lit));
      }

      throw new UnsupportedOperationException("Cannot convert to Parquet filter: " + pred);
    }

    @Override
    public <T> FilterPredicate predicate(UnboundPredicate<T> pred) {
      Expression bound = bind(pred);
      if (bound instanceof BoundPredicate) {
        return predicate((BoundPredicate<?>) bound);
      } else if (bound == Expressions.alwaysTrue()) {
        return AlwaysTrue.INSTANCE;
      } else if (bound == Expressions.alwaysFalse()) {
        return AlwaysFalse.INSTANCE;
      }
      throw new UnsupportedOperationException("Cannot convert to Parquet filter: " + pred);
    }
  }

  /**
   * 按 Operation 生成 Parquet FilterPredicate（eq/notEq/gt/gtEq/lt/ltEq/isNull/notNull/isNaN/notNaN）。
   *
   * @param op 操作类型
   * @param col Parquet 列
   * @param value 比较值
   * @return FilterPredicate
   * @throws UnsupportedOperationException 若操作不受支持
   */
  @SuppressWarnings("checkstyle:MethodTypeParameterName")
  private static <C extends Comparable<C>, COL extends Operators.Column<C> & Operators.SupportsLtGt>
      FilterPredicate pred(Operation op, COL col, C value) {
    switch (op) {
      case IS_NULL:
        return FilterApi.eq(col, null);
      case NOT_NULL:
        return FilterApi.notEq(col, null);
      case IS_NAN:
        if (col.getColumnType().equals(Double.class)) {
          return FilterApi.eq(col, (C) (Double) Double.NaN);
        } else if (col.getColumnType().equals(Float.class)) {
          return FilterApi.eq(col, (C) (Float) Float.NaN);
        } else {
          return AlwaysFalse.INSTANCE;
        }
      case NOT_NAN:
        if (col.getColumnType().equals(Double.class)) {
          return FilterApi.notEq(col, (C) (Double) Double.NaN);
        } else if (col.getColumnType().equals(Float.class)) {
          return FilterApi.notEq(col, (C) (Float) Float.NaN);
        } else {
          return AlwaysTrue.INSTANCE;
        }
      case EQ:
        return FilterApi.eq(col, value);
      case NOT_EQ:
        return FilterApi.notEq(col, value);
      case GT:
        return FilterApi.gt(col, value);
      case GT_EQ:
        return FilterApi.gtEq(col, value);
      case LT:
        return FilterApi.lt(col, value);
      case LT_EQ:
        return FilterApi.ltEq(col, value);
      default:
        throw new UnsupportedOperationException("Unsupported predicate operation: " + op);
    }
  }

  /**
   * 将 Iceberg Literal 值转为 Parquet 可比较的原始值。
   *
   * <p>逻辑：Number 直接返回，CharSequence 转为 Binary（fromString），ByteBuffer 转为 Binary。
   *
   * @param lit Iceberg Literal
   * @return Parquet 可比较值
   * @throws UnsupportedOperationException 若类型不支持
   */
  @SuppressWarnings("unchecked")
  private static <C extends Comparable<C>> C getParquetPrimitive(Literal<?> lit) {
    if (lit == null) {
      return null;
    }

    // TODO: this needs to convert to handle BigDecimal and UUID
    Object value = lit.value();
    if (value instanceof Number) {
      return (C) lit.value();
    } else if (value instanceof CharSequence) {
      return (C) Binary.fromString(value.toString());
    } else if (value instanceof ByteBuffer) {
      return (C) Binary.fromReusedByteBuffer((ByteBuffer) value);
    }
    throw new UnsupportedOperationException(
        "Type not supported yet: " + value.getClass().getName());
  }

  /** AlwaysTrue 占位符：表示恒真谓词，不生成实际 FilterPredicate。 */
  private static class AlwaysTrue implements FilterPredicate {
    static final AlwaysTrue INSTANCE = new AlwaysTrue();

    @Override
    public <R> R accept(Visitor<R> visitor) {
      throw new UnsupportedOperationException("AlwaysTrue is a placeholder only");
    }
  }

  /** AlwaysFalse 占位符：表示恒假谓词，不生成实际 FilterPredicate。 */
  private static class AlwaysFalse implements FilterPredicate {
    static final AlwaysFalse INSTANCE = new AlwaysFalse();

    @Override
    public <R> R accept(Visitor<R> visitor) {
      throw new UnsupportedOperationException("AlwaysTrue is a placeholder only");
    }
  }
}
