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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：删除可达文件的 Spark 动作，删除指定快照/分支可达的所有文件（用于彻底删除表）。
 *
 * <p>设计意图：遍历快照引用的文件清单并删除，用于表删除前的物理文件清理。
 *
 * <p>上下游关系：由 SparkActions 创建。
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
  /** 执行动作并返回结果。 */
  @Override
  public Result execute() {
    Preconditions.checkArgument(io != null, "File IO cannot be null");
    String jobDesc = String.format("Deleting files reachable from %s", metadataFileLocation);
    JobGroupInfo info = newJobGroupInfo("DELETE-REACHABLE-FILES", jobDesc);
    return withJobGroupInfo(info, this::doExecute);
  }
  /** 执行 doExecute 相关操作。 */
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
  /** 执行 reachableFileDS 相关操作。 */
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
