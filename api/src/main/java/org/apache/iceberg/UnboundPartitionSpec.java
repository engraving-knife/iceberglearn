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

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;

/**
 * 未绑定到具体 schema 的分区 spec 表示。
 *
 * <p>所属模块：iceberg-api（分区抽象层）。
 *
 * <p>职责：以"transform 字符串 + sourceId + 可选 partitionId + name"的形式承载分区字段， 不直接引用 schema 中的类型，便于序列化与跨
 * schema 传输；通过 {@link #bind(Schema)} 在 给定 schema 上还原为 {@link PartitionSpec}。
 *
 * <p>设计意图：将"分区 spec 的结构"与"具体 schema 类型"解耦，使 spec 可独立于 schema 持久化或传输；绑定时再按 schema 解析 transform
 * 类型并校验兼容。
 *
 * <p>上下游关系：由 {@link PartitionSpec#toUnbound()} 转换而来；通过 {@link #bind(Schema)} 还原为
 * PartitionSpec；被元数据序列化/反序列化路径使用。
 */
public class UnboundPartitionSpec {

  private final int specId;
  private final List<UnboundPartitionField> fields;

  /**
   * 构造未绑定分区 spec。
   *
   * @param specId spec ID
   * @param fields 未绑定分区字段列表
   */
  public UnboundPartitionSpec(int specId, List<UnboundPartitionField> fields) {
    this.specId = specId;
    this.fields = fields;
  }

  /** 返回 spec ID。 */
  public int specId() {
    return specId;
  }

  /** 返回未绑定分区字段列表。 */
  public List<UnboundPartitionField> fields() {
    return fields;
  }

  /**
   * 在给定 schema 上绑定并构建 {@link PartitionSpec}（含兼容性校验）。
   *
   * @param schema 表 schema
   * @return 绑定后的 {@link PartitionSpec}
   */
  public PartitionSpec bind(Schema schema) {
    return copyToBuilder(schema).build();
  }

  /**
   * 在给定 schema 上绑定并构建 {@link PartitionSpec}（不做兼容性校验，内部使用）。
   *
   * @param schema 表 schema
   * @return 绑定后的 {@link PartitionSpec}
   */
  PartitionSpec bindUnchecked(Schema schema) {
    return copyToBuilder(schema).buildUnchecked();
  }

  /**
   * 把本未绑定 spec 拷贝到 PartitionSpec.Builder。
   *
   * <p>逻辑：以 specId 构造 builder，逐个字段查找 schema 中的源类型；若类型存在则用带类型 的 {@link Transforms#fromString(Type,
   * String)} 解析 transform，否则用无类型版本； partitionId 非 null 时显式指定字段 ID，否则由 builder 自增分配。
   *
   * @param schema 表 schema
   * @return 已填充字段的 builder
   */
  private PartitionSpec.Builder copyToBuilder(Schema schema) {
    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema).withSpecId(specId);

    for (UnboundPartitionField field : fields) {
      Type fieldType = schema.findType(field.sourceId);
      Transform<?, ?> transform;
      if (fieldType != null) {
        transform = Transforms.fromString(fieldType, field.transform.toString());
      } else {
        transform = Transforms.fromString(field.transform.toString());
      }
      if (field.partitionId != null) {
        builder.add(field.sourceId, field.partitionId, field.name, transform);
      } else {
        builder.add(field.sourceId, field.name, transform);
      }
    }

    return builder;
  }

  /** 创建未绑定分区 spec 的构建器（包级可见）。 */
  static Builder builder() {
    return new Builder();
  }

  /**
   * 未绑定分区 spec 的构建器。
   *
   * <p>设计意图：累积字段并支持指定 specId，最终构造 {@link UnboundPartitionSpec}。
   */
  static class Builder {
    private final List<UnboundPartitionField> fields;
    private int specId = 0;

    private Builder() {
      this.fields = Lists.newArrayList();
    }

    /** 设置 spec ID。 */
    Builder withSpecId(int newSpecId) {
      this.specId = newSpecId;
      return this;
    }

    /** 添加一个带显式 partitionId 的字段。 */
    Builder addField(String transformAsString, int sourceId, int partitionId, String name) {
      fields.add(new UnboundPartitionField(transformAsString, sourceId, partitionId, name));
      return this;
    }

    /** 添加一个由 builder 自增分配 partitionId 的字段。 */
    Builder addField(String transformAsString, int sourceId, String name) {
      fields.add(new UnboundPartitionField(transformAsString, sourceId, null, name));
      return this;
    }

    /** 构建未绑定分区 spec。 */
    UnboundPartitionSpec build() {
      return new UnboundPartitionSpec(specId, fields);
    }
  }

  /**
   * 未绑定分区字段：以 transform 字符串、sourceId、可选 partitionId、name 描述，不绑定类型。
   *
   * <p>设计意图：在未绑定形式下仍保留 transform 的字符串与解析后实例两种视图，便于 序列化与按需绑定。
   */
  static class UnboundPartitionField {
    private final Transform<?, ?> transform;
    private final int sourceId;
    private final Integer partitionId;
    private final String name;

    /** 返回解析后的 transform 实例。 */
    public Transform<?, ?> transform() {
      return transform;
    }

    /** 返回 transform 的字符串形式。 */
    public String transformAsString() {
      return transform.toString();
    }

    /** 返回源字段 ID。 */
    public int sourceId() {
      return sourceId;
    }

    /** 返回分区字段 ID，可能为 null（表示由 builder 自增分配）。 */
    public Integer partitionId() {
      return partitionId;
    }

    /** 返回分区字段名。 */
    public String name() {
      return name;
    }

    /**
     * 构造未绑定分区字段。
     *
     * @param transformAsString transform 字符串
     * @param sourceId 源字段 ID
     * @param partitionId 分区字段 ID，可为 null
     * @param name 分区字段名
     */
    private UnboundPartitionField(
        String transformAsString, int sourceId, Integer partitionId, String name) {
      this.transform = Transforms.fromString(transformAsString);
      this.sourceId = sourceId;
      this.partitionId = partitionId;
      this.name = name;
    }
  }
}
