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
package org.apache.iceberg.flink.util;

import java.util.List;
import java.util.Map;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.catalog.TableChange;
import org.apache.flink.table.catalog.UniqueConstraint;
import org.apache.iceberg.Table;
import org.apache.iceberg.Transaction;
import org.apache.iceberg.UpdateProperties;
import org.apache.iceberg.UpdateSchema;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;

/**
 * 文件级说明：把 Flink 的 ALTER TABLE 操作应用到 Iceberg 表的工具类。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 util 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Flink 的 {@link TableChange}（schema 变更、属性变更）翻译为 Iceberg 的对应操作。
 *   <li>支持 SET LOCATION、SET SNAPSHOT、CHERRYPICK SNAPSHOT、属性设置等操作。
 *   <li>把多个变更放在一个 {@link Transaction} 中提交以保证原子性。
 * </ul>
 *
 * <p>设计意图：作为 Flink 与 Iceberg 表变更操作的适配层， 屏蔽两种 API 的差异，便于支持 ALTER TABLE 语法。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.FlinkCatalog}， 下游为 Iceberg 的 {@link
 * UpdateSchema}、{@link UpdateProperties}、{@link Transaction} 等 API。
 */
public class FlinkAlterTableUtil {
  private FlinkAlterTableUtil() {}

  /**
   * 提交属性与位置变更到 Iceberg 表。
   *
   * <p>逻辑：先处理 snapshot 管理操作，然后开启事务依次执行 位置变更与属性变更，最后统一提交。
   *
   * @param table Iceberg 表
   * @param setLocation 新位置，可为空
   * @param setSnapshotId 设置当前 snapshot 的 ID，可为空
   * @param pickSnapshotId cherry-pick 的 snapshot ID，可为空
   * @param setProperties 属性变更映射，value 为 null 表示删除
   */
  public static void commitChanges(
      Table table,
      String setLocation,
      String setSnapshotId,
      String pickSnapshotId,
      Map<String, String> setProperties) {
    commitManageSnapshots(table, setSnapshotId, pickSnapshotId);

    Transaction transaction = table.newTransaction();

    if (setLocation != null) {
      transaction.updateLocation().setLocation(setLocation).commit();
    }

    if (!setProperties.isEmpty()) {
      UpdateProperties updateProperties = transaction.updateProperties();
      setProperties.forEach(
          (k, v) -> {
            if (v == null) {
              updateProperties.remove(k);
            } else {
              updateProperties.set(k, v);
            }
          });
      updateProperties.commit();
    }

    transaction.commitTransaction();
  }

  /**
   * 提交 schema 与属性变更到 Iceberg 表。
   *
   * <p>逻辑：先处理 snapshot 管理操作，再开启事务依次执行位置、schema、属性变更， 最后统一提交以保证原子性。
   *
   * @param table Iceberg 表
   * @param setLocation 新位置，可为空
   * @param setSnapshotId 设置当前 snapshot 的 ID，可为空
   * @param pickSnapshotId cherry-pick 的 snapshot ID，可为空
   * @param schemaChanges Flink schema 变更列表
   * @param propertyChanges Flink 属性变更列表
   */
  public static void commitChanges(
      Table table,
      String setLocation,
      String setSnapshotId,
      String pickSnapshotId,
      List<TableChange> schemaChanges,
      List<TableChange> propertyChanges) {
    commitManageSnapshots(table, setSnapshotId, pickSnapshotId);

    Transaction transaction = table.newTransaction();

    if (setLocation != null) {
      transaction.updateLocation().setLocation(setLocation).commit();
    }

    if (!schemaChanges.isEmpty()) {
      UpdateSchema updateSchema = transaction.updateSchema();
      FlinkAlterTableUtil.applySchemaChanges(updateSchema, schemaChanges);
      updateSchema.commit();
    }

    if (!propertyChanges.isEmpty()) {
      UpdateProperties updateProperties = transaction.updateProperties();
      FlinkAlterTableUtil.applyPropertyChanges(updateProperties, propertyChanges);
      updateProperties.commit();
    }

    transaction.commitTransaction();
  }

