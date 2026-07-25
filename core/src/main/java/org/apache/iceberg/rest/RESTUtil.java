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
package org.apache.iceberg.rest;

import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：REST Catalog 客户端使用的工具类，提供 URL 编解码、命名空间编解码、map 合并等通用方法。
 *
 * <p>所属模块：iceberg-core（REST Catalog 工具层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供字符串与 form 数据的 URL 编解码。
 *   <li>提供命名空间（{@link Namespace}）在 URL 路径中的编码与解码，使用百分号转义分隔符。
 *   <li>提供 map 合并、前缀提取、尾部斜杠去除等辅助方法。
 * </ul>
 *
 * <p>设计意图：命名空间层级在 URL 中传输时需要特殊处理，本类使用 {@code %1F}（转义后的单元分隔符） 作为层级分隔符，避免与路径分隔符冲突。所有方法为静态方法，工具类不可实例化。
 *
 * <p>上下游关系：被 {@link RESTSessionCatalog}、{@link ResourcePaths}、{@link RESTClient} 等广泛使用。
 */
public class RESTUtil {
  private static final char NAMESPACE_SEPARATOR = '\u001f';
  public static final Joiner NAMESPACE_JOINER = Joiner.on(NAMESPACE_SEPARATOR);
  public static final Splitter NAMESPACE_SPLITTER = Splitter.on(NAMESPACE_SEPARATOR);
  private static final String NAMESPACE_ESCAPED_SEPARATOR = "%1F";
  private static final Joiner NAMESPACE_ESCAPED_JOINER = Joiner.on(NAMESPACE_ESCAPED_SEPARATOR);
  private static final Splitter NAMESPACE_ESCAPED_SPLITTER =
      Splitter.on(NAMESPACE_ESCAPED_SEPARATOR);

  /** 私有构造函数，禁止实例化工具类。 */
  private RESTUtil() {}

  /**
   * 去除路径末尾的所有斜杠。输入为 null 时返回 null。
   *
   * @param path 待处理的路径
   * @return 去除尾部斜杠后的路径
   */
  public static String stripTrailingSlash(String path) {
    if (path == null) {
      return null;
    }

    String result = path;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }

  /**
   * 将 updates 合并到 target map 中，updates 中的键覆盖 target 中的同名键。
   *
   * @param target 基础 map
   * @param updates 待合并的更新 map
   * @return 由 target 与 updates 合并而成的不可变 map
   */
  public static Map<String, String> merge(Map<String, String> target, Map<String, String> updates) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    target.forEach(
        (key, value) -> {
          if (!updates.containsKey(key)) {
            builder.put(key, value);
          }
        });

    updates.forEach(builder::put);

    return builder.build();
  }

  /**
   * 从 map 中提取键以指定前缀开头的条目，并在结果中去掉前缀。
   *
   * <p>键不以该前缀开头的条目不会返回。
   *
   * <p>典型用途：从 Spark 配置中提取以 {@code spark.sql.catalog.my_catalog.rest.} 为前缀的 REST Catalog 专属属性。
   *
   * @param properties 原始配置 map
   * @param prefix 键前缀
   * @return 去除前缀后的子 map
   */
  public static Map<String, String> extractPrefixMap(
      Map<String, String> properties, String prefix) {
    Preconditions.checkNotNull(properties, "Invalid properties map: null");
    Map<String, String> result = Maps.newHashMap();
    properties.forEach(
        (key, value) -> {
          if (key != null && key.startsWith(prefix)) {
            result.put(key.substring(prefix.length()), value);
          }
        });

    return result;
  }

  private static final Joiner.MapJoiner FORM_JOINER = Joiner.on("&").withKeyValueSeparator("=");
  private static final Splitter.MapSplitter FORM_SPLITTER =
      Splitter.on("&").withKeyValueSeparator("=");

  /**
   * 将 form 数据 map 编码为 application/x-www-form-urlencoded 格式字符串。
   *
   * <p>键值对以 &amp; 分隔，键与值以 = 分隔，并对键值进行 URL 编码。
   *
   * @param formData form 数据 map
   * @return 编码后的 form 字符串
   */
  public static String encodeFormData(Map<?, ?> formData) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    formData.forEach(
        (key, value) ->
            builder.put(encodeString(String.valueOf(key)), encodeString(String.valueOf(value))));
    return FORM_JOINER.join(builder.build());
  }

  /**
   * 将 application/x-www-form-urlencoded 格式字符串解码为 form 数据 map。
   *
   * <p>键值对以 &amp; 分隔，键与值以 = 分隔，并对键值进行 URL 解码。
   *
   * @param formString form 编码字符串
   * @return 解码后的键值对 map
   */
  public static Map<String, String> decodeFormData(String formString) {
    return FORM_SPLITTER.split(formString).entrySet().stream()
        .collect(
            ImmutableMap.toImmutableMap(
                e -> RESTUtil.decodeString(e.getKey()), e -> RESTUtil.decodeString(e.getValue())));
  }

  /**
   * 使用 URL 编码对字符串进行编码（UTF-8）。
   *
   * <p>解码请使用 {@link #decodeString(String)}。
   *
   * @param toEncode 待编码字符串
   * @return UTF-8 编码后的字符串，可用作 URL 参数
   */
  public static String encodeString(String toEncode) {
    Preconditions.checkArgument(toEncode != null, "Invalid string to encode: null");
    try {
      return URLEncoder.encode(toEncode, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new UncheckedIOException(
          String.format("Failed to URL encode '%s': UTF-8 encoding is not supported", toEncode), e);
    }
  }

  /**
   * 对 URL 编码的字符串进行解码（UTF-8）。
   *
   * <p>编码请使用 {@link #encodeString(String)}。
   *
   * @param encoded 待解码字符串
   * @return 解码后的字符串
   */
  public static String decodeString(String encoded) {
    Preconditions.checkArgument(encoded != null, "Invalid string to decode: null");
    try {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new UncheckedIOException(
          String.format("Failed to URL decode '%s': UTF-8 encoding is not supported", encoded), e);
    }
  }

  /**
   * 将命名空间编码为可用于 URL 路径/查询参数的字符串表示。
   *
   * <p>当命名空间作为路径变量或查询参数时必须调用此方法，按规范对层级进行 URL 编码， 并以 {@code %1F} 作为层级分隔符连接。
   *
   * <p>解析请使用 {@link #decodeNamespace}。
   *
   * @param ns 待编码的命名空间
   * @return UTF-8 编码后的命名空间字符串，可用作 URL 参数
   */
  public static String encodeNamespace(Namespace ns) {
    Preconditions.checkArgument(ns != null, "Invalid namespace: null");
    String[] levels = ns.levels();
    String[] encodedLevels = new String[levels.length];

    for (int i = 0; i < levels.length; i++) {
      encodedLevels[i] = encodeString(levels[i]);
    }

    return NAMESPACE_ESCAPED_JOINER.join(encodedLevels);
  }

  /**
   * 将 URL 参数中的命名空间字符串表示解码为 {@link Namespace} 对象。
   *
   * <p>编码请使用 {@link #encodeNamespace}。
   *
   * @param encodedNs 待解码的命名空间字符串
   * @return 解码后的命名空间
   */
  public static Namespace decodeNamespace(String encodedNs) {
    Preconditions.checkArgument(encodedNs != null, "Invalid namespace: null");
    String[] levels = Iterables.toArray(NAMESPACE_ESCAPED_SPLITTER.split(encodedNs), String.class);

    // Decode levels in place
    for (int i = 0; i < levels.length; i++) {
      levels[i] = decodeString(levels[i]);
    }

    return Namespace.of(levels);
  }
}
