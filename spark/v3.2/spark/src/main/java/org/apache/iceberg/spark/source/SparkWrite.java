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

import static org.apache.iceberg.IsolationLevel.SERIALIZABLE;
import static org.apache.iceberg.IsolationLevel.SNAPSHOT;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.iceberg.AppendFiles;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.OverwriteFiles;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.ReplacePartitions;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.SnapshotUpdate;
import org.apache.iceberg.Table;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.ClusteredDataWriter;
import org.apache.iceberg.io.DataWriteResult;
import org.apache.iceberg.io.FanoutDataWriter;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.FileWriter;
import org.apache.iceberg.io.OutputFileFactory;
import org.apache.iceberg.io.PartitioningWriter;
import org.apache.iceberg.io.RollingDataWriter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.CommitMetadata;
import org.apache.iceberg.spark.FileRewriteCoordinator;
import org.apache.iceberg.spark.SparkWriteConf;
import org.apache.spark.TaskContext;
import org.apache.spark.TaskContext$;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.executor.OutputMetrics;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.distributions.Distribution;
import org.apache.spark.sql.connector.expressions.SortOrder;
import org.apache.spark.sql.connector.write.BatchWrite;
import org.apache.spark.sql.connector.write.DataWriter;
import org.apache.spark.sql.connector.write.DataWriterFactory;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.PhysicalWriteInfo;
import org.apache.spark.sql.connector.write.RequiresDistributionAndOrdering;
import org.apache.spark.sql.connector.write.Write;
import org.apache.spark.sql.connector.write.WriterCommitMessage;
import org.apache.spark.sql.connector.write.streaming.StreamingDataWriterFactory;
import org.apache.spark.sql.connector.write.streaming.StreamingWrite;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkWrite。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
abstract class SparkWrite implements Write, RequiresDistributionAndOrdering {
  private static final Logger LOG = LoggerFactory.getLogger(SparkWrite.class);

  private final JavaSparkContext sparkContext;
  private final SparkWriteConf writeConf;
  private final Table table;
  private final String queryId;
  private final FileFormat format;
  private final String applicationId;
  private final boolean wapEnabled;
  private final String wapId;
  private final int outputSpecId;
  private final long targetFileSize;
  private final Schema writeSchema;
  private final StructType dsSchema;
  private final Map<String, String> extraSnapshotMetadata;
  private final boolean partitionedFanoutEnabled;
  private final Distribution requiredDistribution;
  private final SortOrder[] requiredOrdering;

  private boolean cleanupOnAbort = true;

