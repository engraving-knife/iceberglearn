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
package org.apache.iceberg.spark.source;

import java.util.Map;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.data.SparkAvroReader;
import org.apache.iceberg.spark.data.SparkOrcReader;
import org.apache.iceberg.spark.data.SparkParquetReaders;
import org.apache.iceberg.types.TypeUtil;
import org.apache.spark.sql.catalyst.InternalRow;

/**
 * 行式读取器基类：按文件格式构造对应的可迭代行读取器。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源行式读取路径）。
 *
 * <p>职责：根据文件格式（Parquet/Avro/ORC）与读取范围、残留过滤条件、投影和常量列， 构造对应的 {@link CloseableIterable}<{@link
 * InternalRow}>，供子类逐行消费。
 *
 * <p>设计意图：把「按格式分派构造读取器」的通用逻辑抽取到基类，子类只需关注行级遍历与过滤。 各格式读取器均复用容器、应用残留过滤、注入常量列与名称映射。
 *
 * <p>上下游关系：继承 {@link BaseReader}，被 {@link RowDataReader} 等行式读取器继承； 内部委托 {@link
 * SparkParquetReaders}、{@link SparkAvroReader}、{@link SparkOrcReader}。
 *
 * @param <T> 扫描任务类型
 */
abstract class BaseRowReader<T extends ScanTask> extends BaseReader<InternalRow, T> {
  BaseRowReader(
      Table table,
      ScanTaskGroup<T> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {
    super(table, taskGroup, tableSchema, expectedSchema, caseSensitive);
  }

  /**
   * 按文件格式创建对应的行可迭代读取器。
   *
   * <p>逻辑：根据 format 分派到 Parquet/Avro/ORC 的具体构造方法，未知格式抛出异常。
   *
   * @param file 输入文件
   * @param format 文件格式
   * @param start 读取起始偏移
   * @param length 读取长度
   * @param residual 残留过滤表达式（文件内过滤）
   * @param projection 投影 schema
   * @param idToConstant 字段 ID 到常量值的映射
   * @return 行可迭代读取器
   * @throws UnsupportedOperationException 当格式不支持时抛出
   */
  protected CloseableIterable<InternalRow> newIterable(
      InputFile file,
      FileFormat format,
      long start,
      long length,
      Expression residual,
      Schema projection,
      Map<Integer, ?> idToConstant) {
    switch (format) {
      case PARQUET:
        return newParquetIterable(file, start, length, residual, projection, idToConstant);

      case AVRO:
        return newAvroIterable(file, start, length, projection, idToConstant);

      case ORC:
        return newOrcIterable(file, start, length, residual, projection, idToConstant);

      default:
        throw new UnsupportedOperationException("Cannot read unknown format: " + format);
    }
  }

  /** 创建 Avro 行可迭代读取器，应用投影、分片、常量列与名称映射。 */
  private CloseableIterable<InternalRow> newAvroIterable(
      InputFile file, long start, long length, Schema projection, Map<Integer, ?> idToConstant) {
    return Avro.read(file)
        .reuseContainers()
        .project(projection)
        .split(start, length)
        .createReaderFunc(readSchema -> new SparkAvroReader(projection, readSchema, idToConstant))
        .withNameMapping(nameMapping())
        .build();
  }

  /** 创建 Parquet 行可迭代读取器，应用投影、残留过滤、常量列与名称映射。 */
  private CloseableIterable<InternalRow> newParquetIterable(
      InputFile file,
      long start,
      long length,
      Expression residual,
      Schema readSchema,
      Map<Integer, ?> idToConstant) {
    return Parquet.read(file)
        .reuseContainers()
        .split(start, length)
        .project(readSchema)
        .createReaderFunc(
            fileSchema -> SparkParquetReaders.buildReader(readSchema, fileSchema, idToConstant))
        .filter(residual)
        .caseSensitive(caseSensitive())
        .withNameMapping(nameMapping())
        .build();
  }

  /**
   * 创建 ORC 行可迭代读取器。
   *
   * <p>设计要点：ORC 读取前先剔除常量列与元数据列，避免读取不存在的物理列； 常量列与元数据列仍通过 idToConstant 与读取器注入。
   */
  private CloseableIterable<InternalRow> newOrcIterable(
      InputFile file,
      long start,
      long length,
      Expression residual,
      Schema readSchema,
      Map<Integer, ?> idToConstant) {
    Schema readSchemaWithoutConstantAndMetadataFields =
        TypeUtil.selectNot(
            readSchema, Sets.union(idToConstant.keySet(), MetadataColumns.metadataFieldIds()));

    return ORC.read(file)
        .project(readSchemaWithoutConstantAndMetadataFields)
        .split(start, length)
        .createReaderFunc(
            readOrcSchema -> new SparkOrcReader(readSchema, readOrcSchema, idToConstant))
        .filter(residual)
        .caseSensitive(caseSensitive())
        .withNameMapping(nameMapping())
        .build();
  }
}
