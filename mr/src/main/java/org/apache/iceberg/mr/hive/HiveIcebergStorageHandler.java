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

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.HiveMetaHook;
import org.apache.hadoop.hive.ql.metadata.HiveStorageHandler;
import org.apache.hadoop.hive.ql.metadata.HiveStoragePredicateHandler;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.plan.TableDesc;
import org.apache.hadoop.hive.ql.security.authorization.HiveAuthorizationProvider;
import org.apache.hadoop.hive.serde2.AbstractSerDe;
import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Table;
import org.apache.iceberg.hadoop.HadoopConfigurable;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.SerializationUtil;

/**
 * 文件级说明：Iceberg 的 Hive StorageHandler，是 Hive 与 Iceberg 集成的总入口。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，作为 Hive 识别 Iceberg 表 的核心适配器，串联
 * InputFormat/OutputFormat/SerDe/MetaHook/谓词下推）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>声明 Hive 使用的 InputFormat/OutputFormat/SerDe/MetaHook 类。
 *   <li>在 configureInputJobProperties / configureOutputJobProperties 阶段，把表信息（schema、 location、序列化的
 *       Table 对象、catalog 名）写入 job 配置，分发到 executor。
 *   <li>实现 {@link HiveStoragePredicateHandler}，把 Hive 谓词同时作为 pushed/residual 返回， 让 Hive 与 Iceberg
 *       各自做裁剪。
 *   <li>提供静态方法供 executor 侧反序列化 Table、读取输出表列表与 catalog 名。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>表对象通过 {@link SerializableTable#copyOf} 转为可序列化形式，再 base64 写入配置， 避免每个 executor 重新访问 catalog。
 *   <li>可选禁用 FileIO 配置序列化以减小 job.xml 体积，但需要 executor 侧调用 {@link #checkAndSetIoConfig} 重新注入
 *       Configuration。
 *   <li>多表 insert 时用 ".." 分隔表名，写入 {@link InputFormatConfig#OUTPUT_TABLES}。
 *   <li>WRITE_KEY 同时写入 map 与 tableDesc 属性，确保 SerDe 能在表级别判断是写路径 （map 中的属性会出现在所有表的 serde 配置中，无法区分）。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive 编译器与执行引擎调用；下游依赖 {@link Catalogs}、 {@link SerializableTable}、{@link
 * HiveIcebergInputFormat} 等具体实现类。
 */
public class HiveIcebergStorageHandler implements HiveStoragePredicateHandler, HiveStorageHandler {
  private static final Splitter TABLE_NAME_SPLITTER = Splitter.on("..");
  private static final String TABLE_NAME_SEPARATOR = "..";

  /** 标识当前为写路径的 key；同时写入 map 与 tableDesc 属性，便于 SerDe 在表级别识别。 */
  static final String WRITE_KEY = "HiveIcebergStorageHandler_write";

  private Configuration conf;

  /** 返回 Hive 读取 Iceberg 表使用的 InputFormat 类。 */
  @Override
  public Class<? extends InputFormat> getInputFormatClass() {
    return HiveIcebergInputFormat.class;
  }

  /** 返回 Hive 写入 Iceberg 表使用的 OutputFormat 类。 */
  @Override
  public Class<? extends OutputFormat> getOutputFormatClass() {
    return HiveIcebergOutputFormat.class;
  }

  /** 返回 Hive 使用的 SerDe 类。 */
  @Override
  public Class<? extends AbstractSerDe> getSerDeClass() {
    return HiveIcebergSerDe.class;
  }

  /** 返回 Iceberg 表的元数据钩子实例。 */
  @Override
  public HiveMetaHook getMetaHook() {
    return new HiveIcebergMetaHook(conf);
  }

  /** 返回授权提供者，当前返回 null（不提供）。 */
  @Override
  public HiveAuthorizationProvider getAuthorizationProvider() {
    return null;
  }

