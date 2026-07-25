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
package org.apache.iceberg.hive;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * Hive Schema 与 Iceberg Schema 之间的双向转换工具类。
 *
 * <p>所属模块：iceberg-hive-metastore（Schema 映射层，对外公共入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg {@link Schema} 转换为 Hive {@link FieldSchema} 列表（用于注册 HMS 表）。
 *   <li>将 Hive {@link FieldSchema} 列表或列名/类型列表转换为 Iceberg {@link Schema}。
 *   <li>将 Hive 分区列转换为 Iceberg identity 分区规格 {@link PartitionSpec}。
 *   <li>提供 Iceberg {@link Type} 与 Hive {@link TypeInfo} 之间的单类型转换。
 * </ul>
 *
 * <p>设计意图：作为 Schema 转换的统一对外入口，内部委托 {@link HiveSchemaConverter} 完成实际转换。 工具类不可实例化（private 构造器 +
 * final）。提供多个重载以适配不同输入形式（FieldSchema 列表、 原始名/类型列表等）和 autoConvert 开关。
 *
 * <p>上下游关系：被 {@link HiveTableOperations}、{@link HiveCatalog} 在读写 HMS 表结构时调用； 内部委托 {@link
 * HiveSchemaConverter}。
 */
public final class HiveSchemaUtil {

  private HiveSchemaUtil() {}

  /**
   * 将 Iceberg {@link Schema} 转换为 Hive 列定义列表（{@link FieldSchema}）。
   *
   * @param schema 原始 Iceberg Schema
   * @return 转换后的 Hive 列列表
   */
  public static List<FieldSchema> convert(Schema schema) {
    return schema.columns().stream()
        .map(col -> new FieldSchema(col.name(), convertToTypeString(col.type()), col.doc()))
        .collect(Collectors.toList());
  }

  /**
   * 将 Hive 列定义列表转换为 Iceberg {@link Schema}（不自动转换不兼容类型）。
   *
   * <p>若遇到 TINYINT/SMALLINT/CHAR/VARCHAR 等无直接对应的类型则抛异常。
   *
   * @param fieldSchemas Hive 列列表
   * @return 等价的 Iceberg Schema
   */
  public static Schema convert(List<FieldSchema> fieldSchemas) {
    return convert(fieldSchemas, false);
  }

  /**
   * 将 Hive 列定义列表转换为 Iceberg {@link Schema}。
   *
   * @param fieldSchemas Hive 列列表
   * @param autoConvert 为 true 时将 TINYINT/SMALLINT 转为 INTEGER、CHAR/VARCHAR 转为 STRING； 为 false
   *     时遇到这些类型抛异常
   * @return 等价的 Iceberg Schema
   */
  public static Schema convert(List<FieldSchema> fieldSchemas, boolean autoConvert) {
    List<String> names = Lists.newArrayListWithExpectedSize(fieldSchemas.size());
    List<TypeInfo> typeInfos = Lists.newArrayListWithExpectedSize(fieldSchemas.size());
    List<String> comments = Lists.newArrayListWithExpectedSize(fieldSchemas.size());

    for (FieldSchema col : fieldSchemas) {
      names.add(col.getName());
      typeInfos.add(TypeInfoUtils.getTypeInfoFromTypeString(col.getType()));
      comments.add(col.getComment());
    }
    return HiveSchemaConverter.convert(names, typeInfos, comments, autoConvert);
  }

