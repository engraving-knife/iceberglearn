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

import static org.apache.iceberg.expressions.Expressions.rewriteNot;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.NaNUtil;

/**
 * DataFile 级严格求值器：基于文件列指标（value/null/NaN 计数、上下界）判定文件中 <b>所有行</b> 是否必然满足过滤条件。
 *
 * <p>所属模块：iceberg-api（表达式体系在文件级指标求值场景的另一个具体应用；与 {@link ManifestEvaluator} 的“包含性”语义相反，本类为“严格”语义）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把未绑定表达式先做 not 重写并绑定到 schema。
 *   <li>对每个 {@link ContentFile} 调用 {@link #eval(ContentFile)}，结合该文件的
 *       valueCounts/nullCounts/nanCounts/lowerBounds/upperBounds 判定是否“所有行都匹配”。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>“严格”语义：只有能保证所有行都匹配时才返回 true；不确定时返回 false。常用于 “可安全删除文件”等需要保证不误删的场景。
 *   <li>对 NaN 单独处理：ORC 等格式在首值为 NaN 时会把上下界都报为 NaN，因此显式检查 NaN 计数与下界是否为 NaN，避免误判。
 *   <li>不支持变换 term（如 bucket(x)）：handleNonReference 直接返回 false。
 * </ul>
 *
 * <p>上下游关系：被 core 模块在“严格文件裁剪/删除”等场景调用；与 {@link InclusiveMetricsEvaluator} （在 core 中）配对使用。
 */
public class StrictMetricsEvaluator {
  private final Schema schema;
  private final StructType struct;
  private final Expression expr;

  /**
   * 构造严格求值器（默认大小写敏感）。
   *
   * @param schema 表 schema
   * @param unbound 未绑定表达式
   */
  public StrictMetricsEvaluator(Schema schema, Expression unbound) {
    this(schema, unbound, true);
  }

  /**
   * 构造严格求值器。
   *
   * <p>逻辑：保存 schema 与 struct，对未绑定表达式做 not 重写并绑定到 struct。
   *
   * @param schema 表 schema
   * @param unbound 未绑定表达式
   * @param caseSensitive 是否大小写敏感
   */
  public StrictMetricsEvaluator(Schema schema, Expression unbound, boolean caseSensitive) {
    this.schema = schema;
    this.struct = schema.asStruct();
    this.expr = Binder.bind(struct, rewriteNot(unbound), caseSensitive);
  }

  /**
   * 判定文件中所有行是否必然匹配表达式。
   *
   * <p>语义：返回 true 表示文件所有行都匹配；返回 false 表示可能有不匹配的行 （不能保证全部匹配）。
   *
   * @param file 数据文件
   * @return false 表示可能有不匹配行，true 表示所有行必然匹配
   */
  public boolean eval(ContentFile<?> file) {
    // TODO: detect the case where a column is missing from the file using file's max field id.
    return new MetricsEvalVisitor().eval(file);
  }

  private static final boolean ROWS_MUST_MATCH = true;
  private static final boolean ROWS_MIGHT_NOT_MATCH = false;

  /**
   * 严格指标求值访问器：基于文件级指标对已绑定表达式做严格布尔求值。
   *
   * <p>设计意图：继承 {@link BoundExpressionVisitor}，按操作分派；每个谓词方法利用 文件的 value/null/NaN
   * 计数与上下界判定“所有行是否必然匹配”。
   */
  private class MetricsEvalVisitor extends BoundExpressionVisitor<Boolean> {
    private Map<Integer, Long> valueCounts = null;
    private Map<Integer, Long> nullCounts = null;
    private Map<Integer, Long> nanCounts = null;
    private Map<Integer, ByteBuffer> lowerBounds = null;
    private Map<Integer, ByteBuffer> upperBounds = null;

    /**
     * 对一个文件求值。
     *
     * <p>逻辑：recordCount &lt;= 0 视为空文件，所有行（无）必然匹配；否则缓存各项指标 并委托 {@link
     * ExpressionVisitors#visitEvaluator} 遍历表达式求值。
     *
     * @param file 数据文件
     * @return 是否所有行必然匹配
     */
    private boolean eval(ContentFile<?> file) {
      if (file.recordCount() <= 0) {
        return ROWS_MUST_MATCH;
      }

      this.valueCounts = file.valueCounts();
      this.nullCounts = file.nullValueCounts();
      this.nanCounts = file.nanValueCounts();
      this.lowerBounds = file.lowerBounds();
      this.upperBounds = file.upperBounds();

      return ExpressionVisitors.visitEvaluator(expr, this);
    }

    /**
     * 处理非直接引用的 term（如变换）：保守返回可能不匹配。
     *
     * <p>设计要点：本求值器基于数据指标而非分区值，无法判断变换结果，故一律返回 false。
     *
     * @param term 绑定 term
     * @param <T> 值类型
     * @return 恒为可能不匹配
     */
    @Override
    public <T> Boolean handleNonReference(Bound<T> term) {
      // If the term in any expression is not a direct reference, assume that rows may not match.
      // This happens when
      // transforms or other expressions are passed to this evaluator. For example, bucket16(x) = 0
      // can't be determined
      // because this visitor operates on data metrics and not partition values. It may be possible
      // to un-transform
      // expressions for order preserving transforms in the future, but this is not currently
      // supported.
      return ROWS_MIGHT_NOT_MATCH;
    }

