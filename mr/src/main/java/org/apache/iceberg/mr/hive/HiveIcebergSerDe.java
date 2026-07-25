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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.hadoop.hive.serde2.AbstractSerDe;
import org.apache.hadoop.hive.serde2.ColumnProjectionUtils;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.SerDeStats;
import org.apache.hadoop.hive.serde2.SerDeUtils;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.hadoop.io.Writable;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.hive.HiveSchemaUtil;
import org.apache.iceberg.mr.Catalogs;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.mr.hive.serde.objectinspector.IcebergObjectInspector;
import org.apache.iceberg.mr.mapred.Container;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg 的 Hive SerDe（序列化/反序列化器）实现。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，是 Hive 读写 Iceberg 表 的字段映射枢纽）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>初始化阶段：解析表 schema（优先用户指定，其次从 catalog 加载，最后回退到 Hive DDL schema）， 并按列裁剪生成投影 schema。
 *   <li>创建 Iceberg 侧 {@link ObjectInspector}，供 Hive 解析读取结果。
 *   <li>写入路径：把 Hive 行对象通过 {@link Deserializer} 转为 Iceberg {@link Record} 并包装为 {@link Container}。
 *   <li>读取路径：从 {@link Container} 中取出 Iceberg Record 交回 Hive。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>initialize 会被 Hive 在多处调用（DDL、编译期、执行期），代码中通过 serDeProperties 内容 区分场景：WRITE_KEY
 *       存在时不做投影下推，读取时按列裁剪。
 *   <li>同表多次 join 会导致列名重复，需先 distinct 再投影，避免位置错乱。
 *   <li>deserializers 按 ObjectInspector 缓存，避免重复构建 schema 访问器树。
 *   <li>schema 加载失败时回退到 Hive 提供的列定义，并通过 autoConversion 做类型兼容转换。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive 执行引擎与 StorageHandler 调用；下游依赖 {@link Catalogs}、 {@link
 * IcebergObjectInspector}、{@link Deserializer}、{@link HiveSchemaUtil}。
 */
public class HiveIcebergSerDe extends AbstractSerDe {
  private static final Logger LOG = LoggerFactory.getLogger(HiveIcebergSerDe.class);
  private static final String LIST_COLUMN_COMMENT = "columns.comments";

  private ObjectInspector inspector;
  private Schema tableSchema;
  private Map<ObjectInspector, Deserializer> deserializers = Maps.newHashMapWithExpectedSize(1);
  private Container<Record> row = new Container<>();

  /**
   * 初始化 SerDe：解析 schema、计算投影、创建 ObjectInspector。
   *
   * <p>Hive 会在多处调用本方法：
   *
   * <ul>
   *   <li>建表时：serDeProperties 中是 HiveDDL 数据，Iceberg 表尚未创建。
   *   <li>编译期（HiveServer2）：只有表 location/name，需读表数据获取 schema；可能多次调用。
   *   <li>执行期：serDeProperties 由 StorageHandler.configureInputJobProperties 填充并序列化 分发到
   *       executor，无需在每个 executor 上重复加载表。
   * </ul>
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>优先使用 serDeProperties 中的 {@link InputFormatConfig#TABLE_SCHEMA}；否则尝试 {@link
   *       Catalogs#loadTable} 加载表 schema；都失败则回退到 Hive schema。
   *   <li>写路径（WRITE_KEY 存在）：投影即全表 schema；读路径：按列裁剪做投影， 且去重后再投影，投影失败则回退全表 schema。
   *   <li>用 {@link IcebergObjectInspector#create} 创建 ObjectInspector。
   * </ol>
   *
   * @param configuration Hadoop 配置
   * @param serDeProperties SerDe 属性
   * @throws SerDeException schema 解析或 inspector 创建失败时抛出
   */
  @Override
  public void initialize(@Nullable Configuration configuration, Properties serDeProperties)
      throws SerDeException {
    // HiveIcebergSerDe.initialize is called multiple places in Hive code:
    // - When we are trying to create a table - HiveDDL data is stored at the serDeProperties, but
    // no Iceberg table
    // is created yet.
    // - When we are compiling the Hive query on HiveServer2 side - We only have table information
    // (location/name),
    // and we have to read the schema using the table data. This is called multiple times so there
    // is room for
    // optimizing here.
    // - When we are executing the Hive query in the execution engine - We do not want to load the
    // table data on every
    // executor, but serDeProperties are populated by
    // HiveIcebergStorageHandler.configureInputJobProperties() and
    // the resulting properties are serialized and distributed to the executors

    if (serDeProperties.get(InputFormatConfig.TABLE_SCHEMA) != null) {
      this.tableSchema =
          SchemaParser.fromJson((String) serDeProperties.get(InputFormatConfig.TABLE_SCHEMA));
    } else {
      try {
        // always prefer the original table schema if there is one
        this.tableSchema = Catalogs.loadTable(configuration, serDeProperties).schema();
        LOG.info("Using schema from existing table {}", SchemaParser.toJson(tableSchema));
      } catch (Exception e) {
        boolean autoConversion =
            configuration.getBoolean(InputFormatConfig.SCHEMA_AUTO_CONVERSION, false);
        // If we can not load the table try the provided hive schema
        this.tableSchema = hiveSchemaOrThrow(serDeProperties, e, autoConversion);
      }
    }

    Schema projectedSchema;
    if (serDeProperties.get(HiveIcebergStorageHandler.WRITE_KEY) != null) {
      // when writing out data, we should not do projection pushdown
      projectedSchema = tableSchema;
    } else {
      configuration.setBoolean(InputFormatConfig.CASE_SENSITIVE, false);
      String[] selectedColumns = ColumnProjectionUtils.getReadColumnNames(configuration);
      // When same table is joined multiple times, it is possible some selected columns are
      // duplicated,
      // in this case wrong recordStructField position leads wrong value or
      // ArrayIndexOutOfBoundException
      String[] distinctSelectedColumns =
          Arrays.stream(selectedColumns).distinct().toArray(String[]::new);
      projectedSchema =
          distinctSelectedColumns.length > 0
              ? tableSchema.caseInsensitiveSelect(distinctSelectedColumns)
              : tableSchema;
      // the input split mapper handles does not belong to this table
      // it is necessary to ensure projectedSchema equals to tableSchema,
      // or we cannot find selectOperator's column from inspector
      if (projectedSchema.columns().size() != distinctSelectedColumns.length) {
        projectedSchema = tableSchema;
      }
    }

    try {
      this.inspector = IcebergObjectInspector.create(projectedSchema);
    } catch (Exception e) {
      throw new SerDeException(e);
    }
  }