  /**
   * 提交 snapshot 管理操作（设置当前 snapshot 或 cherry-pick）。
   *
   * <p>逻辑：不允许同时设置 snapshot 与 cherry-pick，因二者顺序敏感会导致不同结果。 若设置 snapshot，调用
   * manageSnapshots().setCurrentSnapshot().commit()； 若 cherry-pick，先于其他操作执行以避免失败影响后续。
   *
   * @param table Iceberg 表
   * @param setSnapshotId 设置当前 snapshot 的 ID，可为空
   * @param cherrypickSnapshotId cherry-pick 的 snapshot ID，可为空
   */
  public static void commitManageSnapshots(
      Table table, String setSnapshotId, String cherrypickSnapshotId) {
    // 不允许同时设置 snapshot 与 cherry-pick，因顺序敏感会导致不同结果
    Preconditions.checkArgument(
        setSnapshotId == null || cherrypickSnapshotId == null,
        "Cannot set the current snapshot ID and cherry-pick snapshot changes");

    if (setSnapshotId != null) {
      long newSnapshotId = Long.parseLong(setSnapshotId);
      table.manageSnapshots().setCurrentSnapshot(newSnapshotId).commit();
    }

    // 若更新表 snapshot，先于其他操作执行，便于失败时不影响后续
    if (cherrypickSnapshotId != null) {
      long newSnapshotId = Long.parseLong(cherrypickSnapshotId);
      table.manageSnapshots().cherrypick(newSnapshotId).commit();
    }
  }

  /**
   * 把 Flink 表变更列表应用到 {@link UpdateSchema}。
   *
   * @param pendingUpdate 未提交的 UpdateSchema 操作
   * @param schemaChanges Flink 表变更列表
   */
  public static void applySchemaChanges(
      UpdateSchema pendingUpdate, List<TableChange> schemaChanges) {
    for (TableChange change : schemaChanges) {
      if (change instanceof TableChange.AddColumn) {
        TableChange.AddColumn addColumn = (TableChange.AddColumn) change;
        Column flinkColumn = addColumn.getColumn();
        Preconditions.checkArgument(
            FlinkCompatibilityUtil.isPhysicalColumn(flinkColumn),
            "Unsupported table change: Adding computed column %s.",
            flinkColumn.getName());
        Type icebergType = FlinkSchemaUtil.convert(flinkColumn.getDataType().getLogicalType());
        if (flinkColumn.getDataType().getLogicalType().isNullable()) {
          pendingUpdate.addColumn(flinkColumn.getName(), icebergType);
        } else {
          pendingUpdate.addRequiredColumn(flinkColumn.getName(), icebergType);
        }
      } else if (change instanceof TableChange.ModifyColumn) {
        TableChange.ModifyColumn modifyColumn = (TableChange.ModifyColumn) change;
        applyModifyColumn(pendingUpdate, modifyColumn);
      } else if (change instanceof TableChange.DropColumn) {
        TableChange.DropColumn dropColumn = (TableChange.DropColumn) change;
        pendingUpdate.deleteColumn(dropColumn.getColumnName());
      } else if (change instanceof TableChange.AddWatermark) {
        throw new UnsupportedOperationException("Unsupported table change: AddWatermark.");
      } else if (change instanceof TableChange.ModifyWatermark) {
        throw new UnsupportedOperationException("Unsupported table change: ModifyWatermark.");
      } else if (change instanceof TableChange.DropWatermark) {
        throw new UnsupportedOperationException("Unsupported table change: DropWatermark.");
      } else if (change instanceof TableChange.AddUniqueConstraint) {
        TableChange.AddUniqueConstraint addPk = (TableChange.AddUniqueConstraint) change;
        applyUniqueConstraint(pendingUpdate, addPk.getConstraint());
      } else if (change instanceof TableChange.ModifyUniqueConstraint) {
        TableChange.ModifyUniqueConstraint modifyPk = (TableChange.ModifyUniqueConstraint) change;
        applyUniqueConstraint(pendingUpdate, modifyPk.getNewConstraint());
      } else if (change instanceof TableChange.DropConstraint) {
        throw new UnsupportedOperationException("Unsupported table change: DropConstraint.");
      } else {
        throw new UnsupportedOperationException("Cannot apply unknown table change: " + change);
      }
    }
  }