    @Override
    public Boolean alwaysTrue() {
      return ROWS_MUST_MATCH; // all rows match
    }

    @Override
    public Boolean alwaysFalse() {
      return ROWS_MIGHT_NOT_MATCH; // no rows match
    }

    @Override
    public Boolean not(Boolean result) {
      return !result;
    }

    @Override
    public Boolean and(Boolean leftResult, Boolean rightResult) {
      return leftResult && rightResult;
    }

    @Override
    public Boolean or(Boolean leftResult, Boolean rightResult) {
      return leftResult || rightResult;
    }

    /**
     * 判定 IS_NULL 是否对所有行成立。
     *
     * <p>逻辑：仅当该字段全为 null（值计数等于 null 计数）时返回必然匹配；否则可能不匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean isNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has any non-null values, the expression does not match
      int id = ref.fieldId();
      Preconditions.checkNotNull(
          struct.field(id), "Cannot filter by nested column: %s", schema.findField(id));

      if (containsNullsOnly(id)) {
        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 NOT_NULL 是否对所有行成立。
     *
     * <p>逻辑：仅当 null 计数为 0 时返回必然匹配；否则可能不匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has any null values, the expression does not match
      int id = ref.fieldId();
      Preconditions.checkNotNull(
          struct.field(id), "Cannot filter by nested column: %s", schema.findField(id));

      if (nullCounts != null && nullCounts.containsKey(id) && nullCounts.get(id) == 0) {
        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 IS_NAN 是否对所有行成立。
     *
     * <p>逻辑：仅当该字段全为 NaN（NaN 计数等于值计数）时返回必然匹配；否则可能不匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      int id = ref.fieldId();

      if (containsNaNsOnly(id)) {
        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 NOT_NAN 是否对所有行成立。
     *
     * <p>逻辑：NaN 计数为 0 或该字段全为 null 时返回必然匹配；否则可能不匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      int id = ref.fieldId();

      if (nanCounts != null && nanCounts.containsKey(id) && nanCounts.get(id) == 0) {
        return ROWS_MUST_MATCH;
      }

      if (containsNullsOnly(id)) {
        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 LT（ref &lt; lit）是否对所有行成立。
     *
     * <p>逻辑：若字段可能含 null 或 NaN 则不能保证；否则当上界 &lt; lit 时所有值都 &lt; lit， 必然匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when: <----------Min----Max---X------->
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp < 0) {
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 LT_EQ（ref &lt;= lit）是否对所有行成立。
     *
     * <p>逻辑：当上界 &lt;= lit 时必然匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when: <----------Min----Max---X------->
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp <= 0) {
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 GT（ref &gt; lit）是否对所有行成立。
     *
     * <p>逻辑：当下界 &gt; lit 时所有值都 &gt; lit，必然匹配。下界为 NaN 视为不可靠。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when: <-------X---Min----Max---------->
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(field.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the StrictMetricsEvaluator docs for more.
          return ROWS_MIGHT_NOT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 GT_EQ（ref &gt;= lit）是否对所有行成立。
     *
     * <p>逻辑：当下界 &gt;= lit 时必然匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when: <-------X---Min----Max---------->
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(field.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the StrictMetricsEvaluator docs for more.
          return ROWS_MIGHT_NOT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp >= 0) {
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 EQ（ref == lit）是否对所有行成立。
     *
     * <p>逻辑：当上下界都等于 lit（即 Min == X == Max）时必然匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when Min == X == Max
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (lowerBounds != null
          && lowerBounds.containsKey(id)
          && upperBounds != null
          && upperBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(struct.field(id).type(), lowerBounds.get(id));

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp != 0) {
          return ROWS_MIGHT_NOT_MATCH;
        }

        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));

        cmp = lit.comparator().compare(upper, lit.value());
        if (cmp != 0) {
          return ROWS_MIGHT_NOT_MATCH;
        }

        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 NOT_EQ（ref != lit）是否对所有行成立。
     *
     * <p>逻辑：当 lit 落在 [lower, upper] 之外（X &lt; Min 或 Max &lt; X）时必然匹配； 全 null 或全 NaN
     * 也视为必然匹配（不等于任何字面量）。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
      // Rows must match when X < Min or Max < X because it is not in the range
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_MUST_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(struct.field(id).type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the StrictMetricsEvaluator docs for more.
          return ROWS_MIGHT_NOT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_MUST_MATCH;
        }
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp < 0) {
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 IN 是否对所有行成立。
     *
     * <p>逻辑：上下界都在集合中且相等时（即所有值相同且属于集合）必然匹配。
     *
     * @param ref 绑定引用
     * @param literalSet 字面量集合
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (canContainNulls(id) || canContainNaNs(id)) {
        return ROWS_MIGHT_NOT_MATCH;
      }

      if (lowerBounds != null
          && lowerBounds.containsKey(id)
          && upperBounds != null
          && upperBounds.containsKey(id)) {
        // similar to the implementation in eq, first check if the lower bound is in the set
        T lower = Conversions.fromByteBuffer(struct.field(id).type(), lowerBounds.get(id));
        if (!literalSet.contains(lower)) {
          return ROWS_MIGHT_NOT_MATCH;
        }

        // check if the upper bound is in the set
        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));
        if (!literalSet.contains(upper)) {
          return ROWS_MIGHT_NOT_MATCH;
        }

        // finally check if the lower bound and the upper bound are equal
        if (ref.comparator().compare(lower, upper) != 0) {
          return ROWS_MIGHT_NOT_MATCH;
        }

        // All values must be in the set if the lower bound and the upper bound are in the set and
        // are equal.
        return ROWS_MUST_MATCH;
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 NOT_IN 是否对所有行成立。
     *
     * <p>逻辑：先用上下界过滤集合，若过滤后集合为空（即所有元素都在 [lower, upper] 之外） 则必然匹配；否则可能不匹配。
     *
     * @param ref 绑定引用
     * @param literalSet 字面量集合
     * @param <T> 值类型
     * @return 是否所有行必然匹配
     */
    @Override
    public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
      Integer id = ref.fieldId();
      Types.NestedField field = struct.field(id);
      Preconditions.checkNotNull(field, "Cannot filter by nested column: %s", schema.findField(id));

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_MUST_MATCH;
      }

