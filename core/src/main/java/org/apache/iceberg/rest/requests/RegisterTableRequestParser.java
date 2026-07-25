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
package org.apache.iceberg.rest.requests;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link RegisterTableRequest} 的 JSON 序列化/反序列化解析器。
 *
 * <p>所属模块：iceberg-core，REST 请求解析层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link RegisterTableRequest} 对象序列化为 REST 协议规定的 JSON 字符串；
 *   <li>将 JSON 字符串或 {@link JsonNode} 反序列化为 {@link RegisterTableRequest} 对象；
 *   <li>统一管理字段名常量（{@code name}、{@code metadata-location}），保证读写一致。
 * </ul>
 *
 * <p>设计意图：采用工具类 + 静态方法模式，构造器私有化禁止实例化；与 {@link ImmutableRegisterTableRequest} 的 Builder
 * 协作完成对象构建，避免在请求模型中 引入 Jackson 注解，保持请求对象的纯粹性。
 *
 * <p>上下游关系：被 REST Catalog 客户端与服务端在 register 接口收发请求时调用； 底层依赖 {@link JsonUtil} 完成实际的 JSON 读写。
 */
public class RegisterTableRequestParser {

  private static final String NAME = "name";
  private static final String METADATA_LOCATION = "metadata-location";

  /** 私有构造器，禁止实例化该工具类。 */
  private RegisterTableRequestParser() {}

  /**
   * 将请求序列化为紧凑格式 JSON 字符串。
   *
   * @param request 待序列化的注册表请求
   * @return JSON 字符串
   */
  public static String toJson(RegisterTableRequest request) {
    return toJson(request, false);
  }

  /**
   * 将请求序列化为 JSON 字符串，可指定是否美化输出。
   *
   * @param request 待序列化的注册表请求
   * @param pretty 是否以缩进格式输出
   * @return JSON 字符串
   */
  public static String toJson(RegisterTableRequest request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将请求直接写入给定的 {@link JsonGenerator}，用于流式输出场景。
   *
   * <p>逻辑：先断言请求非 null，随后写起始对象标记，依次写入 {@code name} 与 {@code metadata-location} 两个字段，最后写结束对象标记。
   *
   * @param request 待序列化的请求，不可为 null
   * @param gen Jackson 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJson(RegisterTableRequest request, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != request, "Invalid register table request: null");

    gen.writeStartObject();

    gen.writeStringField(NAME, request.name());
    gen.writeStringField(METADATA_LOCATION, request.metadataLocation());

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串反序列化为 {@link RegisterTableRequest}。
   *
   * @param json JSON 字符串
   * @return 反序列化得到的请求对象
   */
  public static RegisterTableRequest fromJson(String json) {
    return JsonUtil.parse(json, RegisterTableRequestParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 反序列化为 {@link RegisterTableRequest}。
   *
   * <p>逻辑：先断言输入非 null；通过 {@link JsonUtil#getString(String, JsonNode)} 读取 必填的 {@code name} 与 {@code
   * metadata-location} 字段；最后通过 {@link ImmutableRegisterTableRequest} 的 Builder 构建不可变实例。
   *
   * @param json JSON 节点，不可为 null
   * @return 反序列化得到的请求对象
   */
  public static RegisterTableRequest fromJson(JsonNode json) {
    Preconditions.checkArgument(
        null != json, "Cannot parse register table request from null object");

    String name = JsonUtil.getString(NAME, json);
    String metadataLocation = JsonUtil.getString(METADATA_LOCATION, json);

    return ImmutableRegisterTableRequest.builder()
        .name(name)
        .metadataLocation(metadataLocation)
        .build();
  }
}
