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

import java.util.Comparator;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type.PrimitiveType;
import org.apache.iceberg.types.Types;

/**
 * MIN 聚合：求字段最小值，对应 {@link Operation#MIN}。
 *
 * <p>所属模块：iceberg-api（聚合分支具体实现之一）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>行级累积：用 {@link MinAggregator} 维护当前最小值，新值更小时替换。
 *   <li>文件级求值：直接读取 DataFile 的 lowerBounds 作为该文件的最小值贡献。
 * </ul>
 *
 * <p>设计意图：构造时一次性缓存字段 id、类型与比较器，避免每次求值重复查找； 文件级 hasValue 同时考虑“有下界”与“全 null（值计数等于 null 计数）”两种情况， 使全
 * null 文件也被视为有效贡献（贡献 null）。
 *
 * <p>上下游关系：被 {@link AggregateEvaluator} 通过 newAggregator 驱动；与 {@link ValueAggregate} / {@link
 * MaxAggregate}（在 core 中）配对实现 MIN/MAX。
 *
 * @param <T> 聚合输入值的 Java 类型
 */
public class MinAggregate<T> extends ValueAggregate<T> {
  private final int fieldId;
  private final PrimitiveType type;
  private final Comparator<T> comparator;

  /**
   * 构造 MIN 聚合并预取字段 id、类型与比较器。
   *
   * @param term 已绑定 term
   */
  protected MinAggregate(BoundTerm<T> term) {
    super(Operation.MIN, term);
    Types.NestedField field = term.ref().field();
    this.fieldId = field.fieldId();
    this.type = field.type().asPrimitiveType();
    this.comparator = Comparators.forType(type);
  }

  /**
   * 判断该文件是否提供了本聚合所需统计值。
   *
   * <p>逻辑：文件 lowerBounds 含该字段，或文件该字段“值计数=null 计数且 &gt;0” （即全 null）时视为有效。
   *
   * @param file 数据文件
   * @return 有效返回 true
   */
  @Override
  protected boolean hasValue(DataFile file) {
    boolean hasBound = file.lowerBounds().containsKey(fieldId);
    Long valueCount = safeGet(file.valueCounts(), fieldId);
    Long nullCount = safeGet(file.nullValueCounts(), fieldId);
    boolean boundAllNull =
        valueCount != null
            && valueCount > 0
            && nullCount != null
            && nullCount.longValue() == valueCount.longValue();
    return hasBound || boundAllNull;
  }

  /**
   * 从文件 lowerBounds 读取该字段的最小值。
   *
   * @param file 数据文件
   * @return 该文件的最小值（可能为 null）
   */
  @Override
  protected Object evaluateRef(DataFile file) {
    return Conversions.fromByteBuffer(type, safeGet(file.lowerBounds(), fieldId));
  }

  /**
   * 创建 MIN 聚合器。
   *
   * @return 新的 {@link MinAggregator}
   */
  @Override
  public Aggregator<T> newAggregator() {
    return new MinAggregator<>(this, comparator);
  }

  /**
   * MIN 聚合器：维护当前最小值，遇到更小值则替换。
   *
   * <p>设计意图：复用 {@link NullSafeAggregator} 的 null 安全逻辑，自身只维护一个 min 字段。
   *
   * @param <T> 聚合输入类型
   */
  private static class MinAggregator<T> extends NullSafeAggregator<T, T> {
    private final Comparator<T> comparator;
    private T min = null;

    MinAggregator(MinAggregate<T> aggregate, Comparator<T> comparator) {
      super(aggregate);
      this.comparator = comparator;
    }

    @Override
    protected void update(T value) {
      if (min == null || comparator.compare(value, min) < 0) {
        this.min = value;
      }
    }

    @Override
    protected T current() {
      return min;
    }
  }
}
