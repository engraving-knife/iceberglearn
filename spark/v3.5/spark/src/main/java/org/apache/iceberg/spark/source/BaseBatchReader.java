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
import java.util.Set;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.spark.data.vectorized.VectorizedSparkOrcReaders;
import org.apache.iceberg.spark.data.vectorized.VectorizedSparkParquetReaders;
import org.apache.iceberg.types.TypeUtil;
import org.apache.spark.sql.vectorized.ColumnarBatch;

/**
 * 向量化批量读取器基类：返回 Spark {@link ColumnarBatch}。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：根据文件格式（Parquet/ORC）构造对应的向量化读取 Iterable， 按 batchSize 返回 ColumnarBatch，支持删除过滤与常量列注入。
 *
 * <p>设计意图：把格式分支封装在 newBatchIterable，子类只需指定任务类型； Parquet 路径启用 reuseContainers，因为 Spark 立即消费
 * batch，可复用底层内存减少分配开销； ORC 路径先剔除常量与元数据字段再投影，常量由 reader 注入。
 *
 * <p>上下游关系：继承 {@link BaseReader}，被向量化扫描 reader 继承；依赖 iceberg-parquet/orc 与 VectorizedSpark*Readers。
 */
abstract class BaseBatchReader<T extends ScanTask> extends BaseReader<ColumnarBatch, T> {
  private final int batchSize;

  /** 构造批量读取器，记录 batchSize。 */
  BaseBatchReader(
      Table table,
      ScanTaskGroup<T> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive,
      int batchSize) {
    super(table, taskGroup, tableSchema, expectedSchema, caseSensitive);
    this.batchSize = batchSize;
  }

  /**
   * 按文件格式构造 ColumnarBatch 的可迭代读取器。
   *
   * <p>逻辑：PARQUET 走 newParquetIterable，ORC 走 newOrcIterable，其余抛出不支持。
   *
   * @param inputFile 输入文件
   * @param format 文件格式
   * @param start 起始偏移
   * @param length 读取长度
   * @param residual 残余过滤器
   * @param idToConstant 常量列映射
   * @param deleteFilter 删除过滤器
   * @return ColumnarBatch 可迭代对象
   */
  protected CloseableIterable<ColumnarBatch> newBatchIterable(
      InputFile inputFile,
      FileFormat format,
      long start,
      long length,
      Expression residual,
      Map<Integer, ?> idToConstant,
      SparkDeleteFilter deleteFilter) {
    switch (format) {
      case PARQUET:
        return newParquetIterable(inputFile, start, length, residual, idToConstant, deleteFilter);

      case ORC:
        return newOrcIterable(inputFile, start, length, residual, idToConstant);

      default:
        throw new UnsupportedOperationException(
            "Format: " + format + " not supported for batched reads");
    }
  }

  /**
   * 构造 Parquet 向量化读取 Iterable。
   *
   * <p>逻辑：若有删除过滤则用 requiredSchema；调用 VectorizedSparkParquetReaders.buildReader 构造 reader； 设置
   * batchSize、残余过滤、reuseContainers（Spark 立即消费 batch 故可复用内存）。
   */
  private CloseableIterable<ColumnarBatch> newParquetIterable(
      InputFile inputFile,
      long start,
      long length,
      Expression residual,
      Map<Integer, ?> idToConstant,
      SparkDeleteFilter deleteFilter) {
    // get required schema if there are deletes
    Schema requiredSchema = deleteFilter != null ? deleteFilter.requiredSchema() : expectedSchema();

    return Parquet.read(inputFile)
        .project(requiredSchema)
        .split(start, length)
        .createBatchedReaderFunc(
            fileSchema ->
                VectorizedSparkParquetReaders.buildReader(
                    requiredSchema, fileSchema, idToConstant, deleteFilter))
        .recordsPerBatch(batchSize)
        .filter(residual)
        .caseSensitive(caseSensitive())
        // Spark eagerly consumes the batches. So the underlying memory allocated could be reused
        // without worrying about subsequent reads clobbering over each other. This improves
        // read performance as every batch read doesn't have to pay the cost of allocating memory.
        .reuseContainers()
        .withNameMapping(nameMapping())
        .build();
  }

  /**
   * 构造 ORC 向量化读取 Iterable。
   *
   * <p>逻辑：合并常量与元数据字段 ID，从 expectedSchema 剔除后投影（这些字段由 reader 注入）； 调用
   * VectorizedSparkOrcReaders.buildReader 构造 reader。
   */
  private CloseableIterable<ColumnarBatch> newOrcIterable(
      InputFile inputFile,
      long start,
      long length,
      Expression residual,
      Map<Integer, ?> idToConstant) {
    Set<Integer> constantFieldIds = idToConstant.keySet();
    Set<Integer> metadataFieldIds = MetadataColumns.metadataFieldIds();
    Sets.SetView<Integer> constantAndMetadataFieldIds =
        Sets.union(constantFieldIds, metadataFieldIds);
    Schema schemaWithoutConstantAndMetadataFields =
        TypeUtil.selectNot(expectedSchema(), constantAndMetadataFieldIds);

    return ORC.read(inputFile)
        .project(schemaWithoutConstantAndMetadataFields)
        .split(start, length)
        .createBatchedReaderFunc(
            fileSchema ->
                VectorizedSparkOrcReaders.buildReader(expectedSchema(), fileSchema, idToConstant))
        .recordsPerBatch(batchSize)
        .filter(residual)
        .caseSensitive(caseSensitive())
        .withNameMapping(nameMapping())
        .build();
  }
}