  /**
   * 把 Flink 属性变更列表应用到 {@link UpdateProperties}。
   *
   * <p>逻辑：SetOption 调用 {@code set}，ResetOption 调用 {@code remove}，其他类型抛异常。
   *
   * @param pendingUpdate 未提交的 UpdateProperties 操作
   * @param propertyChanges Flink 表变更列表
   */
  public static void applyPropertyChanges(
      UpdateProperties pendingUpdate, List<TableChange> propertyChanges) {
    for (TableChange change : propertyChanges) {
      if (change instanceof TableChange.SetOption) {
        TableChange.SetOption setOption = (TableChange.SetOption) change;
        pendingUpdate.set(setOption.getKey(), setOption.getValue());
      } else if (change instanceof TableChange.ResetOption) {
        TableChange.ResetOption resetOption = (TableChange.ResetOption) change;
        pendingUpdate.remove(resetOption.getKey());
      } else {
        throw new UnsupportedOperationException(
            "The given table change is not a property change: " + change);
      }
    }
  }

  /**
   * 把 Flink 修改列变更应用到 {@link UpdateSchema}。
   *
   * <p>逻辑：按子类型分发：重命名、移动位置、修改类型、修改注释，其他抛异常。
   *
   * @param pendingUpdate 未提交的 UpdateSchema
   * @param modifyColumn Flink 修改列变更
   */
  private static void applyModifyColumn(
      UpdateSchema pendingUpdate, TableChange.ModifyColumn modifyColumn) {
    if (modifyColumn instanceof TableChange.ModifyColumnName) {
      TableChange.ModifyColumnName modifyName = (TableChange.ModifyColumnName) modifyColumn;
      pendingUpdate.renameColumn(modifyName.getOldColumnName(), modifyName.getNewColumnName());
    } else if (modifyColumn instanceof TableChange.ModifyColumnPosition) {
      TableChange.ModifyColumnPosition modifyPosition =
          (TableChange.ModifyColumnPosition) modifyColumn;
      applyModifyColumnPosition(pendingUpdate, modifyPosition);
    } else if (modifyColumn instanceof TableChange.ModifyPhysicalColumnType) {
      TableChange.ModifyPhysicalColumnType modifyType =
          (TableChange.ModifyPhysicalColumnType) modifyColumn;
      Type type = FlinkSchemaUtil.convert(modifyType.getNewType().getLogicalType());
      String columnName = modifyType.getOldColumn().getName();
      pendingUpdate.updateColumn(columnName, type.asPrimitiveType());
      if (modifyType.getNewColumn().getDataType().getLogicalType().isNullable()) {
        pendingUpdate.makeColumnOptional(columnName);
      } else {
        pendingUpdate.requireColumn(columnName);
      }
    } else if (modifyColumn instanceof TableChange.ModifyColumnComment) {
      TableChange.ModifyColumnComment modifyComment =
          (TableChange.ModifyColumnComment) modifyColumn;
      pendingUpdate.updateColumnDoc(
          modifyComment.getOldColumn().getName(), modifyComment.getNewComment());
    } else {
      throw new UnsupportedOperationException(
          "Cannot apply unknown modify-column change: " + modifyColumn);
    }
  }

  /** 把 Flink 修改列位置变更（First/After）应用到 UpdateSchema。 */
  private static void applyModifyColumnPosition(
      UpdateSchema pendingUpdate, TableChange.ModifyColumnPosition modifyColumnPosition) {
    TableChange.ColumnPosition newPosition = modifyColumnPosition.getNewPosition();
    if (newPosition instanceof TableChange.First) {
      pendingUpdate.moveFirst(modifyColumnPosition.getOldColumn().getName());
    } else if (newPosition instanceof TableChange.After) {
      TableChange.After after = (TableChange.After) newPosition;
      pendingUpdate.moveAfter(modifyColumnPosition.getOldColumn().getName(), after.column());
    } else {
      throw new UnsupportedOperationException(
          "Cannot apply unknown modify-column-position change: " + modifyColumnPosition);
    }
  }

  /** 把 Flink 唯一约束（主键）应用到 UpdateSchema 的 identifier fields。 */
  private static void applyUniqueConstraint(
      UpdateSchema pendingUpdate, UniqueConstraint constraint) {
    switch (constraint.getType()) {
      case PRIMARY_KEY:
        pendingUpdate.setIdentifierFields(constraint.getColumns());
        break;
      case UNIQUE_KEY:
        throw new UnsupportedOperationException(
            "Unsupported table change: setting unique key constraints.");
      default:
        throw new UnsupportedOperationException(
            "Cannot apply unknown unique constraint: " + constraint.getType().name());
    }
  }
}
