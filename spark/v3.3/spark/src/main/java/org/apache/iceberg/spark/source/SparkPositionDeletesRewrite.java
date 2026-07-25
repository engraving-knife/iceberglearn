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
 * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkPositionDeletesRewrite。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
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

  /**
   * Constructs a {@link SparkPositionDeletesRewrite}.
   *
   * @param spark Spark session
   * @param table instance of {@link PositionDeletesTable}
   * @param writeConf Spark write config
   * @param writeInfo Spark write info
   * @param writeSchema Iceberg output schema
   * @param dsSchema schema of original incoming position deletes dataset
   * @param specId spec id of position deletes
   * @param partition partition value of position deletes
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
  }

  /**
   * 转换为batch。
   *
   * @return 结果对象
   */
  @Override
  public BatchWrite toBatch() {
    /** 执行该方法的具体逻辑。 */
    return new PositionDeleteBatchWrite();
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 PositionDeleteBatchWrite。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  class PositionDeleteBatchWrite implements BatchWrite {

    /**
     * 创建并返回新实例。
     *
     * @param info 参数
     * @return 结果对象
     */
    @Override
    public DataWriterFactory createBatchWriterFactory(PhysicalWriteInfo info) {
      // broadcast the table metadata as the writer factory will be sent to executors
      Broadcast<Table> tableBroadcast =
          sparkContext.broadcast(SerializableTableWithSize.copyOf(table));
      /** 执行该方法的具体逻辑。 */
      return new PositionDeletesWriterFactory(
          tableBroadcast,
          queryId,
          format,
          targetFileSize,
          writeSchema,
          dsSchema,
          specId,
          partition);
    }

    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      PositionDeletesRewriteCoordinator coordinator = PositionDeletesRewriteCoordinator.get();
      coordinator.stageRewrite(table, fileSetId, ImmutableSet.copyOf(files(messages)));
    }

    /**
     * 中止并回滚当前操作。
     *
     * @param messages 参数
     */
    @Override
    public void abort(WriterCommitMessage[] messages) {
      SparkCleanupUtil.deleteFiles("job abort", table.io(), files(messages));
    }

    /** 执行该方法的具体逻辑。 */
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
   * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 PositionDeletesWriterFactory。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
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

    PositionDeletesWriterFactory(
        Broadcast<Table> tableBroadcast,
        String queryId,
        FileFormat format,
        long targetFileSize,
        Schema writeSchema,
        StructType dsSchema,
        int specId,
        StructLike partition) {
      this.tableBroadcast = tableBroadcast;
      this.queryId = queryId;
      this.format = format;
      this.targetFileSize = targetFileSize;
      this.writeSchema = writeSchema;
      this.dsSchema = dsSchema;
      this.specId = specId;
      this.partition = partition;
    }

    /**
     * 创建并返回新实例。
     *
     * @param partitionId 参数
     * @param taskId 参数
     * @return 结果对象
     */
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
              .build();
      SparkFileWriterFactory writerFactoryWithoutRow =
          SparkFileWriterFactory.builderFor(table)
              .deleteFileFormat(format)
              .positionDeleteSparkType(deleteSparkTypeWithoutRow)
              .build();

      /** 删除数据或文件。 */
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

    /** 执行该方法的具体逻辑。 */
    private Schema positionDeleteRowSchema() {
      /** 执行该方法的具体逻辑。 */
      return new Schema(
          writeSchema
              .findField(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME)
              .type()
              .asStructType()
              .fields());
    }

    /** 删除数据或文件。 */
    private StructType deleteSparkType() {
      return new StructType(
          new StructField[] {
            dsSchema.apply(MetadataColumns.DELETE_FILE_PATH.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_POS.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_ROW_FIELD_NAME)
          });
    }

    /** 删除数据或文件。 */
    private StructType deleteSparkTypeWithoutRow() {
      return new StructType(
          new StructField[] {
            dsSchema.apply(MetadataColumns.DELETE_FILE_PATH.name()),
            dsSchema.apply(MetadataColumns.DELETE_FILE_POS.name()),
          });
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 DeleteWriter。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
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
     * Constructs a {@link DeleteWriter}.
     *
     * @param table position deletes metadata table
     * @param writerFactoryWithRow writer factory for deletes with non-null 'row'
     * @param writerFactoryWithoutRow writer factory for deletes with null 'row'
     * @param deleteFileFactory delete file factory
     * @param targetFileSize target file size
     * @param dsSchema schema of incoming dataset of position deletes
     * @param specId partition spec id of incoming position deletes. All incoming partition deletes
     *     are required to have the same spec id.
     * @param partition partition value of incoming position delete. All incoming partition deletes
     *     are required to have the same partition.
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

    /**
     * 写入数据。
     *
     * @param record 参数
     */
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

    /**
     * 提交事务或写入结果。
     *
     * @return 结果对象
     */
    @Override
    public WriterCommitMessage commit() throws IOException {
      close();
      /** 删除数据或文件。 */
      return new DeleteTaskCommit(allDeleteFiles());
    }

    /** 中止并回滚当前操作。 */
    @Override
    public void abort() throws IOException {
      close();
      SparkCleanupUtil.deleteTaskFiles(io, allDeleteFiles());
    }

    /** 释放底层资源。 */
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

    /** 执行该方法的具体逻辑。 */
    private ClusteredPositionDeleteWriter<InternalRow> lazyWriterWithRow() {
      if (writerWithRow == null) {
        this.writerWithRow =
            new ClusteredPositionDeleteWriter<>(
                writerFactoryWithRow, deleteFileFactory, io, targetFileSize);
      }
      return writerWithRow;
    }

    /** 执行该方法的具体逻辑。 */
    private ClusteredPositionDeleteWriter<InternalRow> lazyWriterWithoutRow() {
      if (writerWithoutRow == null) {
        this.writerWithoutRow =
            new ClusteredPositionDeleteWriter<>(
                writerFactoryWithoutRow, deleteFileFactory, io, targetFileSize);
      }
      return writerWithoutRow;
    }

    /** 执行该方法的具体逻辑。 */
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

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现，实现 DELETE 行级操作。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 DeleteTaskCommit。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  public static class DeleteTaskCommit implements WriterCommitMessage {
    private final DeleteFile[] taskFiles;

    DeleteTaskCommit(List<DeleteFile> deleteFiles) {
      this.taskFiles = deleteFiles.toArray(new DeleteFile[0]);
    }

    /** 执行该方法的具体逻辑。 */
    DeleteFile[] files() {
      return taskFiles;
    }
  }
}
