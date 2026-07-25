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
 * 删除文件（DeleteFile）的通用实现类。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DeleteFile} 接口，表示一个位置删除文件或等值删除文件。
 *   <li>继承 {@link BaseFile} 复用文件元数据存储（路径、格式、分区、指标等）。
 *   <li>支持 Avro 反射序列化/反序列化，用于 manifest 文件读写。
 * </ul>
 *
 * <p>设计意图：与 {@link GenericDataFile} 对称，专门承载删除文件特有的元数据 （如 equalityFieldIds）。通过 Avro schema 反射构造，支持
 * manifest 读取时实例化。
 *
 * <p>上下游关系：由 {@link ManifestWriter} 写入 manifest、{@link ManifestReader} 读取； 被 {@link
 * DeleteFileIndex} 等使用。
 */
class GenericDeleteFile extends BaseFile<DeleteFile> implements DeleteFile {
  /**
   * Avro 反射构造器：读取 manifest 文件时由 Avro 反射实例化。
   *
   * @param avroSchema Avro schema
   */
  GenericDeleteFile(Schema avroSchema) {
    super(avroSchema);
  }

  /**
   * 全参数构造器：用完整元数据构造删除文件。
   *
   * @param specId 分区 spec id
   * @param content 文件内容类型（位置删除/等值删除）
   * @param filePath 文件路径
   * @param format 文件格式
   * @param partition 分区数据
   * @param fileSizeInBytes 文件大小（字节）
   * @param metrics 文件指标
   * @param equalityFieldIds 等值删除的字段 id 数组
   * @param sortOrderId 排序顺序 id
   * @param splitOffsets 分割偏移量列表
   * @param keyMetadata 加密密钥元数据
   */
  GenericDeleteFile(
      int specId,
      FileContent content,
      String filePath,
      FileFormat format,
      PartitionData partition,
      long fileSizeInBytes,
      Metrics metrics,
      int[] equalityFieldIds,
      Integer sortOrderId,
      List<Long> splitOffsets,
      ByteBuffer keyMetadata) {
    super(
        specId,
        content,
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
   * 拷贝构造器。
   *
   * @param toCopy 待拷贝的删除文件
   * @param fullCopy true 全量拷贝；false 丢弃列级统计
   */
  private GenericDeleteFile(GenericDeleteFile toCopy, boolean fullCopy) {
    super(toCopy, fullCopy);
  }

  /** Java 序列化用无参构造器。 */
  GenericDeleteFile() {}

  /** 返回丢弃列级统计的副本。 */
  @Override
  public DeleteFile copyWithoutStats() {
    return new GenericDeleteFile(this, false /* drop stats */);
  }

  /** 返回全量深拷贝副本。 */
  @Override
  public DeleteFile copy() {
    return new GenericDeleteFile(this, true /* full copy */);
  }

  /**
   * 根据分区结构生成本类对应的 Avro schema。
   *
   * <p>逻辑：用 {@link AvroSchemaUtil#convert} 把 DataFile 类型结构转为 Avro schema， 并绑定 GenericDeleteFile 与
   * PartitionData 作为反射类。
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
            type, GenericDeleteFile.class.getName(),
            partitionStruct, PartitionData.class.getName()));
  }
}
