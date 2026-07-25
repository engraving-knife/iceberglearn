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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.Accessors;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.ManifestFile.PartitionFieldSummary;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.BinaryUtil;

/**
 * Manifest 文件级谓词求值器：基于 manifest 内嵌的分区字段统计（min/max/null/NaN）判定 一个 manifest 是否可能包含匹配过滤条件的文件。
 *
 * <p>所属模块：iceberg-api（表达式体系在 manifest 过滤场景的具体应用；是 Iceberg 列式 元数据驱动的文件裁剪核心之一）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对行级过滤条件先做 inclusive 投影到分区 spec，再绑定到分区类型上。
 *   <li>用 {@link ManifestEvalVisitor} 遍历已绑定表达式，结合每个分区字段的 {@link
 *       PartitionFieldSummary}（下界/上界/containsNull/containsNaN）做包含性判定： 返回 true 表示“可能匹配、不能跳过”，返回
 *       false 表示“必然不匹配、可跳过该 manifest”。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>“包含性”语义：宁可保留也不能误裁，因此任何不确定情形都返回 true。
 *   <li>对 NaN、null、越界等边界情形做单独处理，避免下界/上界为 null 时误判。
 *   <li>IN 谓词元素过多时（超过 {@link #IN_PREDICATE_LIMIT}）直接放行，避免逐元素比较 反而拖慢裁剪阶段。
 * </ul>
 *
 * <p>上下游关系：由 {@code forRowFilter} / {@code forPartitionFilter} 工厂构造；被 core 模块 的扫描规划调用，对每个 manifest
 * 调用 {@link #eval(ManifestFile)} 决定是否读取其文件列表。
 */
public class ManifestEvaluator {
  /** IN 谓词元素数超过此阈值时不再逐元素比较，直接判定为可能匹配。 */
  private static final int IN_PREDICATE_LIMIT = 200;

  private final Expression expr;

  /**
   * 基于行级过滤条件构造 ManifestEvaluator。
   *
   * <p>逻辑：用 {@link Projections#inclusive} 把行级过滤投影到分区 spec 上得到分区过滤， 再交给构造器绑定。
   *
   * @param rowFilter 行级过滤表达式
   * @param spec 分区 spec
   * @param caseSensitive 是否大小写敏感
   * @return 新的 {@link ManifestEvaluator}
   */
  public static ManifestEvaluator forRowFilter(
      Expression rowFilter, PartitionSpec spec, boolean caseSensitive) {
    return new ManifestEvaluator(
        spec, Projections.inclusive(spec, caseSensitive).project(rowFilter), caseSensitive);
  }

  /**
   * 基于分区过滤条件构造 ManifestEvaluator（跳过投影，直接使用分区过滤）。
   *
   * @param partitionFilter 分区级过滤表达式
   * @param spec 分区 spec
   * @param caseSensitive 是否大小写敏感
   * @return 新的 {@link ManifestEvaluator}
   */
  public static ManifestEvaluator forPartitionFilter(
      Expression partitionFilter, PartitionSpec spec, boolean caseSensitive) {
    return new ManifestEvaluator(spec, partitionFilter, caseSensitive);
  }

  /**
   * 私有构造器：先做 not 重写（消除 NOT 节点），再把过滤绑定到分区类型上。
   *
   * @param spec 分区 spec
   * @param partitionFilter 分区过滤表达式
   * @param caseSensitive 是否大小写敏感
   */
  private ManifestEvaluator(PartitionSpec spec, Expression partitionFilter, boolean caseSensitive) {
    this.expr = Binder.bind(spec.partitionType(), rewriteNot(partitionFilter), caseSensitive);
  }

  /**
   * 判定给定 manifest 是否可能包含匹配过滤条件的文件。
   *
   * <p>语义：返回 false 时该 manifest 必然不匹配，可安全跳过；返回 true 表示可能匹配， 不能跳过。语义为“包含性”——不确定时倾向于保留。
   *
   * @param manifest 待判定的 manifest 文件
   * @return false 表示必然不匹配，true 表示可能匹配
   */
  public boolean eval(ManifestFile manifest) {
    return new ManifestEvalVisitor().eval(manifest);
  }

  private static final boolean ROWS_MIGHT_MATCH = true;
  private static final boolean ROWS_CANNOT_MATCH = false;

