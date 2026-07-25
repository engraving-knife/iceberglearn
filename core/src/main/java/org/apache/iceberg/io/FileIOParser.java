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
package org.apache.iceberg.io;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：FileIO 的 JSON 序列化/反序列化工具。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：将 {@link FileIO} 实例与其配置属性序列化为 JSON 字符串，或将 JSON 反序列化为 新的 FileIO 实例。用于在分布式执行环境中传递 FileIO 配置（如
 * Spark 任务序列化）。
 *
 * <p>设计意图：FileIO 实现类名作为 io-impl 字段、配置作为 properties map，反序列化时通过 {@link CatalogUtil#loadFileIO}
 * 反射加载实现类。这样无需将具体实现类硬编码为可序列化， 而是通过"类名 + 配置"在中立 JSON 中传递，支持跨进程/跨引擎传输。
 *
 * <p>上下游关系：被 Spark/Flink 等引擎集成模块用于在 driver 与 executor 间传递 FileIO； 依赖 CatalogUtil 进行反射加载。
 */
public class FileIOParser {
  private FileIOParser() {}

  private static final String FILE_IO_IMPL = "io-impl";
  private static final String PROPERTIES = "properties";

  /** 等价于 {@link #toJson(FileIO, boolean)} 的 pretty=false 版本。 */
  public static String toJson(FileIO io) {
    return toJson(io, false);
  }

  /**
   * 将 FileIO 序列化为 JSON 字符串。
   *
   * @param io 待序列化的 FileIO
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(FileIO io, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(io, gen), pretty);
  }

  /**
   * 将 FileIO 写入 JsonGenerator。
   *
   * <p>逻辑：取实现类全限定名作为 io-impl；调用 io.properties() 取配置 map；若实现不支持 properties()（抛
   * UnsupportedOperationException）则包装为 IllegalArgumentException； 写入 io-impl 与 properties 两个字段。
   *
   * @param io FileIO 实例
   * @param generator Jackson JsonGenerator
   * @throws IOException 写入过程发生 IO 错误
   */
  private static void toJson(FileIO io, JsonGenerator generator) throws IOException {
    String impl = io.getClass().getName();
    Map<String, String> properties;
    try {
      properties = io.properties();
    } catch (UnsupportedOperationException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot serialize FileIO: %s does not expose configuration properties", impl));
    }

    Preconditions.checkArgument(
        properties != null,
        "Cannot serialize FileIO: invalid configuration properties (null)",
        impl);

    generator.writeStartObject();

    generator.writeStringField(FILE_IO_IMPL, impl);
    JsonUtil.writeStringMap(PROPERTIES, properties, generator);

    generator.writeEndObject();
  }

  /** 等价于 {@link #fromJson(String, Object)} 的 conf=null 版本。 */
  public static FileIO fromJson(String json) {
    return fromJson(json, null);
  }

  /**
   * 从 JSON 字符串反序列化为 FileIO 实例。
   *
   * @param json JSON 字符串
   * @param conf 引擎特定的配置对象（如 Hadoop Configuration），传给 CatalogUtil.loadFileIO
   * @return 加载并初始化后的 FileIO 实例
   */
  public static FileIO fromJson(String json, Object conf) {
    return JsonUtil.parse(json, node -> fromJson(node, conf));
  }

  /**
   * 从 JsonNode 解析并加载 FileIO。
   *
   * <p>逻辑：校验 JSON 为对象；取出 io-impl 与 properties；委托 {@link CatalogUtil#loadFileIO} 反射加载实现类并
   * initialize。
   *
   * @param json JSON 节点
   * @param conf 引擎配置对象
   * @return 加载后的 FileIO 实例
   */
  private static FileIO fromJson(JsonNode json, Object conf) {
    Preconditions.checkArgument(json.isObject(), "Cannot parse FileIO from non-object: %s", json);
    String impl = JsonUtil.getString(FILE_IO_IMPL, json);
    Map<String, String> properties = JsonUtil.getStringMap(PROPERTIES, json);
    return CatalogUtil.loadFileIO(impl, properties, conf);
  }
}
