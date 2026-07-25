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
import java.util.stream.Stream;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.DataTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.source.metrics.TaskNumDeletes;
import org.apache.iceberg.spark.source.metrics.TaskNumSplits;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.rdd.InputFileBlockHolder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.metric.CustomTaskMetric;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spark 行式分区读取器：逐行读取 Iceberg 数据文件并应用删除过滤。
 *
 * <p>所属模块：iceberg-spark（source 子包，Spark 数据源行式读取执行层）。
 *
 * <p>职责：实现 {@link PartitionReader}，遍历文件扫描任务，打开数据文件并应用位置/等值删除过滤， 产出 Spark {@link
 * InternalRow}；同时上报分片数与删除数指标。
 *
 * <p>设计意图：通过 {@link SparkDeleteFilter} 在读取时应用删除文件，先求得 requiredSchema 再读取，避免读取不必要的列；设置 Spark {@link
 * InputFileBlockHolder} 以支持 SQL 中 {@code filename()} 等函数。数据任务（DataTask）直接转为行迭代，不读文件。
 *
 * <p>上下游关系：继承 {@link BaseRowReader}，由 {@link SparkRowReaderFactory} 创建， 消费 {@link
 * SparkInputPartition} 中的任务。
 */
class RowDataReader extends BaseRowReader<FileScanTask> implements PartitionReader<InternalRow> {
  private static final Logger LOG = LoggerFactory.getLogger(RowDataReader.class);

  private final long numSplits;

  /** 从输入分区构造行读取器，按分支解析表 schema。 */
  RowDataReader(SparkInputPartition partition) {
    this(
        partition.table(),
        partition.taskGroup(),
        SnapshotUtil.schemaFor(partition.table(), partition.branch()),
        partition.expectedSchema(),
        partition.isCaseSensitive());
  }

  RowDataReader(
      Table table,
      ScanTaskGroup<FileScanTask> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {

    super(table, taskGroup, tableSchema, expectedSchema, caseSensitive);

    numSplits = taskGroup.tasks().size();
    LOG.debug("Reading {} file split(s) for table {}", numSplits, table.name());
  }

  /** 返回当前任务的分片数与已应用删除数指标。 */
  @Override
  public CustomTaskMetric[] currentMetricsValues() {
    return new CustomTaskMetric[] {
      new TaskNumSplits(numSplits), new TaskNumDeletes(counter().get())
    };
  }

  /** 返回任务引用的数据文件与删除文件流，用于指标统计。 */
  @Override
  protected Stream<ContentFile<?>> referencedFiles(FileScanTask task) {
    return Stream.concat(Stream.of(task.file()), task.deletes().stream());
  }

  /**
   * 打开单个文件扫描任务，返回过滤后的行迭代器。
   *
   * <p>逻辑：构造删除过滤器，求 requiredSchema 与常量列映射；设置 Spark 当前文件块信息 以支持 filename()；读取数据并经删除过滤器过滤后返回迭代器。
   *
   * @param task 文件扫描任务
   * @return 行迭代器
   */
  @Override
  protected CloseableIterator<InternalRow> open(FileScanTask task) {
    String filePath = task.file().path().toString();
    LOG.debug("Opening data file {}", filePath);
    SparkDeleteFilter deleteFilter = new SparkDeleteFilter(filePath, task.deletes(), counter());

    // schema or rows returned by readers
    Schema requiredSchema = deleteFilter.requiredSchema();
    Map<Integer, ?> idToConstant = constantsMap(task, requiredSchema);

    // update the current file for Spark's filename() function
    InputFileBlockHolder.set(filePath, task.start(), task.length());

    return deleteFilter.filter(open(task, requiredSchema, idToConstant)).iterator();
  }

  /**
   * 按任务类型打开数据：数据任务直接转为行迭代，文件任务通过 {@link #newIterable} 读取。
   *
   * @throws NullPointerException 当找不到任务对应的输入文件时抛出
   */
  protected CloseableIterable<InternalRow> open(
      FileScanTask task, Schema readSchema, Map<Integer, ?> idToConstant) {
    if (task.isDataTask()) {
      return newDataIterable(task.asDataTask(), readSchema);
    } else {
      InputFile inputFile = getInputFile(task.file().path().toString());
      Preconditions.checkNotNull(
          inputFile, "Could not find InputFile associated with FileScanTask");
      return newIterable(
          inputFile,
          task.file().format(),
          task.start(),
          task.length(),
          task.residual(),
          readSchema,
          idToConstant);
    }
  }

  /** 将数据任务的行转为 Spark 内部行可迭代对象。 */
  private CloseableIterable<InternalRow> newDataIterable(DataTask task, Schema readSchema) {
    StructInternalRow row = new StructInternalRow(readSchema.asStruct());
    return CloseableIterable.transform(task.asDataTask().rows(), row::setStruct);
  }
}
