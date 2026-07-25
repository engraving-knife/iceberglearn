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
import java.util.List;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.MetadataUpdateParser;
import org.apache.iceberg.UpdateRequirement;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.catalog.TableIdentifierParser;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link UpdateTableRequest} 的 JSON 序列化/反序列化解析器。
 *
 * <p>所属模块：iceberg-core，REST 请求解析层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link UpdateTableRequest} 对象序列化为 REST 协议规定的 JSON 字符串；
 *   <li>将 JSON 字符串或 {@link JsonNode} 反序列化为 {@link UpdateTableRequest} 对象；
 *   <li>统一管理字段名常量（{@code identifier}、{@code requirements}、{@code updates}）， 保证读写一致；
 *   <li>委托 {@link TableIdentifierParser}、{@link org.apache.iceberg.UpdateRequirementParser} 与
 *       {@link MetadataUpdateParser} 完成子结构的解析。
 * </ul>
 *
 * <p>设计意图：采用工具类 + 静态方法模式，构造器私有化禁止实例化；将子结构解析职责 委托给各自的解析器，本类只负责外层结构与字段组装，避免逻辑耦合。
 *
 * <p>上下游关系：被 REST Catalog 客户端与服务端在表更新接口收发请求时调用； 底层依赖 {@link JsonUtil} 完成实际的 JSON 读写。
 */
public class UpdateTableRequestParser {

  private static final String IDENTIFIER = "identifier";
  private static final String REQUIREMENTS = "requirements";
  private static final String UPDATES = "updates";

  /** 私有构造器，禁止实例化该工具类。 */
  private UpdateTableRequestParser() {}

  /**
   * 将请求序列化为紧凑格式 JSON 字符串。
   *
   * @param request 待序列化的表更新请求
   * @return JSON 字符串
   */
  public static String toJson(UpdateTableRequest request) {
    return toJson(request, false);
  }

  /**
   * 将请求序列化为 JSON 字符串，可指定是否美化输出。
   *
   * @param request 待序列化的表更新请求
   * @param pretty 是否以缩进格式输出
   * @return JSON 字符串
   */
  public static String toJson(UpdateTableRequest request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将请求直接写入给定的 {@link JsonGenerator}，用于流式输出场景。
   *
   * <p>逻辑：先断言请求非 null，写起始对象标记；若 identifier 非 null 则写 {@code identifier} 字段（委托 {@link
   * TableIdentifierParser}）；随后写 {@code requirements} 数组（逐项委托 {@link
   * org.apache.iceberg.UpdateRequirementParser}）；再写 {@code updates} 数组 （逐项委托 {@link
   * MetadataUpdateParser}）；最后写结束对象标记。
   *
   * @param request 待序列化的请求，不可为 null
   * @param gen Jackson 生成器
   * @throws IOException 写入失败时抛出
   */
  public static void toJson(UpdateTableRequest request, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != request, "Invalid update table request: null");

    gen.writeStartObject();

    if (null != request.identifier()) {
      gen.writeFieldName(IDENTIFIER);
      TableIdentifierParser.toJson(request.identifier(), gen);
    }

    gen.writeArrayFieldStart(REQUIREMENTS);
    for (UpdateRequirement updateRequirement : request.requirements()) {
      org.apache.iceberg.UpdateRequirementParser.toJson(updateRequirement, gen);
    }
    gen.writeEndArray();

    gen.writeArrayFieldStart(UPDATES);
    for (MetadataUpdate metadataUpdate : request.updates()) {
      MetadataUpdateParser.toJson(metadataUpdate, gen);
    }
    gen.writeEndArray();

    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串反序列化为 {@link UpdateTableRequest}。
   *
   * @param json JSON 字符串
   * @return 反序列化得到的请求对象
   */
  public static UpdateTableRequest fromJson(String json) {
    return JsonUtil.parse(json, UpdateTableRequestParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 反序列化为 {@link UpdateTableRequest}。
   *
   * <p>逻辑：先断言输入非 null；分别处理 {@code identifier}、{@code requirements}、 {@code updates} 三个可选字段：若字段存在且非
   * null，则校验其类型（数组字段须为数组） 并委托对应解析器逐项解析；最后通过 {@link UpdateTableRequest#create} 组装实例。
   *
   * @param json JSON 节点，不可为 null
   * @return 反序列化得到的请求对象
   */
  public static UpdateTableRequest fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse update table request from null object");

    TableIdentifier identifier = null;
    List<UpdateRequirement> requirements = Lists.newArrayList();
    List<MetadataUpdate> updates = Lists.newArrayList();

    if (json.hasNonNull(IDENTIFIER)) {
      identifier = TableIdentifierParser.fromJson(JsonUtil.get(IDENTIFIER, json));
    }

    if (json.hasNonNull(REQUIREMENTS)) {
      JsonNode requirementsNode = JsonUtil.get(REQUIREMENTS, json);
      Preconditions.checkArgument(
          requirementsNode.isArray(),
          "Cannot parse requirements from non-array: %s",
          requirementsNode);
      requirementsNode.forEach(
          req -> requirements.add(org.apache.iceberg.UpdateRequirementParser.fromJson(req)));
    }

    if (json.hasNonNull(UPDATES)) {
      JsonNode updatesNode = JsonUtil.get(UPDATES, json);
      Preconditions.checkArgument(
          updatesNode.isArray(), "Cannot parse metadata updates from non-array: %s", updatesNode);

      updatesNode.forEach(update -> updates.add(MetadataUpdateParser.fromJson(update)));
    }

    return UpdateTableRequest.create(identifier, requirements, updates);
  }
}
