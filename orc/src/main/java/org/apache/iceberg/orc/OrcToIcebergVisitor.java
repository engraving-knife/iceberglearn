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
package org.apache.iceberg.orc;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;

/**
 * 把 ORC TypeDescription 树转为 Iceberg NestedField 树的访问器。
 *
 * <p>所属模块：iceberg-orc。是 {@link ORCSchemaUtil#convert(TypeDescription)} 的核心实现， 把带 Iceberg id 属性的 ORC
 * schema 还原为 Iceberg Schema。
 *
 * <p>职责：遍历 ORC TypeDescription，按节点 category 和 Iceberg 属性（id/required/binary-type/
 * long-type/length）构造对应的 Iceberg NestedField（包装在 Optional 中）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无 Iceberg id 的节点返回 Optional.empty()，struct 中全部子字段为 empty 时整个 struct 也为 empty， 实现“无 id
 *       的列被忽略”。
 *   <li>LONG 类别按 ICEBERG_LONG_TYPE_ATTRIBUTE 区分 TIME/LONG；BINARY 类别按 ICEBERG_BINARY_TYPE_ATTRIBUTE
 *       区分 UUID/FIXED/BINARY（FIXED 还需读 length）。
 *   <li>list/map 的元素 required/optional 从 ORC 子节点的 ICEBERG_REQUIRED_ATTRIBUTE 推断。
 * </ul>
 *
 * <p>上下游关系：被 {@link ORCSchemaUtil#convert(TypeDescription)} 调用。
 */
class OrcToIcebergVisitor extends OrcSchemaVisitor<Optional<Types.NestedField>> {

