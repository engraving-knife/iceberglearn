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

import java.util.Collections;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;

/**
 * 文件级说明：未绑定的排序序号（UnboundSortOrder），表示尚未关联到具体 schema 的排序定义。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示从元数据反序列化得到的、尚未绑定到 {@link Schema} 的排序序号。
 *   <li>提供 {@link #bind(Schema)} 将未绑定排序序号绑定到具体 schema，生成已绑定的 {@link SortOrder}。
 *   <li>提供 {@link #bindUnchecked(Schema)} 跳过类型校验的快速绑定路径。
 * </ul>
 *
 * <p>设计意图：Iceberg 元数据中持久化的排序序号以"未绑定"形式存储（字段以 sourceId 引用， transform 以字符串表示），读取时需绑定到当前 schema
 * 才能使用。分离未绑定/已绑定两种形态， 使元数据序列化与 schema 演化解耦——即使表 schema 已变更，未绑定的排序定义仍可尝试绑定到 新 schema（若字段仍存在）。{@code
 * UNSORTED_ORDER} 是无排序的空对象单例，orderId 固定为 0。
 *
 * <p>上下游关系：由元数据解析器从 JSON 反序列化产生；通过 {@link #bind(Schema)} 转换为 {@link SortOrder} 供扫描与写入使用。
 */
public class UnboundSortOrder {
  private static final UnboundSortOrder UNSORTED_ORDER =
      new UnboundSortOrder(0, Collections.emptyList());

  private final int orderId;
  private final List<UnboundSortField> fields;

  private UnboundSortOrder(int orderId, List<UnboundSortField> fields) {
    this.orderId = orderId;
    this.fields = fields;
  }

  /**
   * 将本未绑定排序序号绑定到指定 schema，生成已绑定的 {@link SortOrder}。
   *
   * <p>逻辑：遍历每个排序字段，在 schema 中查找其 sourceId 对应的类型；若找到则用该类型 重新解析 transform 字符串（确保类型安全），否则直接使用未绑定的
   * transform。最后通过 {@link SortOrder.Builder} 构建已绑定的排序序号。
   *
   * @param schema 目标 schema
   * @return 已绑定的 {@link SortOrder}
   */
  public SortOrder bind(Schema schema) {
    SortOrder.Builder builder = SortOrder.builderFor(schema).withOrderId(orderId);

    for (UnboundSortField field : fields) {
      Type sourceType = schema.findType(field.sourceId);
      Transform<?, ?> transform;
      if (sourceType != null) {
        transform = Transforms.fromString(sourceType, field.transform.toString());
      } else {
        transform = field.transform;
      }
      builder.addSortField(transform, field.sourceId, field.direction, field.nullOrder);
    }

    return builder.build();
  }

  /**
   * 将本未绑定排序序号绑定到指定 schema（跳过类型校验），生成已绑定的 {@link SortOrder}。
   *
   * <p>与 {@link #bind(Schema)} 的区别：不重新解析 transform 字符串，直接使用未绑定的
   * transform，跳过类型安全校验。适用于性能敏感且已确保类型兼容的场景。
   *
   * @param schema 目标 schema
   * @return 已绑定的 {@link SortOrder}
   */
  SortOrder bindUnchecked(Schema schema) {
    SortOrder.Builder builder = SortOrder.builderFor(schema).withOrderId(orderId);

    for (UnboundSortField field : fields) {
      builder.addSortField(field.transform, field.sourceId, field.direction, field.nullOrder);
    }

    return builder.buildUnchecked();
  }

  /** 返回排序序号 ID（0 表示无排序）。 */
  int orderId() {
    return orderId;
  }

  /** 返回排序字段列表。 */
  List<UnboundSortField> fields() {
    return fields;
  }

  /**
   * 创建一个新的 {@link Builder 未绑定排序序号构建器}。
   *
   * @return 未绑定排序序号构建器
   */
  static Builder builder() {
    return new Builder();
  }

  /**
   * 文件级说明：未绑定排序序号构建器。
   *
   * <p>职责：链式收集排序字段并配置 orderId，最终构建 {@link UnboundSortOrder}。 通过 {@link #builder()} 创建新实例。
   */
  static class Builder {
    private final List<UnboundSortField> fields = Lists.newArrayList();
    private Integer orderId = null;

    private Builder() {}

    /** 设置排序序号 ID。 */
    Builder withOrderId(int newOrderId) {
      this.orderId = newOrderId;
      return this;
    }

    /**
     * 添加一个排序字段。
     *
     * @param transformAsString transform 的字符串表示
     * @param sourceId 源字段 ID
     * @param direction 排序方向
     * @param nullOrder 空值排序位置
     * @return this，便于链式调用
     */
    Builder addSortField(
        String transformAsString, int sourceId, SortDirection direction, NullOrder nullOrder) {
      fields.add(new UnboundSortField(transformAsString, sourceId, direction, nullOrder));
      return this;
    }

    /**
     * 构建并返回 {@link UnboundSortOrder}。
     *
     * <p>逻辑：若无排序字段且 orderId 为 0 或 null，返回 {@code UNSORTED_ORDER} 单例； 若无字段但 orderId 非 0，抛异常（无排序的 ID
     * 必须为 0）。若有字段但 orderId 为 0， 抛异常（0 保留给无排序）。orderId 为 null 时默认为 1（0 保留给无排序）。
     *
     * @return 构建完成的未绑定排序序号
     * @throws IllegalArgumentException 若 orderId 约束违反
     */
    UnboundSortOrder build() {
      if (fields.isEmpty()) {
        if (orderId != null && orderId != 0) {
          throw new IllegalArgumentException("Unsorted order ID must be 0");
        }
        return UNSORTED_ORDER;
      }

      if (orderId != null && orderId == 0) {
        throw new IllegalArgumentException("Sort order ID 0 is reserved for unsorted order");
      }

      // default ID to 1 as 0 is reserved for unsorted order
      int actualOrderId = orderId != null ? orderId : 1;
      return new UnboundSortOrder(actualOrderId, fields);
    }
  }

  /** 文件级说明：未绑定的单个排序字段，记录 transform、源字段 ID、方向与空值位置。 */
  static class UnboundSortField {
    private final Transform<?, ?> transform;
    private final int sourceId;
    private final SortDirection direction;
    private final NullOrder nullOrder;

    /**
     * 构造未绑定排序字段。
     *
     * @param transformAsString transform 的字符串表示，将解析为 {@link Transform} 对象
     * @param sourceId 源字段 ID
     * @param direction 排序方向
     * @param nullOrder 空值排序位置
     */
    private UnboundSortField(
        String transformAsString, int sourceId, SortDirection direction, NullOrder nullOrder) {
      this.transform = Transforms.fromString(transformAsString);
      this.sourceId = sourceId;
      this.direction = direction;
      this.nullOrder = nullOrder;
    }

    /** 返回 transform 的字符串表示。 */
    public String transformAsString() {
      return transform.toString();
    }

    /** 返回源字段 ID。 */
    public int sourceId() {
      return sourceId;
    }

    /** 返回排序方向。 */
    public SortDirection direction() {
      return direction;
    }

    /** 返回空值排序位置。 */
    public NullOrder nullOrder() {
      return nullOrder;
    }
  }
}