  /**
   * 将 Hive 分区列转换为 Iceberg identity 分区规格。
   *
   * <p>逻辑：对每个分区列按列名创建 identity 分区字段。
   *
   * @param schema Iceberg Schema（用于解析分区字段引用）
   * @param fieldSchemas Hive 分区列定义
   * @return Iceberg identity 分区规格
   */
  public static PartitionSpec spec(Schema schema, List<FieldSchema> fieldSchemas) {
    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);
    fieldSchemas.forEach(fieldSchema -> builder.identity(fieldSchema.getName()));
    return builder.build();
  }

  /**
   * 将 Hive 列名/类型/注释列表转换为 Iceberg {@link Schema}（不自动转换不兼容类型）。
   *
   * @param names Hive 列名列表
   * @param types Hive 列类型列表
   * @param comments Hive 列注释列表
   * @return 转换后的 Iceberg Schema
   */
  public static Schema convert(List<String> names, List<TypeInfo> types, List<String> comments) {
    return HiveSchemaConverter.convert(names, types, comments, false);
  }

  /**
   * 将 Hive 列名/类型/注释列表转换为 Iceberg {@link Schema}。
   *
   * @param names Hive 列名列表
   * @param types Hive 列类型列表
   * @param comments Hive 列注释列表（可为 null）
   * @param autoConvert 为 true 时自动转换 TINYINT/SMALLINT→INTEGER、CHAR/VARCHAR→STRING
   * @return 转换后的 Iceberg Schema
   */
  public static Schema convert(
      List<String> names, List<TypeInfo> types, List<String> comments, boolean autoConvert) {
    return HiveSchemaConverter.convert(names, types, comments, autoConvert);
  }

  /**
   * 将 Iceberg {@link Type} 转换为 Hive {@link TypeInfo}。
   *
   * @param type Iceberg 类型
   * @return Hive 类型信息
   */
  public static TypeInfo convert(Type type) {
    return TypeInfoUtils.getTypeInfoFromTypeString(convertToTypeString(type));
  }

  /**
   * 将 Hive {@link TypeInfo} 转换为 Iceberg {@link Type}（不自动转换不兼容类型）。
   *
   * @param typeInfo Hive 类型信息
   * @return Iceberg 类型
   */
  public static Type convert(TypeInfo typeInfo) {
    return HiveSchemaConverter.convert(typeInfo, false);
  }

  /**
   * 将 Iceberg {@link Type} 转换为 Hive 类型字符串。
   *
   * <p>逻辑：按 typeId 分发，映射到 Hive 类型字符串（如 int/bigint/decimal(p,s)/struct<...>/
   * array<...>/map<...>等）；TIMESTAMP 带时区时在 Hive 3+ 映射为 "timestamp with local time
   * zone"；TIME/STRING/UUID 均映射为 string；FIXED/BINARY 映射为 binary。
   *
   * @param type Iceberg 类型
   * @return Hive 类型字符串
   * @throws UnsupportedOperationException 遇到不支持的类型
   */
  private static String convertToTypeString(Type type) {
    switch (type.typeId()) {
      case BOOLEAN:
        return "boolean";
      case INTEGER:
        return "int";
      case LONG:
        return "bigint";
      case FLOAT:
        return "float";
      case DOUBLE:
        return "double";
      case DATE:
        return "date";
      case TIME:
      case STRING:
      case UUID:
        return "string";
      case TIMESTAMP:
        Types.TimestampType timestampType = (Types.TimestampType) type;
        if (HiveVersion.min(HiveVersion.HIVE_3) && timestampType.shouldAdjustToUTC()) {
          return "timestamp with local time zone";
        }
        return "timestamp";
      case FIXED:
      case BINARY:
        return "binary";
      case DECIMAL:
        final Types.DecimalType decimalType = (Types.DecimalType) type;
        return String.format("decimal(%s,%s)", decimalType.precision(), decimalType.scale());
      case STRUCT:
        final Types.StructType structType = type.asStructType();
        final String nameToType =
            structType.fields().stream()
                .map(f -> String.format("%s:%s", f.name(), convert(f.type())))
                .collect(Collectors.joining(","));
        return String.format("struct<%s>", nameToType);
      case LIST:
        final Types.ListType listType = type.asListType();
        return String.format("array<%s>", convert(listType.elementType()));
      case MAP:
        final Types.MapType mapType = type.asMapType();
        return String.format(
            "map<%s,%s>", convert(mapType.keyType()), convert(mapType.valueType()));
      default:
        throw new UnsupportedOperationException(type + " is not supported");
    }
  }
}
