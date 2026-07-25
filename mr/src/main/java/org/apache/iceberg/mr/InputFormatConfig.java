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
package org.apache.iceberg.mr;

import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.util.SerializationUtil;

/**
 * 文件级说明：Iceberg InputFormat/OutputFormat 的配置常量与构建器。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类是整个 mr 模块配置项的集中定义点， 被 Hive 集成类与 mapreduce/mapred
 * InputFormat 共用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 Iceberg MR 集成相关的全部 Hadoop Configuration 键名（表定位、过滤、投影、提交、 catalog 等）。
 *   <li>提供 {@link ConfigBuilder} 链式构造器，封装常用配置组合。
 *   <li>提供 schema/列选择的解析辅助方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>所有配置键集中在一处，便于维护与查找，避免散落各处。
 *   <li>{@link ConfigBuilder} 在构造时设置一组合理默认值（如关闭容器复用、关闭 locality、 大小写敏感），并提供流式 API 配置过滤、投影、snapshot
 *       等。
 *   <li>{@link InMemoryDataModel} 枚举区分 PIG/HIVE/GENERIC 三种内存数据模型，默认 GENERIC。
 * </ul>
 *
 * <p>上下游关系：上游被 {@link org.apache.iceberg.mr.hive.HiveIcebergInputFormat}、 {@link
 * org.apache.iceberg.mr.hive.HiveIcebergStorageHandler}、 {@link
 * org.apache.iceberg.mr.mapreduce.IcebergInputFormat} 等多处引用；下游依赖 iceberg-core 的 {@link
 * SchemaParser}、{@link SerializationUtil}。
 */
public class InputFormatConfig {

  private InputFormatConfig() {}

  // configuration values for Iceberg input formats
  /** 是否复用 Container 对象以减少 GC，默认 false。 */
  public static final String REUSE_CONTAINERS = "iceberg.mr.reuse.containers";
  /** 是否跳过残留过滤（由计算平台而非 Iceberg 应用残留谓词），默认 false。 */
  public static final String SKIP_RESIDUAL_FILTERING = "skip.residual.filtering";
  /** 时间旅行：按时间戳读取对应快照。 */
  public static final String AS_OF_TIMESTAMP = "iceberg.mr.as.of.time";
  /** 序列化后的 Iceberg 过滤表达式（base64）。 */
  public static final String FILTER_EXPRESSION = "iceberg.mr.filter.expression";
  /** 内存数据模型类型（PIG/HIVE/GENERIC）。 */
  public static final String IN_MEMORY_DATA_MODEL = "iceberg.mr.in.memory.data.model";
  /** 读投影 schema（JSON）。 */
  public static final String READ_SCHEMA = "iceberg.mr.read.schema";
  /** 时间旅行：指定 snapshot id。 */
  public static final String SNAPSHOT_ID = "iceberg.mr.snapshot.id";
  /** 切分大小（字节）。 */
  public static final String SPLIT_SIZE = "iceberg.mr.split.size";
  /** 是否自动转换不兼容类型（如 tinyint -> int），默认 false。 */
  public static final String SCHEMA_AUTO_CONVERSION = "iceberg.mr.schema.auto.conversion";
  /** 表标识符字符串。 */
  public static final String TABLE_IDENTIFIER = "iceberg.mr.table.identifier";
  /** 表路径。 */
  public static final String TABLE_LOCATION = "iceberg.mr.table.location";
  /** 表 schema（JSON）。 */
  public static final String TABLE_SCHEMA = "iceberg.mr.table.schema";
  /** 分区规格（JSON）。 */
  public static final String PARTITION_SPEC = "iceberg.mr.table.partition.spec";
  /** 序列化表对象的前缀键。 */
  public static final String SERIALIZED_TABLE_PREFIX = "iceberg.mr.serialized.table.";
  /** 表 catalog 配置前缀键。 */
  public static final String TABLE_CATALOG_PREFIX = "iceberg.mr.table.catalog.";
  /** 是否在 split 中包含数据本地性信息，默认 false。 */
  public static final String LOCALITY = "iceberg.mr.locality";

  /** 列裁剪选中的列名数组。 */
  public static final String SELECTED_COLUMNS = "iceberg.mr.selected.columns";
  /** 外部表删除时是否 purge 数据。 */
  public static final String EXTERNAL_TABLE_PURGE = "external.table.purge";

