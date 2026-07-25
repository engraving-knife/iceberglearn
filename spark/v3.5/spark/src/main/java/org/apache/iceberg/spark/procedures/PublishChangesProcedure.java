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
package org.apache.iceberg.spark.procedures;

import java.util.Optional;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.spark.procedures.SparkProcedures.ProcedureBuilder;
import org.apache.iceberg.util.WapUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Spark 存储过程：发布 Write-Audit-Publish（WAP）工作流中暂存的变更。
 *
 * <p>所属模块：iceberg-spark（procedures 子包，通过 Spark CALL 语句暴露 WAP 发布能力）。
 *
 * <p>职责：根据 wap_id 查找在 WAP 流程中暂存的快照，调用 {@link org.apache.iceberg.ManageSnapshots#cherrypick(long)}
 * 将其变更应用为新快照并设为当前快照， 返回源快照 ID 与新的当前快照 ID。
 *
 * <p>设计意图：WAP 模式下写入会暂存为带 wap_id 的快照而非直接成为当前快照， 经审计确认后通过本过程「挑选（cherrypick）」发布，实现写入与发布的解耦。
 *
 * <p>上下游关系：由 {@link SparkProcedures} 注册，调用 Iceberg 表快照管理 API。
 */
class PublishChangesProcedure extends BaseProcedure {

  private static final ProcedureParameter[] PARAMETERS =
      new ProcedureParameter[] {
        ProcedureParameter.required("table", DataTypes.StringType),
        ProcedureParameter.required("wap_id", DataTypes.StringType)
      };

  private static final StructType OUTPUT_TYPE =
      new StructType(
          new StructField[] {
            new StructField("source_snapshot_id", DataTypes.LongType, false, Metadata.empty()),
            new StructField("current_snapshot_id", DataTypes.LongType, false, Metadata.empty())
          });
  /** 执行 builder 相关操作。 */
  public static ProcedureBuilder builder() {
    return new Builder<PublishChangesProcedure>() {
      /** 执行 doBuild 相关操作。 */
      @Override
      protected PublishChangesProcedure doBuild() {
        return new PublishChangesProcedure(tableCatalog());
      }
    };
  }

  private PublishChangesProcedure(TableCatalog catalog) {
    super(catalog);
  }
  /** 返回参数。 */
  @Override
  public ProcedureParameter[] parameters() {
    return PARAMETERS;
  }
  /** 执行 outputType 相关操作。 */
  @Override
  public StructType outputType() {
    return OUTPUT_TYPE;
  }

  /**
   * 执行 WAP 变更发布。
   *
   * <p>逻辑：解析表名与 wap_id，在表快照中查找 stagedWapId 匹配的快照， 未找到则抛出校验异常；对其执行 cherrypick 提交，返回源快照与当前快照 ID。
   *
   * @param args 调用参数行
   * @return 含 source_snapshot_id 与 current_snapshot_id 的单行结果
   * @throws ValidationException 当 wap_id 无法匹配任何暂存快照时抛出
   */
  @Override
  public InternalRow[] call(InternalRow args) {
    Identifier tableIdent = toIdentifier(args.getString(0), PARAMETERS[0].name());
    String wapId = args.getString(1);

    return modifyIcebergTable(
        tableIdent,
        table -> {
          Optional<Snapshot> wapSnapshot =
              Optional.ofNullable(
                  Iterables.find(
                      table.snapshots(),
                      snapshot -> wapId.equals(WapUtil.stagedWapId(snapshot)),
                      null));
          if (!wapSnapshot.isPresent()) {
            throw new ValidationException(String.format("Cannot apply unknown WAP ID '%s'", wapId));
          }

          long wapSnapshotId = wapSnapshot.get().snapshotId();
          table.manageSnapshots().cherrypick(wapSnapshotId).commit();

          Snapshot currentSnapshot = table.currentSnapshot();

          InternalRow outputRow = newInternalRow(wapSnapshotId, currentSnapshot.snapshotId());
          return new InternalRow[] {outputRow};
        });
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "ApplyWapChangesProcedure";
  }
}