  SparkWrite(
      SparkSession spark,
      Table table,
      SparkWriteConf writeConf,
      LogicalWriteInfo writeInfo,
      String applicationId,
      Schema writeSchema,
      StructType dsSchema,
      Distribution requiredDistribution,
      SortOrder[] requiredOrdering) {
    this.sparkContext = JavaSparkContext.fromSparkContext(spark.sparkContext());
    this.table = table;
    this.writeConf = writeConf;
    this.queryId = writeInfo.queryId();
    this.format = writeConf.dataFileFormat();
    this.applicationId = applicationId;
    this.wapEnabled = writeConf.wapEnabled();
    this.wapId = writeConf.wapId();
    this.targetFileSize = writeConf.targetDataFileSize();
    this.writeSchema = writeSchema;
    this.dsSchema = dsSchema;
    this.extraSnapshotMetadata = writeConf.extraSnapshotMetadata();
    this.partitionedFanoutEnabled = writeConf.fanoutWriterEnabled();
    this.requiredDistribution = requiredDistribution;
    this.requiredOrdering = requiredOrdering;
    this.outputSpecId = writeConf.outputSpecId();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Distribution requiredDistribution() {
    return requiredDistribution;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public SortOrder[] requiredOrdering() {
    return requiredOrdering;
  }

  /** 执行该方法的具体逻辑。 */
  BatchWrite asBatchAppend() {
    /** 执行该方法的具体逻辑。 */
    return new BatchAppend();
  }

  /** 执行该方法的具体逻辑。 */
  BatchWrite asDynamicOverwrite() {
    /** 执行该方法的具体逻辑。 */
    return new DynamicOverwrite();
  }

  /** 执行该方法的具体逻辑。 */
  BatchWrite asOverwriteByFilter(Expression overwriteExpr) {
    /** 执行该方法的具体逻辑。 */
    return new OverwriteByFilter(overwriteExpr);
  }

  /** 执行该方法的具体逻辑。 */
  BatchWrite asCopyOnWriteOperation(SparkCopyOnWriteScan scan, IsolationLevel isolationLevel) {
    /** 执行该方法的具体逻辑。 */
    return new CopyOnWriteOperation(scan, isolationLevel);
  }

  /** 执行该方法的具体逻辑。 */
  BatchWrite asRewrite(String fileSetID) {
    /** 重写计划或文件。 */
    return new RewriteFiles(fileSetID);
  }

  /** 执行该方法的具体逻辑。 */
  StreamingWrite asStreamingAppend() {
    /** 执行该方法的具体逻辑。 */
    return new StreamingAppend();
  }

  /** 执行该方法的具体逻辑。 */
  StreamingWrite asStreamingOverwrite() {
    /** 执行该方法的具体逻辑。 */
    return new StreamingOverwrite();
  }

  // the writer factory works for both batch and streaming
  /** 创建并返回新实例。 */
  private WriterFactory createWriterFactory() {
    // broadcast the table metadata as the writer factory will be sent to executors
    Broadcast<Table> tableBroadcast =
        sparkContext.broadcast(SerializableTableWithSize.copyOf(table));
    /** 写入数据。 */
    return new WriterFactory(
        tableBroadcast,
        queryId,
        format,
        outputSpecId,
        targetFileSize,
        writeSchema,
        dsSchema,
        partitionedFanoutEnabled);
  }

  /** 提交事务或写入结果。 */
  private void commitOperation(SnapshotUpdate<?> operation, String description) {
    LOG.info("Committing {} to table {}", description, table);
    if (applicationId != null) {
      operation.set("spark.app.id", applicationId);
    }

    if (!extraSnapshotMetadata.isEmpty()) {
      extraSnapshotMetadata.forEach(operation::set);
    }

    if (!CommitMetadata.commitProperties().isEmpty()) {
      CommitMetadata.commitProperties().forEach(operation::set);
    }

    if (wapEnabled && wapId != null) {
      // write-audit-publish is enabled for this table and job
      // stage the changes without changing the current snapshot
      operation.set(SnapshotSummary.STAGED_WAP_ID_PROP, wapId);
      operation.stageOnly();
    }

    try {
      long start = System.currentTimeMillis();
      operation.commit(); // abort is automatically called if this fails
      long duration = System.currentTimeMillis() - start;
      LOG.info("Committed in {} ms", duration);
    } catch (CommitStateUnknownException commitStateUnknownException) {
      cleanupOnAbort = false;
      throw commitStateUnknownException;
    }
  }

  /** 中止并回滚当前操作。 */
  private void abort(WriterCommitMessage[] messages) {
    if (cleanupOnAbort) {
      SparkCleanupUtil.deleteFiles("job abort", table.io(), files(messages));
    } else {
      LOG.warn("Skipping cleanup of written files");
    }
  }

  /** 执行该方法的具体逻辑。 */
  private List<DataFile> files(WriterCommitMessage[] messages) {
    List<DataFile> files = Lists.newArrayList();

    for (WriterCommitMessage message : messages) {
      if (message != null) {
        TaskCommit taskCommit = (TaskCommit) message;
        files.addAll(Arrays.asList(taskCommit.files()));
      }
    }

    return files;
  }

  /** 返回该对象的字符串表示。 */
  @Override
  public String toString() {
    return String.format("IcebergWrite(table=%s, format=%s)", table, format);
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 BaseBatchWrite。
   *
   * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private abstract class BaseBatchWrite implements BatchWrite {
    /**
     * 创建并返回新实例。
     *
     * @param info 参数
     * @return 结果对象
     */
    @Override
    public DataWriterFactory createBatchWriterFactory(PhysicalWriteInfo info) {
      return createWriterFactory();
    }

    /**
     * 中止并回滚当前操作。
     *
     * @param messages 参数
     */
    @Override
    public void abort(WriterCommitMessage[] messages) {
      SparkWrite.this.abort(messages);
    }

    /** 返回该对象的字符串表示。 */
    @Override
    public String toString() {
      return String.format("IcebergBatchWrite(table=%s, format=%s)", table, format);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现，处理列式批量数据。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 BatchAppend。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class BatchAppend extends BaseBatchWrite {
    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      AppendFiles append = table.newAppend();

      int numFiles = 0;
      for (DataFile file : files(messages)) {
        numFiles += 1;
        append.appendFile(file);
      }

      commitOperation(append, String.format("append with %d new data files", numFiles));
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 DynamicOverwrite。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class DynamicOverwrite extends BaseBatchWrite {
    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      List<DataFile> files = files(messages);

      if (files.isEmpty()) {
        LOG.info("Dynamic overwrite is empty, skipping commit");
        return;
      }

      ReplacePartitions dynamicOverwrite = table.newReplacePartitions();
      IsolationLevel isolationLevel = writeConf.isolationLevel();
      Long validateFromSnapshotId = writeConf.validateFromSnapshotId();

      if (isolationLevel != null && validateFromSnapshotId != null) {
        dynamicOverwrite.validateFromSnapshot(validateFromSnapshotId);
      }

      if (isolationLevel == SERIALIZABLE) {
        dynamicOverwrite.validateNoConflictingData();
        dynamicOverwrite.validateNoConflictingDeletes();

      } else if (isolationLevel == SNAPSHOT) {
        dynamicOverwrite.validateNoConflictingDeletes();
      }

      int numFiles = 0;
      for (DataFile file : files) {
        numFiles += 1;
        dynamicOverwrite.addFile(file);
      }

      commitOperation(
          dynamicOverwrite,
          String.format("dynamic partition overwrite with %d new data files", numFiles));
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 OverwriteByFilter。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class OverwriteByFilter extends BaseBatchWrite {
    private final Expression overwriteExpr;

    /** 构造 OverwriteByFilter 实例。 */
    private OverwriteByFilter(Expression overwriteExpr) {
      this.overwriteExpr = overwriteExpr;
    }

    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      OverwriteFiles overwriteFiles = table.newOverwrite();
      overwriteFiles.overwriteByRowFilter(overwriteExpr);

      int numFiles = 0;
      for (DataFile file : files(messages)) {
        numFiles += 1;
        overwriteFiles.addFile(file);
      }

      IsolationLevel isolationLevel = writeConf.isolationLevel();
      Long validateFromSnapshotId = writeConf.validateFromSnapshotId();

      if (isolationLevel != null && validateFromSnapshotId != null) {
        overwriteFiles.validateFromSnapshot(validateFromSnapshotId);
      }

      if (isolationLevel == SERIALIZABLE) {
        overwriteFiles.validateNoConflictingDeletes();
        overwriteFiles.validateNoConflictingData();

      } else if (isolationLevel == SNAPSHOT) {
        overwriteFiles.validateNoConflictingDeletes();
      }

      String commitMsg =
          String.format("overwrite by filter %s with %d new data files", overwriteExpr, numFiles);
      commitOperation(overwriteFiles, commitMsg);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 CopyOnWriteOperation。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class CopyOnWriteOperation extends BaseBatchWrite {
    private final SparkCopyOnWriteScan scan;
    private final IsolationLevel isolationLevel;

    /** 构造 CopyOnWriteOperation 实例。 */
    private CopyOnWriteOperation(SparkCopyOnWriteScan scan, IsolationLevel isolationLevel) {
      this.scan = scan;
      this.isolationLevel = isolationLevel;
    }

    /** 执行该方法的具体逻辑。 */
    private List<DataFile> overwrittenFiles() {
      return scan.files().stream().map(FileScanTask::file).collect(Collectors.toList());
    }

    /** 执行该方法的具体逻辑。 */
    private Expression conflictDetectionFilter() {
      // the list of filter expressions may be empty but is never null
      List<Expression> scanFilterExpressions = scan.filterExpressions();

      Expression filter = Expressions.alwaysTrue();

      for (Expression expr : scanFilterExpressions) {
        filter = Expressions.and(filter, expr);
      }

      return filter;
    }

    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      OverwriteFiles overwriteFiles = table.newOverwrite();

      List<DataFile> overwrittenFiles = overwrittenFiles();
      int numOverwrittenFiles = overwrittenFiles.size();
      for (DataFile overwrittenFile : overwrittenFiles) {
        overwriteFiles.deleteFile(overwrittenFile);
      }

      int numAddedFiles = 0;
      for (DataFile file : files(messages)) {
        numAddedFiles += 1;
        overwriteFiles.addFile(file);
      }

      if (isolationLevel == SERIALIZABLE) {
        commitWithSerializableIsolation(overwriteFiles, numOverwrittenFiles, numAddedFiles);
      } else if (isolationLevel == SNAPSHOT) {
        commitWithSnapshotIsolation(overwriteFiles, numOverwrittenFiles, numAddedFiles);
      } else {
        throw new IllegalArgumentException("Unsupported isolation level: " + isolationLevel);
      }
    }

    /** 提交事务或写入结果。 */
    private void commitWithSerializableIsolation(
        OverwriteFiles overwriteFiles, int numOverwrittenFiles, int numAddedFiles) {
      Long scanSnapshotId = scan.snapshotId();
      if (scanSnapshotId != null) {
        overwriteFiles.validateFromSnapshot(scanSnapshotId);
      }

      Expression conflictDetectionFilter = conflictDetectionFilter();
      overwriteFiles.conflictDetectionFilter(conflictDetectionFilter);
      overwriteFiles.validateNoConflictingData();
      overwriteFiles.validateNoConflictingDeletes();

      String commitMsg =
          String.format(
              "overwrite of %d data files with %d new data files, scanSnapshotId: %d, conflictDetectionFilter: %s",
              numOverwrittenFiles, numAddedFiles, scanSnapshotId, conflictDetectionFilter);
      commitOperation(overwriteFiles, commitMsg);
    }

    /** 提交事务或写入结果。 */
    private void commitWithSnapshotIsolation(
        OverwriteFiles overwriteFiles, int numOverwrittenFiles, int numAddedFiles) {
      Long scanSnapshotId = scan.snapshotId();
      if (scanSnapshotId != null) {
        overwriteFiles.validateFromSnapshot(scanSnapshotId);
      }

      Expression conflictDetectionFilter = conflictDetectionFilter();
      overwriteFiles.conflictDetectionFilter(conflictDetectionFilter);
      overwriteFiles.validateNoConflictingDeletes();

      String commitMsg =
          String.format(
              "overwrite of %d data files with %d new data files",
              numOverwrittenFiles, numAddedFiles);
      commitOperation(overwriteFiles, commitMsg);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 RewriteFiles。
   *
   * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class RewriteFiles extends BaseBatchWrite {
    private final String fileSetID;

    /** 构造 RewriteFiles 实例。 */
    private RewriteFiles(String fileSetID) {
      this.fileSetID = fileSetID;
    }

    /**
     * 提交事务或写入结果。
     *
     * @param messages 参数
     */
    @Override
    public void commit(WriterCommitMessage[] messages) {
      FileRewriteCoordinator coordinator = FileRewriteCoordinator.get();
      coordinator.stageRewrite(table, fileSetID, ImmutableSet.copyOf(files(messages)));
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 BaseStreamingWrite。
   *
   * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private abstract class BaseStreamingWrite implements StreamingWrite {
    private static final String QUERY_ID_PROPERTY = "spark.sql.streaming.queryId";
    private static final String EPOCH_ID_PROPERTY = "spark.sql.streaming.epochId";

    /** 执行该方法的具体逻辑。 */
    protected abstract String mode();

    /**
     * 创建并返回新实例。
     *
     * @param info 参数
     * @return 结果对象
     */
    @Override
    public StreamingDataWriterFactory createStreamingWriterFactory(PhysicalWriteInfo info) {
      return createWriterFactory();
    }

    /** 提交事务或写入结果。 */
    @Override
    public final void commit(long epochId, WriterCommitMessage[] messages) {
      LOG.info("Committing epoch {} for query {} in {} mode", epochId, queryId, mode());

      table.refresh();

      Long lastCommittedEpochId = findLastCommittedEpochId();
      if (lastCommittedEpochId != null && epochId <= lastCommittedEpochId) {
        LOG.info("Skipping epoch {} for query {} as it was already committed", epochId, queryId);
        return;
      }

      doCommit(epochId, messages);
    }

    /** 执行该方法的具体逻辑。 */
    protected abstract void doCommit(long epochId, WriterCommitMessage[] messages);

    /** 提交事务或写入结果。 */
    protected <T> void commit(SnapshotUpdate<T> snapshotUpdate, long epochId, String description) {
      snapshotUpdate.set(QUERY_ID_PROPERTY, queryId);
      snapshotUpdate.set(EPOCH_ID_PROPERTY, Long.toString(epochId));
      commitOperation(snapshotUpdate, description);
    }

    /** 查找并返回结果。 */
    private Long findLastCommittedEpochId() {
      Snapshot snapshot = table.currentSnapshot();
      Long lastCommittedEpochId = null;
      while (snapshot != null) {
        Map<String, String> summary = snapshot.summary();
        String snapshotQueryId = summary.get(QUERY_ID_PROPERTY);
        if (queryId.equals(snapshotQueryId)) {
          lastCommittedEpochId = Long.valueOf(summary.get(EPOCH_ID_PROPERTY));
          break;
        }
        Long parentSnapshotId = snapshot.parentId();
        snapshot = parentSnapshotId != null ? table.snapshot(parentSnapshotId) : null;
      }
      return lastCommittedEpochId;
    }

    /**
     * 中止并回滚当前操作。
     *
     * @param epochId 参数
     * @param messages 参数
     */
    @Override
    public void abort(long epochId, WriterCommitMessage[] messages) {
      SparkWrite.this.abort(messages);
    }

    /** 返回该对象的字符串表示。 */
    @Override
    public String toString() {
      return String.format("IcebergStreamingWrite(table=%s, format=%s)", table, format);
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StreamingAppend。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class StreamingAppend extends BaseStreamingWrite {
    /** 执行该方法的具体逻辑。 */
    @Override
    protected String mode() {
      return "append";
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected void doCommit(long epochId, WriterCommitMessage[] messages) {
      AppendFiles append = table.newFastAppend();
      int numFiles = 0;
      for (DataFile file : files(messages)) {
        append.appendFile(file);
        numFiles++;
      }
      commit(append, epochId, String.format("streaming append with %d new data files", numFiles));
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入组件，负责数据写入与提交。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StreamingOverwrite。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private class StreamingOverwrite extends BaseStreamingWrite {
    /** 执行该方法的具体逻辑。 */
    @Override
    protected String mode() {
      return "complete";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param epochId 参数
     * @param messages 参数
     */
    @Override
    public void doCommit(long epochId, WriterCommitMessage[] messages) {
      OverwriteFiles overwriteFiles = table.newOverwrite();
      overwriteFiles.overwriteByRowFilter(Expressions.alwaysTrue());
      int numFiles = 0;
      for (DataFile file : files(messages)) {
        overwriteFiles.addFile(file);
        numFiles++;
      }
      commit(
          overwriteFiles,
          epochId,
          String.format("streaming complete overwrite with %d new data files", numFiles));
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 TaskCommit。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  public static class TaskCommit implements WriterCommitMessage {
    private final DataFile[] taskFiles;

    TaskCommit(DataFile[] taskFiles) {
      this.taskFiles = taskFiles;
    }

    // Reports bytesWritten and recordsWritten to the Spark output metrics.
    // Can only be called in executor.
    /** 执行该方法的具体逻辑。 */
    void reportOutputMetrics() {
      long bytesWritten = 0L;
      long recordsWritten = 0L;
      for (DataFile dataFile : taskFiles) {
        bytesWritten += dataFile.fileSizeInBytes();
        recordsWritten += dataFile.recordCount();
      }

      TaskContext taskContext = TaskContext$.MODULE$.get();
      if (taskContext != null) {
        OutputMetrics outputMetrics = taskContext.taskMetrics().outputMetrics();
        outputMetrics.setBytesWritten(bytesWritten);
        outputMetrics.setRecordsWritten(recordsWritten);
      }
    }

    /** 执行该方法的具体逻辑。 */
    DataFile[] files() {
      return taskFiles;
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的工厂，负责创建实例。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 WriterFactory。
   *
   * <p>设计意图：工厂模式，集中创建逻辑便于扩展。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private static class WriterFactory implements DataWriterFactory, StreamingDataWriterFactory {
    private final Broadcast<Table> tableBroadcast;
    private final FileFormat format;
    private final int outputSpecId;
    private final long targetFileSize;
    private final Schema writeSchema;
    private final StructType dsSchema;
    private final boolean partitionedFanoutEnabled;
    private final String queryId;

    /** 构造 WriterFactory 实例。 */
    protected WriterFactory(
        Broadcast<Table> tableBroadcast,
        String queryId,
        FileFormat format,
        int outputSpecId,
        long targetFileSize,
        Schema writeSchema,
        StructType dsSchema,
        boolean partitionedFanoutEnabled) {
      this.tableBroadcast = tableBroadcast;
      this.format = format;
      this.outputSpecId = outputSpecId;
      this.targetFileSize = targetFileSize;
      this.writeSchema = writeSchema;
      this.dsSchema = dsSchema;
      this.partitionedFanoutEnabled = partitionedFanoutEnabled;
      this.queryId = queryId;
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
      return createWriter(partitionId, taskId, 0);
    }

    /**
     * 创建并返回新实例。
     *
     * @param partitionId 参数
     * @param taskId 参数
     * @param epochId 参数
     * @return 结果对象
     */
    @Override
    public DataWriter<InternalRow> createWriter(int partitionId, long taskId, long epochId) {
      Table table = tableBroadcast.value();
      PartitionSpec spec = table.specs().get(outputSpecId);
      FileIO io = table.io();

      OutputFileFactory fileFactory =
          OutputFileFactory.builderFor(table, partitionId, taskId)
              .format(format)
              .operationId(queryId)
              .build();
      SparkFileWriterFactory writerFactory =
          SparkFileWriterFactory.builderFor(table)
              .dataFileFormat(format)
              .dataSchema(writeSchema)
              .dataSparkType(dsSchema)
              .build();

      if (spec.isUnpartitioned()) {
        /** 执行该方法的具体逻辑。 */
        return new UnpartitionedDataWriter(writerFactory, fileFactory, io, spec, targetFileSize);

      } else {
        /** 执行该方法的具体逻辑。 */
        return new PartitionedDataWriter(
            writerFactory,
            fileFactory,
            io,
            spec,
            writeSchema,
            dsSchema,
            targetFileSize,
            partitionedFanoutEnabled);
      }
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 UnpartitionedDataWriter。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private static class UnpartitionedDataWriter implements DataWriter<InternalRow> {
    private final FileWriter<InternalRow, DataWriteResult> delegate;
    private final FileIO io;

    /** 构造 UnpartitionedDataWriter 实例。 */
    private UnpartitionedDataWriter(
        SparkFileWriterFactory writerFactory,
        OutputFileFactory fileFactory,
        FileIO io,
        PartitionSpec spec,
        long targetFileSize) {
      this.delegate =
          new RollingDataWriter<>(writerFactory, fileFactory, io, targetFileSize, spec, null);
      this.io = io;
    }

    /**
     * 写入数据。
     *
     * @param record 参数
     */
    @Override
    public void write(InternalRow record) throws IOException {
      delegate.write(record);
    }

    /**
     * 提交事务或写入结果。
     *
     * @return 结果对象
     */
    @Override
    public WriterCommitMessage commit() throws IOException {
      close();

      DataWriteResult result = delegate.result();
      TaskCommit taskCommit = new TaskCommit(result.dataFiles().toArray(new DataFile[0]));
      taskCommit.reportOutputMetrics();
      return taskCommit;
    }

    /** 中止并回滚当前操作。 */
    @Override
    public void abort() throws IOException {
      close();

      DataWriteResult result = delegate.result();
      SparkCleanupUtil.deleteTaskFiles(io, result.dataFiles());
    }

    /** 释放底层资源。 */
    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 PartitionedDataWriter。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  private static class PartitionedDataWriter implements DataWriter<InternalRow> {
    private final PartitioningWriter<InternalRow, DataWriteResult> delegate;
    private final FileIO io;
    private final PartitionSpec spec;
    private final PartitionKey partitionKey;
    private final InternalRowWrapper internalRowWrapper;

    /** 构造 PartitionedDataWriter 实例。 */
    private PartitionedDataWriter(
        SparkFileWriterFactory writerFactory,
        OutputFileFactory fileFactory,
        FileIO io,
        PartitionSpec spec,
        Schema dataSchema,
        StructType dataSparkType,
        long targetFileSize,
        boolean fanoutEnabled) {
      if (fanoutEnabled) {
        this.delegate = new FanoutDataWriter<>(writerFactory, fileFactory, io, targetFileSize);
      } else {
        this.delegate = new ClusteredDataWriter<>(writerFactory, fileFactory, io, targetFileSize);
      }
      this.io = io;
      this.spec = spec;
      this.partitionKey = new PartitionKey(spec, dataSchema);
      this.internalRowWrapper = new InternalRowWrapper(dataSparkType);
    }

    /**
     * 写入数据。
     *
     * @param row 参数
     */
    @Override
    public void write(InternalRow row) throws IOException {
      partitionKey.partition(internalRowWrapper.wrap(row));
      delegate.write(row, spec, partitionKey);
    }

    /**
     * 提交事务或写入结果。
     *
     * @return 结果对象
     */
    @Override
    public WriterCommitMessage commit() throws IOException {
      close();

      DataWriteResult result = delegate.result();
      TaskCommit taskCommit = new TaskCommit(result.dataFiles().toArray(new DataFile[0]));
      taskCommit.reportOutputMetrics();
      return taskCommit;
    }

    /** 中止并回滚当前操作。 */
    @Override
    public void abort() throws IOException {
      close();

      DataWriteResult result = delegate.result();
      SparkCleanupUtil.deleteTaskFiles(io, result.dataFiles());
    }

    /** 释放底层资源。 */
    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
