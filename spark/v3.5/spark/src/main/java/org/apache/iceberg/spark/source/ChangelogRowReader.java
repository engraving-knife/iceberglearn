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

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.iceberg.AddedRowsScanTask;
import org.apache.iceberg.ChangelogScanTask;
import org.apache.iceberg.ChangelogUtil;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.DeletedDataFileScanTask;
import org.apache.iceberg.DeletedRowsScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.rdd.InputFileBlockHolder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.catalyst.expressions.JoinedRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Changelog（行变更日志）行读取器：把 Iceberg changelog 任务转为 Spark {@link InternalRow}。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），source 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为每个 ChangelogScanTask 打开行迭代器，附加 changelog 元数据列（operation/change ordinal/commit snapshot
 *       id）。
 *   <li>区分 AddedRowsScanTask、DeletedRowsScanTask、DeletedDataFileScanTask 三种任务类型分别读取。
 *   <li>应用 SparkDeleteFilter 过滤已删行，更新 Spark InputFileBlockHolder 供 filename() 函数使用。
 * </ul>
 *
 * <p>设计意图：用 JoinedRow 把数据行与元数据行拼接，避免每行复制；changelog schema 在构造时 去除 changelog 元数据列（由本 reader
 * 附加）；不同任务类型走不同 open 路径，DeletedRowsScanTask 暂未支持。
 *
 * <p>上下游关系：被 {@link SparkChangelogTable} 的扫描构造；继承 {@link BaseRowReader}。
 */
class ChangelogRowReader extends BaseRowReader<ChangelogScanTask>
    implements PartitionReader<InternalRow> {

  /** 从 SparkInputPartition 构造 reader。 */
  ChangelogRowReader(SparkInputPartition partition) {
    this(
        partition.table(),
        partition.taskGroup(),
        SnapshotUtil.schemaFor(partition.table(), partition.branch()),
        partition.expectedSchema(),
        partition.isCaseSensitive());
  }

  /** 构造 reader，传入的 expectedSchema 会去除 changelog 元数据列（由本类附加）。 */
  ChangelogRowReader(
      Table table,
      ScanTaskGroup<ChangelogScanTask> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {
    super(
        table,
        taskGroup,
        tableSchema,
        ChangelogUtil.dropChangelogMetadata(expectedSchema),
        caseSensitive);
  }

  /**
   * 打开单个 changelog 任务的行迭代器。
   *
   * <p>逻辑：构造 JoinedRow，右侧放 changelog 元数据行；打开数据行迭代器， 用 cdcRow.withLeft 把每行数据与元数据拼接。
   */
  @Override
  protected CloseableIterator<InternalRow> open(ChangelogScanTask task) {
    JoinedRow cdcRow = new JoinedRow();

    cdcRow.withRight(changelogMetadata(task));

    CloseableIterable<InternalRow> rows = openChangelogScanTask(task);
    CloseableIterable<InternalRow> cdcRows = CloseableIterable.transform(rows, cdcRow::withLeft);

    return cdcRows.iterator();
  }

  /** 构造 changelog 元数据行：operation、change ordinal、commit snapshot id。 */
  private static InternalRow changelogMetadata(ChangelogScanTask task) {
    InternalRow metadataRow = new GenericInternalRow(3);

    metadataRow.update(0, UTF8String.fromString(task.operation().name()));
    metadataRow.update(1, task.changeOrdinal());
    metadataRow.update(2, task.commitSnapshotId());

    return metadataRow;
  }

  /** 按任务子类型分发到对应 open 方法；DeletedRowsScanTask 暂不支持。 */
  private CloseableIterable<InternalRow> openChangelogScanTask(ChangelogScanTask task) {
    if (task instanceof AddedRowsScanTask) {
      return openAddedRowsScanTask((AddedRowsScanTask) task);

    } else if (task instanceof DeletedRowsScanTask) {
      throw new UnsupportedOperationException("Deleted rows scan task is not supported yet");

    } else if (task instanceof DeletedDataFileScanTask) {
      return openDeletedDataFileScanTask((DeletedDataFileScanTask) task);

    } else {
      throw new IllegalArgumentException(
          "Unsupported changelog scan task type: " + task.getClass().getName());
    }
  }

  /** 打开新增行任务：构造 SparkDeleteFilter 过滤后返回行。 */
  CloseableIterable<InternalRow> openAddedRowsScanTask(AddedRowsScanTask task) {
    String filePath = task.file().path().toString();
    SparkDeleteFilter deletes = new SparkDeleteFilter(filePath, task.deletes(), counter());
    return deletes.filter(rows(task, deletes.requiredSchema()));
  }

  /** 打开被删数据文件任务：用 existingDeletes 过滤后返回行。 */
  private CloseableIterable<InternalRow> openDeletedDataFileScanTask(DeletedDataFileScanTask task) {
    String filePath = task.file().path().toString();
    SparkDeleteFilter deletes = new SparkDeleteFilter(filePath, task.existingDeletes(), counter());
    return deletes.filter(rows(task, deletes.requiredSchema()));
  }

  /**
   * 读取单个数据文件扫描任务的行。
   *
   * <p>逻辑：取常量列映射；设置 Spark InputFileBlockHolder（供 filename()）； 获取 InputFile 并调 newIterable 构造读取器。
   */
  private CloseableIterable<InternalRow> rows(ContentScanTask<DataFile> task, Schema readSchema) {
    Map<Integer, ?> idToConstant = constantsMap(task, readSchema);

    String filePath = task.file().path().toString();

    // update the current file for Spark's filename() function
    InputFileBlockHolder.set(filePath, task.start(), task.length());

    InputFile location = getInputFile(filePath);
    Preconditions.checkNotNull(location, "Could not find InputFile");
    return newIterable(
        location,
        task.file().format(),
        task.start(),
        task.length(),
        task.residual(),
        readSchema,
        idToConstant);
  }

  /** 返回任务引用的所有文件（数据文件 + 删除文件），用于指标统计。 */
  @Override
  protected Stream<ContentFile<?>> referencedFiles(ChangelogScanTask task) {
    if (task instanceof AddedRowsScanTask) {
      return addedRowsScanTaskFiles((AddedRowsScanTask) task);

    } else if (task instanceof DeletedRowsScanTask) {
      throw new UnsupportedOperationException("Deleted rows scan task is not supported yet");

    } else if (task instanceof DeletedDataFileScanTask) {
      return deletedDataFileScanTaskFiles((DeletedDataFileScanTask) task);

    } else {
      throw new IllegalArgumentException(
          "Unsupported changelog scan task type: " + task.getClass().getName());
    }
  }

  /** 被删数据文件任务的引用文件：数据文件 + existingDeletes。 */
  private static Stream<ContentFile<?>> deletedDataFileScanTaskFiles(DeletedDataFileScanTask task) {
    DataFile file = task.file();
    List<DeleteFile> existingDeletes = task.existingDeletes();
    return Stream.concat(Stream.of(file), existingDeletes.stream());
  }

  /** 新增行任务的引用文件：数据文件 + deletes。 */
  private static Stream<ContentFile<?>> addedRowsScanTaskFiles(AddedRowsScanTask task) {
    DataFile file = task.file();
    List<DeleteFile> deletes = task.deletes();
    return Stream.concat(Stream.of(file), deletes.stream());
  }
}