  @Override
  /**
   * 把 ORC struct 转为 Iceberg NestedField（含 StructType）。
   *
   * <p>逻辑：若自身无 id 或所有子字段都为 empty 则返回 empty；否则收集非 empty 子字段 构建 StructType，用 currentFieldName()
   * 作为字段名。
   */
  public Optional<Types.NestedField> record(
      TypeDescription record, List<String> names, List<Optional<Types.NestedField>> fields) {
    boolean isOptional = ORCSchemaUtil.isOptional(record);
    Optional<Integer> icebergIdOpt = ORCSchemaUtil.icebergID(record);
    if (!icebergIdOpt.isPresent() || fields.stream().noneMatch(Optional::isPresent)) {
      return Optional.empty();
    }

    Types.StructType structType =
        Types.StructType.of(
            fields.stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList()));
    return Optional.of(
        Types.NestedField.of(icebergIdOpt.get(), isOptional, currentFieldName(), structType));
  }

  @Override
  /**
   * 把 ORC list 转为 Iceberg NestedField（含 ListType）。
   *
   * <p>逻辑：若自身或元素无 id 则返回 empty；否则按 ORC 子节点的 required 属性 构建 ListType.ofOptional/ofRequired。
   */
  public Optional<Types.NestedField> list(
      TypeDescription array, Optional<Types.NestedField> element) {
    boolean isOptional = ORCSchemaUtil.isOptional(array);
    Optional<Integer> icebergIdOpt = ORCSchemaUtil.icebergID(array);

    if (!icebergIdOpt.isPresent() || !element.isPresent()) {
      return Optional.empty();
    }

    Types.NestedField foundElement = element.get();
    Types.ListType listTypeWithElem =
        ORCSchemaUtil.isOptional(array.getChildren().get(0))
            ? Types.ListType.ofOptional(foundElement.fieldId(), foundElement.type())
            : Types.ListType.ofRequired(foundElement.fieldId(), foundElement.type());

    return Optional.of(
        Types.NestedField.of(icebergIdOpt.get(), isOptional, currentFieldName(), listTypeWithElem));
  }

  @Override
  /**
   * 把 ORC map 转为 Iceberg NestedField（含 MapType）。
   *
   * <p>逻辑：若自身/key/value 任一无 id 则返回 empty；否则按 ORC value 子节点的 required 属性 构建
   * MapType.ofOptional/ofRequired。
   */
  public Optional<Types.NestedField> map(
      TypeDescription map, Optional<Types.NestedField> key, Optional<Types.NestedField> value) {
    boolean isOptional = ORCSchemaUtil.isOptional(map);
    Optional<Integer> icebergIdOpt = ORCSchemaUtil.icebergID(map);

    if (!icebergIdOpt.isPresent() || !key.isPresent() || !value.isPresent()) {
      return Optional.empty();
    }

    Types.NestedField foundKey = key.get();
    Types.NestedField foundValue = value.get();
    Types.MapType mapTypeWithKV =
        ORCSchemaUtil.isOptional(map.getChildren().get(1))
            ? Types.MapType.ofOptional(
                foundKey.fieldId(), foundValue.fieldId(), foundKey.type(), foundValue.type())
            : Types.MapType.ofRequired(
                foundKey.fieldId(), foundValue.fieldId(), foundKey.type(), foundValue.type());

    return Optional.of(
        Types.NestedField.of(icebergIdOpt.get(), isOptional, currentFieldName(), mapTypeWithKV));
  }

  @Override
  /**
   * 把 ORC 叶子类型转为 Iceberg NestedField。
   *
   * <p>逻辑：若无 id 返回 empty；否则按 ORC category 查表映射为 Iceberg 类型， LONG 按 ICEBERG_LONG_TYPE_ATTRIBUTE 区分
   * TIME/LONG，BINARY 按 ICEBERG_BINARY_TYPE_ATTRIBUTE 区分 UUID/FIXED/BINARY，
   * TIMESTAMP/TIMESTAMP_INSTANT 映射为 withoutZone/withZone。
   *
   * @throws IllegalArgumentException 出现未覆盖的 ORC category
   */
  public Optional<Types.NestedField> primitive(TypeDescription primitive) {
    boolean isOptional = ORCSchemaUtil.isOptional(primitive);
    Optional<Integer> icebergIdOpt = ORCSchemaUtil.icebergID(primitive);

    if (!icebergIdOpt.isPresent()) {
      return Optional.empty();
    }

    final Types.NestedField foundField;
    int icebergID = icebergIdOpt.get();
    String name = currentFieldName();
    switch (primitive.getCategory()) {
      case BOOLEAN:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.BooleanType.get());
        break;
      case BYTE:
      case SHORT:
      case INT:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.IntegerType.get());
        break;
      case LONG:
        String longAttributeValue =
            primitive.getAttributeValue(ORCSchemaUtil.ICEBERG_LONG_TYPE_ATTRIBUTE);
        ORCSchemaUtil.LongType longType =
            longAttributeValue == null
                ? ORCSchemaUtil.LongType.LONG
                : ORCSchemaUtil.LongType.valueOf(longAttributeValue);
        switch (longType) {
          case TIME:
            foundField = Types.NestedField.of(icebergID, isOptional, name, Types.TimeType.get());
            break;
          case LONG:
            foundField = Types.NestedField.of(icebergID, isOptional, name, Types.LongType.get());
            break;
          default:
            throw new IllegalStateException("Invalid Long type found in ORC type attribute");
        }
        break;
      case FLOAT:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.FloatType.get());
        break;
      case DOUBLE:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.DoubleType.get());
        break;
      case STRING:
      case CHAR:
      case VARCHAR:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.StringType.get());
        break;
      case BINARY:
        String binaryAttributeValue =
            primitive.getAttributeValue(ORCSchemaUtil.ICEBERG_BINARY_TYPE_ATTRIBUTE);
        ORCSchemaUtil.BinaryType binaryType =
            binaryAttributeValue == null
                ? ORCSchemaUtil.BinaryType.BINARY
                : ORCSchemaUtil.BinaryType.valueOf(binaryAttributeValue);
        switch (binaryType) {
          case UUID:
            foundField = Types.NestedField.of(icebergID, isOptional, name, Types.UUIDType.get());
            break;
          case FIXED:
            int fixedLength =
                Integer.parseInt(primitive.getAttributeValue(ORCSchemaUtil.ICEBERG_FIELD_LENGTH));
            foundField =
                Types.NestedField.of(
                    icebergID, isOptional, name, Types.FixedType.ofLength(fixedLength));
            break;
          case BINARY:
            foundField = Types.NestedField.of(icebergID, isOptional, name, Types.BinaryType.get());
            break;
          default:
            throw new IllegalStateException("Invalid Binary type found in ORC type attribute");
        }
        break;
      case DATE:
        foundField = Types.NestedField.of(icebergID, isOptional, name, Types.DateType.get());
        break;
      case TIMESTAMP:
        foundField =
            Types.NestedField.of(icebergID, isOptional, name, Types.TimestampType.withoutZone());
        break;
      case TIMESTAMP_INSTANT:
        foundField =
            Types.NestedField.of(icebergID, isOptional, name, Types.TimestampType.withZone());
        break;
      case DECIMAL:
        foundField =
            Types.NestedField.of(
                icebergID,
                isOptional,
                name,
                Types.DecimalType.of(primitive.getPrecision(), primitive.getScale()));
        break;
      default:
        throw new IllegalArgumentException("Can't handle " + primitive);
    }
    return Optional.of(foundField);
  }
}
