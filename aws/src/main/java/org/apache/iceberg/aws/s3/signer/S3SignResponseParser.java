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
import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：S3 签名响应（{@link S3SignResponse}）的 JSON 序列化/反序列化工具。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：把签名响应对象与 JSON 互转，字段包括 uri 与 headers（多值）， 供 REST Catalog 解析远程签名服务返回结果。
 *
 * <p>设计意图：与 {@link S3SignRequestParser} 对称设计，工具类模式； headers 序列化复用 {@link
 * S3SignRequestParser#headersToJson} 以保证一致性。
 *
 * <p>上下游关系：被 {@link S3ObjectMapper} 注册的序列化器/反序列化器调用。
 */
public class S3SignResponseParser {

  private static final String URI = "uri";
  private static final String HEADERS = "headers";

  private S3SignResponseParser() {}

  /** 将签名响应序列化为紧凑 JSON 字符串。 */
  public static String toJson(S3SignResponse request) {
    return toJson(request, false);
  }

  /** 将签名响应序列化为 JSON 字符串，pretty 控制是否美化输出。 */
  public static String toJson(S3SignResponse request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将签名响应写入 JsonGenerator：写出 uri 与 headers。
   *
   * @param response 签名响应
   * @param gen JSON 生成器
   * @throws IOException 写入异常
   */
  public static void toJson(S3SignResponse response, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != response, "Invalid s3 sign response: null");

    gen.writeStartObject();

    gen.writeStringField(URI, response.uri().toString());
    S3SignRequestParser.headersToJson(HEADERS, response.headers(), gen);

    gen.writeEndObject();
  }

  /** 从 JSON 字符串解析出 S3SignResponse。 */
  public static S3SignResponse fromJson(String json) {
    return JsonUtil.parse(json, S3SignResponseParser::fromJson);
  }

  /**
   * 从 JsonNode 解析出 S3SignResponse：读取 uri 与 headers，构造 ImmutableS3SignResponse。
   *
   * @param json JSON 节点
   * @return 签名响应对象
   */
  public static S3SignResponse fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse s3 sign response from null object");
    Preconditions.checkArgument(
        json.isObject(), "Cannot parse s3 sign response from non-object: %s", json);

    java.net.URI uri = java.net.URI.create(JsonUtil.getString(URI, json));
    Map<String, List<String>> headers = S3SignRequestParser.headersFromJson(HEADERS, json);

    return ImmutableS3SignResponse.builder().uri(uri).headers(headers).build();
  }
}
