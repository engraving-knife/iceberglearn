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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.BinaryUtil;
import org.apache.iceberg.util.NaNUtil;

/**
 * 文件级包容性指标求值器：基于数据文件的列统计（min/max/null 计数等）判定文件是否“可能”包含匹配行。
 *
 * <p>所属模块：iceberg-api（表达式体系的文件级裁剪入口，与行级 {@link Evaluator} 互补， 是 Iceberg 数据跳过（data skipping）的核心）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造时把未绑定表达式经 {@link Binder} 绑定，并先用 {@link Expressions#rewriteNot} 把 NOT 下沉到叶子（因投影/指标求值假设无
 *       NOT）。
 *   <li>提供 {@link #eval(ContentFile)}：对单个数据文件返回“是否可能匹配”。
 * </ul>
 *
 * <p>设计意图：“包容（inclusive）”语义——返回 true 表示文件可能匹配（不能跳过）， 返回 false 表示文件必定不匹配（可安全跳过）。基于每个文件按列记录的
 * valueCounts、 nullValueCounts、nanValueCounts、lowerBounds、upperBounds 做区间判定，无需读数据。 因 ORC 的
 * float/double 列在首值为 NaN 时会把上下界都报为 NaN（不可靠）， 故遇到 NaN 界时保守返回 ROWS_MIGHT_MATCH，避免误跳过。
 *
 * <p>上下游关系：被 core 模块的扫描任务裁剪流程调用，对每个候选 DataFile 判定是否跳过； 输入 schema 与表达式由扫描配置提供。
 */
public class InclusiveMetricsEvaluator {
  /** IN 谓词值数量上限：超过此值则不做集合裁剪，直接判可能匹配，避免开销过大。 */
  private static final int IN_PREDICATE_LIMIT = 200;

  private final Expression expr;

  /**
   * 构造包容性指标求值器（大小写敏感，默认）。
   *
   * @param schema 表 schema
   * @param unbound 未绑定表达式
   */
  public InclusiveMetricsEvaluator(Schema schema, Expression unbound) {
    this(schema, unbound, true);
  }

  /**
   * 构造包容性指标求值器（可指定大小写敏感）。
   *
   * <p>逻辑：取 schema 的 struct，先用 rewriteNot 把 NOT 下沉，再经 Binder 绑定得到已绑定表达式。
   *
   * @param schema 表 schema
   * @param unbound 未绑定表达式
   * @param caseSensitive 是否大小写敏感
   */
  public InclusiveMetricsEvaluator(Schema schema, Expression unbound, boolean caseSensitive) {
    StructType struct = schema.asStruct();
    this.expr = Binder.bind(struct, rewriteNot(unbound), caseSensitive);
  }

  /**
   * 判定文件是否可能包含匹配表达式的行。
   *
   * @param file 数据文件
   * @return false 表示文件不可能匹配（可跳过），true 表示可能匹配
   */
  public boolean eval(ContentFile<?> file) {
    // TODO: detect the case where a column is missing from the file using file's max field id.
    return new MetricsEvalVisitor().eval(file);
  }

  private static final boolean ROWS_MIGHT_MATCH = true;
  private static final boolean ROWS_CANNOT_MATCH = false;

  /**
   * 指标求值访问者：把已绑定表达式树映射为“文件是否可能匹配”的布尔结果。
   *
   * <p>设计意图：继承 {@link BoundExpressionVisitor}，按谓词类型分发到 isNull/lt/eq/in 等方法；
   * 每个方法利用文件级列统计（上下界、null/nan 计数）做区间判定。非引用 term（如变换） 经 {@link #handleNonReference} 保守返回可能匹配。
   */
  private class MetricsEvalVisitor extends BoundExpressionVisitor<Boolean> {
    private Map<Integer, Long> valueCounts = null;
    private Map<Integer, Long> nullCounts = null;
    private Map<Integer, Long> nanCounts = null;
    private Map<Integer, ByteBuffer> lowerBounds = null;
    private Map<Integer, ByteBuffer> upperBounds = null;

