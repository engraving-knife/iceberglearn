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
 * 基于 Spark 执行的 Iceberg 表维护动作，执行快照过期、文件清理、数据压缩等表维护操作。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 BaseTableCreationSparkAction。
 *
 * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
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

  /** 校验前置条件或参数。 */
  protected abstract TableCatalog checkSourceCatalog(CatalogPlugin catalog);

  /** 执行该方法的具体逻辑。 */
  protected abstract StagingTableCatalog destCatalog();

  /** 执行该方法的具体逻辑。 */
  protected abstract Identifier destTableIdent();

  /** 执行该方法的具体逻辑。 */
  protected abstract Map<String, String> destTableProps();

  /** 执行该方法的具体逻辑。 */
  protected String sourceTableLocation() {
    return sourceTableLocation;
  }

  /** 执行该方法的具体逻辑。 */
  protected CatalogTable v1SourceTable() {
    return sourceCatalogTable;
  }

  /** 执行该方法的具体逻辑。 */
  protected TableCatalog sourceCatalog() {
    return sourceCatalog;
  }

  /** 执行该方法的具体逻辑。 */
  protected Identifier sourceTableIdent() {
    return sourceTableIdent;
  }

  /** 设置properties。 */
  protected void setProperties(Map<String, String> properties) {
    additionalProperties.putAll(properties);
  }

  /** 设置property。 */
  protected void setProperty(String key, String value) {
    additionalProperties.put(key, value);
  }

  /** 添加元素或项。 */
  protected Map<String, String> additionalProperties() {
    return additionalProperties;
  }

  /** 校验前置条件或参数。 */
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

  /** 校验前置条件或参数。 */
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

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  protected void ensureNameMappingPresent(Table table) {
    if (!table.properties().containsKey(TableProperties.DEFAULT_NAME_MAPPING)) {
      NameMapping nameMapping = MappingUtil.create(table.schema());
      String nameMappingJson = NameMappingParser.toJson(nameMapping);
      table.updateProperties().set(TableProperties.DEFAULT_NAME_MAPPING, nameMappingJson).commit();
    }
  }

  /** 返回metadatalocation。 */
  protected String getMetadataLocation(Table table) {
    return table
        .properties()
        .getOrDefault(
            TableProperties.WRITE_METADATA_LOCATION,
            table.location() + "/" + ICEBERG_METADATA_FOLDER);
  }
}
