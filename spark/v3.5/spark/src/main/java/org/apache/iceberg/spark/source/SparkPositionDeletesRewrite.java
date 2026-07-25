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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PositionDeletesTable;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.io.ClusteredPositionDeleteWriter;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.PositionDeletesRewriteCoordinator;
import org.apache.iceberg.spark.ScanTaskSetManager;
import org.apache.iceberg.spark.SparkWriteConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.write.BatchWrite;
import org.apache.spark.sql.connector.write.DataWriter;
import org.apache.spark.sql.connector.write.DataWriterFactory;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.PhysicalWriteInfo;
import org.apache.spark.sql.connector.write.Write;
import org.apache.spark.sql.connector.write.WriterCommitMessage;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 从 Spark 重写 position 删除文件的 {@link Write} 实现。
 *
 * <p>所属模块：iceberg-spark（source 子包）。用于 position deletes 重写动作，负责创建 {@link
 * PositionDeleteBatchWrite}。假定待重写的 position 删除均来自 {@link ScanTaskSetManager}， 且具有相同的分区规范 ID 与分区值。
 *
 * <p>设计意图：作为 Spark 写入接口与 Iceberg 重写协调器之间的适配层，将写出的新删除文件 通过 {@link PositionDeletesRewriteCoordinator}
 * 暂存，供动作统一提交；失败时清理已写文件。
 *
 * <p>上下游关系：由 {@link RewritePositionDeleteFilesSparkAction} 触发；底层使用 {@link SparkFileWriterFactory}
 * 写删除文件。
 */
public class SparkPositionDeletesRewrite implements Write {

  private final JavaSparkContext sparkContext;
  private final Table table;
  private final String queryId;
  private final FileFormat format;
  private final long targetFileSize;
  private final Schema writeSchema;
  private final StructType dsSchema;
  private final String fileSetId;
  private final int specId;
  private final StructLike partition;
  private final Map<String, String> writeProperties;

  /**
   * 构造重写写入。
   *
   * @param spark Spark 会话
   * @param table {@link PositionDeletesTable} 实例
   * @param writeConf Spark 写配置
   * @param writeInfo Spark 写信息
   * @param writeSchema Iceberg 输出 schema
   * @param dsSchema 输入 position 删除数据集 schema
   * @param specId position 删除的分区规范 ID
   * @param partition position 删除的分区值
   */
  SparkPositionDeletesRewrite(
      SparkSession spark,
      Table table,
      SparkWriteConf writeConf,
      LogicalWriteInfo writeInfo,
      Schema writeSchema,
      StructType dsSchema,
      int specId,
      StructLike partition) {
    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
    this.table = table;
    this.queryId = writeInfo.queryId();
    this.format = writeConf.deleteFileFormat();
    this.targetFileSize = writeConf.targetDeleteFileSize();
    this.writeSchema = writeSchema;
    this.dsSchema = dsSchema;
    this.fileSetId = writeConf.rewrittenFileSetId();
    this.specId = specId;
    this.partition = partition;
    this.writeProperties = writeConf.writeProperties();
  }

  /** 返回 position 删除批写入。 */
  @Override
  public BatchWrite toBatch() {
    return new PositionDeleteBatchWrite();
  }

  /** 从 Spark 重写 position 删除文件的 {@link BatchWrite}。 */
  class PositionDeleteBatchWrite implements BatchWrite {

    /** 广播表元数据并构造 {@link PositionDeletesWriterFactory}。 */
    @Override
    public DataWriterFactory createBatchWriterFactory(PhysicalWriteInfo info) {
      // broadcast the table metadata as the writer factory will be sent to executors
      Broadcast<Table> tableBroadcast =
          sparkContext.broadcast(SerializableTableWithSize.copyOf(table));
      return new PositionDeletesWriterFactory(
          tableBroadcast,
          queryId,
          format,
          targetFileSize,
          writeSchema,
          dsSchema,
          specId,
          partition,
          writeProperties);
    }

    /** 提交：将任务写出的删除文件通过 {@link PositionDeletesRewriteCoordinator} 暂存。 */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      PositionDeletesRewriteCoordinator coordinator = PositionDeletesRewriteCoordinator.get();
      coordinator.stageRewrite(table, fileSetId, ImmutableSet.copyOf(files(messages)));
    }

    /** 中止：删除任务已写出的文件。 */
    @Override
    public void abort(WriterCommitMessage[] messages) {
      SparkCleanupUtil.deleteFiles("job abort", table.io(), files(messages));
    }

