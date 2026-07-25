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
package org.apache.iceberg.expressions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 表达式工具集：提供表达式脱敏、等价判定、分区选择判定、term 描述与解绑等通用静态方法。
 *
 * <p>所属模块：iceberg-api（表达式体系的对外工具门面；既被 api 内部使用，也被 core/引擎 模块用于日志脱敏、过滤条件比较等场景）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@code sanitize*}：把表达式中的具体值替换为描述（数字按位数、字符串按 hash、 日期/时间按相对描述），用于日志/审计脱敏。
 *   <li>{@code equivalent}：判定两个未绑定表达式在绑定到同一 struct 后是否语义等价。
 *   <li>{@code selectsPartitions}：判定表达式是否在某分区 spec 下选择完整分区 （inclusive 投影与 strict 投影等价即视为整分区选择）。
 *   <li>{@code describe} / {@code unbind}：生成 term 可读描述、把 BoundTerm 还原为 UnboundTerm。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>脱敏采用“相对时间”描述（如 (date-3-days-ago)），既隐藏真实值又保留语义信息， 便于排查过滤条件。
 *   <li>StringSanitizer 直接产出可读字符串，避免先建表达式再 toString 的开销。
 *   <li>abbreviateValues 在长 IN 列表中去重并隐藏重复值，控制日志体积。
 * </ul>
 *
 * <p>上下游关系：被 {@link AggregateEvaluator}、core 模块日志与 metric 输出、引擎层 过滤条件打印等调用。
 */
