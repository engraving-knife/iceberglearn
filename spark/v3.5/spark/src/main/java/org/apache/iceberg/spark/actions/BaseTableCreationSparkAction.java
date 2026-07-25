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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.SparkCatalog;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.source.StagedSparkTable;
import org.apache.iceberg.util.LocationUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.catalog.CatalogTable;
import org.apache.spark.sql.catalyst.catalog.CatalogUtils;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.StagingTableCatalog;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.catalog.V1Table;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.types.StructType;

/**
 * 基于 Spark 的"建表型"动作基类。
 *
 * <p>所属模块：iceberg-spark（Spark 运行时集成模块）；本类位于 actions 子包，为 snapshot/migrate 等把外部表转换为 Iceberg
 * 表的动作提供公共能力。抽象泛型 ThisT 供子类链式调用返回自身类型。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>加载并校验源表（须为 v1 表，且 provider 属于 parquet/avro/orc/hive）。
 *   <li>在目标 Iceberg Catalog 中暂存创建（stageCreate）目标表，并提供目标表属性、标识等抽象接口。
 *   <li>辅助：确保 NameMapping 存在、计算元数据目录位置等。
 * </ul>
 *
 * <p>设计意图：将源表加载/校验、目标 Catalog 校验、暂存建表等通用流程上提，子类只需实现 与具体动作相关的目录与属性差异，避免重复代码。使用 StagingTableCatalog
 * 实现原子建表。
 *
 * <p>上下游关系：继承 {@link BaseSparkAction}；被 {@link SnapshotTableSparkAction}、 {@link
 * MigrateTableSparkAction} 等继承；依赖 {@link SparkCatalog}/{@link SparkSessionCatalog}。
 */
abstract class BaseTableCreationSparkAction<ThisT> extends BaseSparkAction<ThisT> {
  private static final Set<String> ALLOWED_SOURCES =
      ImmutableSet.of("parquet", "avro", "orc", "hive");
  protected static final String LOCATION = "location";
  protected static final String ICEBERG_METADATA_FOLDER = "metadata";
  protected static final List<String> EXCLUDED_PROPERTIES =
      ImmutableList.of("path", "transient_lastDdlTime", "serialization.format");

  // Source Fields
  private final V1Table sourceTable;
  private final CatalogTable sourceCatalogTable;
  private final String sourceTableLocation;
  private final TableCatalog sourceCatalog;
  private final Identifier sourceTableIdent;

  // Optional Parameters for destination
  private final Map<String, String> additionalProperties = Maps.newHashMap();

  /**
   * 构造并初始化源表信息。
   *
   * <p>逻辑：校验源 Catalog，加载源表并强转为 V1Table（否则报错），调用 {@link #validateSourceTable} 校验 provider 与
   * location，最终记录源表存储位置。
   */
  BaseTableCreationSparkAction(
      SparkSession spark, CatalogPlugin sourceCatalog, Identifier sourceTableIdent) {
    super(spark);

    this.sourceCatalog = checkSourceCatalog(sourceCatalog);
    this.sourceTableIdent = sourceTableIdent;

    try {
      this.sourceTable = (V1Table) this.sourceCatalog.loadTable(sourceTableIdent);
      this.sourceCatalogTable = sourceTable.v1Table();
    } catch (org.apache.spark.sql.catalyst.analysis.NoSuchTableException e) {
      throw new NoSuchTableException("Cannot not find source table '%s'", sourceTableIdent);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format("Cannot use non-v1 table '%s' as a source", sourceTableIdent), e);
    }
    validateSourceTable();

