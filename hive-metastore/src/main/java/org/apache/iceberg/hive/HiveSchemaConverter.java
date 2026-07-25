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

import java.util.Collections;
import java.util.List;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hive Schema 到 Iceberg Schema 的转换器（包级可见）。
 *
 * <p>所属模块：iceberg-hive-metastore（Schema 映射层）。
 *
 * <p>职责：将 Hive 的 {@link TypeInfo} 类型树递归转换为 Iceberg 的 {@link Type}/{@link Schema}， 同时维护字段 id 的自增分配。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>包级可见：仅作为 {@link HiveSchemaUtil} 的内部实现，外部应使用 HiveSchemaUtil。
 *   <li>autoConvert 开关：Hive 的 TINYINT/SMALLINT 在 Iceberg 中无直接对应（Iceberg 仅有
 *       IntegerType），CHAR/VARCHAR 同理。开启 autoConvert 时自动转为 INTEGER/STRING， 否则抛异常，让调用方显式决定是否容忍精度损失。
 *   <li>字段 id 自增：转换过程中通过实例字段 id 递增分配，保证 struct/map/list 内字段 id 唯一。
 *   <li>所有字段按 optional（可空）处理，因为 Hive 不区分 nullable/required。
 * </ul>
 *
 * <p>上下游关系：被 {@link HiveSchemaUtil} 的各 convert 方法调用；输入来自 Hive Metastore 表结构。
 */
class HiveSchemaConverter {
  private static final Logger LOG = LoggerFactory.getLogger(HiveSchemaConverter.class);

  private int id;
  private boolean autoConvert;

  private HiveSchemaConverter(boolean autoConvert) {
    this.autoConvert = autoConvert;
    this.id = 0;
  }

  /**
   * 将 Hive 列名/类型/注释列表转换为 Iceberg {@link Schema}。
   *
   * <p>逻辑：创建一个新转换器实例，委托 {@link #convertInternal} 生成字段列表后包装为 Schema。
   *
   * @param names 列名列表
   * @param typeInfos 列类型信息列表
   * @param comments 列注释列表
   * @param autoConvert 是否自动转换不兼容类型（TINYINT/SMALLINT→INT，CHAR/VARCHAR→STRING）
   * @return 转换后的 Iceberg Schema
   */
  static Schema convert(
      List<String> names, List<TypeInfo> typeInfos, List<String> comments, boolean autoConvert) {
    HiveSchemaConverter converter = new HiveSchemaConverter(autoConvert);
    return new Schema(converter.convertInternal(names, typeInfos, comments));
  }

  /**
   * 将单个 Hive {@link TypeInfo} 转换为 Iceberg {@link Type}。
   *
   * @param typeInfo Hive 类型信息
   * @param autoConvert 是否自动转换不兼容类型
   * @return 转换后的 Iceberg Type
   */
  static Type convert(TypeInfo typeInfo, boolean autoConvert) {
    HiveSchemaConverter converter = new HiveSchemaConverter(autoConvert);
    return converter.convertType(typeInfo);
  }

  /**
   * 将 Hive 列列表转换为 Iceberg NestedField 列表。
   *
   * <p>逻辑：遍历列名列表，对每列分配递增 id，调用 {@link #convertType} 转换类型， 并关联注释（若提供），所有字段按 optional 创建。
   *
   * @param names 列名列表
   * @param typeInfos 列类型信息列表
   * @param comments 列注释列表（可为空或短于 names）
   * @return Iceberg NestedField 列表
   */
  List<Types.NestedField> convertInternal(
      List<String> names, List<TypeInfo> typeInfos, List<String> comments) {
    List<Types.NestedField> result = Lists.newArrayListWithExpectedSize(names.size());
    for (int i = 0; i < names.size(); ++i) {
      result.add(
          Types.NestedField.optional(
              id++,
              names.get(i),
              convertType(typeInfos.get(i)),
              (comments.isEmpty() || i >= comments.size()) ? null : comments.get(i)));
    }

    return result;
  }