    /** 汇总所有任务提交消息中的删除文件。 */
    private List<DeleteFile> files(WriterCommitMessage[] messages) {
      List<DeleteFile> files = Lists.newArrayList();

      for (WriterCommitMessage message : messages) {
        if (message != null) {
          DeleteTaskCommit taskCommit = (DeleteTaskCommit) message;
          files.addAll(Arrays.asList(taskCommit.files()));
        }
      }

      return files;
    }
  }

  /**
   * position 删除元数据表的写入器工厂，负责创建 {@link DeleteWriter}。
   *
   * <p>假定所有输入删除属于同一分区，且来自 {@link ScanTaskSetManager}。
   */
  static class PositionDeletesWriterFactory implements DataWriterFactory {
    private final Broadcast<Table> tableBroadcast;
    private final String queryId;
    private final FileFormat format;
    private final Long targetFileSize;
    private final Schema writeSchema;
    private final StructType dsSchema;
    private final int specId;
    private final StructLike partition;
    private final Map<String, String> writeProperties;

    PositionDeletesWriterFactory(
        Broadcast<Table> tableBroadcast,
        String queryId,
        FileFormat format,
        long targetFileSize,
        Schema writeSchema,
        StructType dsSchema,
        int specId,
        StructLike partition,
        Map<String, String> writeProperties) {
      this.tableBroadcast = tableBroadcast;
      this.queryId = queryId;
      this.format = format;
      this.targetFileSize = targetFileSize;
      this.writeSchema = writeSchema;
      this.dsSchema = dsSchema;
      this.specId = specId;
      this.partition = partition;
      this.writeProperties = writeProperties;
    }

    /** 创建写入器：构造含行与不含行两种 {@link SparkFileWriterFactory}，按目标大小与分区构建 {@link DeleteWriter}。 */
    @Override
    public DataWriter<InternalRow> createWriter(int partitionId, long taskId) {
      Table table = tableBroadcast.value();

      OutputFileFactory deleteFileFactory =
          OutputFileFactory.builderFor(table, partitionId, taskId)
              .format(format)
              .operationId(queryId)
              .suffix("deletes")
              .build();

      Schema positionDeleteRowSchema = positionDeleteRowSchema();
      StructType deleteSparkType = deleteSparkType();
      StructType deleteSparkTypeWithoutRow = deleteSparkTypeWithoutRow();

      SparkFileWriterFactory writerFactoryWithRow =
          SparkFileWriterFactory.builderFor(table)
              .deleteFileFormat(format)
              .positionDeleteRowSchema(positionDeleteRowSchema)
              .positionDeleteSparkType(deleteSparkType)
              .writeProperties(writeProperties)
              .build();
      SparkFileWriterFactory writerFactoryWithoutRow =
          SparkFileWriterFactory.builderFor(table)
              .deleteFileFormat(format)
              .positionDeleteSparkType(deleteSparkTypeWithoutRow)
              .writeProperties(writeProperties)
              .build();

      return new DeleteWriter(
          table,
          writerFactoryWithRow,
          writerFactoryWithoutRow,
          deleteFileFactory,
          targetFileSize,
          dsSchema,
          specId,
          partition);
    }

    /** 由写 schema 中的 DELETE_FILE_ROW 字段拆出 position 删除行 schema。 */
    private Schema positionDeleteRowSchema() {
      return new Schema(
          writeSchema
              .findField(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME)
              .type()
              .asStructType()
              .fields());
    }

    /** 返回含 path/pos/row 的删除 Spark 类型。 */
    private StructType deleteSparkType() {
      return new StructType(
          new StructField[] {
            dsSchema.apply(MetadataColumns.DELETE_FILE_PATH.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_POS.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME)
          });
    }

    /** 返回仅含 path/pos 的删除 Spark 类型（不含行）。 */
    private StructType deleteSparkTypeWithoutRow() {
      return new StructType(
          new StructField[] {
            dsSchema.apply(MetadataColumns.DELETE_FILE_PATH.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_POS.name()),
          });
    }
  }

  /**
   * position 删除元数据表的写入器。
   *
   * <p>Iceberg 删除文件 schema 要么要求 'row' 必填、要么完全省略 'row'，以保证 row 列统计准确。 因此本写入器在收到含 null 与非 null
   * 行的源删除时，将 null 行与非 null 行分别导向不同文件写入器。
   *
   * <p>假定所有输入删除属于同一分区。
   */
  private static class DeleteWriter implements DataWriter<InternalRow> {
    private final SparkFileWriterFactory writerFactoryWithRow;
    private final SparkFileWriterFactory writerFactoryWithoutRow;
    private final OutputFileFactory deleteFileFactory;
    private final long targetFileSize;
    private final PositionDelete<InternalRow> positionDelete;
    private final FileIO io;
    private final PartitionSpec spec;
    private final int fileOrdinal;
    private final int positionOrdinal;
    private final int rowOrdinal;
    private final int rowSize;
    private final StructLike partition;

    private ClusteredPositionDeleteWriter<InternalRow> writerWithRow;
    private ClusteredPositionDeleteWriter<InternalRow> writerWithoutRow;
    private boolean closed = false;

    /**
     * 构造 {@link DeleteWriter}。
     *
     * @param table position 删除元数据表
     * @param writerFactoryWithRow 含非 null 行删除的写入器工厂
     * @param writerFactoryWithoutRow 含 null 行删除的写入器工厂
     * @param deleteFileFactory 删除文件工厂
     * @param targetFileSize 目标文件大小
     * @param dsSchema 输入 position 删除数据集 schema
     * @param specId 输入删除的分区规范 ID（须一致）
     * @param partition 输入删除的分区值（须一致）
     */
    DeleteWriter(
        Table table,
        SparkFileWriterFactory writerFactoryWithRow,
        SparkFileWriterFactory writerFactoryWithoutRow,
        OutputFileFactory deleteFileFactory,
        long targetFileSize,
        StructType dsSchema,
        int specId,
        StructLike partition) {
      this.deleteFileFactory = deleteFileFactory;
      this.targetFileSize = targetFileSize;
      this.writerFactoryWithRow = writerFactoryWithRow;
      this.writerFactoryWithoutRow = writerFactoryWithoutRow;
      this.positionDelete = PositionDelete.create();
      this.io = table.io();
      this.spec = table.specs().get(specId);
      this.partition = partition;

      this.fileOrdinal = dsSchema.fieldIndex(MetadataColumns.DELETE_FILE_PATH.name());
      this.positionOrdinal = dsSchema.fieldIndex(MetadataColumns.DELETE_FILE_POS.name());

      this.rowOrdinal = dsSchema.fieldIndex(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME);
      DataType type = dsSchema.apply(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME).dataType();
      Preconditions.checkArgument(
          type instanceof StructType, "Expected row as struct type but was %s", type);
      this.rowSize = ((StructType) type).size();
    }

    /** 写一条 position 删除记录：行非空走含行写入器，行为空走不含行写入器。 */
    @Override
    public void write(InternalRow record) throws IOException {
      String file = record.getString(fileOrdinal);
      long position = record.getLong(positionOrdinal);
      InternalRow row = record.getStruct(rowOrdinal, rowSize);
      if (row != null) {
        positionDelete.set(file, position, row);
        lazyWriterWithRow().write(positionDelete, spec, partition);
      } else {
        positionDelete.set(file, position, null);
        lazyWriterWithoutRow().write(positionDelete, spec, partition);
      }
    }

    /** 关闭写入器并返回含全部删除文件的 {@link DeleteTaskCommit}。 */
    @Override
    public WriterCommitMessage commit() throws IOException {
      close();
      return new DeleteTaskCommit(allDeleteFiles());
    }

    /** 中止：关闭后删除已写文件。 */
    @Override
    public void abort() throws IOException {
      close();
      SparkCleanupUtil.deleteTaskFiles(io, allDeleteFiles());
    }

    /** 关闭两个写入器（幂等）。 */
    @Override
    public void close() throws IOException {
      if (!closed) {
        if (writerWithRow != null) {
          writerWithRow.close();
        }
        if (writerWithoutRow != null) {
          writerWithoutRow.close();
        }
        this.closed = true;
      }
    }

    /** 懒初始化含行写入器。 */
    private ClusteredPositionDeleteWriter<InternalRow> lazyWriterWithRow() {
      if (writerWithRow == null) {
        this.writerWithRow =
            new ClusteredPositionDeleteWriter<>(
                writerFactoryWithRow, deleteFileFactory, io, targetFileSize);
      }
      return writerWithRow;
    }

    /** 懒初始化不含行写入器。 */
    private ClusteredPositionDeleteWriter<InternalRow> lazyWriterWithoutRow() {
      if (writerWithoutRow == null) {
        this.writerWithoutRow =
            new ClusteredPositionDeleteWriter<>(
                writerFactoryWithoutRow, deleteFileFactory, io, targetFileSize);
      }
      return writerWithoutRow;
    }

    /** 汇总两个写入器产出的全部删除文件。 */
    private List<DeleteFile> allDeleteFiles() {
      List<DeleteFile> allDeleteFiles = Lists.newArrayList();
      if (writerWithRow != null) {
        allDeleteFiles.addAll(writerWithRow.result().deleteFiles());
      }
      if (writerWithoutRow != null) {
        allDeleteFiles.addAll(writerWithoutRow.result().deleteFiles());
      }
      return allDeleteFiles;
    }
  }

  /** 任务提交消息，携带该任务写出的删除文件数组。 */
  public static class DeleteTaskCommit implements WriterCommitMessage {
    private final DeleteFile[] taskFiles;

    /** 以删除文件列表构造。 */
    DeleteTaskCommit(List<DeleteFile> deleteFiles) {
      this.taskFiles = deleteFiles.toArray(new DeleteFile[0]);
    }

    /** 返回任务删除文件数组。 */
    DeleteFile[] files() {
      return taskFiles;
    }
  }
}
