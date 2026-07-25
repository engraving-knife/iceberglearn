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
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：{@link BlobMetadata} 的通用实现类，描述一个统计 Blob 的元信息。
 *
 * <p>所属模块：iceberg-core。职责：实现 {@link BlobMetadata} 接口，持有 blob 类型、 快照 ID、序列号、输入字段列表和属性映射，并提供从 Puffin
 * BlobMetadata 转换的工厂方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>作为 BlobMetadata 接口的"值对象"实现，不可变。
 *   <li>{@link #from(org.apache.iceberg.puffin.BlobMetadata)} 桥接 Puffin 层的 BlobMetadata 到 Iceberg
 *       core 层的 BlobMetadata，解耦两层。
 * </ul>
 *
 * <p>上下游关系：被 {@link GenericStatisticsFile} 持有；由统计文件解析器创建。
 */
public class GenericBlobMetadata implements BlobMetadata {

  /**
   * 从 Puffin 层的 BlobMetadata 转换为 Iceberg core 层的 GenericBlobMetadata。
   *
   * <p>设计要点：桥接 Puffin 统计文件格式层与 Iceberg core 元数据层，解耦两层。
   *
   * @param puffinMetadata Puffin 层的 blob 元信息
   * @return 转换后的 GenericBlobMetadata 实例
   */
  public static BlobMetadata from(org.apache.iceberg.puffin.BlobMetadata puffinMetadata) {
    return new GenericBlobMetadata(
        puffinMetadata.type(),
        puffinMetadata.snapshotId(),
        puffinMetadata.sequenceNumber(),
        puffinMetadata.inputFields(),
        puffinMetadata.properties());
  }

  private final String type;
  private final long sourceSnapshotId;
  private final long sourceSnapshotSequenceNumber;
  private final List<Integer> fields;
  private final Map<String, String> properties;

  /**
   * 构造 blob 元信息，校验非空字段并把列表/映射拷贝为不可变副本。
   *
   * @param type blob 类型
   * @param sourceSnapshotId 来源快照 ID
   * @param sourceSnapshotSequenceNumber 来源快照序列号
   * @param fields 输入字段 ID 列表
   * @param properties 属性映射
   */
  public GenericBlobMetadata(
      String type,
      long sourceSnapshotId,
      long sourceSnapshotSequenceNumber,
      List<Integer> fields,
      Map<String, String> properties) {
    Preconditions.checkNotNull(type, "type is null");
    Preconditions.checkNotNull(fields, "fields is null");
    Preconditions.checkNotNull(properties, "properties is null");
    this.type = type;
    this.sourceSnapshotId = sourceSnapshotId;
    this.sourceSnapshotSequenceNumber = sourceSnapshotSequenceNumber;
    this.fields = ImmutableList.copyOf(fields);
    this.properties = ImmutableMap.copyOf(properties);
  }

  @Override
  /**
   * 返回 blob 类型标识。
   *
   * @return blob 类型字符串
   */
  public String type() {
    return type;
  }

  @Override
  public long sourceSnapshotId() {
    return sourceSnapshotId;
  }

  @Override
  public long sourceSnapshotSequenceNumber() {
    return sourceSnapshotSequenceNumber;
  }

  @Override
  public List<Integer> fields() {
    return fields;
  }

  @Override
  /**
   * 返回 blob 的附加属性。
   *
   * @return 属性映射
   */
  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenericBlobMetadata that = (GenericBlobMetadata) o;
    return sourceSnapshotId == that.sourceSnapshotId
        && sourceSnapshotSequenceNumber == that.sourceSnapshotSequenceNumber
        && Objects.equals(type, that.type)
        && Objects.equals(fields, that.fields)
        && Objects.equals(properties, that.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, sourceSnapshotId, sourceSnapshotSequenceNumber, fields, properties);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", GenericBlobMetadata.class.getSimpleName() + "[", "]")
        .add("type='" + type + "'")
        .add("sourceSnapshotId=" + sourceSnapshotId)
        .add("sourceSnapshotSequenceNumber=" + sourceSnapshotSequenceNumber)
        .add("fields=" + fields)
        .add("properties=" + properties)
        .toString();
  }
}
