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
package org.apache.iceberg.pig;

import java.io.IOException;
import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.pig.ResourceSchema;
import org.apache.pig.ResourceSchema.ResourceFieldSchema;
import org.apache.pig.data.DataType;
import org.apache.pig.impl.logicalLayer.FrontendException;

/**
 * 文件级说明：Iceberg Schema 与 Pig ResourceSchema 之间的双向转换工具。
 *
 * <p>所属模块：iceberg-pig（Pig 引擎集成模块；被 {@link IcebergStorage} 与 {@link IcebergPigInputFormat} 调用，做
 * Iceberg 类型系统与 Pig 类型系统的桥接）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #convert(Schema)}：把 Iceberg 表 Schema 转为 Pig {@link ResourceSchema}， 供 Pig frontend
 *       了解表结构。
 *   <li>{@link #project(Schema, List)}：按字段名列表裁剪出投影后 Schema。
 *   <li>内部提供 Iceberg {@link Type} 与 Pig {@link DataType} 之间的映射， 以及 struct/list/map 等复杂类型的
 *       ResourceSchema 构造。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Iceberg 与 Pig 类型系统差异较大（如 timestamp/date 在 Pig 端以 CHARARRAY 表示、 list 以 BAG 表示、struct 以 TUPLE
 *       表示），需集中做映射，避免散落各处。
 *   <li>list 的元素非 struct 时需包一层 tuple（Pig bag 元素必须是 tuple）， 详见 {@link #convertComplex(Type)} 中 LIST
 *       分支。
 *   <li>map 仅支持 STRING key，遇非 STRING key 抛异常，提前暴露不兼容。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergStorage#getSchema}、{@link IcebergPigInputFormat} 调用； 依赖 Iceberg {@link
 * Schema}/{@link Types} 与 Pig {@link ResourceSchema}/{@link DataType}。
 */
public class SchemaUtil {

  /** 工具类，禁止实例化。 */
  private SchemaUtil() {}

  /**
   * 把 Iceberg 表 Schema 转换为 Pig {@link ResourceSchema}。
   *
   * @param icebergSchema Iceberg 表 Schema
   * @return Pig ResourceSchema
   * @throws IOException 转换失败
   */
  public static ResourceSchema convert(Schema icebergSchema) throws IOException {
    ResourceSchema result = new ResourceSchema();
    result.setFields(convertFields(icebergSchema.columns()));
    return result;
  }

  /**
   * 把 Iceberg {@link Types.NestedField} 转换为 Pig {@link ResourceFieldSchema}， 字段名与 fieldId（写入
   * description）一并设置。
   *
   * @param field Iceberg 字段
   * @return Pig 字段 schema
   * @throws IOException 转换失败
   */
  private static ResourceFieldSchema convert(Types.NestedField field) throws IOException {
    ResourceFieldSchema result = convert(field.type());
    result.setName(field.name());
    result.setDescription(String.format("FieldId: %s", field.fieldId()));

    return result;
  }

  /**
   * 把 Iceberg {@link Type} 转换为 Pig {@link ResourceFieldSchema}。 非基本类型时递归调用 {@link
   * #convertComplex(Type)} 构造子 schema。
   *
   * @param type Iceberg 类型
   * @return Pig 字段 schema
   * @throws IOException 转换失败
   */
  private static ResourceFieldSchema convert(Type type) throws IOException {
    ResourceFieldSchema result = new ResourceFieldSchema();
    result.setType(convertType(type));

    if (!type.isPrimitiveType()) {
      result.setSchema(convertComplex(type));
    }

    return result;
  }

  /**
   * 批量转换字段列表为 Pig {@link ResourceFieldSchema} 数组。
   *
   * @param fields Iceberg 字段列表
   * @return Pig 字段 schema 数组
   * @throws IOException 转换失败
   */
  private static ResourceFieldSchema[] convertFields(List<Types.NestedField> fields)
      throws IOException {
    List<ResourceFieldSchema> result = Lists.newArrayList();

    for (Types.NestedField nf : fields) {
      result.add(convert(nf));
    }

    return result.toArray(new ResourceFieldSchema[0]);
  }