    /**
     * 在单个文件上求“是否可能匹配”。
     *
     * <p>逻辑：记录数为 0 直接判不匹配；记录数小于 0（avro 导入未解析）保守判可能匹配； 否则加载文件各项列统计，再用带短路的 visitEvaluator 遍历表达式。
     *
     * @param file 数据文件
     * @return 是否可能匹配
     */
    private boolean eval(ContentFile<?> file) {
      if (file.recordCount() == 0) {
        return ROWS_CANNOT_MATCH;
      }

      if (file.recordCount() < 0) {
        // we haven't implemented parsing record count from avro file and thus set record count -1
        // when importing avro tables to iceberg tables. This should be updated once we implemented
        // and set correct record count.
        return ROWS_MIGHT_MATCH;
      }

      this.valueCounts = file.valueCounts();
      this.nullCounts = file.nullValueCounts();
      this.nanCounts = file.nanValueCounts();
      this.lowerBounds = file.lowerBounds();
      this.upperBounds = file.upperBounds();

      return ExpressionVisitors.visitEvaluator(expr, this);
    }

    @Override
    public <T> Boolean handleNonReference(Bound<T> term) {
      // If the term in any expression is not a direct reference, assume that rows may match. This
      // happens when
      // transforms or other expressions are passed to this evaluator. For example, bucket16(x) = 0
      // can't be determined
      // because this visitor operates on data metrics and not partition values. It may be possible
      // to un-transform
      // expressions for order preserving transforms in the future, but this is not currently
      // supported.
      return ROWS_MIGHT_MATCH;
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

    @Override
    public <T> Boolean isNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has no null values, the expression cannot match
      Integer id = ref.fieldId();

      if (nullCounts != null && nullCounts.containsKey(id) && nullCounts.get(id) == 0) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has no non-null values, the expression cannot match
      Integer id = ref.fieldId();

      if (containsNullsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      Integer id = ref.fieldId();

      if (nanCounts != null && nanCounts.containsKey(id) && nanCounts.get(id) == 0) {
        return ROWS_CANNOT_MATCH;
      }

      // when there's no nanCounts information, but we already know the column only contains null,
      // it's guaranteed that there's no NaN value
      if (containsNullsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      Integer id = ref.fieldId();

      if (containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 col &lt; value 是否可能在文件中匹配。
     *
     * <p>逻辑：列全为 null/NaN 则不匹配；否则取列下界，若下界 &gt;= value（即所有值都 &gt;= value） 则不匹配；下界为 NaN（不可靠）时保守判可能匹配。
     */
    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the InclusiveMetricsEvaluator docs for more.
          return ROWS_MIGHT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp >= 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the InclusiveMetricsEvaluator docs for more.
          return ROWS_MIGHT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp <= 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp < 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 col == value 是否可能在文件中匹配。
     *
     * <p>逻辑：列全为 null/NaN 则不匹配；否则取下界，若下界 &gt; value 则不匹配； 再取上界，若上界 &lt; value 则不匹配；下界为 NaN 时保守判可能匹配。
     */
    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the InclusiveMetricsEvaluator docs for more.
          return ROWS_MIGHT_MATCH;
        }

        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));

        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp < 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notEq(BoundReference<T> ref, Literal<T> lit) {
      // because the bounds are not necessarily a min or max value, this cannot be answered using
      // them. notEq(col, X) with (X, Y) doesn't guarantee that X is a value in col.
      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 col IN {values} 是否可能在文件中匹配。
     *
     * <p>逻辑：列全为 null/NaN 则不匹配；值数量超过 {@link #IN_PREDICATE_LIMIT} 直接判可能匹配；
     * 否则用下界过滤掉所有小于下界的值、用上界过滤掉所有大于上界的值，若过滤后集合为空则不匹配。
     */
    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id) || containsNaNsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      Collection<T> literals = literalSet;

      if (literals.size() > IN_PREDICATE_LIMIT) {
        // skip evaluating the predicate if the number of values is too big
        return ROWS_MIGHT_MATCH;
      }

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        T lower = Conversions.fromByteBuffer(ref.type(), lowerBounds.get(id));

        if (NaNUtil.isNaN(lower)) {
          // NaN indicates unreliable bounds. See the InclusiveMetricsEvaluator docs for more.
          return ROWS_MIGHT_MATCH;
        }

        literals =
            literals.stream()
                .filter(v -> ref.comparator().compare(lower, v) <= 0)
                .collect(Collectors.toList());
        if (literals.isEmpty()) { // if all values are less than lower bound, rows cannot match.
          return ROWS_CANNOT_MATCH;
        }
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        T upper = Conversions.fromByteBuffer(ref.type(), upperBounds.get(id));
        literals =
            literals.stream()
                .filter(v -> ref.comparator().compare(upper, v) >= 0)
                .collect(Collectors.toList());
        if (literals
            .isEmpty()) { // if all remaining values are greater than upper bound, rows cannot
          // match.
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notIn(BoundReference<T> ref, Set<T> literalSet) {
      // because the bounds are not necessarily a min or max value, this cannot be answered using
      // them. notIn(col, {X, ...}) with (X, Y) doesn't guarantee that X is a value in col.
      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 col STARTS_WITH prefix 是否可能在文件中匹配。
     *
     * <p>逻辑：列全为 null 则不匹配；否则把 prefix 与下界/上界按 prefix 字节长度截断后做 无符号字节比较——下界截断后大于 prefix 则不匹配，上界截断后小于
     * prefix 则不匹配。
     */
    @Override
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (containsNullsOnly(id)) {
        return ROWS_CANNOT_MATCH;
      }

      ByteBuffer prefixAsBytes = lit.toByteBuffer();

      Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

      if (lowerBounds != null && lowerBounds.containsKey(id)) {
        ByteBuffer lower = lowerBounds.get(id);
        // truncate lower bound so that its length in bytes is not greater than the length of prefix
        int length = Math.min(prefixAsBytes.remaining(), lower.remaining());
        int cmp = comparator.compare(BinaryUtil.truncateBinary(lower, length), prefixAsBytes);
        if (cmp > 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      if (upperBounds != null && upperBounds.containsKey(id)) {
        ByteBuffer upper = upperBounds.get(id);
        // truncate upper bound so that its length in bytes is not greater than the length of prefix
        int length = Math.min(prefixAsBytes.remaining(), upper.remaining());
        int cmp = comparator.compare(BinaryUtil.truncateBinary(upper, length), prefixAsBytes);
        if (cmp < 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    /**
     * 判定 col NOT_STARTS_WITH prefix 是否可能在文件中匹配。
     *
     * <p>逻辑：列可能含 null 则保守判可能匹配；否则仅当上下界截断后都等于 prefix （即所有值必然以 prefix 开头）时才判不匹配。
     */
    @Override
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      Integer id = ref.fieldId();

      if (mayContainNull(id)) {
        return ROWS_MIGHT_MATCH;
      }

      ByteBuffer prefixAsBytes = lit.toByteBuffer();

      Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

      // notStartsWith will match unless all values must start with the prefix. This happens when
      // the lower and upper
      // bounds both start with the prefix.
      if (lowerBounds != null
          && upperBounds != null
          && lowerBounds.containsKey(id)
          && upperBounds.containsKey(id)) {
        ByteBuffer lower = lowerBounds.get(id);
        // if lower is shorter than the prefix then lower doesn't start with the prefix
        if (lower.remaining() < prefixAsBytes.remaining()) {
          return ROWS_MIGHT_MATCH;
        }

        int cmp =
            comparator.compare(
                BinaryUtil.truncateBinary(lower, prefixAsBytes.remaining()), prefixAsBytes);
        if (cmp == 0) {
          ByteBuffer upper = upperBounds.get(id);
          // if upper is shorter than the prefix then upper can't start with the prefix
          if (upper.remaining() < prefixAsBytes.remaining()) {
            return ROWS_MIGHT_MATCH;
          }

          cmp =
              comparator.compare(
                  BinaryUtil.truncateBinary(upper, prefixAsBytes.remaining()), prefixAsBytes);
          if (cmp == 0) {
            // both bounds match the prefix, so all rows must match the prefix and therefore do not
            // satisfy
            // the predicate
            return ROWS_CANNOT_MATCH;
          }
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    /** 判断列是否可能含 null（null 计数缺失或非 0）。 */
    private boolean mayContainNull(Integer id) {
      return nullCounts == null || (nullCounts.containsKey(id) && nullCounts.get(id) != 0);
    }

    /** 判断列是否全为 null（值计数等于 null 计数）。 */
    private boolean containsNullsOnly(Integer id) {
      return valueCounts != null
          && valueCounts.containsKey(id)
          && nullCounts != null
          && nullCounts.containsKey(id)
          && valueCounts.get(id) - nullCounts.get(id) == 0;
    }

    /** 判断列是否全为 NaN（NaN 计数等于值计数）。 */
    private boolean containsNaNsOnly(Integer id) {
      return nanCounts != null
          && nanCounts.containsKey(id)
          && valueCounts != null
          && nanCounts.get(id).equals(valueCounts.get(id));
    }
  }
}
