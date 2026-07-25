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
package org.apache.iceberg;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.io.BaseEncoding;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.JsonUtil;

/**
 * 单个类型值与 JSON 互转的工具。
 *
 * <p>所属模块：iceberg-core。职责：把某个 {@link Type} 对应的单个字面值在 Java 对象与 JSON 节点之间互转， 用于默认值、分区值等单值场景的持久化。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>按类型 ID 分发：boolean/int/long/decimal/binary/uuid/string/list/map 等各自处理。
 *   <li>二进制用 base64：ByteBuffer 与 JSON 之间通过 BaseEncoding 转换。
 *   <li>时间类型走 {@link DateTimeUtil}：保证时区/格式一致。
 * </ul>
 *
 * <p>上下游关系：被 schema/分区/默认值相关解析逻辑调用；依赖 {@link JsonUtil}、{@link ByteBuffers}。
 */
public class SingleValueParser {

  /** 私有构造：工具类禁止实例化。 */
  private SingleValueParser() {}

  private static final String KEYS = "keys";
  private static final String VALUES = "values";

  /**
   * 把 JSON 节点按 {@link Type} 解析为对应的 Java 对象。
   *
   * <p>步骤：null 节点返回 null；其他按类型 ID 分发：
   *
   * <ul>
   *   <li>布尔/整数/浮点：直接取对应 Java 数值；
   *   <li>decimal：解析 BigDecimal 并校验 scale；
   *   <li>string/uuid：取文本，UUID 校验长度 36；
   *   <li>date/time/timestamp：通过 {@link DateTimeUtil} 转 ISO 字符串为 days/micros；
   *   <li>fixed/binary：base16 解码为 ByteBuffer；
   *   <li>list/map/struct：递归调用对应方法。
   * </ul>
   *
   * @param type 期望的类型
   * @param defaultValue JSON 节点
   * @return 解析得到的 Java 对象，null 节点返回 null
   * @throws IllegalArgumentException 当 JSON 与类型不匹配时
   */
  public static Object fromJson(Type type, JsonNode defaultValue) {
    if (defaultValue == null || defaultValue.isNull()) {
      return null;
    }

    switch (type.typeId()) {
      case BOOLEAN:
        Preconditions.checkArgument(
            defaultValue.isBoolean(), "Cannot parse default as a %s value: %s", type, defaultValue);
        return defaultValue.booleanValue();
      case INTEGER:
        Preconditions.checkArgument(
            defaultValue.isIntegralNumber() && defaultValue.canConvertToInt(),
            "Cannot parse default as a %s value: %s",
            type,
            defaultValue);
        return defaultValue.intValue();
      case LONG:
        Preconditions.checkArgument(
            defaultValue.isIntegralNumber() && defaultValue.canConvertToLong(),
            "Cannot parse default as a %s value: %s",
            type,
            defaultValue);
        return defaultValue.longValue();
      case FLOAT:
        Preconditions.checkArgument(
            defaultValue.isFloatingPointNumber(),
            "Cannot parse default as a %s value: %s",
            type,
            defaultValue);
        return defaultValue.floatValue();
      case DOUBLE:
        Preconditions.checkArgument(
            defaultValue.isFloatingPointNumber(),
            "Cannot parse default as a %s value: %s",
            type,
            defaultValue);
        return defaultValue.doubleValue();
      case DECIMAL:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        BigDecimal retDecimal;
        try {
          retDecimal = new BigDecimal(defaultValue.textValue());
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException(
              String.format("Cannot parse default as a %s value: %s", type, defaultValue), e);
        }
        Preconditions.checkArgument(
            retDecimal.scale() == ((Types.DecimalType) type).scale(),
            "Cannot parse default as a %s value: %s, the scale doesn't match",
            type,
            defaultValue);
        return retDecimal;
      case STRING:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        return defaultValue.textValue();
      case UUID:
        Preconditions.checkArgument(
            defaultValue.isTextual() && defaultValue.textValue().length() == 36,
            "Cannot parse default as a %s value: %s",
            type,
            defaultValue);
        UUID uuid;
        try {
          uuid = UUID.fromString(defaultValue.textValue());
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              String.format("Cannot parse default as a %s value: %s", type, defaultValue), e);
        }
        return uuid;
      case DATE:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        return DateTimeUtil.isoDateToDays(defaultValue.textValue());
      case TIME:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        return DateTimeUtil.isoTimeToMicros(defaultValue.textValue());
      case TIMESTAMP:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          String timestampTz = defaultValue.textValue();
          Preconditions.checkArgument(
              DateTimeUtil.isUTCTimestamptz(timestampTz),
              "Cannot parse default as a %s value: %s, offset must be +00:00",
              type,
              defaultValue);
          return DateTimeUtil.isoTimestamptzToMicros(timestampTz);
        } else {
          return DateTimeUtil.isoTimestampToMicros(defaultValue.textValue());
        }
      case FIXED:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        int defaultLength = defaultValue.textValue().length();
        int fixedLength = ((Types.FixedType) type).length();
        Preconditions.checkArgument(
            defaultLength == fixedLength * 2,
            "Cannot parse default %s value: %s, incorrect length: %s",
            type,
            defaultValue,
            defaultLength);
        byte[] fixedBytes =
            BaseEncoding.base16().decode(defaultValue.textValue().toUpperCase(Locale.ROOT));
        return ByteBuffer.wrap(fixedBytes);
      case BINARY:
        Preconditions.checkArgument(
            defaultValue.isTextual(), "Cannot parse default as a %s value: %s", type, defaultValue);
        byte[] binaryBytes =
            BaseEncoding.base16().decode(defaultValue.textValue().toUpperCase(Locale.ROOT));
        return ByteBuffer.wrap(binaryBytes);
      case LIST:
        return listFromJson(type, defaultValue);
      case MAP:
        return mapFromJson(type, defaultValue);
      case STRUCT:
        return structFromJson(type, defaultValue);
      default:
        throw new UnsupportedOperationException(String.format("Type: %s is not supported", type));
    }
  }

  /**
   * 从 JSON 节点解析 StructType 默认值，构造 {@link GenericRecord}。
   *
   * <p>步骤：按字段 id（字符串形式）查 JSON 对象，存在则递归解析后 set 到对应位置。
   *
   * @param type struct 类型
   * @param defaultValue JSON 节点
   * @return 解析得到的 StructLike
   */
  private static StructLike structFromJson(Type type, JsonNode defaultValue) {
    Preconditions.checkArgument(
        defaultValue.isObject(), "Cannot parse default as a %s value: %s", type, defaultValue);
    Types.StructType struct = type.asStructType();
    StructLike defaultRecord = GenericRecord.create(struct);

    List<Types.NestedField> fields = struct.fields();
    for (int pos = 0; pos < fields.size(); pos += 1) {
      Types.NestedField field = fields.get(pos);
      String idString = String.valueOf(field.fieldId());
      if (defaultValue.has(idString)) {
        defaultRecord.set(pos, fromJson(field.type(), defaultValue.get(idString)));
      }
    }
    return defaultRecord;
  }

  /**
   * 从 JSON 节点解析 MapType 默认值。
   *
   * <p>JSON 形式为 {keys:[...], values:[...]}，长度必须相等。
   *
   * @param type map 类型
   * @param defaultValue JSON 节点
   * @return 解析得到的不可变 map
   */
  private static Map<Object, Object> mapFromJson(Type type, JsonNode defaultValue) {
    Preconditions.checkArgument(
        defaultValue.isObject()
            && defaultValue.has(KEYS)
            && defaultValue.has(VALUES)
            && defaultValue.get(KEYS).isArray()
            && defaultValue.get(VALUES).isArray(),
        "Cannot parse %s to a %s value",
        defaultValue,
        type);
    JsonNode keys = defaultValue.get(KEYS);
    JsonNode values = defaultValue.get(VALUES);
    Preconditions.checkArgument(
        keys.size() == values.size(), "Cannot parse default as a %s value: %s", type, defaultValue);

    ImmutableMap.Builder<Object, Object> mapBuilder = ImmutableMap.builder();

    Iterator<JsonNode> keyIter = keys.iterator();
    Type keyType = type.asMapType().keyType();
    Iterator<JsonNode> valueIter = values.iterator();
    Type valueType = type.asMapType().valueType();

    while (keyIter.hasNext()) {
      mapBuilder.put(fromJson(keyType, keyIter.next()), fromJson(valueType, valueIter.next()));
    }

    return mapBuilder.build();
  }

  /**
   * 从 JSON 节点解析 ListType 默认值。
   *
   * @param type list 类型
   * @param defaultValue JSON 节点（必须是数组）
   * @return 解析得到的元素列表
   */
  private static List<Object> listFromJson(Type type, JsonNode defaultValue) {
    Preconditions.checkArgument(
        defaultValue.isArray(), "Cannot parse default as a %s value: %s", type, defaultValue);
    Type elementType = type.asListType().elementType();
    return Lists.newArrayList(Iterables.transform(defaultValue, e -> fromJson(elementType, e)));
  }

  /**
   * 把 JSON 字符串按 {@link Type} 解析为对应的 Java 对象。
   *
   * @param type 期望的类型
   * @param defaultValue JSON 字符串
   * @return 解析得到的 Java 对象
   */
  public static Object fromJson(Type type, String defaultValue) {
    return JsonUtil.parse(defaultValue, node -> SingleValueParser.fromJson(type, node));
  }

  /**
   * 把 Java 对象按 {@link Type} 序列化为 JSON 字符串（紧凑形式）。
   *
   * @param type 值的类型
   * @param defaultValue Java 对象
   * @return JSON 字符串
   */
  public static String toJson(Type type, Object defaultValue) {
    return toJson(type, defaultValue, false);
  }

  /**
   * 把 Java 对象按 {@link Type} 序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param type 值的类型
   * @param defaultValue Java 对象
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(Type type, Object defaultValue, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(type, defaultValue, gen), pretty);
  }

  /**
   * 把 Java 对象按 {@link Type} 写入 JSON 生成器。
   *
   * <p>步骤：null 写 writeNull；其他按类型 ID 分发到对应的写入分支， 复杂类型（list/map/struct）递归调用本方法。
   *
   * @param type 值的类型
   * @param defaultValue Java 对象
   * @param generator JSON 生成器
   * @throws IOException 写入失败
   */
  @SuppressWarnings("checkstyle:MethodLength")
  public static void toJson(Type type, Object defaultValue, JsonGenerator generator)
      throws IOException {
    if (defaultValue == null) {
      generator.writeNull();
      return;
    }

    switch (type.typeId()) {
      case BOOLEAN:
        Preconditions.checkArgument(
            defaultValue instanceof Boolean, "Invalid default %s value: %s", type, defaultValue);
        generator.writeBoolean((Boolean) defaultValue);
        break;
      case INTEGER:
        Preconditions.checkArgument(
            defaultValue instanceof Integer, "Invalid default %s value: %s", type, defaultValue);
        generator.writeNumber((Integer) defaultValue);
        break;
      case LONG:
        Preconditions.checkArgument(
            defaultValue instanceof Long, "Invalid default %s value: %s", type, defaultValue);
        generator.writeNumber((Long) defaultValue);
        break;
      case FLOAT:
        Preconditions.checkArgument(
            defaultValue instanceof Float, "Invalid default %s value: %s", type, defaultValue);
        generator.writeNumber((Float) defaultValue);
        break;
      case DOUBLE:
        Preconditions.checkArgument(
            defaultValue instanceof Double, "Invalid default %s value: %s", type, defaultValue);
        generator.writeNumber((Double) defaultValue);
        break;
      case DATE:
        Preconditions.checkArgument(
            defaultValue instanceof Integer, "Invalid default %s value: %s", type, defaultValue);
        generator.writeString(DateTimeUtil.daysToIsoDate((Integer) defaultValue));
        break;
      case TIME:
        Preconditions.checkArgument(
            defaultValue instanceof Long, "Invalid default %s value: %s", type, defaultValue);
        generator.writeString(DateTimeUtil.microsToIsoTime((Long) defaultValue));
        break;
      case TIMESTAMP:
        Preconditions.checkArgument(
            defaultValue instanceof Long, "Invalid default %s value: %s", type, defaultValue);
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          generator.writeString(DateTimeUtil.microsToIsoTimestamptz((Long) defaultValue));
        } else {
          generator.writeString(DateTimeUtil.microsToIsoTimestamp((Long) defaultValue));
        }
        break;
      case STRING:
        Preconditions.checkArgument(
            defaultValue instanceof CharSequence,
            "Invalid default %s value: %s",
            type,
            defaultValue);
        generator.writeString(((CharSequence) defaultValue).toString());
        break;
      case UUID:
        Preconditions.checkArgument(
            defaultValue instanceof UUID, "Invalid default %s value: %s", type, defaultValue);
        generator.writeString(defaultValue.toString());
        break;
      case FIXED:
        Preconditions.checkArgument(
            defaultValue instanceof ByteBuffer, "Invalid default %s value: %s", type, defaultValue);
        ByteBuffer byteBufferValue = (ByteBuffer) defaultValue;
        int expectedLength = ((Types.FixedType) type).length();
        Preconditions.checkArgument(
            byteBufferValue.remaining() == expectedLength,
            "Invalid default %s value, incorrect length: %s",
            type,
            byteBufferValue.remaining());
        generator.writeString(
            BaseEncoding.base16().encode(ByteBuffers.toByteArray(byteBufferValue)));
        break;
      case BINARY:
        Preconditions.checkArgument(
            defaultValue instanceof ByteBuffer, "Invalid default %s value: %s", type, defaultValue);
        generator.writeString(
            BaseEncoding.base16().encode(ByteBuffers.toByteArray((ByteBuffer) defaultValue)));
        break;
      case DECIMAL:
        Preconditions.checkArgument(
            defaultValue instanceof BigDecimal
                && ((BigDecimal) defaultValue).scale() == ((Types.DecimalType) type).scale(),
            "Invalid default %s value: %s",
            type,
            defaultValue);
        BigDecimal decimalValue = (BigDecimal) defaultValue;
        if (decimalValue.scale() >= 0) {
          generator.writeString(decimalValue.toPlainString());
        } else {
          generator.writeString(decimalValue.toString());
        }
        break;
      case LIST:
        Preconditions.checkArgument(
            defaultValue instanceof List, "Invalid default %s value: %s", type, defaultValue);
        List<Object> defaultList = (List<Object>) defaultValue;
        Type elementType = type.asListType().elementType();
        generator.writeStartArray();
        for (Object element : defaultList) {
          toJson(elementType, element, generator);
        }
        generator.writeEndArray();
        break;
      case MAP:
        Preconditions.checkArgument(
            defaultValue instanceof Map, "Invalid default %s value: %s", type, defaultValue);
        Map<Object, Object> defaultMap = (Map<Object, Object>) defaultValue;
        Type keyType = type.asMapType().keyType();
        Type valueType = type.asMapType().valueType();

        List<Object> valueList = Lists.newArrayListWithExpectedSize(defaultMap.size());
        generator.writeStartObject();
        generator.writeArrayFieldStart(KEYS);
        for (Map.Entry<Object, Object> entry : defaultMap.entrySet()) {
          toJson(keyType, entry.getKey(), generator);
          valueList.add(entry.getValue());
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart(VALUES);
        for (Object value : valueList) {
          toJson(valueType, value, generator);
        }
        generator.writeEndArray();
        generator.writeEndObject();
        break;
      case STRUCT:
        Preconditions.checkArgument(
            defaultValue instanceof StructLike, "Invalid default %s value: %s", type, defaultValue);
        Types.StructType structType = type.asStructType();
        List<Types.NestedField> fields = structType.fields();
        StructLike defaultStruct = (StructLike) defaultValue;

        generator.writeStartObject();
        for (int i = 0; i < defaultStruct.size(); i++) {
          Types.NestedField field = fields.get(i);
          int fieldId = field.fieldId();
          Object fieldDefaultValue = defaultStruct.get(i, Object.class);
          if (fieldDefaultValue != null) {
            generator.writeFieldName(String.valueOf(fieldId));
            toJson(field.type(), fieldDefaultValue, generator);
          }
        }
        generator.writeEndObject();
        break;
      default:
        throw new UnsupportedOperationException(String.format("Type: %s is not supported", type));
    }
  }
}
