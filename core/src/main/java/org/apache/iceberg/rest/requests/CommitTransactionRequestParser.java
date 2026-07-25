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
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.JsonUtil;

/**
 * 所属模块：iceberg-core；REST Catalog 序列化层。
 *
 * <p>职责：负责 {@link CommitTransactionRequest} 与 JSON 之间的序列化/反序列化。具体包括：
 *
 * <ul>
 *   <li>将 {@link CommitTransactionRequest} 写出为 JSON 字符串或写入 {@link JsonGenerator}
 *   <li>从 JSON 字符串或 {@link JsonNode} 解析为 {@link CommitTransactionRequest}
 * </ul>
 *
 * <p>设计意图：手写 JSON 读写以精确控制字段名（如 {@code table-changes}）与嵌套结构，避免 依赖 ObjectMapper 的注解配置；委托 {@link
 * UpdateTableRequestParser} 处理单个表变更的序列化， 实现层间的复用。
 *
 * <p>上下游关系：由 {@code RESTSessionCatalog} 或 {@link org.apache.iceberg.rest.HTTPClient} 在 事务提交时调用；依赖
 * {@link JsonUtil} 与 {@link UpdateTableRequestParser}。
 */
public class CommitTransactionRequestParser {
  // JSON 字段名：表变更列表
  private static final String TABLE_CHANGES = "table-changes";

  /** 工具类，禁止实例化。 */
  private CommitTransactionRequestParser() {}

  /**
   * 将请求序列化为紧凑格式的 JSON 字符串。
   *
   * @param request 事务提交请求
   * @return JSON 字符串
   */
  public static String toJson(CommitTransactionRequest request) {
    return toJson(request, false);
  }

  /**
   * 将请求序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param request 事务提交请求
   * @param pretty 是否美化输出
   * @return JSON 字符串
   */
  public static String toJson(CommitTransactionRequest request, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(request, gen), pretty);
  }

  /**
   * 将请求写入 {@link JsonGenerator}。
   *
   * <p>逻辑：校验 request 非空；写入对象起始、{@code table-changes} 字段及数组起始； 遍历每个表变更调用 {@link
   * UpdateTableRequestParser#toJson} 写入；最后闭合数组与对象。
   *
   * @param request 事务提交请求
   * @param gen JSON 生成器
   * @throws IOException 写入过程中发生 IO 异常
   */
  public static void toJson(CommitTransactionRequest request, JsonGenerator gen)
      throws IOException {
    Preconditions.checkArgument(null != request, "Invalid commit transaction request: null");

    gen.writeStartObject();
    gen.writeFieldName(TABLE_CHANGES);
    gen.writeStartArray();

    for (UpdateTableRequest tableChange : request.tableChanges()) {
      UpdateTableRequestParser.toJson(tableChange, gen);
    }

    gen.writeEndArray();
    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析为 {@link CommitTransactionRequest}。
   *
   * @param json JSON 字符串
   * @return 解析得到的事务提交请求
   */
  public static CommitTransactionRequest fromJson(String json) {
    return JsonUtil.parse(json, CommitTransactionRequestParser::fromJson);
  }

  /**
   * 从 {@link JsonNode} 解析为 {@link CommitTransactionRequest}。
   *
   * <p>逻辑：校验 json 非空；读取 {@code table-changes} 字段并校验为数组；遍历每个元素 调用 {@link
   * UpdateTableRequestParser#fromJson(JsonNode)} 解析为 {@link UpdateTableRequest}， 最终构造 {@link
   * CommitTransactionRequest}（构造时会触发 {@link CommitTransactionRequest#validate()}）。
   *
   * @param json JSON 节点
   * @return 解析得到的事务提交请求
   */
  public static CommitTransactionRequest fromJson(JsonNode json) {
    Preconditions.checkArgument(
        null != json, "Cannot parse commit transaction request from null object");

    List<UpdateTableRequest> tableChanges = Lists.newArrayList();
    JsonNode changes = JsonUtil.get(TABLE_CHANGES, json);

    Preconditions.checkArgument(
        changes.isArray(), "Cannot parse commit transaction request from non-array: %s", changes);

    for (JsonNode node : changes) {
      tableChanges.add(UpdateTableRequestParser.fromJson(node));
    }

    return new CommitTransactionRequest(tableChanges);
  }
}