    this.sourceTableLocation =
        CatalogUtils.URIToString(sourceCatalogTable.storage().locationUri().get());
  }

  /** 校验源 Catalog 类型是否可用，由子类实现。 */
  protected abstract TableCatalog checkSourceCatalog(CatalogPlugin catalog);

  /** 返回目标暂存建表 Catalog，由子类实现。 */
  protected abstract StagingTableCatalog destCatalog();

  /** 返回目标表标识，由子类实现。 */
  protected abstract Identifier destTableIdent();

  /** 返回目标表属性，由子类实现。 */
  protected abstract Map<String, String> destTableProps();

  /** 返回源表存储位置。 */
  protected String sourceTableLocation() {
    return sourceTableLocation;
  }

  /** 返回源表的 Spark v1 CatalogTable。 */
  protected CatalogTable v1SourceTable() {
    return sourceCatalogTable;
  }

  /** 返回源 Catalog。 */
  protected TableCatalog sourceCatalog() {
    return sourceCatalog;
  }

  /** 返回源表标识。 */
  protected Identifier sourceTableIdent() {
    return sourceTableIdent;
  }

  /** 批量追加目标表附加属性。 */
  protected void setProperties(Map<String, String> properties) {
    additionalProperties.putAll(properties);
  }

  /** 追加单个目标表附加属性。 */
  protected void setProperty(String key, String value) {
    additionalProperties.put(key, value);
  }

  /** 返回已设置的附加属性。 */
  protected Map<String, String> additionalProperties() {
    return additionalProperties;
  }

  /**
   * 校验源表可用性。
   *
   * <p>逻辑：要求 provider 属于允许集合（parquet/avro/orc/hive），且源表必须显式指定存储位置。
   */
  private void validateSourceTable() {
    String sourceTableProvider = sourceCatalogTable.provider().get().toLowerCase(Locale.ROOT);
    Preconditions.checkArgument(
        ALLOWED_SOURCES.contains(sourceTableProvider),
        "Cannot create an Iceberg table from source provider: '%s'",
        sourceTableProvider);
    Preconditions.checkArgument(
        !sourceCatalogTable.storage().locationUri().isEmpty(),
        "Cannot create an Iceberg table from a source without an explicit location");
  }

  /**
   * 校验目标 Catalog 必须是 Iceberg Catalog（{@link SparkCatalog} 或 {@link SparkSessionCatalog}）， 并以 {@link
   * StagingTableCatalog} 形式返回。
   */
  protected StagingTableCatalog checkDestinationCatalog(CatalogPlugin catalog) {
    Preconditions.checkArgument(
        catalog instanceof SparkSessionCatalog || catalog instanceof SparkCatalog,
        "Cannot create Iceberg table in non-Iceberg Catalog. "
            + "Catalog '%s' was of class '%s' but '%s' or '%s' are required",
        catalog.name(),
        catalog.getClass().getName(),
        SparkSessionCatalog.class.getName(),
        SparkCatalog.class.getName());

    return (StagingTableCatalog) catalog;
  }

  /**
   * 在目标 Catalog 中暂存创建目标表。
   *
   * <p>逻辑：使用源表 schema 与分区变换、目标属性调用 stageCreate；命名空间不存在或表已存在时 转换为对应 Iceberg 异常抛出。
   *
   * @return 暂存的 {@link StagedSparkTable}
   */
  protected StagedSparkTable stageDestTable() {
    try {
      Map<String, String> props = destTableProps();
      StructType schema = sourceTable.schema();
      Transform[] partitioning = sourceTable.partitioning();
      return (StagedSparkTable)
          destCatalog().stageCreate(destTableIdent(), schema, partitioning, props);
    } catch (org.apache.spark.sql.catalyst.analysis.NoSuchNamespaceException e) {
      throw new NoSuchNamespaceException(
          "Cannot create table %s as the namespace does not exist", destTableIdent());
    } catch (org.apache.spark.sql.catalyst.analysis.TableAlreadyExistsException e) {
      throw new AlreadyExistsException(
          "Cannot create table %s as it already exists", destTableIdent());
    }
  }

  /**
   * 确保目标表配置了默认 NameMapping。
   *
   * <p>逻辑：若表属性未包含 {@link TableProperties#DEFAULT_NAME_MAPPING}，则基于当前 schema 生成 NameMapping
   * 并写入表属性后提交，便于无 schema 信息的文件能匹配字段。
   */
  protected void ensureNameMappingPresent(Table table) {
    if (!table.properties().containsKey(TableProperties.DEFAULT_NAME_MAPPING)) {
      NameMapping nameMapping = MappingUtil.create(table.schema());
      String nameMappingJson = NameMappingParser.toJson(nameMapping);
      table.updateProperties().set(TableProperties.DEFAULT_NAME_MAPPING, nameMappingJson).commit();
    }
  }

  /** 计算目标表元数据目录位置：优先取表属性中的写入元数据位置，否则用表 location + "/metadata"。 */
  protected String getMetadataLocation(Table table) {
    String defaultValue =
        LocationUtil.stripTrailingSlash(table.location()) + "/" + ICEBERG_METADATA_FOLDER;
    return LocationUtil.stripTrailingSlash(
        table.properties().getOrDefault(TableProperties.WRITE_METADATA_LOCATION, defaultValue));
  }
}
