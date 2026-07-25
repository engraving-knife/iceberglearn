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
package org.apache.iceberg.aws.s3.signer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：S3 签名请求（{@link S3SignRequest}）的 JSON 序列化/反序列化工具。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：把 S3 签名请求对象与 JSON 互转，字段包括 region、method、uri、headers（多值）、 properties、body，供 REST Catalog
 * 与远程签名服务通信。
 *
 * <p>设计意图：工具类模式，私有构造器 + 静态方法；headers 采用“头名→值数组”结构以保留 同名多值语义；非必填字段（properties、body）按存在性写出，避免 JSON
 * 中出现 null。
 *
 * <p>上下游关系：被 {@link S3ObjectMapper} 注册的序列化器/反序列化器调用， 也供需要直接控制 JSON 生成的场景使用。
 */
public class S3SignRequestParser {

  private static final String REGION = "region";
  private static final String METHOD = "method";
  private static final String URI = "uri";
  private static final String HEADERS = "headers";
  private static final String PROPERTIES = "properties";
  private static final String BODY = "body";

  private S3SignRequestParser() {}

  /** 将签名请求序列化为紧凑 JSON 字符串。 */
  public static String toJson(S3SignRequest request) {
    return toJson(request, false);
  }

  /** 将签名请求序列化为 JSON 字符串，pretty 控制是否美化输出。 */
  public static String toJson(S3SignRequest request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将签名请求写入 JsonGenerator。
   *
   * <p>逻辑：写出 region/method/uri/headers，properties 非空时写出，body 非空时写出。
   *
   * @param request 签名请求
   * @param gen JSON 生成器
   * @throws IOException 写入异常
   */
  public static void toJson(S3SignRequest request, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != request, "Invalid s3 sign request: null");

    gen.writeStartObject();

    gen.writeStringField(REGION, request.region());
    gen.writeStringField(METHOD, request.method());
    gen.writeStringField(URI, request.uri().toString());
    headersToJson(HEADERS, request.headers(), gen);

    if (!request.properties().isEmpty()) {
      JsonUtil.writeStringMap(PROPERTIES, request.properties(), gen);
    }

    if (request.body() != null && !request.body().isEmpty()) {
      gen.writeStringField(BODY, request.body());
    }

    gen.writeEndObject();
  }

  /** 从 JSON 字符串解析出 S3SignRequest。 */
  public static S3SignRequest fromJson(String json) {
    return JsonUtil.parse(json, S3SignRequestParser::fromJson);
  }

  /**
   * 从 JsonNode 解析出 S3SignRequest。
   *
   * <p>逻辑：读取 region/method/uri/headers，可选读取 properties 与 body，构造 ImmutableS3SignRequest。
   *
   * @param json JSON 节点
   * @return 签名请求对象
   */
  public static S3SignRequest fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse s3 sign request from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse s3 sign request from non-object: %s", json);

    String region = JsonUtil.getString(REGION, json);
    String method = JsonUtil.getString(METHOD, json);
    java.net.URI uri = java.net.URI.create(JsonUtil.getString(URI, json));
    Map<String, List<String>> headers = headersFromJson(HEADERS, json);

    ImmutableS3SignRequest.Builder builder =
        ImmutableS3SignRequest.builder().region(region).method(method).uri(uri).headers(headers);

    if (json.has(PROPERTIES)) {
      builder.properties(JsonUtil.getStringMap(PROPERTIES, json));
    }

    if (json.has(BODY)) {
      builder.body(JsonUtil.getString(BODY, json));
    }

    return builder.build();
  }

  /**
   * 将多值 headers 写入 JSON：以对象形式，每个头名对应一个字符串数组。
   *
   * @param property JSON 字段名
   * @param headers 头名到值列表映射
   * @param gen JSON 生成器
   * @throws IOException 写入异常
   */
  static void headersToJson(String property, Map<String, List<String>> headers, JsonGenerator gen)
      throws IOException {
    gen.writeObjectFieldStart(property);
    for (Entry<String, List<String>> entry : headers.entrySet()) {
      gen.writeFieldName(entry.getKey());

      gen.writeStartArray();
      for (String val : entry.getValue()) {
        gen.writeString(val);
      }
      gen.writeEndArray();
    }
    gen.writeEndObject();
  }

  /**
   * 从 JSON 解析多值 headers：遍历子节点，每个头名对应一个字符串列表。
   *
   * @param property JSON 字段名
   * @param json JSON 节点
   * @return 头名到值列表映射
   */
  static Map<String, List<String>> headersFromJson(String property, JsonNode json) {
    Map<String, List<String>> headers = Maps.newHashMap();
    JsonNode headersNode = JsonUtil.get(property, json);
    headersNode
        .fields()
        .forEachRemaining(
            entry -> {
              String key = entry.getKey();
              List<String> values = Arrays.asList(JsonUtil.getStringArray(entry.getValue()));
              headers.put(key, values);
            });
    return headers;
  }
}
