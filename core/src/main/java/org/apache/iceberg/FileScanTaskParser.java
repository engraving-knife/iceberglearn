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
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionParser;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.ResidualEvaluator;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link FileScanTask} 的 JSON 序列化/反序列化工具。
 *
 * <p>所属模块：iceberg-core（扫描任务序列化层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 {@link FileScanTask} 序列化为 JSON 字符串，便于跨进程传输（如 Spark 任务下发）。
 *   <li>从 JSON 反序列化重建 {@link FileScanTask}，包括 schema、分区 spec、数据文件、 删除文件、切分区间、残余表达式等所有字段。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 JSON 格式以便跨语言/跨进程兼容，且便于调试查看。
 *   <li>反序列化时通过 caseSensitive 参数正确重建 {@link ResidualEvaluator}，确保读取侧 过滤行为与计划侧一致。
 *   <li>schema 与 spec 同时保存字符串与解析对象，便于在序列化与重建时复用。
 * </ul>
 *
 * <p>上下游关系：依赖 {@link SchemaParser}、{@link PartitionSpecParser}、 {@link ContentFileParser}、{@link
 * ExpressionParser}；被分布式执行框架用于任务序列化。
 */
public class FileScanTaskParser {
  private static final String SCHEMA = "schema";
  private static final String SPEC = "spec";
  private static final String DATA_FILE = "data-file";
  private static final String START = "start";
  private static final String LENGTH = "length";
  private static final String DELETE_FILES = "delete-files";
  private static final String RESIDUAL = "residual-filter";

  private FileScanTaskParser() {}

  /**
   * 将文件扫描任务序列化为 JSON 字符串（单行紧凑格式）。
   *
   * @param fileScanTask 待序列化的文件扫描任务
   * @return JSON 字符串
   */
  public static String toJson(FileScanTask fileScanTask) {
    return JsonUtil.generate(
        generator -> FileScanTaskParser.toJson(fileScanTask, generator), false);
  }

  /**
   * 将文件扫描任务写入给定的 JSON 生成器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 fileScanTask 与 generator 非空。
   *   <li>写入 schema（委托 {@link SchemaParser}）与 spec（委托 {@link PartitionSpecParser}）。
   *   <li>若数据文件非空，写入 data-file 字段（委托 {@link ContentFileParser}）。
   *   <li>写入切分起始位置 start 与长度 length。
   *   <li>若删除文件列表非空，写入 delete-files 数组。
   *   <li>若残余表达式非空，写入 residual-filter 字段（委托 {@link ExpressionParser}）。
   * </ol>
   *
   * @param fileScanTask 待序列化的文件扫描任务
   * @param generator JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  private static void toJson(FileScanTask fileScanTask, JsonGenerator generator)
      throws IOException {
    Preconditions.checkArgument(fileScanTask != null, "Invalid file scan task: null");
    Preconditions.checkArgument(generator != null, "Invalid JSON generator: null");
    generator.writeStartObject();

    generator.writeFieldName(SCHEMA);
    SchemaParser.toJson(fileScanTask.schema(), generator);

    generator.writeFieldName(SPEC);
    PartitionSpec spec = fileScanTask.spec();
    PartitionSpecParser.toJson(spec, generator);

    if (fileScanTask.file() != null) {
      generator.writeFieldName(DATA_FILE);
      ContentFileParser.toJson(fileScanTask.file(), spec, generator);
    }

    generator.writeNumberField(START, fileScanTask.start());
    generator.writeNumberField(LENGTH, fileScanTask.length());

    if (fileScanTask.deletes() != null) {
      generator.writeArrayFieldStart(DELETE_FILES);
      for (DeleteFile deleteFile : fileScanTask.deletes()) {
        ContentFileParser.toJson(deleteFile, spec, generator);
      }
      generator.writeEndArray();
    }

    if (fileScanTask.residual() != null) {
      generator.writeFieldName(RESIDUAL);
      ExpressionParser.toJson(fileScanTask.residual(), generator);
    }

    generator.writeEndObject();
  }

  /**
   * 从 JSON 字符串反序列化文件扫描任务。
   *
   * @param json JSON 字符串
   * @param caseSensitive 反序列化残余表达式时是否区分字段名大小写
   * @return 重建的 {@link FileScanTask}
   */
  public static FileScanTask fromJson(String json, boolean caseSensitive) {
    Preconditions.checkArgument(json != null, "Invalid JSON string for file scan task: null");
    return JsonUtil.parse(json, node -> FileScanTaskParser.fromJson(node, caseSensitive));
  }

