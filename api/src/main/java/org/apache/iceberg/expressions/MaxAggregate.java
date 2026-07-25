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
 * MAX 聚合：求某字段的最大值。
 *
 * <p>所属模块：iceberg-api（表达式体系聚合分支中 Max 的具体实现，继承 {@link ValueAggregate}， 与 {@link MinAggregate} 并列）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>文件级求值（{@link #evaluateRef(DataFile)}）：复用 DataFile 的上界（upperBounds）作为该文件最大值。
 *   <li>判断文件是否具备最大值统计（{@link #hasValue(DataFile)}）：有上界，或全为 null（此时最大值即 null）。
 *   <li>提供行级增量聚合器 {@link MaxAggregator}：逐行比较保留最大值。
 * </ul>
 *
 * <p>设计意图：MAX 的文件级值可直接取列上界（Iceberg 每个数据文件按列记录 min/max）， 无需逐行扫描；当列全为 null 时（valueCount ==
 * nullCount）也认为有值（最大值为 null）， 以保证聚合不因全 null 文件而失效。比较器在构造时按字段类型一次性确定。
 *
 * <p>上下游关系：由 {@link UnboundAggregate#bind} 在 MAX 操作下构造； 被 {@link AggregateEvaluator} 通过 {@link
 * BoundAggregate.Aggregator} 驱动累积。
 *
 * @param <T> 聚合字段的 Java 类型
 */
public class MaxAggregate<T> extends ValueAggregate<T> {
  private final int fieldId;
  private final PrimitiveType type;
  private final Comparator<T> comparator;

  protected MaxAggregate(BoundTerm<T> term) {
    super(Operation.MAX, term);
    Types.NestedField field = term.ref().field();
    this.fieldId = field.fieldId();
    this.type = field.type().asPrimitiveType();
    this.comparator = Comparators.forType(type);
  }

  /**
   * 判断文件是否具备 MAX 所需统计。
   *
   * <p>逻辑：若文件上界包含本字段则有效；否则若值计数与 null 计数相等且大于 0（即全 null）， 也认为有效（最大值为 null）。
   *
   * @param file 数据文件
   * @return 文件具备 MAX 统计返回 true
   */
  @Override
  protected boolean hasValue(DataFile file) {
    boolean hasBound = file.upperBounds().containsKey(fieldId);
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
   * 从文件上界统计中反序列化出本字段的最大值。
   *
   * @param file 数据文件
   * @return 该文件本字段的最大值
   */
  @Override
  protected Object evaluateRef(DataFile file) {
    return Conversions.fromByteBuffer(type, safeGet(file.upperBounds(), fieldId));
  }

  /**
   * 创建用于行级增量累积最大值的聚合器。
   *
   * @return 新的 {@link MaxAggregator}
   */
  @Override
  public Aggregator<T> newAggregator() {
    return new MaxAggregator<>(this, comparator);
  }

  /**
   * MAX 行级聚合器：逐行比较保留当前最大值。
   *
   * <p>设计意图：继承 {@link NullSafeAggregator}，由基类处理 null 跳过与文件统计缺失失效， 本类只实现 {@link
   * #update(Object)}（取较大者）与 {@link #current()}（返回当前最大值）。
   */
  private static class MaxAggregator<T> extends NullSafeAggregator<T, T> {
    private final Comparator<T> comparator;
    private T max = null;

    MaxAggregator(MaxAggregate<T> aggregate, Comparator<T> comparator) {
      super(aggregate);
      this.comparator = comparator;
    }

    /**
     * 用一个非 null 值更新最大值。
     *
     * <p>逻辑：当前最大值为 null 或新值更大时替换。
     *
     * @param value 新输入值
     */
    @Override
    protected void update(T value) {
      if (max == null || comparator.compare(value, max) > 0) {
        this.max = value;
      }
    }

    /** 返回当前累积的最大值。 */
    @Override
    protected T current() {
      return max;
    }
  }
}