  /** 返回序列化产物类型，固定为 {@link Container}。 */
  @Override
  public Class<? extends Writable> getSerializedClass() {
    return Container.class;
  }

  /**
   * 把 Hive 行对象序列化为 {@link Container}（内含 Iceberg Record）。
   *
   * <p>逻辑：按 objectInspector 缓存 {@link Deserializer}，命中则复用；否则用 Builder 构建新 deserializer 并缓存。最终调用
   * {@link Deserializer#deserialize(Object)} 转换并写入复用的 row 容器。
   *
   * @param o Hive 行对象
   * @param objectInspector 行对象对应的 ObjectInspector
   * @return 包含 Iceberg Record 的 Container
   */
  @Override
  public Writable serialize(Object o, ObjectInspector objectInspector) {
    Deserializer deserializer = deserializers.get(objectInspector);
    if (deserializer == null) {
      deserializer =
          new Deserializer.Builder()
              .schema(tableSchema)
              .sourceInspector((StructObjectInspector) objectInspector)
              .writerInspector((StructObjectInspector) inspector)
              .build();
      deserializers.put(objectInspector, deserializer);
    }

    row.set(deserializer.deserialize(o));
    return row;
  }

  /** 返回 SerDe 统计信息，当前不实现，返回 null。 */
  @Override
  public SerDeStats getSerDeStats() {
    return null;
  }

  /**
   * 反序列化读取路径：从 {@link Container} 中取出 Iceberg Record 交回 Hive。
   *
   * @param writable Container 包装
   * @return 内部 Iceberg Record
   */
  @Override
  public Object deserialize(Writable writable) {
    return ((Container<?>) writable).get();
  }

  /** 返回 Iceberg 侧 ObjectInspector。 */
  @Override
  public ObjectInspector getObjectInspector() {
    return inspector;
  }

  /**
   * 从 serDeProperties 中解析 Hive schema；若不存在则抛出 SerDeException 并把先前异常作为 cause。
   *
   * <p>逻辑：读取 LIST_COLUMNS / LIST_COLUMN_TYPES / columns.comments / COLUMN_NAME_DELIMITER， 用 {@link
   * HiveSchemaUtil#convert} 转换为 Iceberg schema。autoConversion 控制是否把不支持 的类型转换为更宽松的类型（如 tinyint ->
   * int）。
   *
   * @param serDeProperties Hive schema 来源
   * @param previousException 之前加载表时的异常，作为新异常的 cause
   * @param autoConversion true 时做类型自动转换
   * @return 解析得到的 Hive schema
   * @throws SerDeException serDeProperties 中无有效 schema 时抛出
   */
  private static Schema hiveSchemaOrThrow(
      Properties serDeProperties, Exception previousException, boolean autoConversion)
      throws SerDeException {
    // Read the configuration parameters
    String columnNames = serDeProperties.getProperty(serdeConstants.LIST_COLUMNS);
    String columnTypes = serDeProperties.getProperty(serdeConstants.LIST_COLUMN_TYPES);
    // No constant for column comments and column comments delimiter.
    String columnComments = serDeProperties.getProperty(LIST_COLUMN_COMMENT);
    String columnNameDelimiter =
        serDeProperties.containsKey(serdeConstants.COLUMN_NAME_DELIMITER)
            ? serDeProperties.getProperty(serdeConstants.COLUMN_NAME_DELIMITER)
            : String.valueOf(SerDeUtils.COMMA);
    if (columnNames != null
        && columnTypes != null
        && columnNameDelimiter != null
        && !columnNames.isEmpty()
        && !columnTypes.isEmpty()
        && !columnNameDelimiter.isEmpty()) {
      // Parse the configuration parameters
      List<String> names = Lists.newArrayList();
      Collections.addAll(names, columnNames.split(columnNameDelimiter));
      List<String> comments = Lists.newArrayList();
      if (columnComments != null) {
        Collections.addAll(comments, columnComments.split(Character.toString(Character.MIN_VALUE)));
      }
      Schema hiveSchema =
          HiveSchemaUtil.convert(
              names,
              TypeInfoUtils.getTypeInfosFromTypeString(columnTypes),
              comments,
              autoConversion);
      LOG.info("Using hive schema {}", SchemaParser.toJson(hiveSchema));
      return hiveSchema;
    } else {
      throw new SerDeException(
          "Please provide an existing table or a valid schema", previousException);
    }
  }
}
