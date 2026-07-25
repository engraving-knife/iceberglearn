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
package org.apache.iceberg.puffin;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：Puffin 文件中单个"数据块（Blob）"的内存表示。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现所在模块，被统计信息写入/读取、 删除向量等场景使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装待写入 Puffin 文件的一个 Blob：类型、输入字段列表、所属快照与序列号、 原始字节缓冲、期望的压缩方式及附加属性。
 *   <li>作为 {@link PuffinWriter} 写入流程中的输入数据单元，与面向读取的 {@link BlobMetadata}（仅含元信息）形成对照。
 * </ul>
 *
 * <p>设计意图：该类是不可变值对象，所有集合入参通过 Guava Immutable* 拷贝， 避免外部修改影响内部状态；{@code requestedCompression} 允许为
 * null， 表示由 writer 的默认压缩策略决定，从而支持文件级与 Blob 级两层压缩配置。
 *
 * <p>上下游关系：由统计信息收集器（如 NDV/Bloom/删除向量生成逻辑）构造， 传递给 {@link PuffinWriter} 序列化到 Puffin 文件；读取侧对应类型为
 * {@link BlobMetadata}。
 */
public final class Blob {
  private final String type;
  private final List<Integer> inputFields;
  private final long snapshotId;
  private final long sequenceNumber;
  private final ByteBuffer blobData;
  private final PuffinCompressionCodec requestedCompression;
  private final Map<String, String> properties;

  /**
   * 构造 Blob，使用默认值：不指定压缩方式、空属性。
   *
   * @param type Blob 类型字符串（参见 {@link StandardBlobTypes}）
   * @param inputFields 生成该 Blob 所依据的表字段序号列表
   * @param snapshotId 关联的 Iceberg 表快照 ID
   * @param sequenceNumber 关联的快照序列号
   * @param blobData Blob 原始字节数据
   */
  public Blob(
      String type,
      List<Integer> inputFields,
      long snapshotId,
      long sequenceNumber,
      ByteBuffer blobData) {
    this(type, inputFields, snapshotId, sequenceNumber, blobData, null, ImmutableMap.of());
  }

  /**
   * 全参数构造方法，指定压缩方式与属性。
   *
   * <p>逻辑：对入参做非空校验，并将集合类型以 Immutable* 拷贝存入字段， 保证不可变性。{@code requestedCompression} 为 null 表示使用
   * writer 默认策略。
   *
   * @param type Blob 类型字符串（参见 {@link StandardBlobTypes}）
   * @param inputFields 生成该 Blob 所依据的表字段序号列表
   * @param snapshotId 关联的 Iceberg 表快照 ID
   * @param sequenceNumber 关联的快照序列号
   * @param blobData Blob 原始字节数据
   * @param requestedCompression 期望的压缩方式，null 表示由 writer 决定
   * @param properties 附加属性键值对
   */
  public Blob(
      String type,
      List<Integer> inputFields,
      long snapshotId,
      long sequenceNumber,
      ByteBuffer blobData,
      @Nullable PuffinCompressionCodec requestedCompression,
      Map<String, String> properties) {
    Preconditions.checkNotNull(type, "type is null");
    Preconditions.checkNotNull(inputFields, "inputFields is null");
    Preconditions.checkNotNull(blobData, "blobData is null");
    Preconditions.checkNotNull(properties, "properties is null");
    this.type = type;
    this.inputFields = ImmutableList.copyOf(inputFields);
    this.snapshotId = snapshotId;
    this.sequenceNumber = sequenceNumber;
    this.blobData = blobData;
    this.requestedCompression = requestedCompression;
    this.properties = ImmutableMap.copyOf(properties);
  }

  /** 返回 Blob 类型字符串（参见 {@link StandardBlobTypes}）。 */
  public String type() {
    return type;
  }

  /** 返回生成该 Blob 所依据的表字段序号列表。 */
  public List<Integer> inputFields() {
    return inputFields;
  }

  /** 返回关联的 Iceberg 表快照 ID。 */
  public long snapshotId() {
    return snapshotId;
  }

  /** 返回关联的快照序列号。 */
  public long sequenceNumber() {
    return sequenceNumber;
  }

  /** 返回 Blob 原始字节数据。 */
  public ByteBuffer blobData() {
    return blobData;
  }

  /** 返回期望的压缩方式；为 null 表示由 writer 默认策略决定。 */
  @Nullable
  public PuffinCompressionCodec requestedCompression() {
    return requestedCompression;
  }

  /** 返回附加属性键值对。 */
  public Map<String, String> properties() {
    return properties;
  }
}