  /**
   * 从 JSON 节点反序列化文件扫描任务（核心实现）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 jsonNode 非空且为对象类型。
   *   <li>解析 schema 与 spec，并预生成其字符串形式以便构造任务。
   *   <li>解析 data-file（若存在）为 {@link DataFile}。
   *   <li>读取 start 与 length。
   *   <li>解析 delete-files 数组（若存在）为 {@link DeleteFile} 数组。
   *   <li>解析 residual-filter（若存在）为 {@link Expression}，否则使用 alwaysTrue。
   *   <li>基于以上字段构造 {@link ResidualEvaluator} 与 {@link BaseFileScanTask}， 再包装为 {@link
   *       BaseFileScanTask.SplitScanTask} 返回。
   * </ol>
   *
   * @param jsonNode JSON 节点
   * @param caseSensitive 是否区分字段名大小写
   * @return 重建的 {@link FileScanTask}
   */
  private static FileScanTask fromJson(JsonNode jsonNode, boolean caseSensitive) {
    Preconditions.checkArgument(jsonNode != null, "Invalid JSON node for file scan task: null");
    Preconditions.checkArgument(
        jsonNode.isObject(), "Invalid JSON node for file scan task: non-object (%s)", jsonNode);

    Schema schema = SchemaParser.fromJson(JsonUtil.get(SCHEMA, jsonNode));
    String schemaString = SchemaParser.toJson(schema);

    PartitionSpec spec = PartitionSpecParser.fromJson(schema, JsonUtil.get(SPEC, jsonNode));
    String specString = PartitionSpecParser.toJson(spec);

    DataFile dataFile = null;
    if (jsonNode.has(DATA_FILE)) {
      dataFile = (DataFile) ContentFileParser.fromJson(jsonNode.get(DATA_FILE), spec);
    }

    long start = JsonUtil.getLong(START, jsonNode);
    long length = JsonUtil.getLong(LENGTH, jsonNode);

    DeleteFile[] deleteFiles = null;
    if (jsonNode.has(DELETE_FILES)) {
      JsonNode deletesArray = jsonNode.get(DELETE_FILES);
      Preconditions.checkArgument(
          deletesArray.isArray(),
          "Invalid JSON node for delete files: non-array (%s)",
          deletesArray);
      // parse the schema array
      ImmutableList.Builder<DeleteFile> builder = ImmutableList.builder();
      for (JsonNode deleteFileNode : deletesArray) {
        DeleteFile deleteFile = (DeleteFile) ContentFileParser.fromJson(deleteFileNode, spec);
        builder.add(deleteFile);
      }

      deleteFiles = builder.build().toArray(new DeleteFile[0]);
    }

    Expression filter = Expressions.alwaysTrue();
    if (jsonNode.has(RESIDUAL)) {
      filter = ExpressionParser.fromJson(jsonNode.get(RESIDUAL));
    }

    ResidualEvaluator residualEvaluator = ResidualEvaluator.of(spec, filter, caseSensitive);
    BaseFileScanTask baseFileScanTask =
        new BaseFileScanTask(dataFile, deleteFiles, schemaString, specString, residualEvaluator);
    return new BaseFileScanTask.SplitScanTask(start, length, baseFileScanTask);
  }
}
