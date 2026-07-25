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
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Bound;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.BinaryUtil;
import org.apache.parquet.column.statistics.Statistics;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：基于 Parquet 列统计（min/max/null 计数）的 row group 过滤器。
 *
 * <p>所属模块：iceberg-parquet（读取侧 row group 裁剪，利用列级统计下推过滤）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取 row group 各列的 {@link Statistics}（min/max/numNulls/valueCount）。
 *   <li>用 {@link BoundExpressionVisitor} 遍历已绑定表达式，对 eq/lt/gt/in/startsWith 等 谓词基于 min/max 区间判断该 row
 *       group 是否可能包含匹配行。
 *   <li>对 notEq/notIn 因 min/max 不保证是真实值，保守返回可能匹配。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>统计下推：列统计远小于数据本身，可在读取前快速排除不匹配的 row group。
 *   <li>类型转换：通过 {@link ParquetConversions} 把 Parquet 统计值转为 Iceberg 类型比较。
 *   <li>保守安全：当统计缺失、min/max 未定义或含 null 时，保守返回可能匹配，避免漏数据。
 *   <li>IN 谓词限制：literalSet 超过 {@link #IN_PREDICATE_LIMIT} 时跳过评估。
 * </ul>
 *
 * <p>上下游关系：被 {@link ParquetReader} / 读取入口在读取 row group 前调用； 依赖 {@link Statistics} 与 {@link
 * ParquetConversions}。
 */
public class ParquetMetricsRowGroupFilter {
  private static final int IN_PREDICATE_LIMIT = 200;

  private final Schema schema;
  private final Expression expr;

  /**
   * 构造统计过滤器，默认大小写敏感。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定表达式
   */
  public ParquetMetricsRowGroupFilter(Schema schema, Expression unbound) {
    this(schema, unbound, true);
  }

  /**
   * 构造统计过滤器。
   *
   * <p>逻辑：把未绑定表达式通过 {@link Binder#bind} 绑定到 schema，并重写 not 操作。
   *
   * @param schema Iceberg schema
   * @param unbound 未绑定表达式
   * @param caseSensitive 是否大小写敏感
   */
  public ParquetMetricsRowGroupFilter(Schema schema, Expression unbound, boolean caseSensitive) {
    this.schema = schema;
    StructType struct = schema.asStruct();
    this.expr = Binder.bind(struct, Expressions.rewriteNot(unbound), caseSensitive);
  }

  /**
   * 判断 row group 是否可能包含匹配表达式的记录。
   *
   * @param fileSchema Parquet 文件 schema
   * @param rowGroup row group 元数据
   * @return false 表示该 row group 不可能包含匹配行，可跳过；true 表示可能匹配
   */
  public boolean shouldRead(MessageType fileSchema, BlockMetaData rowGroup) {
    return new MetricsEvalVisitor().eval(fileSchema, rowGroup);
  }

  private static final boolean ROWS_MIGHT_MATCH = true;
  private static final boolean ROWS_CANNOT_MATCH = false;

  /**
   * 统计评估访问者：基于列 min/max/null 统计对已绑定表达式求值。
   *
   * <p>设计意图：持有 stats/valueCounts/conversions 三个映射，每个谓词方法 先检查列是否存在与统计是否可用，再基于 min/max 区间判断是否可能匹配。
   */
  private class MetricsEvalVisitor extends BoundExpressionVisitor<Boolean> {
    private Map<Integer, Statistics<?>> stats = null;
    private Map<Integer, Long> valueCounts = null;
    private Map<Integer, Function<Object, Object>> conversions = null;

    /**
     * 初始化评估上下文并求值表达式。
     *
     * <p>逻辑：rowCount<=0 直接排除；遍历 rowGroup 各列建立 fieldId->Statistics/ valueCount/conversion 映射；用
     * ExpressionVisitors 求值表达式。
     *
     * @param fileSchema Parquet 文件 schema
     * @param rowGroup row group 元数据
     * @return true 表示可能匹配，false 表示不可能匹配
     */
    private boolean eval(MessageType fileSchema, BlockMetaData rowGroup) {
      if (rowGroup.getRowCount() <= 0) {
        return ROWS_CANNOT_MATCH;
      }

      this.stats = Maps.newHashMap();
      this.valueCounts = Maps.newHashMap();
      this.conversions = Maps.newHashMap();
      for (ColumnChunkMetaData col : rowGroup.getColumns()) {
        PrimitiveType colType = fileSchema.getType(col.getPath().toArray()).asPrimitiveType();
        if (colType.getId() != null) {
          int id = colType.getId().intValue();
          Type icebergType = schema.findType(id);
          stats.put(id, col.getStatistics());
          valueCounts.put(id, col.getValueCount());
          conversions.put(id, ParquetConversions.converterFromParquet(colType, icebergType));
        }
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

    @Override
    public <T> Boolean isNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has no null values, the expression cannot match
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_MIGHT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty() && colStats.getNumNulls() == 0) {
        // there are stats and no values are null => all values are non-null
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNull(BoundReference<T> ref) {
      // no need to check whether the field is required because binding evaluates that case
      // if the column has no non-null values, the expression cannot match
      int id = ref.fieldId();

      // When filtering nested types notNull() is implicit filter passed even though complex
      // filters aren't pushed down in Parquet. Leave all nested column type filters to be
      // evaluated post scan.
      if (schema.findType(id) instanceof Type.NestedType) {
        return ROWS_MIGHT_MATCH;
      }

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && valueCount - colStats.getNumNulls() == 0) {
        // (num nulls == value count) => all values are null => no non-null values
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean isNaN(BoundReference<T> ref) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && valueCount - colStats.getNumNulls() == 0) {
        // (num nulls == value count) => all values are null => no nan values
        return ROWS_CANNOT_MATCH;
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean notNaN(BoundReference<T> ref) {
      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean lt(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        T lower = min(colStats, id);
        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp >= 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean ltEq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        T lower = min(colStats, id);
        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gt(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        T upper = max(colStats, id);
        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp <= 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean gtEq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        T upper = max(colStats, id);
        int cmp = lit.comparator().compare(upper, lit.value());
        if (cmp < 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    public <T> Boolean eq(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      // When filtering nested types notNull() is implicit filter passed even though complex
      // filters aren't pushed down in Parquet. Leave all nested column type filters to be
      // evaluated post scan.
      if (schema.findType(id) instanceof Type.NestedType) {
        return ROWS_MIGHT_MATCH;
      }

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        T lower = min(colStats, id);
        int cmp = lit.comparator().compare(lower, lit.value());
        if (cmp > 0) {
          return ROWS_CANNOT_MATCH;
        }

        T upper = max(colStats, id);
        cmp = lit.comparator().compare(upper, lit.value());
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

    @Override
    public <T> Boolean in(BoundReference<T> ref, Set<T> literalSet) {
      int id = ref.fieldId();

      // When filtering nested types notNull() is implicit filter passed even though complex
      // filters aren't pushed down in Parquet. Leave all nested column type filters to be
      // evaluated post scan.
      if (schema.findType(id) instanceof Type.NestedType) {
        return ROWS_MIGHT_MATCH;
      }

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<?> colStats = stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        Collection<T> literals = literalSet;

        if (literals.size() > IN_PREDICATE_LIMIT) {
          // skip evaluating the predicate if the number of values is too big
          return ROWS_MIGHT_MATCH;
        }

        T lower = min(colStats, id);
        literals =
            literals.stream()
                .filter(v -> ref.comparator().compare(lower, v) <= 0)
                .collect(Collectors.toList());
        if (literals.isEmpty()) { // if all values are less than lower bound, rows cannot match.
          return ROWS_CANNOT_MATCH;
        }

        T upper = max(colStats, id);
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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Boolean startsWith(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();

      Long valueCount = valueCounts.get(id);
      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_CANNOT_MATCH;
      }

      Statistics<Binary> colStats = (Statistics<Binary>) stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (allNulls(colStats, valueCount)) {
          return ROWS_CANNOT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        ByteBuffer prefixAsBytes = lit.toByteBuffer();

        Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

        Binary lower = colStats.genericGetMin();
        // truncate lower bound so that its length in bytes is not greater than the length of prefix
        int lowerLength = Math.min(prefixAsBytes.remaining(), lower.length());
        int lowerCmp =
            comparator.compare(
                BinaryUtil.truncateBinary(lower.toByteBuffer(), lowerLength), prefixAsBytes);
        if (lowerCmp > 0) {
          return ROWS_CANNOT_MATCH;
        }

        Binary upper = colStats.genericGetMax();
        // truncate upper bound so that its length in bytes is not greater than the length of prefix
        int upperLength = Math.min(prefixAsBytes.remaining(), upper.length());
        int upperCmp =
            comparator.compare(
                BinaryUtil.truncateBinary(upper.toByteBuffer(), upperLength), prefixAsBytes);
        if (upperCmp < 0) {
          return ROWS_CANNOT_MATCH;
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Boolean notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      int id = ref.fieldId();
      Long valueCount = valueCounts.get(id);

      if (valueCount == null) {
        // the column is not present and is all nulls
        return ROWS_MIGHT_MATCH;
      }

      Statistics<Binary> colStats = (Statistics<Binary>) stats.get(id);
      if (colStats != null && !colStats.isEmpty()) {
        if (mayContainNull(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        if (minMaxUndefined(colStats)) {
          return ROWS_MIGHT_MATCH;
        }

        Binary lower = colStats.genericGetMin();
        Binary upper = colStats.genericGetMax();

        // notStartsWith will match unless all values must start with the prefix. this happens when
        // the lower and upper
        // bounds both start with the prefix.
        if (lower != null && upper != null) {
          ByteBuffer prefix = lit.toByteBuffer();
          Comparator<ByteBuffer> comparator = Comparators.unsignedBytes();

          // if lower is shorter than the prefix, it can't start with the prefix
          if (lower.length() < prefix.remaining()) {
            return ROWS_MIGHT_MATCH;
          }

          // truncate lower bound to the prefix and check for equality
          int cmp =
              comparator.compare(
                  BinaryUtil.truncateBinary(lower.toByteBuffer(), prefix.remaining()), prefix);
          if (cmp == 0) {
            // the lower bound starts with the prefix; check the upper bound
            // if upper is shorter than the prefix, it can't start with the prefix
            if (upper.length() < prefix.remaining()) {
              return ROWS_MIGHT_MATCH;
            }

            // truncate upper bound so that its length in bytes is not greater than the length of
            // prefix
            cmp =
                comparator.compare(
                    BinaryUtil.truncateBinary(upper.toByteBuffer(), prefix.remaining()), prefix);
            if (cmp == 0) {
              // both bounds match the prefix, so all rows must match the prefix and none do not
              // match
              return ROWS_CANNOT_MATCH;
            }
          }
        }
      }

      return ROWS_MIGHT_MATCH;
    }

    @SuppressWarnings("unchecked")
    private <T> T min(Statistics<?> statistics, int id) {
      return (T) conversions.get(id).apply(statistics.genericGetMin());
    }

    @SuppressWarnings("unchecked")
    private <T> T max(Statistics<?> statistics, int id) {
      return (T) conversions.get(id).apply(statistics.genericGetMax());
    }

    @Override
    public <T> Boolean handleNonReference(Bound<T> term) {
      return ROWS_MIGHT_MATCH;
    }
  }

  /**
   * 判断旧版 Parquet 统计的 min/max 是否未定义。
   *
   * <p>旧版 Parquet（如 1.5.0-CDH）可能只有 null 计数但 min/max 未定义， 类似于含 NaN 时的行为。OSS Parquet 因 PARQUET-251
   * 不受影响。
   *
   * @param statistics 待检查的统计
   * @return true 表示 min/max 字节为 null
   */
  static boolean nullMinMax(Statistics statistics) {
    return statistics.getMaxBytes() == null || statistics.getMinBytes() == null;
  }

  /**
   * 判断列的 min/max 是否未定义。
   *
   * <p>逻辑：当 numNulls 已设置但 hasNonNullValue 为 false，或 min/max 字节为 null 时， min/max 视为未定义（如含 NaN 场景）。
   *
   * @param statistics 待检查的统计
   * @return true 表示 min/max 未定义
   */
  static boolean minMaxUndefined(Statistics statistics) {
    return (statistics.isNumNullsSet() && !statistics.hasNonNullValue()) || nullMinMax(statistics);
  }

  /**
   * 判断列是否全为 null。
   *
   * @param statistics 列统计
   * @param valueCount 值总数
   * @return true 表示全部为 null
   */
  static boolean allNulls(Statistics statistics, long valueCount) {
    return statistics.isNumNullsSet() && valueCount == statistics.getNumNulls();
  }

  private static boolean mayContainNull(Statistics statistics) {
    return !statistics.isNumNullsSet() || statistics.getNumNulls() > 0;
  }
}
