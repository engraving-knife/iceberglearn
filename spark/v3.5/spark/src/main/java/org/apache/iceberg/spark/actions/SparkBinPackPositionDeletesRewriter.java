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

import static org.apache.iceberg.MetadataTableType.POSITION_DELETES;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.MetadataTableType;
import org.apache.iceberg.MetadataTableUtils;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.actions.SizeBasedPositionDeletesRewriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.spark.PositionDeletesRewriteCoordinator;
import org.apache.iceberg.spark.ScanTaskSetManager;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkTableCache;
import org.apache.iceberg.spark.SparkTableUtil;
import org.apache.iceberg.spark.SparkValueConverter;
import org.apache.iceberg.spark.SparkWriteOptions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;

/**
 * 基于 Spark 的位置删除文件（Position Delete）Bin-Pack 重写器。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层）。
 *
 * <p>职责：将一组过小或过多的位置删除文件按目标大小（bin-pack）合并重写为更少、 更大的文件，以减少后续读取时的文件打开开销。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link SizeBasedPositionDeletesRewriter} 复用基于大小的文件分组逻辑。
 *   <li>使用 Spark 的分布式读写在 executor 上并行执行合并：通过 ScanTaskSetManager 暂存待重写的扫描任务，通过
 *       PositionDeletesRewriteCoordinator 收集重写产生的新文件。
 *   <li>禁用 AQE（自适应查询执行）以避免 Spark 改变写出分区数，保证每个 split 精确对应一个输出文件。
 * </ul>
 *
 * <p>上下游关系：被 RewritePositionDeleteFilesSparkAction 调用；依赖 Spark 读/写 Iceberg
 * 数据源、ScanTaskSetManager、PositionDeletesRewriteCoordinator。
 */
class SparkBinPackPositionDeletesRewriter extends SizeBasedPositionDeletesRewriter {

  private final SparkSession spark;
  private final SparkTableCache tableCache = SparkTableCache.get();
  private final ScanTaskSetManager taskSetManager = ScanTaskSetManager.get();
  private final PositionDeletesRewriteCoordinator coordinator =
      PositionDeletesRewriteCoordinator.get();

  SparkBinPackPositionDeletesRewriter(SparkSession spark, Table table) {
    super(table);
    // Disable Adaptive Query Execution as this may change the output partitioning of our write
    this.spark = spark.cloneSession();
    this.spark.conf().set(SQLConf.ADAPTIVE_EXECUTION_ENABLED().key(), false);
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "BIN-PACK";
  }

  /**
   * 重写一组位置删除文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>生成唯一 groupId，创建 POSITION_DELETES 元数据表实例并加入 tableCache。
   *   <li>通过 taskSetManager 暂存待重写的扫描任务组。
   *   <li>调用 doRewrite 执行实际的 Spark 读写合并。
   *   <li>从 coordinator 获取重写后产生的新文件集合。
   *   <li>在 finally 中清理所有暂存状态（tableCache、taskSetManager、coordinator）。
   * </ol>
   *
   * @param group 待重写的位置删除扫描任务列表
   * @return 重写后产生的新删除文件集合
   */
  @Override
  public Set<DeleteFile> rewrite(List<PositionDeletesScanTask> group) {
    String groupId = UUID.randomUUID().toString();
    Table deletesTable = MetadataTableUtils.createMetadataTableInstance(table(), POSITION_DELETES);
    try {
      tableCache.add(groupId, deletesTable);
      taskSetManager.stageTasks(deletesTable, groupId, group);

      doRewrite(groupId, group);

      return coordinator.fetchNewFiles(deletesTable, groupId);
    } finally {
      tableCache.remove(groupId);
      taskSetManager.removeTasks(deletesTable, groupId);
      coordinator.clearRewrite(deletesTable, groupId);
    }
  }

  /**
   * 通过 Spark 读写执行实际的 bin-pack 合并。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 group 非空，提取分区类型与分区值（同组所有删除文件属于同一分区）。
   *   <li>通过 Spark 读取暂存的删除文件（SCAN_TASK_SET_ID = groupId）， 按 splitSize 切分使每个 split 对应一个输出文件。
   *   <li>与 DataFiles 元数据表做 leftsemi join，过滤掉已无效的删除文件 （引用的数据文件可能已被删除）。
   *   <li>按 file_path、pos 排序后写出为新的 Iceberg 删除文件。
   * </ol>
   *
   * @param groupId 任务组唯一标识
   * @param group 待重写的位置删除扫描任务列表
   */
  protected void doRewrite(String groupId, List<PositionDeletesScanTask> group) {
    // all position deletes are of the same partition, because they are in same file group
    Preconditions.checkArgument(group.size() > 0, "Empty group");
    Types.StructType partitionType = group.get(0).spec().partitionType();
    StructLike partition = group.get(0).partition();

    // read the deletes packing them into splits of the required size
    Dataset<Row> posDeletes =
        spark
            .read()
            .format("iceberg")
            .option(SparkReadOptions.SCAN_TASK_SET_ID, groupId)
            .option(SparkReadOptions.SPLIT_SIZE, splitSize(inputSize(group)))
            .option(SparkReadOptions.FILE_OPEN_COST, "0")
            .load(groupId);

    // keep only valid position deletes
    Dataset<Row> dataFiles = dataFiles(partitionType, partition);
    Column joinCond = posDeletes.col("file_path").equalTo(dataFiles.col("file_path"));
    Dataset<Row> validDeletes = posDeletes.join(dataFiles, joinCond, "leftsemi");

    // write the packed deletes into new files where each split becomes a new file
    validDeletes
        .sortWithinPartitions("file_path", "pos")
        .write()
        .format("iceberg")
        .option(SparkWriteOptions.REWRITTEN_FILE_SCAN_TASK_SET_ID, groupId)
        .option(SparkWriteOptions.TARGET_DELETE_FILE_SIZE_BYTES, writeMaxFileSize())
        .mode("append")
        .save(groupId);
  }

  /**
   * 返回指定分区下的 DataFiles 元数据表条目，用于与位置删除文件做 join 过滤。
   *
   * <p>逻辑：根据分区类型构造分区列的等值过滤条件（使用 eqNullSafe 处理 null）， 从 DataFiles 元数据表中筛选出该分区的数据文件。无分区字段时返回全部。
   *
   * @param partitionType 分区类型
   * @param partition 分区值
   * @return 该分区的数据文件 Dataset
   */
  private Dataset<Row> dataFiles(Types.StructType partitionType, StructLike partition) {
    List<Types.NestedField> fields = partitionType.fields();
    Optional<Column> condition =
        IntStream.range(0, fields.size())
            .mapToObj(
                i -> {
                  Type type = fields.get(i).type();
                  Object value = partition.get(i, type.typeId().javaClass());
                  Object convertedValue = SparkValueConverter.convertToSpark(type, value);
                  Column col = col("partition.`" + fields.get(i).name() + "`");
                  return col.eqNullSafe(lit(convertedValue));
                })
            .reduce(Column::and);
    if (condition.isPresent()) {
      return SparkTableUtil.loadMetadataTable(spark, table(), MetadataTableType.DATA_FILES)
          .filter(condition.get());
    } else {
      return SparkTableUtil.loadMetadataTable(spark, table(), MetadataTableType.DATA_FILES);
    }
  }
}