  /**
   * 配置读路径的 job 属性：序列化表信息到 map。
   *
   * <p>委托给 {@link #overlayTableProperties}。
   *
   * @param tableDesc 表描述
   * @param map 待填充的 job 属性 map
   */
  @Override
  public void configureInputJobProperties(TableDesc tableDesc, Map<String, String> map) {
    overlayTableProperties(conf, tableDesc, map);
  }

  /**
   * 配置写路径的 job 属性：序列化表信息 + 设置 OutputCommitter + 标记写路径。
   *
   * <p>逻辑：先调 {@link #overlayTableProperties} 注入表信息；再设置 {@code mapred.output.committer.class} 为
   * {@link HiveIcebergOutputCommitter}（Tez 下足够）； 写入 WRITE_KEY="true" 到 map 与 tableDesc 属性，便于 SerDe
   * 与 configureJobConf 识别写路径。
   *
   * @param tableDesc 表描述
   * @param map 待填充的 job 属性 map
   */
  @Override
  public void configureOutputJobProperties(TableDesc tableDesc, Map<String, String> map) {
    overlayTableProperties(conf, tableDesc, map);
    // For Tez, setting the committer here is enough to make sure it'll be part of the jobConf
    map.put("mapred.output.committer.class", HiveIcebergOutputCommitter.class.getName());
    // For MR, the jobConf is set only in configureJobConf, so we're setting the write key here to
    // detect it over there
    map.put(WRITE_KEY, "true");
    // Putting the key into the table props as well, so that projection pushdown can be determined
    // on a
    // table-level and skipped only for output tables in HiveIcebergSerde. Properties from the map
    // will be present in
    // the serde config for all tables in the query, not just the output tables, so we can't rely on
    // that in the serde.
    tableDesc.getProperties().put(WRITE_KEY, "true");
  }

  /** 配置表级 job 属性，当前为空操作。 */
  @Override
  public void configureTableJobProperties(TableDesc tableDesc, Map<String, String> map) {}

  // Override annotation commented out, since this interface method has been introduced only in Hive
  // 3
  // @Override
  /** 配置输入 job 凭据（Hive 3+ 接口），当前为空操作。 */
  public void configureInputJobCredentials(TableDesc tableDesc, Map<String, String> secrets) {}

  /**
   * 在 MR 模式下补充 jobConf：注册输出表与 committer。
   *
   * <p>逻辑：若 tableDesc 标记了 WRITE_KEY，则校验表名不含分隔符 ".."，把表名追加到 {@link
   * InputFormatConfig#OUTPUT_TABLES}（用 ".." 分隔），设置 committer 类， 并把 catalogName 写入 {@link
   * InputFormatConfig#TABLE_CATALOG_PREFIX}+表名。
   *
   * @param tableDesc 表描述
   * @param jobConf JobConf
   */
  @Override
  public void configureJobConf(TableDesc tableDesc, JobConf jobConf) {
    if (tableDesc != null
        && tableDesc.getProperties() != null
        && tableDesc.getProperties().get(WRITE_KEY) != null) {
      String tableName = tableDesc.getTableName();
      Preconditions.checkArgument(
          !tableName.contains(TABLE_NAME_SEPARATOR),
          "Can not handle table "
              + tableName
              + ". Its name contains '"
              + TABLE_NAME_SEPARATOR
              + "'");
      String tables = jobConf.get(InputFormatConfig.OUTPUT_TABLES);
      tables = tables == null ? tableName : tables + TABLE_NAME_SEPARATOR + tableName;
      jobConf.set("mapred.output.committer.class", HiveIcebergOutputCommitter.class.getName());
      jobConf.set(InputFormatConfig.OUTPUT_TABLES, tables);

      String catalogName = tableDesc.getProperties().getProperty(InputFormatConfig.CATALOG_NAME);
      if (catalogName != null) {
        jobConf.set(InputFormatConfig.TABLE_CATALOG_PREFIX + tableName, catalogName);
      }
    }
  }

  /** 返回当前 Hadoop 配置。 */
  @Override
  public Configuration getConf() {
    return conf;
  }

  /** 设置 Hadoop 配置。 */
  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  /** 返回类名作为字符串表示。 */
  @Override
  public String toString() {
    return this.getClass().getName();
  }

