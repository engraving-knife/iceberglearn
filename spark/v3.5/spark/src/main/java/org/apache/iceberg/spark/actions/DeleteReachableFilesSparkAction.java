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
package org.apache.iceberg.spark.actions;

import static org.apache.iceberg.TableProperties.GC_ENABLED;
import static org.apache.iceberg.TableProperties.GC_ENABLED_DEFAULT;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.actions.DeleteReachableFiles;
import org.apache.iceberg.actions.ImmutableDeleteReachableFiles;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.JobGroupInfo;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的可达文件删除 Action 实现。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层）。
 *
 * <p>职责：根据给定的 Iceberg 元数据文件位置，通过 Spark 读取元数据表中所有可达的
 * 文件（数据文件、位置删除文件、等值删除文件、manifest、manifest-list、其他元数据文件）， 并执行物理删除。用于表被删除后清理残留数据文件。
 *
 * <p>设计意图：利用 Spark 的分布式计算能力并行删除大量文件，避免单节点串行删除的 性能瓶颈。支持流式（stream-results）和批量两种模式：流式模式通过
 * toLocalIterator 逐条拉取，降低 driver 内存压力；批量模式使用 collectAsList 一次性收集。 当 FileIO 支持 {@link
 * SupportsBulkOperations} 时优先使用批量删除以提升效率。
 *
 * <p>上下游关系：实现 Iceberg API 的 {@link DeleteReachableFiles} 接口； 由 Spark 存储过程或 Action API 调用；依赖 Iceberg
 * 元数据表与 FileIO。
 */
@SuppressWarnings("UnnecessaryAnonymousClass")
public class DeleteReachableFilesSparkAction
    extends BaseSparkAction<DeleteReachableFilesSparkAction> implements DeleteReachableFiles {

  public static final String STREAM_RESULTS = "stream-results";
  public static final boolean STREAM_RESULTS_DEFAULT = false;

  private static final Logger LOG = LoggerFactory.getLogger(DeleteReachableFilesSparkAction.class);

  private final String metadataFileLocation;

  private Consumer<String> deleteFunc = null;
  private ExecutorService deleteExecutorService = null;
  private FileIO io = new HadoopFileIO(spark().sessionState().newHadoopConf());

  /**
   * 构造方法。
   *
   * @param spark SparkSession 实例
   * @param metadataFileLocation 待删除表的 Iceberg 元数据文件路径
   */
  DeleteReachableFilesSparkAction(SparkSession spark, String metadataFileLocation) {
    super(spark);
    this.metadataFileLocation = metadataFileLocation;
  }
  /** 执行 self 相关操作。 */
  @Override
  protected DeleteReachableFilesSparkAction self() {
    return this;
  }
  /** 执行 io 相关操作。 */
  @Override
  public DeleteReachableFilesSparkAction io(FileIO fileIO) {
    this.io = fileIO;
    return this;
  }
  /** 执行 deleteWith 相关操作。 */
  @Override
  public DeleteReachableFilesSparkAction deleteWith(Consumer<String> newDeleteFunc) {
    this.deleteFunc = newDeleteFunc;
    return this;
  }
  /** 执行 executeDeleteWith 相关操作。 */
  @Override
  public DeleteReachableFilesSparkAction executeDeleteWith(ExecutorService executorService) {
    this.deleteExecutorService = executorService;
    return this;
  }

  /**
   * 执行可达文件删除操作。
   *
   * <p>逻辑：校验 FileIO 非空，创建 JobGroupInfo 标识本次删除任务， 然后在 job group 上下文中执行 doExecute。
   *
   * @return 删除结果，包含各类文件的删除计数
   */
  @Override
  public Result execute() {
    Preconditions.checkArgument(io != null, "File IO cannot be null");
    String jobDesc = String.format("Deleting files reachable from %s", metadataFileLocation);
    JobGroupInfo info = newJobGroupInfo("DELETE-REACHABLE-FILES", jobDesc);
    return withJobGroupInfo(info, this::doExecute);
  }

  /**
   * 实际执行删除的内部方法。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取元数据文件并解析为 TableMetadata。
   *   <li>校验表的 GC 是否开启（GC 关闭时禁止删除，避免误删被其他表引用的文件）。
   *   <li>构建可达文件 Dataset，根据 stream-results 配置选择流式或批量方式删除。
   * </ol>
   *
   * @return 删除结果
   */
  private Result doExecute() {
    TableMetadata metadata = TableMetadataParser.read(io, metadataFileLocation);

    ValidationException.check(
        PropertyUtil.propertyAsBoolean(metadata.properties(), GC_ENABLED, GC_ENABLED_DEFAULT),
        "Cannot delete files: GC is disabled (deleting files may corrupt other tables)");

    Dataset<FileInfo> reachableFileDS = reachableFileDS(metadata);

    if (streamResults()) {
      return deleteFiles(reachableFileDS.toLocalIterator());
    } else {
      return deleteFiles(reachableFileDS.collectAsList().iterator());
    }
  }
  /** 执行 streamResults 相关操作。 */
  private boolean streamResults() {
    return PropertyUtil.propertyAsBoolean(options(), STREAM_RESULTS, STREAM_RESULTS_DEFAULT);
  }

  /**
   * 构建包含所有可达文件的 Dataset：数据文件、manifest、manifest-list 和其他元数据文件的并集去重。
   *
   * @param metadata 表元数据
   * @return 可达文件 Dataset
   */
  private Dataset<FileInfo> reachableFileDS(TableMetadata metadata) {
    Table staticTable = newStaticTable(metadata, io);
    return contentFileDS(staticTable)
        .union(manifestDS(staticTable))
        .union(manifestListDS(staticTable))
        .union(allReachableOtherMetadataFileDS(staticTable))
        .distinct();
  }
  /** 执行 deleteFiles 相关操作。 */
  private DeleteReachableFiles.Result deleteFiles(Iterator<FileInfo> files) {
    DeleteSummary summary;
    if (deleteFunc == null && io instanceof SupportsBulkOperations) {
      summary = deleteFiles((SupportsBulkOperations) io, files);
    } else {

      if (deleteFunc == null) {
        LOG.info(
            "Table IO {} does not support bulk operations. Using non-bulk deletes.",
            io.getClass().getName());
        summary = deleteFiles(deleteExecutorService, io::deleteFile, files);
      } else {
        LOG.info("Custom delete function provided. Using non-bulk deletes");
        summary = deleteFiles(deleteExecutorService, deleteFunc, files);
      }
    }

    LOG.info("Deleted {} total files", summary.totalFilesCount());

    return ImmutableDeleteReachableFiles.Result.builder()
        .deletedDataFilesCount(summary.dataFilesCount())
        .deletedPositionDeleteFilesCount(summary.positionDeleteFilesCount())
        .deletedEqualityDeleteFilesCount(summary.equalityDeleteFilesCount())
        .deletedManifestsCount(summary.manifestsCount())
        .deletedManifestListsCount(summary.manifestListsCount())
        .deletedOtherFilesCount(summary.otherFilesCount())
        .build();
  }
}