public class ExpressionUtil {
  // 字符串脱敏用的 hash 函数：bucket(MAX_VALUE) 绑定到 StringType，结果为稳定整数哈希。
  private static final Function<Object, Integer> HASH_FUNC =
      Transforms.bucket(Integer.MAX_VALUE).bind(Types.StringType.get());
  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final long FIVE_MINUTES_IN_MICROS = TimeUnit.MINUTES.toMicros(5);
  private static final long THREE_DAYS_IN_HOURS = TimeUnit.DAYS.toHours(3);
  private static final long NINETY_DAYS_IN_HOURS = TimeUnit.DAYS.toHours(90);
  // 以下正则用于在字符串值中识别日期/时间格式，以便按时间语义脱敏。
  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
  private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2}(.\\d{1,9})?)?");
  private static final Pattern TIMESTAMP =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(.\\d{1,9})?)?");
  private static final Pattern TIMESTAMPTZ =
      Pattern.compile(
          "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(.\\d{1,9})?)?([-+]\\d{2}:\\d{2}|Z)");
  /** 长 IN 谓词触发缩写显示的元素数阈值。 */
  static final int LONG_IN_PREDICATE_ABBREVIATION_THRESHOLD = 10;

  private static final int LONG_IN_PREDICATE_ABBREVIATION_MIN_GAIN = 5;

  private ExpressionUtil() {}

  /**
   * 对未绑定表达式做脱敏：保留结构，把值替换为描述（数字按位数、字符串按 hash、日期/时间按相对描述）。
   *
   * @param expr 待脱敏表达式
   * @return 脱敏后的未绑定表达式
   */
  public static Expression sanitize(Expression expr) {
    return ExpressionVisitors.visit(expr, new ExpressionSanitizer());
  }

  /**
   * 对表达式做脱敏（先绑定到 struct 再脱敏）。
   *
   * <p>逻辑：尝试用 {@link Binder#bind} 绑定后由 {@link ExpressionSanitizer} 脱敏；
   * 若绑定失败（如字段不存在）则回退到对未绑定表达式脱敏，保证调用方总能拿到结果。
   *
   * @param struct 用于绑定的 StructType
   * @param expr 待脱敏表达式
   * @param caseSensitive 是否大小写敏感绑定
   * @return 脱敏后的表达式
   */
  public static Expression sanitize(
      Types.StructType struct, Expression expr, boolean caseSensitive) {
    try {
      Expression bound = Binder.bind(struct, expr, caseSensitive);
      return ExpressionVisitors.visit(bound, new ExpressionSanitizer());
    } catch (RuntimeException e) {
      // if the expression cannot be bound, sanitize the unbound version
      return ExpressionVisitors.visit(expr, new ExpressionSanitizer());
    }
  }

  /**
   * 直接产出脱敏后的可读字符串（未绑定版本）。
   *
   * @param expr 待脱敏表达式
   * @return 脱敏后的字符串
   */
  public static String toSanitizedString(Expression expr) {
    return ExpressionVisitors.visit(expr, new StringSanitizer());
  }

  /**
   * 直接产出脱敏后的可读字符串（先绑定再脱敏）。
   *
   * <p>逻辑：与 {@link #sanitize(Types.StructType, Expression, boolean)} 同样先尝试绑定， 绑定失败则回退到未绑定脱敏。
   *
   * @param struct 用于绑定的 StructType
   * @param expr 待脱敏表达式
   * @param caseSensitive 是否大小写敏感绑定
   * @return 脱敏后的字符串
   */
  public static String toSanitizedString(
      Types.StructType struct, Expression expr, boolean caseSensitive) {
    try {
      Expression bound = Binder.bind(struct, expr, caseSensitive);
      return ExpressionVisitors.visit(bound, new StringSanitizer());
    } catch (RuntimeException e) {
      // if the expression cannot be bound, sanitize the unbound version
      return ExpressionVisitors.visit(expr, new StringSanitizer());
    }
  }

  /**
   * 从过滤表达式中抽取仅引用给定列 id 的子表达式（inclusive 语义）。
   *
   * <p>语义：结果是“包含性”的——若一行满足原过滤，则必满足结果过滤。实现上构造一个 仅含这些列的 identity 分区 spec，并用 {@link
   * Projections#inclusive} 投影得到。
   *
   * @param expression 原过滤表达式
   * @param schema 表 schema
   * @param caseSensitive 绑定是否大小写敏感
   * @param ids 需要保留的字段 id
   * @return 仅引用指定列的包含性过滤表达式
   */
  public static Expression extractByIdInclusive(
      Expression expression, Schema schema, boolean caseSensitive, int... ids) {
    PartitionSpec spec = identitySpec(schema, ids);
    return Projections.inclusive(spec, caseSensitive).project(Expressions.rewriteNot(expression));
  }

  /**
   * 判定两个未绑定表达式是否语义等价。
   *
   * <p>逻辑：先对两边都做 not 重写并绑定到同一 struct，再调用 {@link Expression#isEquivalentTo} 比较。返回 true 保证等价；返回 false
   * 不保证不等价。
   *
   * @param left 左侧未绑定表达式
   * @param right 右侧未绑定表达式
   * @param struct 用于绑定的 struct 类型
   * @param caseSensitive 是否大小写敏感绑定
   * @return 确定等价返回 true
   */
  public static boolean equivalent(
      Expression left, Expression right, Types.StructType struct, boolean caseSensitive) {
    return Binder.bind(struct, Expressions.rewriteNot(left), caseSensitive)
        .isEquivalentTo(Binder.bind(struct, Expressions.rewriteNot(right), caseSensitive));
  }

  /**
   * 判定表达式是否在表的所有分区 spec 下都选择完整分区。
   *
   * @param expr 未绑定表达式
   * @param table 表
   * @param caseSensitive 绑定是否大小写敏感
   * @return 所有 spec 下都整分区选择返回 true
   */
  public static boolean selectsPartitions(Expression expr, Table table, boolean caseSensitive) {
    return table.specs().values().stream()
        .allMatch(spec -> selectsPartitions(expr, spec, caseSensitive));
  }

  /**
   * 判定表达式是否在指定分区 spec 下选择完整分区。
   *
   * <p>逻辑：当 inclusive 投影与 strict 投影等价时，说明过滤边界恰好与分区边界对齐， 即选择的是完整分区而非部分分区。例如 ts &lt;
   * '2021-03-09T10:00:00.000' 在 [hours(ts)] spec 下不整分区选择，而在 [days(ts)] 下可能整分区选择。
   *
   * @param expr 未绑定表达式
   * @param spec 分区 spec
   * @param caseSensitive 绑定是否大小写敏感
   * @return 整分区选择返回 true
   */
  public static boolean selectsPartitions(
      Expression expr, PartitionSpec spec, boolean caseSensitive) {
    return equivalent(
        Projections.inclusive(spec, caseSensitive).project(expr),
        Projections.strict(spec, caseSensitive).project(expr),
        spec.partitionType(),
        caseSensitive);
  }

  /**
   * 生成 term 的可读描述字符串。
   *
   * <p>逻辑：按 term 类型分派——变换 term 显示为 "transform(ref)"，命名/绑定引用显示字段名； 不支持的 term 抛 {@link
   * UnsupportedOperationException}。
   *
   * @param term 待描述的 term
   * @return 可读描述
   */
  public static String describe(Term term) {
    if (term instanceof UnboundTransform) {
      return ((UnboundTransform<?, ?>) term).transform()
          + "("
          + describe(((UnboundTransform<?, ?>) term).ref())
          + ")";
    } else if (term instanceof BoundTransform) {
      return ((BoundTransform<?, ?>) term).transform()
          + "("
          + describe(((BoundTransform<?, ?>) term).ref())
          + ")";
    } else if (term instanceof NamedReference) {
      return ((NamedReference<?>) term).name();
    } else if (term instanceof BoundReference) {
      return ((BoundReference<?>) term).name();
    } else {
      throw new UnsupportedOperationException("Unsupported term: " + term);
    }
  }

  /**
   * 把已绑定 term 还原为未绑定 term（按字段名重建引用）。
   *
   * <p>逻辑：BoundTransform 还原为 transform(refName)；BoundReference 还原为 ref(name)； 其余抛 {@link
   * UnsupportedOperationException}。
   *
   * @param term 已绑定 term
   * @param <T> term 值类型
   * @return 未绑定 term
   */
  public static <T> UnboundTerm<T> unbind(BoundTerm<T> term) {
    if (term instanceof BoundTransform) {
      BoundTransform<?, T> bound = (BoundTransform<?, T>) term;
      return Expressions.transform(bound.ref().name(), bound.transform());
    } else if (term instanceof BoundReference) {
      return Expressions.ref(((BoundReference<T>) term).name());
    }

    throw new UnsupportedOperationException("Cannot unbind unsupported term: " + term);
  }

  /**
   * 把任意 term 还原为未绑定 term（已是未绑定时直接返回）。
   *
   * @param term 任意 term
   * @param <T> term 值类型
   * @return 未绑定 term
   */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> unbind(Term term) {
    if (term instanceof UnboundTerm) {
      return (UnboundTerm<T>) term;
    } else if (term instanceof BoundTerm) {
      return unbind((BoundTerm<T>) term);
    }

    throw new UnsupportedOperationException("Cannot unbind unsupported term: " + term);
  }

  /**
   * 表达式脱敏访问器：产出结构相同、值被替换为描述的未绑定表达式。
   *
   * <p>设计意图：在构造时记录当前时间（now 微秒、today 天），用于日期/时间值的相对描述。 布尔逻辑节点保持结构，只在叶子谓词处替换字面量。
   */
  private static class ExpressionSanitizer
      extends ExpressionVisitors.ExpressionVisitor<Expression> {
    private final long now;
    private final int today;

    private ExpressionSanitizer() {
      long nowMillis = System.currentTimeMillis();
      OffsetDateTime nowDateTime = Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.UTC);
      this.now = nowMillis * 1000;
      this.today = (int) ChronoUnit.DAYS.between(EPOCH, nowDateTime);
    }

    @Override
    public Expression alwaysTrue() {
      return Expressions.alwaysTrue();
    }

    @Override
    public Expression alwaysFalse() {
      return Expressions.alwaysFalse();
    }

    @Override
    public Expression not(Expression result) {
      return Expressions.not(result);
    }

    @Override
    public Expression and(Expression leftResult, Expression rightResult) {
      return Expressions.and(leftResult, rightResult);
    }

    @Override
    public Expression or(Expression leftResult, Expression rightResult) {
      return Expressions.or(leftResult, rightResult);
    }

    /**
     * 脱敏已绑定谓词。
     *
     * <p>逻辑：一元谓词直接保留 op 并解绑 term；字面量谓词把字面量值脱敏后重建； 集合谓词对集合中每个值脱敏后重建。
     *
     * @param pred 已绑定谓词
     * @param <T> 谓词值类型
     * @return 脱敏后的未绑定谓词
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Expression predicate(BoundPredicate<T> pred) {
      if (pred.isUnaryPredicate()) {
        // unary predicates don't need to be sanitized
        return new UnboundPredicate<>(pred.op(), unbind(pred.term()));
      } else if (pred.isLiteralPredicate()) {
        BoundLiteralPredicate<T> bound = (BoundLiteralPredicate<T>) pred;
        return new UnboundPredicate<>(
            pred.op(),
            unbind(pred.term()),
            (T) sanitize(bound.term().type(), bound.literal(), now, today));
      } else if (pred.isSetPredicate()) {
        BoundSetPredicate<T> bound = (BoundSetPredicate<T>) pred;
        Iterable<T> iter =
            () ->
                bound.literalSet().stream()
                    .map(lit -> (T) sanitize(bound.term().type(), lit, now, today))
                    .iterator();
        return new UnboundPredicate<>(pred.op(), unbind(pred.term()), iter);
      }

      throw new UnsupportedOperationException("Cannot sanitize bound predicate type: " + pred.op());
    }

    /**
     * 脱敏未绑定谓词。
     *
     * <p>逻辑：一元谓词直接返回；字面量谓词脱敏字面量后重建；集合谓词脱敏每个元素后重建。
     *
     * @param pred 未绑定谓词
     * @param <T> 谓词值类型
     * @return 脱敏后的未绑定谓词
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Expression predicate(UnboundPredicate<T> pred) {
      switch (pred.op()) {
        case IS_NULL:
        case NOT_NULL:
        case IS_NAN:
        case NOT_NAN:
          // unary predicates don't need to be sanitized
          return pred;
        case LT:
        case LT_EQ:
        case GT:
        case GT_EQ:
        case EQ:
        case NOT_EQ:
        case STARTS_WITH:
        case NOT_STARTS_WITH:
          return new UnboundPredicate<>(
              pred.op(), pred.term(), (T) sanitize(pred.literal(), now, today));
        case IN:
        case NOT_IN:
          Iterable<String> iter =
              () -> pred.literals().stream().map(lit -> sanitize(lit, now, today)).iterator();
          return new UnboundPredicate<>(pred.op(), pred.term(), (Iterable<T>) iter);
        default:
          throw new UnsupportedOperationException(
              "Cannot sanitize unsupported predicate type: " + pred.op());
      }
    }
  }

  /**
   * 字符串脱敏访问器：直接产出可读的脱敏字符串，避免先建表达式再 toString。
   *
   * <p>设计意图：与 {@link ExpressionSanitizer} 平行，但结果为 String，用于日志直接输出。
   */
  private static class StringSanitizer extends ExpressionVisitors.ExpressionVisitor<String> {
    private final long nowMicros;
    private final int today;

    private StringSanitizer() {
      long nowMillis = System.currentTimeMillis();
      OffsetDateTime nowDateTime = Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.UTC);
      this.nowMicros = nowMillis * 1000;
      this.today = (int) ChronoUnit.DAYS.between(EPOCH, nowDateTime);
    }

    @Override
    public String alwaysTrue() {
      return "true";
    }

    @Override
    public String alwaysFalse() {
      return "false";
    }

    @Override
    public String not(String result) {
      return "NOT (" + result + ")";
    }

    @Override
    public String and(String leftResult, String rightResult) {
      return "(" + leftResult + " AND " + rightResult + ")";
    }

    @Override
    public String or(String leftResult, String rightResult) {
      return "(" + leftResult + " OR " + rightResult + ")";
    }

    /** 取已绑定字面量谓词的脱敏字符串值。 */
    private String value(BoundLiteralPredicate<?> pred) {
      return sanitize(pred.term().type(), pred.literal().value(), nowMicros, today);
    }

    /**
     * 把已绑定谓词渲染为脱敏字符串。
     *
     * <p>逻辑：按 op 拼装可读形式，集合谓词额外做缩写处理。
     *
     * @param pred 已绑定谓词
     * @param <T> 谓词值类型
     * @return 脱敏字符串
     */
    @Override
    public <T> String predicate(BoundPredicate<T> pred) {
      String term = describe(pred.term());
      switch (pred.op()) {
        case IS_NULL:
          return term + " IS NULL";
        case NOT_NULL:
          return term + " IS NOT NULL";
        case IS_NAN:
          return "is_nan(" + term + ")";
        case NOT_NAN:
          return "not_nan(" + term + ")";
        case LT:
          return term + " < " + value((BoundLiteralPredicate<?>) pred);
        case LT_EQ:
          return term + " <= " + value((BoundLiteralPredicate<?>) pred);
        case GT:
          return term + " > " + value((BoundLiteralPredicate<?>) pred);
        case GT_EQ:
          return term + " >= " + value((BoundLiteralPredicate<?>) pred);
        case EQ:
          return term + " = " + value((BoundLiteralPredicate<?>) pred);
        case NOT_EQ:
          return term + " != " + value((BoundLiteralPredicate<?>) pred);
        case IN:
          return term
              + " IN "
              + abbreviateValues(
                      pred.asSetPredicate().literalSet().stream()
                          .map(lit -> sanitize(pred.term().type(), lit, nowMicros, today))
                          .collect(Collectors.toList()))
                  .stream()
                  .collect(Collectors.joining(", ", "(", ")"));
        case NOT_IN:
          return term
              + " NOT IN "
              + abbreviateValues(
                      pred.asSetPredicate().literalSet().stream()
                          .map(lit -> sanitize(pred.term().type(), lit, nowMicros, today))
                          .collect(Collectors.toList()))
                  .stream()
                  .collect(Collectors.joining(", ", "(", ")"));
        case STARTS_WITH:
          return term + " STARTS WITH " + value((BoundLiteralPredicate<?>) pred);
        case NOT_STARTS_WITH:
          return term + " NOT STARTS WITH " + value((BoundLiteralPredicate<?>) pred);
        default:
          throw new UnsupportedOperationException(
              "Cannot sanitize unsupported predicate type: " + pred.op());
      }
    }

    /**
     * 把未绑定谓词渲染为脱敏字符串。
     *
     * @param pred 未绑定谓词
     * @param <T> 谓词值类型
     * @return 脱敏字符串
     */
    @Override
    public <T> String predicate(UnboundPredicate<T> pred) {
      String term = describe(pred.term());
      switch (pred.op()) {
        case IS_NULL:
          return term + " IS NULL";
        case NOT_NULL:
          return term + " IS NOT NULL";
        case IS_NAN:
          return "is_nan(" + term + ")";
        case NOT_NAN:
          return "not_nan(" + term + ")";
        case LT:
          return term + " < " + sanitize(pred.literal(), nowMicros, today);
        case LT_EQ:
          return term + " <= " + sanitize(pred.literal(), nowMicros, today);
        case GT:
          return term + " > " + sanitize(pred.literal(), nowMicros, today);
        case GT_EQ:
          return term + " >= " + sanitize(pred.literal(), nowMicros, today);
        case EQ:
          return term + " = " + sanitize(pred.literal(), nowMicros, today);
        case NOT_EQ:
          return term + " != " + sanitize(pred.literal(), nowMicros, today);
        case IN:
          return term
              + " IN "
              + abbreviateValues(
                      pred.literals().stream()
                          .map(lit -> sanitize(lit, nowMicros, today))
                          .collect(Collectors.toList()))
                  .stream()
                  .collect(Collectors.joining(", ", "(", ")"));
        case NOT_IN:
          return term
              + " NOT IN "
              + abbreviateValues(
                      pred.literals().stream()
                          .map(lit -> sanitize(lit, nowMicros, today))
                          .collect(Collectors.toList()))
                  .stream()
                  .collect(Collectors.joining(", ", "(", ")"));
        case STARTS_WITH:
          return term + " STARTS WITH " + sanitize(pred.literal(), nowMicros, today);
        case NOT_STARTS_WITH:
          return term + " NOT STARTS WITH " + sanitize(pred.literal(), nowMicros, today);
        default:
          throw new UnsupportedOperationException(
              "Cannot sanitize unsupported predicate type: " + pred.op());
      }
    }
  }

  /**
   * 对长 IN 列表做缩写：当元素数达到阈值且去重后能减少足够多重复时，仅保留去重值并附加 "隐藏 N 个值（共 M 个）" 的提示，控制日志体积。
   *
   * @param sanitizedValues 已脱敏的值列表
   * @param <T> 列表元素类型
   * @return 缩写后的列表
   */
  private static <T> List<String> abbreviateValues(List<String> sanitizedValues) {
    if (sanitizedValues.size() >= LONG_IN_PREDICATE_ABBREVIATION_THRESHOLD) {
      Set<String> distinctValues = ImmutableSet.copyOf(sanitizedValues);
      if (distinctValues.size()
          <= sanitizedValues.size() - LONG_IN_PREDICATE_ABBREVIATION_MIN_GAIN) {
        List<String> abbreviatedList = Lists.newArrayListWithCapacity(distinctValues.size() + 1);
        abbreviatedList.addAll(distinctValues);
        abbreviatedList.add(
            String.format(
                "... (%d values hidden, %d in total)",
                sanitizedValues.size() - distinctValues.size(), sanitizedValues.size()));
        return abbreviatedList;
      }
    }
    return sanitizedValues;
  }

  /**
   * 按类型把值脱敏为描述字符串。
   *
   * <p>逻辑：整数/浮点按位数与类型；日期按相对今天；时间固定为 "(time)"；时间戳按相对现在； 字符串先尝试识别为日期/时间格式，否则按简单
   * hash；布尔/UUID/decimal/二进制按字符串 hash。
   *
   * @param type 值的类型
   * @param value 值
   * @param now 当前时间（微秒）
   * @param today 当前日期（自 epoch 起的天数）
   * @return 脱敏描述
   */
  private static String sanitize(Type type, Object value, long now, int today) {
    switch (type.typeId()) {
      case INTEGER:
      case LONG:
        return sanitizeNumber((Number) value, "int");
      case FLOAT:
      case DOUBLE:
        return sanitizeNumber((Number) value, "float");
      case DATE:
        return sanitizeDate((int) value, today);
      case TIME:
        return "(time)";
      case TIMESTAMP:
        return sanitizeTimestamp((long) value, now);
      case STRING:
        return sanitizeString((CharSequence) value, now, today);
      case BOOLEAN:
      case UUID:
      case DECIMAL:
      case FIXED:
      case BINARY:
        // for boolean, uuid, decimal, fixed, and binary, match the string result
        return sanitizeSimpleString(value.toString());
    }
    throw new UnsupportedOperationException(
        String.format("Cannot sanitize value for unsupported type %s: %s", type, value));
  }

  /**
   * 按字面量子类型把值脱敏为描述字符串。
   *
   * <p>逻辑：与 {@link #sanitize(Type, Object, long, int)} 类似，但输入是 {@link Literal}
   * 实例（未绑定谓词场景），按字面量子类型分派。
   *
   * @param literal 字面量
   * @param now 当前时间（微秒）
   * @param today 当前日期
   * @return 脱敏描述
   */
  private static String sanitize(Literal<?> literal, long now, int today) {
    if (literal instanceof Literals.StringLiteral) {
      return sanitizeString(((Literals.StringLiteral) literal).value(), now, today);
    } else if (literal instanceof Literals.DateLiteral) {
      return sanitizeDate(((Literals.DateLiteral) literal).value(), today);
    } else if (literal instanceof Literals.TimestampLiteral) {
      return sanitizeTimestamp(((Literals.TimestampLiteral) literal).value(), now);
    } else if (literal instanceof Literals.TimeLiteral) {
      return "(time)";
    } else if (literal instanceof Literals.IntegerLiteral) {
      return sanitizeNumber(((Literals.IntegerLiteral) literal).value(), "int");
    } else if (literal instanceof Literals.LongLiteral) {
      return sanitizeNumber(((Literals.LongLiteral) literal).value(), "int");
    } else if (literal instanceof Literals.FloatLiteral) {
      return sanitizeNumber(((Literals.FloatLiteral) literal).value(), "float");
    } else if (literal instanceof Literals.DoubleLiteral) {
      return sanitizeNumber(((Literals.DoubleLiteral) literal).value(), "float");
    } else {
      // for uuid, decimal, fixed, and binary, match the string result
      return sanitizeSimpleString(literal.value().toString());
    }
  }

  /**
   * 把日期值脱敏为相对今天的描述。
   *
   * <p>逻辑：今天返回 "(date-today)"；90 天内返回 "(date-N-days-ago/from-now)"； 否则返回 "(date)"。
   *
   * @param days 自 epoch 起的天数
   * @param today 当前日期
   * @return 脱敏描述
   */
  private static String sanitizeDate(int days, int today) {
    String isPast = today > days ? "ago" : "from-now";
    int diff = Math.abs(today - days);
    if (diff == 0) {
      return "(date-today)";
    } else if (diff < 90) {
      return "(date-" + diff + "-days-" + isPast + ")";
    }

    return "(date)";
  }

  /**
   * 把时间戳值脱敏为相对现在的描述。
   *
   * <p>逻辑：5 分钟内返回 "(timestamp-about-now)"；3 天内按小时；90 天内按天；否则返回 "(timestamp)"。
   *
   * @param micros 时间戳（微秒）
   * @param now 当前时间（微秒）
   * @return 脱敏描述
   */
  private static String sanitizeTimestamp(long micros, long now) {
    String isPast = now > micros ? "ago" : "from-now";
    long diff = Math.abs(now - micros);
    if (diff < FIVE_MINUTES_IN_MICROS) {
      return "(timestamp-about-now)";
    }

    long hours = TimeUnit.MICROSECONDS.toHours(diff);
    if (hours <= THREE_DAYS_IN_HOURS) {
      return "(timestamp-" + hours + "-hours-" + isPast + ")";
    } else if (hours < NINETY_DAYS_IN_HOURS) {
      long days = hours / 24;
      return "(timestamp-" + days + "-days-" + isPast + ")";
    }

    return "(timestamp)";
  }

  /**
   * 把数字脱敏为按位数与类型的描述，如 "(3-digit-int)"。
   *
   * @param value 数字值
   * @param type 类型标签（"int" 或 "float"）
   * @return 脱敏描述
   */
  private static String sanitizeNumber(Number value, String type) {
    // log10 of zero isn't defined and will result in negative infinity
    int numDigits =
        0.0d == value.doubleValue() ? 1 : (int) Math.log10(Math.abs(value.doubleValue())) + 1;
    return "(" + numDigits + "-digit-" + type + ")";
  }

  /**
   * 把字符串值脱敏：先尝试识别为日期/时间格式并按时间语义脱敏，否则按简单 hash。
   *
   * <p>设计要点：解析失败时回退到简单 hash，因为用户可能传入看似日期但实际是普通字符串的值， 不应抛异常打断脱敏流程。
   *
   * @param value 字符串值
   * @param now 当前时间（微秒）
   * @param today 当前日期
   * @return 脱敏描述
   */
  private static String sanitizeString(CharSequence value, long now, int today) {
    try {
      if (DATE.matcher(value).matches()) {
        Literal<Integer> date = Literal.of(value).to(Types.DateType.get());
        return sanitizeDate(date.value(), today);
      } else if (TIMESTAMP.matcher(value).matches()) {
        Literal<Long> ts = Literal.of(value).to(Types.TimestampType.withoutZone());
        return sanitizeTimestamp(ts.value(), now);
      } else if (TIMESTAMPTZ.matcher(value).matches()) {
        Literal<Long> ts = Literal.of(value).to(Types.TimestampType.withZone());
        return sanitizeTimestamp(ts.value(), now);
      } else if (TIME.matcher(value).matches()) {
        return "(time)";
      } else {
        return sanitizeSimpleString(value);
      }
    } catch (Exception ex) {
      // Don't throw when parsing failed in sanitizeString
      // because user could provide an invalid integer/date/timestamp string
      // and expect them to be treated as a string instead of specific type
      return sanitizeSimpleString(value);
    }
  }

  /**
   * 把字符串值脱敏为 hash 形式，如 "(hash-1a2b3c4d)"。
   *
   * @param value 字符串值
   * @return hash 形式的脱敏描述
   */
  private static String sanitizeSimpleString(CharSequence value) {
    // hash the value and return the hash as hex
    return String.format("(hash-%08x)", HASH_FUNC.apply(value));
  }

  /**
   * 基于给定字段 id 构造一个仅含 identity 分区的临时 PartitionSpec。
   *
   * <p>逻辑：按 id 查列名，对每个列添加 identity 分区字段，最终 build 出 spec。 用于 {@link #extractByIdInclusive} 的投影计算。
   *
   * @param schema 表 schema
   * @param ids 字段 id 列表
   * @return 临时构造的 identity 分区 spec
   */
  private static PartitionSpec identitySpec(Schema schema, int... ids) {
    PartitionSpec.Builder specBuilder = PartitionSpec.builderFor(schema);

    for (int id : ids) {
      specBuilder.identity(schema.findColumnName(id));
    }

    return specBuilder.build();
  }
}