      Collection<T> literals = literalSet;

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(struct.field(id).type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the StrictMetricsEvaluator docs for more.
          return ROWS_MIGHT_NOT_MATCH;
        }

        literals =
            literals.stream()
                .filter(v -> ref.comparator().compare(lower, v) <= 0)
                .collect(Collectors.toList());
        if (literals
            .isEmpty()) { // if all values are less than lower bound, rows must match (notIn).
          return ROWS_MUST_MATCH;
        }
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(field.type(), upperBounds.get(id));
        literals =
            literals.stream()
                .filter(v -> ref.comparator().compare(upper, v) >= 0)
                .collect(Collectors.toList());
        if (literals
            .isEmpty()) { // if all remaining values are greater than upper bound, rows must match
          // (notIn).
          return ROWS_MUST_MATCH;
        }
      }

      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 STARTS_WITH 是否对所有行成立。
     *
     * <p>逻辑：基于列指标无法保证所有值都以某前缀开头，保守返回可能不匹配。
     *
     * @param ref 绑定引用
     * @param lit 前缀字面量
     * @param <T> 值类型
     * @return 恒为可能不匹配
     */
    @Override
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判定 NOT_STARTS_WITH 是否对所有行成立。
     *
     * <p>逻辑：暂未实现基于上下界的精确判定，保守返回可能不匹配。
     *
     * @param ref 绑定引用
     * @param lit 前缀字面量
     * @param <T> 值类型
     * @return 恒为可能不匹配
     */
    @Override
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      // TODO: Handle cases that definitely cannot match, such as notStartsWith("x") when the bounds
      // are ["a", "b"].
      return ROWS_MIGHT_NOT_MATCH;
    }

    /**
     * 判断该字段是否可能含 null。
     *
     * <p>逻辑：nullCounts 为 null（未知）视为可能；否则 nullCounts[id] &gt; 0 视为可能。
     *
     * @param id 字段 id
     * @return 可能含 null 返回 true
     */
    private boolean canContainNulls(Integer id) {
      return nullCounts == null || (nullCounts.containsKey(id) && nullCounts.get(id) > 0);
    }

    /**
     * 判断该字段是否可能含 NaN。
     *
     * <p>逻辑：nanCounts 为 null（早期写入器未统计）视为不可能；否则 nanCounts[id] &gt; 0 视为可能。
     *
     * @param id 字段 id
     * @return 可能含 NaN 返回 true
     */
    private boolean canContainNaNs(Integer id) {
      // nan counts might be null for early version writers when nan counters are not populated.
      return nanCounts != null && nanCounts.containsKey(id) && nanCounts.get(id) > 0;
    }

    /**
     * 判断该字段是否全部为 null。
     *
     * <p>逻辑：值计数与 null 计数都存在且 valueCount - nullCount == 0 时为全 null。
     *
     * @param id 字段 id
     * @return 全 null 返回 true
     */
    private boolean containsNullsOnly(Integer id) {
      return valueCounts != null
          && valueCounts.containsKey(id)
          && nullCounts != null
          && nullCounts.containsKey(id)
          && valueCounts.get(id) - nullCounts.get(id) == 0;
    }

    /**
     * 判断该字段是否全部为 NaN。
     *
     * <p>逻辑：nanCounts 与 valueCounts 都存在且 nanCount == valueCount 时为全 NaN。
     *
     * @param id 字段 id
     * @return 全 NaN 返回 true
     */
    private boolean containsNaNsOnly(Integer id) {
      return nanCounts != null
          && nanCounts.containsKey(id)
          && valueCounts != null
          && nanCounts.get(id).equals(valueCounts.get(id));
    }
  }
}
