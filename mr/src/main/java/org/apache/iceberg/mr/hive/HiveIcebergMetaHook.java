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
package org.apache.iceberg.mr.hive;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.HiveMetaHook;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.PartitionSpecParser;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.hive.HiveSchemaUtil;
import org.apache.iceberg.hive.HiveTableOperations;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg 表在 Hive Metastore（HMS）侧的元数据钩子。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，作为 HMS 表生命周期 与 Iceberg 表生命周期之间的桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link HiveMetaHook}，在 HMS 表创建/删除前后插入 Iceberg 侧动作。
 *   <li>建表前：解析 schema 与分区规格、设置 Iceberg 表类型标记、保留 purge 标志。
 *   <li>建表后：调用 {@link Catalogs#createTable} 真正创建 Iceberg 表（如尚未存在）。
 *   <li>删表前：记录待清理的元数据与 FileIO；删表后：按 purge 标志清理 Iceberg 表数据。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>同时支持 HiveCatalog 与非 HiveCatalog（HadoopTables 等）两种部署：HiveCatalog 模式下 Iceberg 表元数据存于 HMS；非
 *       HiveCatalog 模式下 HMS 仅作“影子目录”，需要单独建表。
 *   <li>schema 优先级：用户显式提供的 schema/spec 优先；否则由 HMS 列定义转换生成。
 *   <li>清理路径分两支：HiveCatalog 由 HMS 管理 metadata 目录，仅删数据；非 HiveCatalog 直接 调 {@link Catalogs#dropTable}
 *       删表。
 *   <li>异常容忍：drop 阶段的异常不应阻断 HMS 删表命令，因此 catch 后只告警。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive Metastore 在 DDL 事件中调用；下游依赖 {@link Catalogs}、 {@link HiveSchemaUtil}、{@link
 * CatalogUtil}。
 */
public class HiveIcebergMetaHook implements HiveMetaHook {
  private static final Logger LOG = LoggerFactory.getLogger(HiveIcebergMetaHook.class);
  private static final Set<String> PARAMETERS_TO_REMOVE =
      ImmutableSet.of(InputFormatConfig.TABLE_SCHEMA, Catalogs.LOCATION, Catalogs.NAME);
  private static final Set<String> PROPERTIES_TO_REMOVE =
      ImmutableSet
          // We don't want to push down the metadata location props to Iceberg from HMS,
          // since the snapshot pointer in HMS would always be one step ahead
          .of(
          BaseMetastoreTableOperations.METADATA_LOCATION_PROP,
          BaseMetastoreTableOperations.PREVIOUS_METADATA_LOCATION_PROP,
          // Initially we'd like to cache the partition spec in HMS, but not push it down later to
          // Iceberg during alter
          // table commands since by then the HMS info can be stale + Iceberg does not store its
          // partition spec in the props
          InputFormatConfig.PARTITION_SPEC);

  private final Configuration conf;
  private Table icebergTable = null;
  private Properties catalogProperties;
  private boolean deleteIcebergTable;
  private FileIO deleteIo;
  private TableMetadata deleteMetadata;

  /**
   * 构造钩子。
   *
   * @param conf Hadoop 配置，决定 catalog 类型与加载方式
   */
  public HiveIcebergMetaHook(Configuration conf) {
    this.conf = conf;
  }

  /**
   * HMS 建表前钩子。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 HMS 表参数计算 catalog 属性。
   *   <li>无论是否 HiveCatalog，都把表类型参数置为 ICEBERG。
   *   <li>非 HiveCatalog 时：设置 InputFormat/OutputFormat 以便其他引擎（如 Impala）识别； 尝试加载已有 Iceberg
   *       表，若存在则校验未重复提供 schema/spec 并直接返回。
   *   <li>表不存在时：计算 schema 与分区 spec（用户显式提供优先，否则由 HMS 列转换）， 把分区键合并进列列表，序列化 schema/spec 写入 catalog
   *       属性，设置 purge 默认 TRUE， 非 HiveCatalog 时校验 location 已设置。
   *   <li>从 HMS 参数中移除建表专用的控制参数。
   * </ol>
   *
   * @param hmsTable HMS 表对象
   */
  @Override
  public void preCreateTable(org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    this.catalogProperties = getCatalogProperties(hmsTable);

    // Set the table type even for non HiveCatalog based tables
    hmsTable
        .getParameters()
        .put(
            BaseMetastoreTableOperations.TABLE_TYPE_PROP,
            BaseMetastoreTableOperations.ICEBERG_TABLE_TYPE_VALUE.toUpperCase());

    if (!Catalogs.hiveCatalog(conf, catalogProperties)) {
      // For non-HiveCatalog tables too, we should set the input and output format
      // so that the table can be read by other engines like Impala
      hmsTable.getSd().setInputFormat(HiveIcebergInputFormat.class.getCanonicalName());
      hmsTable.getSd().setOutputFormat(HiveIcebergOutputFormat.class.getCanonicalName());

      // If not using HiveCatalog check for existing table
      try {
        this.icebergTable = Catalogs.loadTable(conf, catalogProperties);

        Preconditions.checkArgument(
            catalogProperties.getProperty(InputFormatConfig.TABLE_SCHEMA) == null,
            "Iceberg table already created - can not use provided schema");
        Preconditions.checkArgument(
            catalogProperties.getProperty(InputFormatConfig.PARTITION_SPEC) == null,
            "Iceberg table already created - can not use provided partition specification");

        LOG.info("Iceberg table already exists {}", icebergTable);

        return;
      } catch (NoSuchTableException nte) {
        // If the table does not exist we will create it below
      }
    }

    // If the table does not exist collect data for table creation
    // - InputFormatConfig.TABLE_SCHEMA, InputFormatConfig.PARTITION_SPEC takes precedence so the
    // user can override the
    // Iceberg schema and specification generated by the code

    Schema schema = schema(catalogProperties, hmsTable);
    PartitionSpec spec = spec(schema, catalogProperties, hmsTable);

    // If there are partition keys specified remove them from the HMS table and add them to the
    // column list
    if (hmsTable.isSetPartitionKeys()) {
      hmsTable.getSd().getCols().addAll(hmsTable.getPartitionKeys());
      hmsTable.setPartitionKeysIsSet(false);
    }

    catalogProperties.put(InputFormatConfig.TABLE_SCHEMA, SchemaParser.toJson(schema));
    catalogProperties.put(InputFormatConfig.PARTITION_SPEC, PartitionSpecParser.toJson(spec));

    // Allow purging table data if the table is created now and not set otherwise
    hmsTable.getParameters().putIfAbsent(InputFormatConfig.EXTERNAL_TABLE_PURGE, "TRUE");

    // If the table is not managed by Hive catalog then the location should be set
    if (!Catalogs.hiveCatalog(conf, catalogProperties)) {
      Preconditions.checkArgument(
          hmsTable.getSd() != null && hmsTable.getSd().getLocation() != null,
          "Table location not set");
    }

    // Remove creation related properties
    PARAMETERS_TO_REMOVE.forEach(hmsTable.getParameters()::remove);
  }

  /** HMS 建表回滚钩子，当前无操作。 */
  @Override
  public void rollbackCreateTable(org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    // do nothing
  }

  /**
   * HMS 建表提交钩子。
   *
   * <p>逻辑：若 preCreateTable 阶段未发现已有 Iceberg 表（icebergTable == null），则调用 {@link Catalogs#createTable}
   * 真正建表；HiveCatalog 模式下还会设置 {@link TableProperties#ENGINE_HIVE_ENABLED}。
   *
   * @param hmsTable HMS 表对象
   */
  @Override
  public void commitCreateTable(org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    if (icebergTable == null) {
      if (Catalogs.hiveCatalog(conf, catalogProperties)) {
        catalogProperties.put(TableProperties.ENGINE_HIVE_ENABLED, true);
      }

      Catalogs.createTable(conf, catalogProperties);
    }
  }

  /**
   * HMS 删表前钩子。
   *
   * <p>逻辑：解析 purge 标志；若需要 purge 且为 HiveCatalog，则提前加载 Iceberg 表的 FileIO 与
   * TableMetadata，便于删表后清理数据文件。加载失败仅记错误日志，不阻断删表。
   *
   * @param hmsTable HMS 表对象
   */
  @Override
  public void preDropTable(org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    this.catalogProperties = getCatalogProperties(hmsTable);
    this.deleteIcebergTable =
        hmsTable.getParameters() != null
            && "TRUE"
                .equalsIgnoreCase(
                    hmsTable.getParameters().get(InputFormatConfig.EXTERNAL_TABLE_PURGE));

    if (deleteIcebergTable && Catalogs.hiveCatalog(conf, catalogProperties)) {
      // Store the metadata and the io for deleting the actual table data
      try {
        String metadataLocation =
            hmsTable.getParameters().get(BaseMetastoreTableOperations.METADATA_LOCATION_PROP);
        this.deleteIo = Catalogs.loadTable(conf, catalogProperties).io();
        this.deleteMetadata = TableMetadataParser.read(deleteIo, metadataLocation);
      } catch (Exception e) {
        LOG.error(
            "preDropTable: Error during loading Iceberg table or parsing its metadata for HMS table: {}.{}. "
                + "In some cases, this might lead to undeleted metadata files under the table directory: {}. "
                + "Please double check and, if needed, manually delete any dangling files/folders, if any. "
                + "In spite of this error, the HMS table drop operation should proceed as normal.",
            hmsTable.getDbName(),
            hmsTable.getTableName(),
            hmsTable.getSd().getLocation(),
            e);
      }
    }
  }

  /** HMS 删表回滚钩子，当前无操作。 */
  @Override
  public void rollbackDropTable(org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    // do nothing
  }

  /**
   * HMS 删表提交钩子。
   *
   * <p>逻辑：若 deleteData 且 deleteIcebergTable 为真：
   *
   * <ul>
   *   <li>非 HiveCatalog：调 {@link Catalogs#dropTable} 删表及其数据。
   *   <li>HiveCatalog：若 metadata 目录仍存在，调 {@link CatalogUtil#dropTableData} 清理数据。
   * </ul>
   *
   * <p>异常被 catch 后仅告警，确保 HMS DROP TABLE 命令成功完成。
   *
   * @param hmsTable HMS 表对象
   * @param deleteData HMS 侧是否要求删数据
   */
  @Override
  public void commitDropTable(
      org.apache.hadoop.hive.metastore.api.Table hmsTable, boolean deleteData) {
    if (deleteData && deleteIcebergTable) {
      try {
        if (!Catalogs.hiveCatalog(conf, catalogProperties)) {
          LOG.info(
              "Dropping with purge all the data for table {}.{}",
              hmsTable.getDbName(),
              hmsTable.getTableName());
          Catalogs.dropTable(conf, catalogProperties);
        } else {
          // do nothing if metadata folder has been deleted already (Hive 4 behaviour for
          // purge=TRUE)
          if (deleteMetadata != null && deleteIo.newInputFile(deleteMetadata.location()).exists()) {
            CatalogUtil.dropTableData(deleteIo, deleteMetadata);
          }
        }
      } catch (Exception e) {
        // we want to successfully complete the Hive DROP TABLE command despite catalog-related
        // exceptions here
        // e.g. we wish to successfully delete a Hive table even if the underlying Hadoop table has
        // already been deleted
        LOG.warn(
            "Exception during commitDropTable operation for table {}.{}.",
            hmsTable.getDbName(),
            hmsTable.getTableName(),
            e);
      }
    }
  }

  /**
   * 计算交给 catalog 使用的属性集合。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>以 HMS 表参数为基础，按 {@link HiveTableOperations#translateToIcebergProp} 把部分 HMS 键名翻译为 Iceberg
   *       键名。
   *   <li>补充 {@link Catalogs#LOCATION}（取自 StorageDescriptor）与 {@link Catalogs#NAME} （由 db.table 组成
   *       TableIdentifier）。
   *   <li>移除不应下推到 Iceberg 的 HMS 参数（metadata_location、previous_metadata_location、 partition_spec 等）。
   * </ul>
   *
   * @param hmsTable HMS 表对象
   * @return 整理后的 catalog 属性
   */
  private static Properties getCatalogProperties(
      org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    Properties properties = new Properties();

    hmsTable
        .getParameters()
        .forEach(
            (key, value) -> {
              // translate key names between HMS and Iceberg where needed
              String icebergKey = HiveTableOperations.translateToIcebergProp(key);
              properties.put(icebergKey, value);
            });

    if (properties.get(Catalogs.LOCATION) == null
        && hmsTable.getSd() != null
        && hmsTable.getSd().getLocation() != null) {
      properties.put(Catalogs.LOCATION, hmsTable.getSd().getLocation());
    }

    if (properties.get(Catalogs.NAME) == null) {
      properties.put(
          Catalogs.NAME,
          TableIdentifier.of(hmsTable.getDbName(), hmsTable.getTableName()).toString());
    }

    // Remove HMS table parameters we don't want to propagate to Iceberg
    PROPERTIES_TO_REMOVE.forEach(properties::remove);

    return properties;
  }

  /**
   * 计算建表用的 Iceberg schema。
   *
   * <p>逻辑：用户显式提供的 {@link InputFormatConfig#TABLE_SCHEMA} 优先；否则用 {@link HiveSchemaUtil#convert} 从
   * HMS 列转换（含分区键时合并分区键到列列表）。 autoConversion 控制是否做类型自动转换。
   *
   * @param properties catalog 属性
   * @param hmsTable HMS 表对象
   * @return Iceberg schema
   */
  private Schema schema(
      Properties properties, org.apache.hadoop.hive.metastore.api.Table hmsTable) {
    boolean autoConversion = conf.getBoolean(InputFormatConfig.SCHEMA_AUTO_CONVERSION, false);

    if (properties.getProperty(InputFormatConfig.TABLE_SCHEMA) != null) {
      return SchemaParser.fromJson(properties.getProperty(InputFormatConfig.TABLE_SCHEMA));
    } else if (hmsTable.isSetPartitionKeys() && !hmsTable.getPartitionKeys().isEmpty()) {
      // Add partitioning columns to the original column list before creating the Iceberg Schema
      List<FieldSchema> cols = Lists.newArrayList(hmsTable.getSd().getCols());
      cols.addAll(hmsTable.getPartitionKeys());
      return HiveSchemaUtil.convert(cols, autoConversion);
    } else {
      return HiveSchemaUtil.convert(hmsTable.getSd().getCols(), autoConversion);
    }
  }

  /**
   * 计算建表用的分区规格。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>用户显式提供 {@link InputFormatConfig#PARTITION_SPEC} 时优先使用，且不允许同时设置 Hive 分区键。
   *   <li>否则若 HMS 设置了分区键，则生成 identity 分区规格。
   *   <li>否则返回非分区表。
   * </ul>
   *
   * @param schema Iceberg schema
   * @param properties catalog 属性
   * @param hmsTable HMS 表对象
   * @return 分区规格
   */
  private static PartitionSpec spec(
      Schema schema, Properties properties, org.apache.hadoop.hive.metastore.api.Table hmsTable) {

    if (hmsTable.getParameters().get(InputFormatConfig.PARTITION_SPEC) != null) {
      Preconditions.checkArgument(
          !hmsTable.isSetPartitionKeys() || hmsTable.getPartitionKeys().isEmpty(),
          "Provide only one of the following: Hive partition specification, or the "
              + InputFormatConfig.PARTITION_SPEC
              + " property");
      return PartitionSpecParser.fromJson(
          schema, hmsTable.getParameters().get(InputFormatConfig.PARTITION_SPEC));
    } else if (hmsTable.isSetPartitionKeys() && !hmsTable.getPartitionKeys().isEmpty()) {
      // If the table is partitioned then generate the identity partition definitions for the
      // Iceberg table
      return HiveSchemaUtil.spec(schema, hmsTable.getPartitionKeys());
    } else {
      return PartitionSpec.unpartitioned();
    }
  }
}
