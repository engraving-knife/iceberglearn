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

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：Puffin 文件中某个已落盘 Blob 的"元信息"描述。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>记录单个 Blob 在 Puffin 文件内的定位信息（offset/length）及其类型、输入字段、 关联快照与序列号、压缩编码、附加属性。
 *   <li>作为 {@link FileMetadata} 的组成部分，由 {@link PuffinReader} 解析 footer 后返回， 供调用方按需按偏移读取 Blob 字节。
 * </ul>
 *
 * <p>设计意图：与 {@link Blob}（写入侧含字节缓冲）不同，本类只承载元数据， 不持有 Blob 内容，便于读取侧在不实际加载全部 Blob 数据的情况下描述文件布局。 同样采用
 * Immutable* 拷贝保证不可变。
 *
 * <p>上下游关系：由 {@link FileMetadataParser} 从 footer JSON 解析得到， 由 {@link PuffinReader}
 * 暴露给统计信息读取/删除向量加载等逻辑使用。
 */
public class BlobMetadata {
  private final String type;
  private final List<Integer> inputFields;
  private final long snapshotId;
  private final long sequenceNumber;
  private final long offset;
  private final long length;
  private final String compressionCodec;
  private final Map<String, String> properties;

  /**
   * 全参数构造方法。
   *
   * <p>逻辑：对必填字段做非空校验，并将集合类型以 Immutable* 拷贝存入字段。 {@code compressionCodec} 可为 null，表示该 Blob 未被压缩。
   *
   * @param type Blob 类型字符串（参见 {@link StandardBlobTypes}）
   * @param inputFields 生成该 Blob 所依据的表字段序号列表
   * @param snapshotId 关联的 Iceberg 表快照 ID
   * @param sequenceNumber 关联的快照序列号
   * @param offset Blob 数据在 Puffin 文件中的起始字节偏移
   * @param length Blob 数据在 Puffin 文件中的字节长度
   * @param compressionCodec 压缩编码名称，null 表示未压缩
   * @param properties 附加属性键值对
   */
  public BlobMetadata(
      String type,
      List<Integer> inputFields,
      long snapshotId,
      long sequenceNumber,
      long offset,
      long length,
      @Nullable String compressionCodec,
      Map<String, String> properties) {
    Preconditions.checkNotNull(type, "type is null");
    Preconditions.checkNotNull(inputFields, "inputFields is null");
    Preconditions.checkNotNull(properties, "properties is null");
    this.type = type;
    this.inputFields = ImmutableList.copyOf(inputFields);
    this.snapshotId = snapshotId;
    this.sequenceNumber = sequenceNumber;
    this.offset = offset;
    this.length = length;
    this.compressionCodec = compressionCodec;
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

  /** 返回生成该 Blob 所依据的 Iceberg 表快照 ID。 */
  public long snapshotId() {
    return snapshotId;
  }

  /** 返回生成该 Blob 所依据的快照序列号。 */
  public long sequenceNumber() {
    return sequenceNumber;
  }

  /** 返回该 Blob 数据在 Puffin 文件中的起始字节偏移。 */
  public long offset() {
    return offset;
  }

  /** 返回该 Blob 数据在 Puffin 文件中的字节长度。 */
  public long length() {
    return length;
  }

  /** 返回压缩编码名称；null 表示未压缩。 */
  @Nullable
  public String compressionCodec() {
    return compressionCodec;
  }

  /** 返回附加属性键值对。 */
  public Map<String, String> properties() {
    return properties;
  }
}
