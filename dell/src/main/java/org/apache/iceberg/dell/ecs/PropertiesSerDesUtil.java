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
package org.apache.iceberg.dell.ecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Map 属性与字节序列之间的序列化/反序列化工具，用于把 catalog/namespace/table 的属性 持久化到 ECS 对象中。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #toBytes(Map)}：将 {@code Map<String,String>} 序列化为 UTF-8 字节。
 *   <li>{@link #read(byte[], String)}：按版本号反序列化字节为不可变 Map。
 *   <li>对外暴露当前实现版本号 {@link #currentVersion()}，供读写双方对齐格式。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>复用 JDK {@link Properties} 格式：以成熟稳定的 properties 文本格式存储， 保证可读性与跨版本兼容；UTF-8 编码避免中文等字符乱码。
 *   <li>版本化：序列化结果以 user metadata 携带版本号，反序列化时校验版本匹配， 为未来格式演进预留升级路径（当前版本 "0"）。
 *   <li>不可变结果：{@link #read} 返回不可修改的 Map，防止调用方误改缓存。
 * </ul>
 *
 * <p>上下游关系：被 {@link EcsCatalog} 在读写 properties 对象时调用； 版本号通过 ECS 对象的 user metadata（{@code
 * iceberg_properties_version}）传递。
 */
public class PropertiesSerDesUtil {

  private PropertiesSerDesUtil() {}

  /** 当前序列化实现版本。 */
  private static final String CURRENT_VERSION = "0";

  private static final Logger LOG = LoggerFactory.getLogger(PropertiesSerDesUtil.class);

  /**
   * 按版本号从字节内容反序列化为属性 Map。
   *
   * <p>逻辑：先校验版本号与当前实现一致，再用 UTF-8 Reader 加载 JDK Properties， 最后转为不可变 Map 返回。IO 异常包装为 {@link
   * UncheckedIOException}。
   *
   * @param content 对象字节内容
   * @param version 写入时记录的版本号
   * @return 不可变的属性 Map
   * @throws IllegalArgumentException 当版本号不匹配时抛出
   * @throws UncheckedIOException 当读取发生 IO 异常时抛出
   */
  public static Map<String, String> read(byte[] content, String version) {
    Preconditions.checkArgument(
        CURRENT_VERSION.equals(version), "Properties version is not match", version);
    Properties jdkProperties = new Properties();
    try (Reader reader =
        new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
      jdkProperties.load(reader);
    } catch (IOException e) {
      LOG.error("Fail to read properties", e);
      throw new UncheckedIOException(e);
    }

    Set<String> propertyNames = jdkProperties.stringPropertyNames();
    Map<String, String> properties = Maps.newHashMap();
    for (String name : propertyNames) {
      properties.put(name, jdkProperties.getProperty(name));
    }

    return Collections.unmodifiableMap(properties);
  }

  /**
   * 将属性 Map 序列化为 UTF-8 字节，写入版本号为 {@link #currentVersion()}。
   *
   * <p>逻辑：将 Map 项填入 JDK Properties，再用 UTF-8 Writer 调用 {@link Properties#store} 写出为字节。IO 异常包装为
   * {@link UncheckedIOException}。
   *
   * @param value 待序列化的属性
   * @return 序列化后的字节数组
   * @throws UncheckedIOException 当写入发生 IO 异常时抛出
   */
  public static byte[] toBytes(Map<String, String> value) {
    Properties jdkProperties = new Properties();
    for (Map.Entry<String, String> entry : value.entrySet()) {
      jdkProperties.setProperty(entry.getKey(), entry.getValue());
    }

    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
      jdkProperties.store(writer, null);
      return output.toByteArray();
    } catch (IOException e) {
      LOG.error("Fail to store properties {} to file", value, e);
      throw new UncheckedIOException(e);
    }
  }

  /** 返回当前序列化实现版本号。 */
  public static String currentVersion() {
    return CURRENT_VERSION;
  }
}
