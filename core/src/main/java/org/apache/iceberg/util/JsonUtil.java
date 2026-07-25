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
package org.apache.iceberg.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.io.BaseEncoding;

/**
 * JSON 序列化/反序列化工具类，封装 Iceberg 元数据 JSON 读写所需的通用方法。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供共享的 {@link JsonFactory} 与 {@link ObjectMapper} 单例；
 *   <li>封装按属性名从 {@link JsonNode} 读取基础类型（int/long/boolean/String/ByteBuffer 等）的方法， 含严格的类型校验与缺失字段报错；
 *   <li>提供数组和 Map 类型的读写辅助，以及条件写入字段的方法。
 * </ul>
 *
 * <p>设计意图：Iceberg 元数据（metadata.json、manifest 列表、快照等）以 JSON 持久化， 需要大量强类型读取与校验。本类把重复的 Preconditions
 * 校验与异常包装统一收敛， 使各元数据解析类保持简洁，同时保证反序列化时的格式错误能给出清晰的错误信息。
 *
 * <p>上下游关系：被 core 的元数据序列化类（如 TableMetadataParser、SnapshotParser、ManifestFile 等） 大量调用；依赖 Jackson 与
 * relocated guava。
 */
public class JsonUtil {

  private JsonUtil() {}

  private static final JsonFactory FACTORY = new JsonFactory();
  private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);

  /** 返回 全局共享的 {@link JsonFactory}，用于创建 JsonGenerator/JsonParser。 */
  public static JsonFactory factory() {
    return FACTORY;
  }

  /** 返回 全局共享的 {@link ObjectMapper}，用于把 JSON 字符串解析为 JsonNode。 */
  public static ObjectMapper mapper() {
    return MAPPER;
  }

  @FunctionalInterface
  public interface ToJson {
    void generate(JsonGenerator gen) throws IOException;
  }

  /**
   * 使用 {@link JsonGenerator} 写入 JSON 并返回字符串。
   *
   * @param toJson a function to produce JSON using a JsonGenerator
   * @param pretty whether to pretty-print JSON for readability
   * @return a JSON string produced from the generator
   */
  public static String generate(ToJson toJson, boolean pretty) {
    try (StringWriter writer = new StringWriter();
        JsonGenerator generator = JsonUtil.factory().createGenerator(writer)) {
      if (pretty) {
        generator.useDefaultPrettyPrinter();
      }
      toJson.generate(generator);
      generator.flush();
      return writer.toString();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 函数式接口：从 {@link JsonNode} 解析为 Java 对象。 */
  @FunctionalInterface
  public interface FromJson<T> {
    T parse(JsonNode node);
  }

  /**
   * 从 JSON 字符串解析为 Java 对象的辅助方法。
   *
   * <p>逻辑：先用 ObjectMapper 把字符串读成 JsonNode，再交给 parser 转换； IOException 包装为 {@link
   * UncheckedIOException} 抛出。
   *
   * @param json JSON 字符串
   * @param parser 把 JsonNode 转换为目标对象的函数
   * @param <T> 目标对象类型
   * @return 解析后的 Java 对象
   */
  public static <T> T parse(String json, FromJson<T> parser) {
    try {
      return parser.parse(JsonUtil.mapper().readValue(json, JsonNode.class));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode get(String property, JsonNode node) {
    Preconditions.checkArgument(
        node.hasNonNull(property), "Cannot parse missing field: %s", property);
    return node.get(property);
  }

  public static int getInt(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing int: %s", property);
    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode != null && !pNode.isNull() && pNode.isIntegralNumber() && pNode.canConvertToInt(),
        "Cannot parse to an integer value: %s: %s",
        property,
        pNode);
    return pNode.asInt();
  }

  public static Integer getIntOrNull(String property, JsonNode node) {
    if (!node.hasNonNull(property)) {
      return null;
    }
    return getInt(property, node);
  }

  /**
   * 读取可选的 long 属性，缺失或为 null 时返回 null。
   *
   * @param property 属性名
   * @param node 父节点
   * @return long 值或 null
   */
  public static Long getLongOrNull(String property, JsonNode node) {
    if (!node.hasNonNull(property)) {
      return null;
    }
    return getLong(property, node);
  }

  public static long getLong(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing long: %s", property);
    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode != null && !pNode.isNull() && pNode.isIntegralNumber() && pNode.canConvertToLong(),
        "Cannot parse to a long value: %s: %s",
        property,
        pNode);
    return pNode.asLong();
  }

  public static boolean getBool(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing boolean: %s", property);
    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode != null && !pNode.isNull() && pNode.isBoolean(),
        "Cannot parse to a boolean value: %s: %s",
        property,
        pNode);
    return pNode.asBoolean();
  }

  public static String getString(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing string: %s", property);
    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode != null && !pNode.isNull() && pNode.isTextual(),
        "Cannot parse to a string value: %s: %s",
        property,
        pNode);
    return pNode.asText();
  }

  public static String getStringOrNull(String property, JsonNode node) {
    if (!node.has(property)) {
      return null;
    }
    JsonNode pNode = node.get(property);
    if (pNode != null && pNode.isNull()) {
      return null;
    }
    return getString(property, node);
  }

  public static ByteBuffer getByteBufferOrNull(String property, JsonNode node) {
    if (!node.has(property) || node.get(property).isNull()) {
      return null;
    }

    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode.isTextual(), "Cannot parse byte buffer from non-text value: %s: %s", property, pNode);
    return ByteBuffer.wrap(
        BaseEncoding.base16().decode(pNode.textValue().toUpperCase(Locale.ROOT)));
  }

  /**
   * 读取必需的 String-&gt;String Map 属性。
   *
   * <p>逻辑：节点必须是对象类型，遍历其字段名，每个值按 String 读取，构建不可变 Map。
   *
   * @param property 属性名
   * @param node 父节点
   * @return 不可变的字符串 Map
   * @throws IllegalArgumentException 属性缺失或非对象类型
   */
  public static Map<String, String> getStringMap(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing map: %s", property);
    JsonNode pNode = node.get(property);
    Preconditions.checkArgument(
        pNode != null && !pNode.isNull() && pNode.isObject(),
        "Cannot parse string map from non-object value: %s: %s",
        property,
        pNode);

    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    Iterator<String> fields = pNode.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      builder.put(field, getString(field, pNode));
    }
    return builder.build();
  }

  public static String[] getStringArray(JsonNode node) {
    Preconditions.checkArgument(
        node != null && !node.isNull() && node.isArray(),
        "Cannot parse string array from non-array: %s",
        node);
    ArrayNode arrayNode = (ArrayNode) node;
    String[] arr = new String[arrayNode.size()];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = arrayNode.get(i).asText();
    }
    return arr;
  }

  public static List<String> getStringList(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing list: %s", property);
    return ImmutableList.<String>builder()
        .addAll(new JsonStringArrayIterator(property, node))
        .build();
  }

  public static Set<String> getStringSet(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing set: %s", property);

    return ImmutableSet.<String>builder()
        .addAll(new JsonStringArrayIterator(property, node))
        .build();
  }

  /**
   * 读取可选的 String List 属性，缺失或为 null 时返回 null。
   *
   * @param property 属性名
   * @param node 父节点
   * @return 不可变的 String 列表或 null
   */
  public static List<String> getStringListOrNull(String property, JsonNode node) {
    if (!node.has(property) || node.get(property).isNull()) {
      return null;
    }

    return ImmutableList.<String>builder()
        .addAll(new JsonStringArrayIterator(property, node))
        .build();
  }

  public static int[] getIntArrayOrNull(String property, JsonNode node) {
    if (!node.has(property) || node.get(property).isNull()) {
      return null;
    }

    return ArrayUtil.toIntArray(getIntegerList(property, node));
  }

  public static List<Integer> getIntegerList(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing list: %s", property);
    return ImmutableList.<Integer>builder()
        .addAll(new JsonIntegerArrayIterator(property, node))
        .build();
  }

  public static Set<Integer> getIntegerSetOrNull(String property, JsonNode node) {
    if (!node.has(property) || node.get(property).isNull()) {
      return null;
    }

    return getIntegerSet(property, node);
  }

  public static Set<Integer> getIntegerSet(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing set: %s", property);
    return ImmutableSet.<Integer>builder()
        .addAll(new JsonIntegerArrayIterator(property, node))
        .build();
  }

  public static List<Long> getLongList(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing list: %s", property);
    return ImmutableList.<Long>builder().addAll(new JsonLongArrayIterator(property, node)).build();
  }

  /**
   * 读取可选的 Long List 属性，缺失或为 null 时返回 null。
   *
   * @param property 属性名
   * @param node 父节点
   * @return 不可变的 Long 列表或 null
   */
  public static List<Long> getLongListOrNull(String property, JsonNode node) {
    if (!node.has(property) || node.get(property).isNull()) {
      return null;
    }

    return ImmutableList.<Long>builder().addAll(new JsonLongArrayIterator(property, node)).build();
  }

  /**
   * 读取可选的 Long Set 属性，缺失或为 null 时返回 null。
   *
   * @param property 属性名
   * @param node 父节点
   * @return 不可变的 Long 集合或 null
   */
  public static Set<Long> getLongSetOrNull(String property, JsonNode node) {
    if (!node.hasNonNull(property)) {
      return null;
    }

    return getLongSet(property, node);
  }

  /**
   * 读取必需的 Long Set 属性（数组节点），去重。
   *
   * @param property 属性名
   * @param node 父节点
   * @return 不可变的 Long 集合
   * @throws IllegalArgumentException 属性缺失
   */
  public static Set<Long> getLongSet(String property, JsonNode node) {
    Preconditions.checkArgument(node.has(property), "Cannot parse missing set: %s", property);
    return ImmutableSet.<Long>builder().addAll(new JsonLongArrayIterator(property, node)).build();
  }

  public static void writeIntegerFieldIf(
      boolean condition, String key, Integer value, JsonGenerator generator) throws IOException {
    if (condition) {
      generator.writeNumberField(key, value);
    }
  }

  public static void writeLongFieldIf(
      boolean condition, String key, Long value, JsonGenerator generator) throws IOException {
    if (condition) {
      generator.writeNumberField(key, value);
    }
  }

  abstract static class JsonArrayIterator<T> implements Iterator<T> {

    private final Iterator<JsonNode> elements;

    JsonArrayIterator(String property, JsonNode node) {
      JsonNode pNode = node.get(property);
      Preconditions.checkArgument(
          pNode != null && !pNode.isNull() && pNode.isArray(),
          "Cannot parse JSON array from non-array value: %s: %s",
          property,
          pNode);
      this.elements = pNode.elements();
    }

    @Override
    public boolean hasNext() {
      return elements.hasNext();
    }

    @Override
    public T next() {
      JsonNode element = elements.next();
      validate(element);
      return convert(element);
    }

    abstract T convert(JsonNode element);

    abstract void validate(JsonNode element);
  }

  static class JsonStringArrayIterator extends JsonArrayIterator<String> {
    private final String property;

    JsonStringArrayIterator(String property, JsonNode node) {
      super(property, node);
      this.property = property;
    }

    @Override
    String convert(JsonNode element) {
      return element.asText();
    }

    @Override
    void validate(JsonNode element) {
      Preconditions.checkArgument(
          element.isTextual(),
          "Cannot parse string from non-text value in %s: %s",
          property,
          element);
    }
  }

  static class JsonIntegerArrayIterator extends JsonArrayIterator<Integer> {
    private final String property;

    JsonIntegerArrayIterator(String property, JsonNode node) {
      super(property, node);
      this.property = property;
    }

    @Override
    Integer convert(JsonNode element) {
      return element.asInt();
    }

    @Override
    void validate(JsonNode element) {
      Preconditions.checkArgument(
          element.isInt(), "Cannot parse integer from non-int value in %s: %s", property, element);
    }
  }

  static class JsonLongArrayIterator extends JsonArrayIterator<Long> {
    private final String property;

    JsonLongArrayIterator(String property, JsonNode node) {
      super(property, node);
      this.property = property;
    }

    @Override
    Long convert(JsonNode element) {
      return element.asLong();
    }

    @Override
    void validate(JsonNode element) {
      Preconditions.checkArgument(
          element.isIntegralNumber() && element.canConvertToLong(),
          "Cannot parse long from non-long value in %s: %s",
          property,
          element);
    }
  }

  public static void writeIntegerArray(String property, Iterable<Integer> items, JsonGenerator gen)
      throws IOException {
    gen.writeArrayFieldStart(property);
    for (Integer item : items) {
      gen.writeNumber(item);
    }
    gen.writeEndArray();
  }

  public static void writeLongArray(String property, Iterable<Long> items, JsonGenerator gen)
      throws IOException {
    gen.writeArrayFieldStart(property);
    for (Long item : items) {
      gen.writeNumber(item);
    }
    gen.writeEndArray();
  }

  public static void writeStringArray(String property, Iterable<String> items, JsonGenerator gen)
      throws IOException {
    gen.writeArrayFieldStart(property);
    for (String item : items) {
      gen.writeString(item);
    }
    gen.writeEndArray();
  }

  public static void writeStringMap(String property, Map<String, String> map, JsonGenerator gen)
      throws IOException {
    gen.writeObjectFieldStart(property);
    for (Map.Entry<String, String> pair : map.entrySet()) {
      gen.writeStringField(pair.getKey(), pair.getValue());
    }
    gen.writeEndObject();
  }
}
