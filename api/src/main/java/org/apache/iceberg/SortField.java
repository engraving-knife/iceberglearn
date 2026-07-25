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
package org.apache.iceberg;

import java.io.Serializable;
import java.util.Objects;
import org.apache.iceberg.transforms.Transform;

/**
 * 排序字段：{@link SortOrder} 中的一个字段，描述按哪个源字段、经何种变换、以何种方向排序。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：承载排序所需的四要素——源字段 ID、变换函数、排序方向（升/降）、null 顺序。
 *
 * <p>设计意图：通过 {@link Transform} 把源字段值映射为可排序的值（如 bucket、truncate 等）， 再结合方向与 null 顺序构成完整排序语义。实现 {@link
 * Serializable} 以支持分布式排序。
 *
 * <p>上下游关系：由 {@link SortOrder} 持有；被写入器用于按序写数据、被读取器用于按序合并读取。
 */
public class SortField implements Serializable {

  private final Transform<?, ?> transform;
  private final int sourceId;
  private final SortDirection direction;
  private final NullOrder nullOrder;

  SortField(Transform<?, ?> transform, int sourceId, SortDirection direction, NullOrder nullOrder) {
    this.transform = transform;
    this.sourceId = sourceId;
    this.direction = direction;
    this.nullOrder = nullOrder;
  }

  /**
   * 返回用于从源值生成排序值的变换函数。
   *
   * @param <S> 变换函数输入值的 Java 类型
   * @param <T> 变换函数输出值的 Java 类型
   * @return 变换函数
   */
  @SuppressWarnings("unchecked")
  public <S, T> Transform<S, T> transform() {
    return (Transform<S, T>) transform;
  }

  /** 返回本排序字段对应的源字段 ID（来自 {@link SortOrder} 所属表的 schema）。 */
  public int sourceId() {
    return sourceId;
  }

  /** 返回排序方向（升序/降序）。 */
  public SortDirection direction() {
    return direction;
  }

  /** 返回 null 值的排序顺序（nulls first/nulls last）。 */
  public NullOrder nullOrder() {
    return nullOrder;
  }

  /**
   * 判断本字段的排序是否满足另一个字段的排序要求。
   *
   * <p>逻辑：若两者完全相等则满足；若 sourceId/direction/nullOrder 任一不同则不满足； 否则委托 {@link
   * Transform#satisfiesOrderOf} 判断变换函数是否兼容。
   *
   * @param other 另一个排序字段
   * @return 本排序满足给定排序返回 true
   */
  public boolean satisfies(SortField other) {
    if (Objects.equals(this, other)) {
      return true;
    } else if (sourceId != other.sourceId
        || direction != other.direction
        || nullOrder != other.nullOrder) {
      return false;
    }

    return transform.satisfiesOrderOf(other.transform);
  }

  /** 返回可读字符串表示：transform(sourceId) direction nullOrder。 */
  @Override
  public String toString() {
    return transform + "(" + sourceId + ") " + direction + " " + nullOrder;
  }

  /**
   * 相等判断：比较 transform 字符串、sourceId、direction、nullOrder 四项。
   *
   * @param other 比较对象
   * @return 是否相等
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null || getClass() != other.getClass()) {
      return false;
    }

    SortField that = (SortField) other;
    return transform.toString().equals(that.transform.toString())
        && sourceId == that.sourceId
        && direction == that.direction
        && nullOrder == that.nullOrder;
  }

  /** 哈希值由 transform、sourceId、direction、nullOrder 共同决定。 */
  @Override
  public int hashCode() {
    return Objects.hash(transform, sourceId, direction, nullOrder);
  }
}
