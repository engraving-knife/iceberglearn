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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：变更日志读取器，读取数据与删除文件并输出带变更类型的 changelog 行。
 *
 * <p>设计意图：通过 ChangelogIterator 合并变更，并经 carryover 迭代器清理过渡行。
 *
 * <p>上下游关系：由 CreateChangelogViewProcedure / SparkChangelogScan 使用；继承 BaseBatchReader。
 */
class ChangelogRowReader extends BaseRowReader<ChangelogScanTask>
    implements PartitionReader<InternalRow> {

  ChangelogRowReader(SparkInputPartition partition) {
    this(
        partition.table(),
        partition.taskGroup(),
        SnapshotUtil.schemaFor(partition.table(), partition.branch()),
        partition.expectedSchema(),
        partition.isCaseSensitive());
  }

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
  /** 打开资源。 */
  @Override
  protected CloseableIterator<InternalRow> open(ChangelogScanTask task) {
    JoinedRow cdcRow = new JoinedRow();

    cdcRow.withRight(changelogMetadata(task));

    CloseableIterable<InternalRow> rows = openChangelogScanTask(task);
    CloseableIterable<InternalRow> cdcRows = CloseableIterable.transform(rows, cdcRow::withLeft);

    return cdcRows.iterator();
  }
  /** 执行 changelogMetadata 相关操作。 */
  private static InternalRow changelogMetadata(ChangelogScanTask task) {
    InternalRow metadataRow = new GenericInternalRow(3);

    metadataRow.update(0, UTF8String.fromString(task.operation().name()));
    metadataRow.update(1, task.changeOrdinal());
    metadataRow.update(2, task.commitSnapshotId());

    return metadataRow;
  }
  /** 执行 openChangelogScanTask 相关操作。 */
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

  CloseableIterable<InternalRow> openAddedRowsScanTask(AddedRowsScanTask task) {
    String filePath = task.file().path().toString();
    SparkDeleteFilter deletes = new SparkDeleteFilter(filePath, task.deletes(), counter());
    return deletes.filter(rows(task, deletes.requiredSchema()));
  }
  /** 执行 openDeletedDataFileScanTask 相关操作。 */
  private CloseableIterable<InternalRow> openDeletedDataFileScanTask(DeletedDataFileScanTask task) {
    String filePath = task.file().path().toString();
    SparkDeleteFilter deletes = new SparkDeleteFilter(filePath, task.existingDeletes(), counter());
    return deletes.filter(rows(task, deletes.requiredSchema()));
  }
  /** 执行 rows 相关操作。 */
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
  /** 执行 referencedFiles 相关操作。 */
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
  /** 执行 deletedDataFileScanTaskFiles 相关操作。 */
  private static Stream<ContentFile<?>> deletedDataFileScanTaskFiles(DeletedDataFileScanTask task) {
    DataFile file = task.file();
    List<DeleteFile> existingDeletes = task.existingDeletes();
    return Stream.concat(Stream.of(file), existingDeletes.stream());
  }
  /** 执行 addedRowsScanTaskFiles 相关操作。 */
  private static Stream<ContentFile<?>> addedRowsScanTaskFiles(AddedRowsScanTask task) {
    DataFile file = task.file();
    List<DeleteFile> deletes = task.deletes();
    return Stream.concat(Stream.of(file), deletes.stream());
  }
}