  /**
   * Manifest 求值访问器：基于分区字段统计对已绑定表达式做包含性布尔求值。
   *
   * <p>设计意图：继承 {@link BoundExpressionVisitor} 以获得按操作分派的便利，每个谓词 方法基于 {@link PartitionFieldSummary}
   * 的下界/上界/containsNull/containsNaN 做判定。
   */
  private class ManifestEvalVisitor extends BoundExpressionVisitor<Boolean> {
    private List<PartitionFieldSummary> stats = null;

    /**
     * 对一个 manifest 求值。
     *
     * <p>逻辑：取 manifest 的分区统计；若统计为 null（无分区信息）则保守返回可能匹配； 否则委托 {@link
     * ExpressionVisitors#visitEvaluator} 遍历表达式求值。
     *
     * @param manifest 待判定的 manifest
     * @return 是否可能匹配
     */
    private boolean eval(ManifestFile manifest) {
      this.stats = manifest.partitions();
      if (stats == null) {
        return ROWS_MIGHT_MATCH;
      }

      return ExpressionVisitors.visitEvaluator(expr, this);
    }

    @Override
    public Boolean alwaysTrue() {
      return ROWS_MIGHT_MATCH; // all rows match
    }

    @Override
    public Boolean alwaysFalse() {
      return ROWS_CANNOT_MATCH; // all rows fail
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
     * 判定 IS_NULL 谓词。
     *
     * <p>逻辑：若该字段无 null 值（containsNull=false），则不可能匹配；否则可能匹配。 必填字段已在绑定阶段化简，故此处不再判断。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean isNull(BoundReference<T> ref) {
      int pos = Accessors.toPosition(ref.accessor());
      // no need to check whether the field is required because binding evaluates that case
      // if the column has no null values, the expression cannot match
      if (!stats.get(pos).containsNull()) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 NOT_NULL 谓词。
     *
     * <p>逻辑：若该字段所有值都为 null（containsNull=true 且下界为 null），则不可能匹配； 否则可能匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      int pos = Accessors.toPosition(ref.accessor());

      if (allValuesAreNull(stats.get(pos), ref.type().typeId())) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 IS_NAN 谓词。
     *
     * <p>逻辑：若 containsNaN 显式为 false 则不可能匹配；若所有值都为 null 也不可能匹配； 否则可能匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      int pos = Accessors.toPosition(ref.accessor());

      if (stats.get(pos).containsNaN() != null && !stats.get(pos).containsNaN()) {
        return ROWS_CANNOT_MATCH;
      }

      if (allValuesAreNull(stats.get(pos), ref.type().typeId())) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 NOT_NAN 谓词。
     *
     * <p>逻辑：若 containsNaN=true、containsNull=false 且下界为 null，则所有值都是 NaN， 不可能匹配；否则可能匹配。
     *
     * @param ref 绑定引用
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      PartitionFieldSummary fieldSummary = stats.get(Accessors.toPosition(ref.accessor()));

      // if containsNaN is true, containsNull is false and lowerBound is null, all values are NaN
      if (fieldSummary.containsNaN() != null
          && fieldSummary.containsNaN()
          && !fieldSummary.containsNull()
          && fieldSummary.lowerBound() == null) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 LT 谓词（ref &lt; lit）。
     *
     * <p>逻辑：取分区下界 lower，若 lower &gt;= lit 则所有值都 &gt;= lit，不可能匹配； 否则可能匹配。下界为 null 表示全 null，不可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      ByteBuffer lowerBound = stats.get(pos).lowerBound();
      if (lowerBound == null) {
        return ROWS_CANNOT_MATCH; // values are all null
      }

      T lower = Conversions.fromByteBuffer(ref.type(), lowerBound);

      int cmp = lit.comparator().compare(lower, lit.value());
      if (cmp >= 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 LT_EQ 谓词（ref &lt;= lit）。
     *
     * <p>逻辑：若下界 lower &gt; lit 则不可能匹配；否则可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      ByteBuffer lowerBound = stats.get(pos).lowerBound();
      if (lowerBound == null) {
        return ROWS_CANNOT_MATCH; // values are all null
      }

      T lower = Conversions.fromByteBuffer(ref.type(), lowerBound);

      int cmp = lit.comparator().compare(lower, lit.value());
      if (cmp > 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 GT 谓词（ref &gt; lit）。
     *
     * <p>逻辑：取分区上界 upper，若 upper &lt;= lit 则所有值都 &lt;= lit，不可能匹配； 否则可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      ByteBuffer upperBound = stats.get(pos).upperBound();
      if (upperBound == null) {
        return ROWS_CANNOT_MATCH; // values are all null
      }

      T upper = Conversions.fromByteBuffer(ref.type(), upperBound);

      int cmp = lit.comparator().compare(upper, lit.value());
      if (cmp <= 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 GT_EQ 谓词（ref &gt;= lit）。
     *
     * <p>逻辑：若上界 upper &lt; lit 则不可能匹配；否则可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      ByteBuffer upperBound = stats.get(pos).upperBound();
      if (upperBound == null) {
        return ROWS_CANNOT_MATCH; // values are all null
      }

      T upper = Conversions.fromByteBuffer(ref.type(), upperBound);

      int cmp = lit.comparator().compare(upper, lit.value());
      if (cmp < 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 EQ 谓词（ref == lit）。
     *
     * <p>逻辑：若 lit 落在 [lower, upper] 区间之外则不可能匹配；否则可能匹配。 下界为 null 表示全 null，不可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      PartitionFieldSummary fieldStats = stats.get(pos);
      if (fieldStats.lowerBound() == null) {
        return ROWS_CANNOT_MATCH; // values are all null and literal cannot contain null
      }

      T lower = Conversions.fromByteBuffer(ref.type(), fieldStats.lowerBound());
      int cmp = lit.comparator().compare(lower, lit.value());
      if (cmp > 0) {
        return ROWS_CANNOT_MATCH;
      }

      T upper = Conversions.fromByteBuffer(ref.type(), fieldStats.upperBound());
      cmp = lit.comparator().compare(upper, lit.value());
      if (cmp < 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 NOT_EQ 谓词。
     *
     * <p>逻辑：由于上下界并非真正的 min/max，无法据此断言某值不在区间内，故保守返回 可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 字面量
     * @param <T> 值类型
     * @return 恒为可能匹配
     */
    @Override
    public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
      // because the bounds are not necessarily a min or max value, this cannot be answered using
      // them. notEq(col, X) with (X, Y) doesn't guarantee that X is a value in col.
      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 IN 谓词。
     *
     * <p>逻辑：先用上下界过滤掉必然不在区间的元素；元素数超过 {@link #IN_PREDICATE_LIMIT} 时直接放行。若过滤后集合非空则可能匹配，否则不可能匹配。
     *
     * @param ref 绑定引用
     * @param literalSet 字面量集合
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      int pos = Accessors.toPosition(ref.accessor());
      PartitionFieldSummary fieldStats = stats.get(pos);
      if (fieldStats.lowerBound() == null) {
        return ROWS_CANNOT_MATCH; // values are all null and literalSet cannot contain null.
      }

      Collection<T> literals = literalSet;

      if (literals.size() > IN_PREDICATE_LIMIT) {
        // skip evaluating the predicate if the number of values is too big
        return ROWS_MIGHT_MATCH;
      }

      T lower = Conversions.fromByteBuffer(ref.type(), fieldStats.lowerBound());
      literals =
          literals.stream()
              .filter(v -> ref.comparator().compare(lower, v) <= 0)
              .collect(Collectors.toList());
      if (literals.isEmpty()) { // if all values are less than lower bound, rows cannot match.
        return ROWS_CANNOT_MATCH;
      }

      T upper = Conversions.fromByteBuffer(ref.type(), fieldStats.upperBound());
      literals =
          literals.stream()
              .filter(v -> ref.comparator().compare(upper, v) >= 0)
              .collect(Collectors.toList());
      if (literals
          .isEmpty()) { // if all remaining values are greater than upper bound, rows cannot match.
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 NOT_IN 谓词。
     *
     * <p>逻辑：与 NOT_EQ 类似，上下界无法证明某值必然存在，故保守返回可能匹配。
     *
     * @param ref 绑定引用
     * @param literalSet 字面量集合
     * @param <T> 值类型
     * @return 恒为可能匹配
     */
    @Override
    public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
      // because the bounds are not necessarily a min or max value, this cannot be answered using
      // them. notIn(col, {X, ...}) with (X, Y) doesn't guarantee that X is a value in col.
      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 STARTS_WITH 谓词。
     *
     * <p>逻辑：把字面量转为前缀字节，分别与截断到前缀长度的下界/上界做无符号比较； 若前缀小于下界或大于上界则不可能匹配；否则可能匹配。
     *
     * @param ref 绑定引用
     * @param lit 前缀字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      PartitionFieldSummary fieldStats = stats.get(pos);

      if (fieldStats.lowerBound() == null) {
        return ROWS_CANNOT_MATCH; // values are all null and literal cannot contain null
      }

      ByteBuffer prefixAsBytes = lit.toByteBuffer();

      Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

      ByteBuffer lower = fieldStats.lowerBound();
      // truncate lower bound so that its length in bytes is not greater than the length of prefix
      int lowerLength = Math.min(prefixAsBytes.remaining(), lower.remaining());
      int lowerCmp =
          comparator.compare(BinaryUtil.truncateBinary(lower, lowerLength), prefixAsBytes);
      if (lowerCmp > 0) {
        return ROWS_CANNOT_MATCH;
      }

      ByteBuffer upper = fieldStats.upperBound();
      // truncate upper bound so that its length in bytes is not greater than the length of prefix
      int upperLength = Math.min(prefixAsBytes.remaining(), upper.remaining());
      int upperCmp =
          comparator.compare(BinaryUtil.truncateBinary(upper, upperLength), prefixAsBytes);
      if (upperCmp < 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 NOT_STARTS_WITH 谓词。
     *
     * <p>逻辑：仅当上下界都恰好以该前缀开头（即所有值都以该前缀开头）时才不可能匹配； 否则可能匹配。containsNull=true 时直接可能匹配（null 不以任何前缀开头）。
     *
     * @param ref 绑定引用
     * @param lit 前缀字面量
     * @param <T> 值类型
     * @return 是否可能匹配
     */
    @Override
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      int pos = Accessors.toPosition(ref.accessor());
      PartitionFieldSummary fieldStats = stats.get(pos);

      if (fieldStats.containsNull()) {
        return ROWS_MIGHT_MATCH;
      }

      ByteBuffer lower = fieldStats.lowerBound();
      ByteBuffer upper = fieldStats.upperBound();

      // notStartsWith will match unless all values must start with the prefix. This happens when
      // the lower and upper
      // bounds both start with the prefix.
      if (lower != null && upper != null) {
        ByteBuffer prefixAsBytes = lit.toByteBuffer();
        Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

        // if lower is shorter than the prefix, it can't start with the prefix
        if (lower.remaining() < prefixAsBytes.remaining()) {
          return ROWS_MIGHT_MATCH;
        }

        // truncate lower bound to the prefix and check for equality
        int cmp =
            comparator.compare(
                BinaryUtil.truncateBinary(lower, prefixAsBytes.remaining()), prefixAsBytes);
        if (cmp == 0) {
          // the lower bound starts with the prefix; check the upper bound
          // if upper is shorter than the prefix, it can't start with the prefix
          if (upper.remaining() < prefixAsBytes.remaining()) {
            return ROWS_MIGHT_MATCH;
          }

          // truncate upper bound so that its length in bytes is not greater than the length of
          // prefix
          cmp =
              comparator.compare(
                  BinaryUtil.truncateBinary(upper, prefixAsBytes.remaining()), prefixAsBytes);
          if (cmp == 0) {
            // both bounds match the prefix, so all rows must match the prefix and none do not match
            return ROWS_CANNOT_MATCH;
          }
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定某字段是否所有值都为 null。
     *
     * <p>逻辑：containsNull=true 且 lowerBound=null 表示全 null。对浮点类型额外检查 containsNaN，因为 NaN 不会出现在 bounds
     * 中但也不是 null。
     *
     * @param summary 分区字段统计
     * @param typeId 字段类型 id
     * @return 全 null 返回 true
     */
    private boolean allValuesAreNull(PartitionFieldSummary summary, Type.TypeID typeId) {
      // containsNull encodes whether at least one partition value is null,
      // lowerBound is null if all partition values are null
      boolean allNull = summary.containsNull() && summary.lowerBound() == null;

      if (allNull && (Type.TypeID.DOUBLE.equals(typeId) || Type.TypeID.FLOAT.equals(typeId))) {
        // floating point types may include NaN values, which we check separately.
        // In case bounds don't include NaN value, containsNaN needs to be checked against.
        allNull = summary.containsNaN() != null && !summary.containsNaN();
      }
      return allNull;
    }
  }
}
