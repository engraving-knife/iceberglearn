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
package org.apache.iceberg.arrow;

import java.util.List;
import java.util.Map;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;

/**
 * 文件级说明：Iceberg Schema 与 Arrow Schema 之间的转换工具类。
 *
 * <p>所属模块：iceberg-arrow（Iceberg 类型系统与 Arrow 列式内存模型之间的桥接模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg 的 {@link org.apache.iceberg.Schema} 转换为 Arrow 的 {@link Schema}， 使 Iceberg 表结构可以在
 *       Arrow 向量内存中表示。
 *   <li>逐字段完成 Iceberg 类型到 Arrow 类型的映射，包括基本类型、Decimal、时间戳、 以及嵌套的 Struct/List/Map 结构。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>类型映射遵循 Arrow 规范并与 Iceberg 语义对齐：时间统一用微秒、时间戳用微秒并按 UTC 调整、UUID 映射为 16 字节定长二进制等。
 *   <li>Map 类型通过字段元数据（ORIGINAL_TYPE=mapType）标记其 Iceberg 原始类型，便于 下游读取时还原 Iceberg 语义（Arrow 的 Map 用
 *       "entries" 结构表示）。
 *   <li>字段的可空性（optional）透传到 Arrow 的 FieldType，保持空值语义一致。
 * </ul>
 *
 * <p>上下游关系：上游为 Iceberg Schema 定义；下游被向量化读取器构建逻辑调用，用以在读取 Parquet 数据前确定目标 Arrow 向量的结构。
 */
public class ArrowSchemaUtil {
  /** 字段元数据键：标记字段在 Iceberg 中的原始类型。 */
  private static final String ORIGINAL_TYPE = "originalType";
  /** 字段元数据值：表示该字段源自 Iceberg 的 Map 类型。 */
  private static final String MAP_TYPE = "mapType";

  private ArrowSchemaUtil() {}

  /**
   * 将 Iceberg Schema 整体转换为 Arrow Schema。
   *
   * <p>逻辑：遍历 Iceberg Schema 的每个顶层 {@link NestedField}，逐一调用 {@link #convert(NestedField)} 转换为 Arrow
   * Field，再组装为 Arrow {@link Schema}。
   *
   * @param schema Iceberg 表结构
   * @return 对应的 Arrow Schema
   */
  public static Schema convert(final org.apache.iceberg.Schema schema) {
    ImmutableList.Builder<Field> fields = ImmutableList.builder();

    for (NestedField f : schema.columns()) {
      fields.add(convert(f));
    }

    return new Schema(fields.build());
  }

  /**
   * 将单个 Iceberg {@link NestedField} 转换为 Arrow {@link Field}，含类型映射与子字段构建。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>按 Iceberg 类型 ID 进入对应分支，确定 Arrow {@link ArrowType}。
   *   <li>对于 STRUCT/LIST，递归转换其子字段并加入 children。
   *   <li>对于 MAP，构造 Arrow Map 类型并生成匿名 entry 子字段（含 key/value）， 同时写入 ORIGINAL_TYPE=mapType 元数据以保留
   *       Iceberg Map 语义。
   *   <li>最后以字段名、可空性、Arrow 类型、元数据和子字段构造 Arrow {@link Field} 返回。
   * </ul>
   *
   * @param field Iceberg 嵌套字段
   * @return 对应的 Arrow Field
   * @throws UnsupportedOperationException 若字段类型不被支持
   */
  public static Field convert(final NestedField field) {
    final ArrowType arrowType;

    final List<Field> children = Lists.newArrayList();
    Map<String, String> metadata = null;

    switch (field.type().typeId()) {
      case BINARY:
        arrowType = ArrowType.Binary.INSTANCE;
        break;
      case FIXED:
        final Types.FixedType fixedType = (Types.FixedType) field.type();
        arrowType = new ArrowType.FixedSizeBinary(fixedType.length());
        break;
      case BOOLEAN:
        arrowType = ArrowType.Bool.INSTANCE;
        break;
      case INTEGER:
        arrowType = new ArrowType.Int(Integer.SIZE, true /* signed */);
        break;
      case LONG:
        arrowType = new ArrowType.Int(Long.SIZE, true /* signed */);
        break;
      case FLOAT:
        arrowType = new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE);
        break;
      case DOUBLE:
        arrowType = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
        break;
      case DECIMAL:
        final Types.DecimalType decimalType = (Types.DecimalType) field.type();
        arrowType = new ArrowType.Decimal(decimalType.precision(), decimalType.scale());
        break;
      case STRING:
        arrowType = ArrowType.Utf8.INSTANCE;
        break;
      case TIME:
        arrowType = new ArrowType.Time(TimeUnit.MICROSECOND, Long.SIZE);
        break;
      case UUID:
        arrowType = new ArrowType.FixedSizeBinary(16);
        break;
      case TIMESTAMP:
        arrowType =
            new ArrowType.Timestamp(
                TimeUnit.MICROSECOND,
                ((Types.TimestampType) field.type()).shouldAdjustToUTC() ? "UTC" : null);
        break;
      case DATE:
        arrowType = new ArrowType.Date(DateUnit.DAY);
        break;
      case STRUCT:
        final StructType struct = field.type().asStructType();
        arrowType = ArrowType.Struct.INSTANCE;

        for (NestedField nested : struct.fields()) {
          children.add(convert(nested));
        }
        break;
      case LIST:
        final ListType listType = field.type().asListType();
        arrowType = ArrowType.List.INSTANCE;

        for (NestedField nested : listType.fields()) {
          children.add(convert(nested));
        }
        break;
      case MAP:
        metadata = ImmutableMap.of(ORIGINAL_TYPE, MAP_TYPE);
        final MapType mapType = field.type().asMapType();
        arrowType = new ArrowType.Map(false);
        List<Field> entryFields = Lists.transform(mapType.fields(), ArrowSchemaUtil::convert);
        Field entry =
            new Field("", new FieldType(field.isOptional(), arrowType, null), entryFields);
        children.add(entry);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported field type: " + field);
    }

    return new Field(
        field.name(), new FieldType(field.isOptional(), arrowType, null, metadata), children);
  }
}