  /**
   * 把 Iceberg {@link Type} 映射为 Pig {@link DataType} 字节码。
   *
   * <p>逻辑：按 typeId switch：BOOLEAN-&gt;BOOLEAN、INTEGER-&gt;INTEGER、LONG-&gt;LONG、
   * FLOAT-&gt;FLOAT、DOUBLE-&gt;DOUBLE、TIMESTAMP/DATE/STRING-&gt;CHARARRAY、
   * FIXED/BINARY-&gt;BYTEARRAY、DECIMAL-&gt;BIGDECIMAL、STRUCT-&gt;TUPLE、
   * LIST-&gt;BAG、MAP-&gt;MAP；其它抛 FrontendException。
   *
   * @param type Iceberg 类型
   * @return Pig DataType 字节码
   * @throws IOException 不支持的类型
   */
  private static byte convertType(Type type) throws IOException {
    switch (type.typeId()) {
      case BOOLEAN:
        return DataType.BOOLEAN;
      case INTEGER:
        return DataType.INTEGER;
      case LONG:
        return DataType.LONG;
      case FLOAT:
        return DataType.FLOAT;
      case DOUBLE:
        return DataType.DOUBLE;
      case TIMESTAMP:
        return DataType.CHARARRAY;
      case DATE:
        return DataType.CHARARRAY;
      case STRING:
        return DataType.CHARARRAY;
      case FIXED:
        return DataType.BYTEARRAY;
      case BINARY:
        return DataType.BYTEARRAY;
      case DECIMAL:
        return DataType.BIGDECIMAL;
      case STRUCT:
        return DataType.TUPLE;
      case LIST:
        return DataType.BAG;
      case MAP:
        return DataType.MAP;
      default:
        throw new FrontendException("Unsupported primitive type:" + type);
    }
  }

  /**
   * 构造复杂类型（STRUCT/LIST/MAP）的 Pig {@link ResourceSchema}。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>STRUCT：递归转换所有子字段。
   *   <li>LIST：取元素类型 schema；若元素非 struct 则包一层 tuple（Pig bag 元素必须是 tuple）。
   *   <li>MAP：要求 key 必须为 STRING，否则抛异常；只取 value 类型 schema。
   * </ul>
   *
   * @param type Iceberg 复杂类型
   * @return Pig ResourceSchema
   * @throws IOException 不支持的复杂类型或 map key 非 STRING
   */
  private static ResourceSchema convertComplex(Type type) throws IOException {
    ResourceSchema result = new ResourceSchema();

    switch (type.typeId()) {
      case STRUCT:
        Types.StructType structType = type.asStructType();

        List<ResourceFieldSchema> fields = Lists.newArrayList();

        for (Types.NestedField f : structType.fields()) {
          fields.add(convert(f));
        }

        result.setFields(fields.toArray(new ResourceFieldSchema[0]));

        return result;
      case LIST:
        Types.ListType listType = type.asListType();

        ResourceFieldSchema[] elementFieldSchemas =
            new ResourceFieldSchema[] {convert(listType.elementType())};

        if (listType.elementType().isStructType()) {
          result.setFields(elementFieldSchemas);
        } else {
          // Wrap non-struct types in tuples
          ResourceSchema elementSchema = new ResourceSchema();
          elementSchema.setFields(elementFieldSchemas);

          ResourceFieldSchema tupleSchema = new ResourceFieldSchema();
          tupleSchema.setType(DataType.TUPLE);
          tupleSchema.setSchema(elementSchema);

          result.setFields(new ResourceFieldSchema[] {tupleSchema});
        }

        return result;
      case MAP:
        Types.MapType mapType = type.asMapType();

        if (mapType.keyType().typeId() != Type.TypeID.STRING) {
          throw new FrontendException("Unsupported map key type: " + mapType.keyType());
        }
        result.setFields(new ResourceFieldSchema[] {convert(mapType.valueType())});

        return result;
      default:
        throw new FrontendException("Unsupported complex type: " + type);
    }
  }

  /**
   * 按字段名列表裁剪出投影后 Schema，保持 requiredFields 给定的顺序。
   *
   * <p>设计要点：Pig 下推的投影字段顺序可能与表 schema 不同，本方法按 requiredFields 顺序 重新构造 Schema，使输出 Tuple 列序符合 Pig
   * 期望。若字段不存在会抛 IllegalArgumentException。
   *
   * @param schema 原始 Iceberg Schema
   * @param requiredFields 需保留的字段名列表（决定输出顺序）
   * @return 投影后的 Schema
   */
  public static Schema project(Schema schema, List<String> requiredFields) {
    List<Types.NestedField> columns = Lists.newArrayList();

    for (String column : requiredFields) {
      columns.add(schema.findField(column));
    }

    return new Schema(columns);
  }
}