  /** 是否禁用配置序列化，默认 false。 */
  public static final String CONFIG_SERIALIZATION_DISABLED =
      "iceberg.mr.config.serialization.disabled";
  /** {@link #CONFIG_SERIALIZATION_DISABLED} 的默认值。 */
  public static final boolean CONFIG_SERIALIZATION_DISABLED_DEFAULT = false;
  /** 作业输出目标表列表（多表 insert 时使用）。 */
  public static final String OUTPUT_TABLES = "iceberg.mr.output.tables";
  /** 提交阶段处理多表的线程池大小，默认 10。 */
  public static final String COMMIT_TABLE_THREAD_POOL_SIZE =
      "iceberg.mr.commit.table.thread.pool.size";
  /** {@link #COMMIT_TABLE_THREAD_POOL_SIZE} 的默认值。 */
  public static final int COMMIT_TABLE_THREAD_POOL_SIZE_DEFAULT = 10;
  /** 提交阶段读 forCommit 文件的线程池大小，默认 10。 */
  public static final String COMMIT_FILE_THREAD_POOL_SIZE =
      "iceberg.mr.commit.file.thread.pool.size";
  /** {@link #COMMIT_FILE_THREAD_POOL_SIZE} 的默认值。 */
  public static final int COMMIT_FILE_THREAD_POOL_SIZE_DEFAULT = 10;
  /** 写入目标文件大小（覆盖表属性）。 */
  public static final String WRITE_TARGET_FILE_SIZE = "iceberg.mr.write.target.file.size";

  /** schema 是否大小写敏感，默认 true。 */
  public static final String CASE_SENSITIVE = "iceberg.mr.case.sensitive";
  /** {@link #CASE_SENSITIVE} 的默认值。 */
  public static final boolean CASE_SENSITIVE_DEFAULT = true;

  /** catalog 名称。 */
  public static final String CATALOG_NAME = "iceberg.catalog";
  /** HadoopCatalog 简写。 */
  public static final String HADOOP_CATALOG = "hadoop.catalog";
  /** HadoopTables 简写。 */
  public static final String HADOOP_TABLES = "hadoop.tables";
  /** HiveCatalog 简写。 */
  public static final String HIVE_CATALOG = "hive.catalog";
  /** snapshots 元表后缀。 */
  public static final String ICEBERG_SNAPSHOTS_TABLE_SUFFIX = ".snapshots";
  /** snapshots 元表标记。 */
  public static final String SNAPSHOT_TABLE = "iceberg.snapshots.table";
  /** snapshots 元表后缀（Hive 命名风格）。 */
  public static final String SNAPSHOT_TABLE_SUFFIX = "__snapshots";

  /** catalog 配置键前缀：{@code iceberg.catalog.<catalogName>.<property>}。 */
  public static final String CATALOG_CONFIG_PREFIX = "iceberg.catalog.";

  /**
   * 内存数据模型枚举，决定读取时返回的行对象类型。
   *
   * <ul>
   *   <li>{@link #PIG}：返回 Pig Tuple。
   *   <li>{@link #HIVE}：返回 Hive 行（向量化等场景）。
   *   <li>{@link #GENERIC}：默认，返回 Iceberg GenericRecord。
   * </ul>
   */
  public enum InMemoryDataModel {
    PIG,
    HIVE,
    GENERIC // Default data model is of Iceberg Generics
  }

  /**
   * 链式配置构造器，封装常用配置组合并设置合理默认值。
   *
   * <p>构造时默认：SKIP_RESIDUAL_FILTERING=false、CASE_SENSITIVE=true、REUSE_CONTAINERS=false、
   * LOCALITY=false。
   */
  public static class ConfigBuilder {
    private final Configuration conf;

    public ConfigBuilder(Configuration conf) {
      this.conf = conf;
      // defaults
      conf.setBoolean(SKIP_RESIDUAL_FILTERING, false);
      conf.setBoolean(CASE_SENSITIVE, CASE_SENSITIVE_DEFAULT);
      conf.setBoolean(REUSE_CONTAINERS, false);
      conf.setBoolean(LOCALITY, false);
    }

    /** 返回已配置的 Configuration。 */
    public Configuration conf() {
      return conf;
    }

    /** 设置 Iceberg 过滤表达式（base64 序列化）。 */
    public ConfigBuilder filter(Expression expression) {
      conf.set(FILTER_EXPRESSION, SerializationUtil.serializeToBase64(expression));
      return this;
    }

    /** 设置读投影 schema（JSON）。 */
    public ConfigBuilder project(Schema schema) {
      conf.set(READ_SCHEMA, SchemaParser.toJson(schema));
      return this;
    }

    /** 设置表 schema（JSON）。 */
    public ConfigBuilder schema(Schema schema) {
      conf.set(TABLE_SCHEMA, SchemaParser.toJson(schema));
      return this;
    }

