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

import java.nio.ByteBuffer;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;

/**
 * {@link DataFile} 的通用实现类，作为数据文件元数据的标准载体。
 *
 * <p>所属模块：iceberg-core（数据文件元数据实现层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载单个数据文件的全部元数据：路径、格式、分区数据、文件大小、记录数、列级指标、 切分偏移、等值字段 id、排序订单 id、加密 key 等。
 *   <li>支持 Avro 反射式构造（用于 manifest 文件读取）、全字段构造、拷贝构造。
 *   <li>提供 {@link #copy()} 与 {@link #copyWithoutStats()} 以支持不可变性与指标裁剪。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseFile} 复用文件元数据通用字段与 Avro 序列化逻辑，仅绑定 {@link FileContent#DATA} 内容类型。
 *   <li>Avro 反射构造支持从 manifest 文件按 schema 动态实例化，保证读取兼容性。
 *   <li>copyWithoutStats 用于在不需要列级指标的场景（如下发任务）减少序列化开销。
 * </ul>
 *
 * <p>上下游关系：被 {@link DataFiles.Builder} 构造；被 manifest 写入器写入、manifest 读取器读取； 被扫描任务、维护操作广泛引用。
 */
class GenericDataFile extends BaseFile<DataFile> implements DataFile {
  /**
   * Avro 反射构造方法：用于读取 manifest 文件时按 Avro schema 实例化本类。
   *
   * @param avroSchema Avro schema
   */
  GenericDataFile(Schema avroSchema) {
    super(avroSchema);
  }

  /**
   * 全字段构造方法：用于新建数据文件元数据。
   *
   * @param specId 分区 spec id
   * @param filePath 文件路径
   * @param format 文件格式
   * @param partition 分区数据；无分区表为 null
   * @param fileSizeInBytes 文件大小（字节）
   * @param metrics 文件指标
   * @param keyMetadata 加密 key 元数据；可为 null
   * @param splitOffsets 切分偏移列表；可为 null
   * @param equalityFieldIds 等值删除字段 id 数组；可为 null
   * @param sortOrderId 排序订单 id
   */
  GenericDataFile(
      int specId,
      String filePath,
      FileFormat format,
      PartitionData partition,
      long fileSizeInBytes,
      Metrics metrics,
      ByteBuffer keyMetadata,
      List<Long> splitOffsets,
      int[] equalityFieldIds,
      Integer sortOrderId) {
    super(
        specId,
        FileContent.DATA,
        filePath,
        format,
        partition,
        fileSizeInBytes,
        metrics.recordCount(),
        metrics.columnSizes(),
        metrics.valueCounts(),
        metrics.nullValueCounts(),
        metrics.nanValueCounts(),
        metrics.lowerBounds(),
        metrics.upperBounds(),
        splitOffsets,
        equalityFieldIds,
        sortOrderId,
        keyMetadata);
  }

  /**
   * 拷贝构造方法。
   *
   * @param toCopy 源数据文件
   * @param fullCopy true 进行完整拷贝；false 跳过列级指标以减小体积
   */
  private GenericDataFile(GenericDataFile toCopy, boolean fullCopy) {
    super(toCopy, fullCopy);
  }

  /** Java 序列化用无参构造。 */
  GenericDataFile() {}

  /**
   * 返回不包含列级指标的副本，常用于任务下发以减少序列化数据量。
   *
   * @return 不含 stats 的 {@link DataFile}
   */
  @Override
  public DataFile copyWithoutStats() {
    return new GenericDataFile(this, false /* drop stats */);
  }

  /**
   * 返回完整深拷贝。
   *
   * @return 完整副本
   */
  @Override
  public DataFile copy() {
    return new GenericDataFile(this, true /* full copy */);
  }

  /**
   * 根据分区结构类型构造 Avro schema，并把数据文件类型与分区数据类型分别映射到 {@code GenericDataFile} 与 {@code PartitionData}，以便
   * Avro 反射读写。
   *
   * @param partitionStruct 分区结构类型
   * @return Avro schema
   */
  @Override
  protected Schema getAvroSchema(Types.StructType partitionStruct) {
    Types.StructType type = DataFile.getType(partitionStruct);
    return AvroSchemaUtil.convert(
        type,
        ImmutableMap.of(
            type, GenericDataFile.class.getName(),
            partitionStruct, PartitionData.class.getName()));
  }
}