  /**
   * 将 Hive {@link TypeInfo} 递归转换为 Iceberg {@link Type}。
   *
   * <p>逻辑：按 typeInfo 的 category 分发：
   *
   * <ul>
   *   <li>PRIMITIVE：按 Hive 原始类型映射到 Iceberg 类型；BYTE/SHORT 需要 autoConvert 才映射为
   *       IntegerType；CHAR/VARCHAR 需要 autoConvert 才映射为 StringType；TIMESTAMP 默认无时区，
   *       TIMESTAMPLOCALTZ（Hive3 特有）映射为带时区 TimestampType；DECIMAL 保留精度和小数位。
   *   <li>STRUCT：递归转换各字段为 NestedField，构造 StructType。
   *   <li>MAP：递归转换 key/value 类型，分配 id，构造 MapType.ofOptional。
   *   <li>LIST：递归转换元素类型，构造 ListType.ofOptional。
   *   <li>UNION 及其他：抛 IllegalArgumentException。
   * </ul>
   *
   * @param typeInfo Hive 类型信息
   * @return 转换后的 Iceberg Type
   * @throws IllegalArgumentException 遇到不支持的类型或 autoConvert 关闭时不兼容类型
   */
  Type convertType(TypeInfo typeInfo) {
    switch (typeInfo.getCategory()) {
      case PRIMITIVE:
        switch (((PrimitiveTypeInfo) typeInfo).getPrimitiveCategory()) {
          case FLOAT:
            return Types.FloatType.get();
          case DOUBLE:
            return Types.DoubleType.get();
          case BOOLEAN:
            return Types.BooleanType.get();
          case BYTE:
          case SHORT:
            Preconditions.checkArgument(
                autoConvert,
                "Unsupported Hive type: %s, use integer instead",
                ((PrimitiveTypeInfo) typeInfo).getPrimitiveCategory());

            LOG.debug("Using auto conversion from SHORT/BYTE to INTEGER");
            return Types.IntegerType.get();
          case INT:
            return Types.IntegerType.get();
          case LONG:
            return Types.LongType.get();
          case BINARY:
            return Types.BinaryType.get();
          case CHAR:
          case VARCHAR:
            Preconditions.checkArgument(
                autoConvert,
                "Unsupported Hive type: %s, use string instead",
                ((PrimitiveTypeInfo) typeInfo).getPrimitiveCategory());

            LOG.debug("Using auto conversion from CHAR/VARCHAR to STRING");
            return Types.StringType.get();
          case STRING:
            return Types.StringType.get();
          case TIMESTAMP:
            return Types.TimestampType.withoutZone();
          case DATE:
            return Types.DateType.get();
          case DECIMAL:
            DecimalTypeInfo decimalTypeInfo = (DecimalTypeInfo) typeInfo;
            return Types.DecimalType.of(decimalTypeInfo.precision(), decimalTypeInfo.scale());
          case INTERVAL_YEAR_MONTH:
          case INTERVAL_DAY_TIME:
          default:
            // special case for Timestamp with Local TZ which is only available in Hive3
            if ("TIMESTAMPLOCALTZ"
                .equalsIgnoreCase(((PrimitiveTypeInfo) typeInfo).getPrimitiveCategory().name())) {
              return Types.TimestampType.withZone();
            }
            throw new IllegalArgumentException(
                "Unsupported Hive type ("
                    + ((PrimitiveTypeInfo) typeInfo).getPrimitiveCategory()
                    + ") for Iceberg tables.");
        }
      case STRUCT:
        StructTypeInfo structTypeInfo = (StructTypeInfo) typeInfo;
        List<Types.NestedField> fields =
            convertInternal(
                structTypeInfo.getAllStructFieldNames(),
                structTypeInfo.getAllStructFieldTypeInfos(),
                Collections.emptyList());
        return Types.StructType.of(fields);
      case MAP:
        MapTypeInfo mapTypeInfo = (MapTypeInfo) typeInfo;
        Type keyType = convertType(mapTypeInfo.getMapKeyTypeInfo());
        Type valueType = convertType(mapTypeInfo.getMapValueTypeInfo());
        int keyId = id++;
        int valueId = id++;
        return Types.MapType.ofOptional(keyId, valueId, keyType, valueType);
      case LIST:
        ListTypeInfo listTypeInfo = (ListTypeInfo) typeInfo;
        Type listType = convertType(listTypeInfo.getListElementTypeInfo());
        return Types.ListType.ofOptional(id++, listType);
      case UNION:
      default:
        throw new IllegalArgumentException("Unknown type " + typeInfo.getCategory());
    }
  }
}