    /** 设置列裁剪选中的列名列表。 */
    public ConfigBuilder select(List<String> columns) {
      conf.setStrings(SELECTED_COLUMNS, columns.toArray(new String[0]));
      return this;
    }

    /** 设置列裁剪选中的列名（可变参数）。 */
    public ConfigBuilder select(String... columns) {
      conf.setStrings(SELECTED_COLUMNS, columns);
      return this;
    }

    /** 设置读取的表标识符。 */
    public ConfigBuilder readFrom(TableIdentifier identifier) {
      conf.set(TABLE_IDENTIFIER, identifier.toString());
      return this;
    }

    /** 设置读取的表路径。 */
    public ConfigBuilder readFrom(String location) {
      conf.set(TABLE_LOCATION, location);
      return this;
    }

    /** 是否复用 Container 对象。 */
    public ConfigBuilder reuseContainers(boolean reuse) {
      conf.setBoolean(InputFormatConfig.REUSE_CONTAINERS, reuse);
      return this;
    }

    /** 是否大小写敏感。 */
    public ConfigBuilder caseSensitive(boolean caseSensitive) {
      conf.setBoolean(InputFormatConfig.CASE_SENSITIVE, caseSensitive);
      return this;
    }

    /** 时间旅行：指定 snapshot id。 */
    public ConfigBuilder snapshotId(long snapshotId) {
      conf.setLong(SNAPSHOT_ID, snapshotId);
      return this;
    }

    /** 时间旅行：按时间戳读取。 */
    public ConfigBuilder asOfTime(long asOfTime) {
      conf.setLong(AS_OF_TIMESTAMP, asOfTime);
      return this;
    }

    /** 设置切分大小。 */
    public ConfigBuilder splitSize(long splitSize) {
      conf.setLong(SPLIT_SIZE, splitSize);
      return this;
    }

    /** 启用数据本地性：调用后生成的 split 将携带主机位置信息，便于调度器做本地化调度。 */
    public ConfigBuilder preferLocality() {
      conf.setBoolean(LOCALITY, true);
      return this;
    }

    /** 设置内存数据模型为 Hive 行。 */
    public ConfigBuilder useHiveRows() {
      conf.set(IN_MEMORY_DATA_MODEL, InMemoryDataModel.HIVE.name());
      return this;
    }

    /** 设置内存数据模型为 Pig Tuple。 */
    public ConfigBuilder usePigTuples() {
      conf.set(IN_MEMORY_DATA_MODEL, InMemoryDataModel.PIG.name());
      return this;
    }

    /**
     * 声明计算平台能正确应用残留过滤。
     *
     * <p>计算平台下推过滤到数据源后，若数据源只能部分应用，会返回残留过滤。若平台能正确 应用残留过滤则调用此方法跳过 Iceberg 的残留过滤校验；否则 Iceberg 会在过滤未完全
     * 满足时抛异常。
     */
    public ConfigBuilder skipResidualFiltering() {
      conf.setBoolean(InputFormatConfig.SKIP_RESIDUAL_FILTERING, true);
      return this;
    }
  }

  /** 从 Configuration 读取表 schema（JSON 解析）。 */
  public static Schema tableSchema(Configuration conf) {
    return schema(conf, InputFormatConfig.TABLE_SCHEMA);
  }

  /** 从 Configuration 读取读投影 schema（JSON 解析）。 */
  public static Schema readSchema(Configuration conf) {
    return schema(conf, InputFormatConfig.READ_SCHEMA);
  }

  /**
   * 读取列裁剪选中的列名数组。
   *
   * @return 选中的列名数组，未设置或为空时返回 null
   */
  public static String[] selectedColumns(Configuration conf) {
    String[] readColumns = conf.getStrings(InputFormatConfig.SELECTED_COLUMNS);
    return readColumns != null && readColumns.length > 0 ? readColumns : null;
  }

  /**
   * 生成某 catalog 的某属性对应的 Hadoop 配置键。
   *
   * <p>格式：{@code iceberg.catalog.<catalogName>.<catalogProperty>}。
   *
   * @param catalogName catalog 名称
   * @param catalogProperty catalog 属性名（常用属性见 {@link org.apache.iceberg.CatalogProperties}）
   * @return 完整的 Hadoop 配置键
   */
  public static String catalogPropertyConfigKey(String catalogName, String catalogProperty) {
    return String.format("%s%s.%s", CATALOG_CONFIG_PREFIX, catalogName, catalogProperty);
  }

  /** 内部辅助：从 Configuration 读取指定键的 schema（JSON 解析），不存在返回 null。 */
  private static Schema schema(Configuration conf, String key) {
    String json = conf.get(key);
    return json == null ? null : SchemaParser.fromJson(json);
  }
}