  /**
   * 分解 Hive 谓词为 pushed 与 residual 两部分。
   *
   * <p>设计要点：当前把整个谓词同时作为 pushed 与 residual 返回，即 Hive 与 Iceberg 都做裁剪， 取两者的并集效果。Iceberg 侧的实际过滤转换由
   * {@link HiveIcebergFilterFactory} 完成。
   *
   * @param jobConf job 配置
   * @param deserializer SerDe（不使用）
   * @param exprNodeDesc Hive 提取的过滤表达式
   * @return 包含 pushed/residual 谓词的 DecomposedPredicate
   */
  @Override
  public DecomposedPredicate decomposePredicate(
      JobConf jobConf, Deserializer deserializer, ExprNodeDesc exprNodeDesc) {
    DecomposedPredicate predicate = new DecomposedPredicate();
    predicate.residualPredicate = (ExprNodeGenericFuncDesc) exprNodeDesc;
    predicate.pushedPredicate = (ExprNodeGenericFuncDesc) exprNodeDesc;
    return predicate;
  }

  /**
   * 从配置中反序列化获取指定名称的 Iceberg 表。
   *
   * <p>逻辑：从 {@link InputFormatConfig#SERIALIZED_TABLE_PREFIX}+name 读取 base64 字符串， 反序列化为
   * Table；若开启配置禁用序列化，则用输入 config 重新注入 FileIO 配置。
   *
   * @param config Hadoop 配置
   * @param name 表名（{@code TableDesc.getTableName()} 返回值）
   * @return 反序列化的 Iceberg 表
   */
  public static Table table(Configuration config, String name) {
    Table table =
        SerializationUtil.deserializeFromBase64(
            config.get(InputFormatConfig.SERIALIZED_TABLE_PREFIX + name));
    checkAndSetIoConfig(config, table);
    return table;
  }

  /**
   * 若启用配置禁用序列化，则把输入 config 注入到表的 FileIO（仅当 FileIO 实现 {@link HadoopConfigurable}）。
   *
   * <p>设计要点：表对象可能在不带 FileIO 配置的情况下被序列化，反序列化后需要重新注入才能 正常访问文件系统。
   *
   * @param config 待注入的 Hadoop 配置
   * @param table Iceberg 表对象
   */
  public static void checkAndSetIoConfig(Configuration config, Table table) {
    if (table != null
        && config.getBoolean(
            InputFormatConfig.CONFIG_SERIALIZATION_DISABLED,
            InputFormatConfig.CONFIG_SERIALIZATION_DISABLED_DEFAULT)
        && table.io() instanceof HadoopConfigurable) {
      ((HadoopConfigurable) table.io()).setConf(config);
    }
  }

  /**
   * 若启用配置禁用序列化，则让 FileIO 在序列化时不写出 Hadoop 配置，以减小序列化体积。
   *
   * <p>注意：跳过 FileIO 配置序列化后，反序列化侧需要调用 {@link #checkAndSetIoConfig(Configuration, Table)} 重新注入配置才能使用
   * FileIO。
   *
   * @param config 临时使用的 Hadoop 配置
   * @param table Iceberg 表对象
   */
  public static void checkAndSkipIoConfigSerialization(Configuration config, Table table) {
    if (table != null
        && config.getBoolean(
            InputFormatConfig.CONFIG_SERIALIZATION_DISABLED,
            InputFormatConfig.CONFIG_SERIALIZATION_DISABLED_DEFAULT)
        && table.io() instanceof HadoopConfigurable) {
      ((HadoopConfigurable) table.io())
          .serializeConfWith(conf -> new NonSerializingConfig(config)::get);
    }
  }

  /**
   * 返回配置中的输出表名列表（用 ".." 分隔）。
   *
   * @param config Hadoop 配置
   * @return 表名集合
   */
  public static Collection<String> outputTables(Configuration config) {
    return TABLE_NAME_SPLITTER.splitToList(config.get(InputFormatConfig.OUTPUT_TABLES));
  }

