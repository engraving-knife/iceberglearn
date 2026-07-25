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
package org.apache.iceberg;

import java.util.Locale;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchTableException;

/**
 * 文件级说明：元数据表工具类。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>判断一个表名是否是 Iceberg 的元数据表名（例如 {@code db.t.snapshots}）。
 *   <li>根据元数据表类型（{@link MetadataTableType}）创建对应的元数据表实例 （{@link SnapshotsTable}、{@link
 *       FilesTable}、{@link MetadataLogEntriesTable} 等）。
 *   <li>支持从 {@link Table}、{@link TableOperations} + 表名、以及 catalog + TableIdentifier 等
 *       多种入口创建元数据表，屏蔽底层差异。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>集中管理元数据表的构造逻辑，避免各引擎分散实现造成不一致。
 *   <li>通过 switch-on-type 工厂模式扩展新增元数据表类型，编译期保证覆盖所有 case。
 *   <li>非 BaseTable（例如已经是元数据表或代理表）禁止再创建元数据表，避免元数据表的 元数据表这类无意义组合。
 * </ul>
 *
 * <p>上下游关系：被 catalog 模块（{@code BaseMetastoreCatalog}）及各引擎集成模块在 用户访问 {@code xxx.snapshots}
 * 这类表名时调用，依赖 {@link MetadataTableType} 与 各具体元数据表实现类。
 */
public class MetadataTableUtils {

  /** 私有构造：工具类禁止实例化。 */
  private MetadataTableUtils() {}

  /**
   * 判断给定的表标识符的名称部分是否对应一个元数据表类型。
   *
   * @param identifier 待判定的表标识符
   * @return 若名称能被解析为某个 {@link MetadataTableType} 则返回 true，否则 false
   */
  public static boolean hasMetadataTableName(TableIdentifier identifier) {
    return MetadataTableType.from(identifier.name()) != null;
  }

  /**
   * 基于已有 {@link Table} 创建元数据表实例。
   *
   * <p>仅当传入的是 {@link BaseTable} 时才允许构造；否则抛出 {@link
   * IllegalArgumentException}，以防止对非基础表（如元数据表本身）再创建元数据表。
   *
   * @param table 主表（必须是 BaseTable）
   * @param type 元数据表类型
   * @return 对应的元数据表实例
   * @throws IllegalArgumentException 当 table 不是 BaseTable 时
   */
  public static Table createMetadataTableInstance(Table table, MetadataTableType type) {
    if (table instanceof BaseTable) {
      return createMetadataTableInstance(table, metadataTableName(table.name(), type), type);
    } else {
      throw new IllegalArgumentException(
          String.format("Cannot create metadata table for table %s: not a base table", table));
    }
  }

  /**
   * 基于 {@link TableOperations} 与显式表名创建元数据表实例。
   *
   * <p>先用 ops 包装出一个 {@link BaseTable}，再委托给私有重载方法构造。
   *
   * @param ops 主表的 TableOperations
   * @param baseTableName 主表完整名称（用于命名与展示）
   * @param metadataTableName 元数据表完整名称
   * @param type 元数据表类型
   * @return 对应的元数据表实例
   */
  public static Table createMetadataTableInstance(
      TableOperations ops, String baseTableName, String metadataTableName, MetadataTableType type) {
    Table baseTable = new BaseTable(ops, baseTableName);
    return createMetadataTableInstance(baseTable, metadataTableName, type);
  }

  /**
   * 真正的工厂方法：按 {@link MetadataTableType} 路由到对应元数据表实现类。
   *
   * <p>设计要点：使用 switch 显式枚举所有类型，default 抛出 {@link NoSuchTableException}，让未识别类型在运行时立即暴露问题。
   *
   * @param baseTable 主表实例
   * @param metadataTableName 元数据表完整名称
   * @param type 元数据表类型
   * @return 对应的元数据表实例
   * @throws NoSuchTableException 当 type 不在已知枚举集合中时
   */
  private static Table createMetadataTableInstance(
      Table baseTable, String metadataTableName, MetadataTableType type) {
    switch (type) {
      case ENTRIES:
        return new ManifestEntriesTable(baseTable, metadataTableName);
      case FILES:
        return new FilesTable(baseTable, metadataTableName);
      case DATA_FILES:
        return new DataFilesTable(baseTable, metadataTableName);
      case DELETE_FILES:
        return new DeleteFilesTable(baseTable, metadataTableName);
      case HISTORY:
        return new HistoryTable(baseTable, metadataTableName);
      case SNAPSHOTS:
        return new SnapshotsTable(baseTable, metadataTableName);
      case METADATA_LOG_ENTRIES:
        return new MetadataLogEntriesTable(baseTable, metadataTableName);
      case REFS:
        return new RefsTable(baseTable, metadataTableName);
      case MANIFESTS:
        return new ManifestsTable(baseTable, metadataTableName);
      case PARTITIONS:
        return new PartitionsTable(baseTable, metadataTableName);
      case ALL_DATA_FILES:
        return new AllDataFilesTable(baseTable, metadataTableName);
      case ALL_DELETE_FILES:
        return new AllDeleteFilesTable(baseTable, metadataTableName);
      case ALL_FILES:
        return new AllFilesTable(baseTable, metadataTableName);
      case ALL_MANIFESTS:
        return new AllManifestsTable(baseTable, metadataTableName);
      case ALL_ENTRIES:
        return new AllEntriesTable(baseTable, metadataTableName);
      case POSITION_DELETES:
        return new PositionDeletesTable(baseTable, metadataTableName);
      default:
        throw new NoSuchTableException(
            "Unknown metadata table type: %s for %s", type, metadataTableName);
    }
  }

  /**
   * 基于 catalog 名称与表标识符创建元数据表实例。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>用 {@link BaseMetastoreCatalog#fullTableName} 拼接主表与元数据表的完整名称；
   *   <li>委托给 {@link #createMetadataTableInstance(TableOperations, String, String,
   *       MetadataTableType)}。
   * </ol>
   *
   * @param ops 主表的 TableOperations
   * @param catalogName catalog 名称（用于拼接完整表名）
   * @param baseTableIdentifier 主表标识符
   * @param metadataTableIdentifier 元数据表标识符
   * @param type 元数据表类型
   * @return 对应的元数据表实例
   */
  public static Table createMetadataTableInstance(
      TableOperations ops,
      String catalogName,
      TableIdentifier baseTableIdentifier,
      TableIdentifier metadataTableIdentifier,
      MetadataTableType type) {
    String baseTableName = BaseMetastoreCatalog.fullTableName(catalogName, baseTableIdentifier);
    String metadataTableName =
        BaseMetastoreCatalog.fullTableName(catalogName, metadataTableIdentifier);
    return createMetadataTableInstance(ops, baseTableName, metadataTableName, type);
  }

  /**
   * 根据主表名拼接元数据表名。
   *
   * <p>规则：若主表名包含 "/"（路径式表名，常见于文件系统 catalog），则用 "#" 作分隔符， 否则用 "."。元数据表类型名称以小写形式追加（例如 {@code
   * db.t.snapshots}）。
   *
   * @param tableName 主表完整名称
   * @param type 元数据表类型
   * @return 拼接后的元数据表名称
   */
  private static String metadataTableName(String tableName, MetadataTableType type) {
    return tableName + (tableName.contains("/") ? "#" : ".") + type.name().toLowerCase(Locale.ROOT);
  }
}