  /**
   * 返回序列化到配置中的 catalog 名称。
   *
   * @param config Hadoop 配置
   * @param name 表名
   * @return catalog 名称，未设置时为 null
   */
  public static String catalogName(Configuration config, String name) {
    return config.get(InputFormatConfig.TABLE_CATALOG_PREFIX + name);
  }

  /**
   * 从配置中读取并解析表 schema（JSON）。
   *
   * @param config Hadoop 配置
   * @return Iceberg schema
   */
  public static Schema schema(Configuration config) {
    return SchemaParser.fromJson(config.get(InputFormatConfig.TABLE_SCHEMA));
  }

  /**
   * 把表信息序列化叠加到 job 属性 map，供 executor 反序列化使用。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 catalog 加载 Table，序列化 schema 为 JSON。
   *   <li>把 tableDesc 属性中、且未被 map 覆盖的键复制到 map（map 优先级更高）。
   *   <li>写入表标识符、location、schema JSON。
   *   <li>用 {@link SerializableTable#copyOf} 转为可序列化 Table，按需跳过 FileIO 配置序列化， base64 写入
   *       SERIALIZED_TABLE_PREFIX+表名。
   *   <li>移除 columns.comments（含 \0 分隔符会导致 job.xml 序列化失败）。
   *   <li>把 schema JSON 也写入 tableDesc 属性，避免 SerDe 初始化时反复访问 HMS。
   * </ol>
   *
   * @param configuration Hadoop 配置（含 catalog 信息）
   * @param tableDesc 表描述
   * @param map 待填充的 job 属性 map
   */
  @VisibleForTesting
  static void overlayTableProperties(
      Configuration configuration, TableDesc tableDesc, Map<String, String> map) {
    Properties props = tableDesc.getProperties();
    Table table = Catalogs.loadTable(configuration, props);
    String schemaJson = SchemaParser.toJson(table.schema());

    Maps.fromProperties(props).entrySet().stream()
        .filter(entry -> !map.containsKey(entry.getKey())) // map overrides tableDesc properties
        .forEach(entry -> map.put(entry.getKey(), entry.getValue()));

    map.put(InputFormatConfig.TABLE_IDENTIFIER, props.getProperty(Catalogs.NAME));
    map.put(InputFormatConfig.TABLE_LOCATION, table.location());
    map.put(InputFormatConfig.TABLE_SCHEMA, schemaJson);

    // serialize table object into config
    Table serializableTable = SerializableTable.copyOf(table);
    checkAndSkipIoConfigSerialization(configuration, serializableTable);
    map.put(
        InputFormatConfig.SERIALIZED_TABLE_PREFIX + tableDesc.getTableName(),
        SerializationUtil.serializeToBase64(serializableTable));

    // We need to remove this otherwise the job.xml will be invalid as column comments are separated
    // with '\0' and
    // the serialization utils fail to serialize this character
    map.remove("columns.comments");

    // save schema into table props as well to avoid repeatedly hitting the HMS during serde
    // initializations
    // this is an exception to the interface documentation, but it's a safe operation to add this
    // property
    props.put(InputFormatConfig.TABLE_SCHEMA, schemaJson);
  }

  /**
   * 不序列化 Hadoop 配置的包装器，配合 {@link HadoopConfigurable#serializeConfWith} 使用。
   *
   * <p>设计要点：把 Configuration 字段标记为 transient，使其在 Java 序列化时不被写出； 反序列化后调用 {@link #get()} 会因 conf 为
   * null 抛出异常，提示需要手动注入配置。
   */
  private static class NonSerializingConfig implements Serializable {

    private final transient Configuration conf;

    NonSerializingConfig(Configuration conf) {
      this.conf = conf;
    }

    /**
     * 返回包装的 Configuration。
     *
     * @return Configuration
     * @throws IllegalStateException 若 conf 已被 transient 跳过且未手动注入
     */
    public Configuration get() {
      if (conf == null) {
        throw new IllegalStateException(
            "Configuration was not serialized on purpose but was not set manually either");
      }

      return conf;
    }
  }
}
