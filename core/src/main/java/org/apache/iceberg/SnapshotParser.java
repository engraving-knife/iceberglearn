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
import java.util.Iterator;
import java.util.Map;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.util.JsonUtil;

/**
 * 快照 JSON 序列化与反序列化工具类（iceberg-core 元数据层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link Snapshot} 序列化为 JSON 写入元数据文件；
 *   <li>从 JSON 反序列化重建 {@link Snapshot} 实例；
 *   <li>兼容 v1（内嵌 manifest 列表）与 v2（manifest 列表文件引用）两种格式。
 * </ul>
 *
 * <p>设计意图：工具类不可实例化，全部方法为静态方法； 使用 {@link DummyFileIO} 在 v1 格式下惰性获取 manifest 路径，避免不必要的 I/O。
 *
 * <p>上下游关系：被 {@link TableMetadataParser} 调用，用于读写元数据 JSON 中的快照条目； 依赖 {@link JsonUtil} 提供 JSON 读写工具。
 */
public class SnapshotParser {

  /** 私有构造方法，禁止实例化。 */
  private SnapshotParser() {}

  /** 仅用于惰性获取路径的占位 {@link FileIO} 实现，供 v1 快照序列化 manifest 路径使用。 */
  private static final DummyFileIO DUMMY_FILE_IO = new DummyFileIO();

  private static final String SEQUENCE_NUMBER = "sequence-number";
  private static final String SNAPSHOT_ID = "snapshot-id";
  private static final String PARENT_SNAPSHOT_ID = "parent-snapshot-id";
  private static final String TIMESTAMP_MS = "timestamp-ms";
  private static final String SUMMARY = "summary";
  private static final String OPERATION = "operation";
  private static final String MANIFESTS = "manifests";
  private static final String MANIFEST_LIST = "manifest-list";
  private static final String SCHEMA_ID = "schema-id";

  /**
   * 将快照写入 JSON 生成器。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若序列号大于初始值则写入 sequence-number；
   *   <li>写入 snapshot-id、parent-snapshot-id（若存在）、timestamp-ms；
   *   <li>若存在 operation，写入 summary 对象，并将 operation 字段单独写入，避免重复；
   *   <li>优先写入 manifest-list 文件位置；若不存在（v1），则内嵌 manifest 路径数组；
   *   <li>若 schema-id 非 null 则写入。
   * </ol>
   *
   * @param snapshot 待序列化的快照
   * @param generator JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  static void toJson(Snapshot snapshot, JsonGenerator generator) throws IOException {
    generator.writeStartObject();
    if (snapshot.sequenceNumber() > TableMetadata.INITIAL_SEQUENCE_NUMBER) {
      generator.writeNumberField(SEQUENCE_NUMBER, snapshot.sequenceNumber());
    }
    generator.writeNumberField(SNAPSHOT_ID, snapshot.snapshotId());
    if (snapshot.parentId() != null) {
      generator.writeNumberField(PARENT_SNAPSHOT_ID, snapshot.parentId());
    }
    generator.writeNumberField(TIMESTAMP_MS, snapshot.timestampMillis());

    // if there is an operation, write the summary map
    if (snapshot.operation() != null) {
      generator.writeObjectFieldStart(SUMMARY);
      generator.writeStringField(OPERATION, snapshot.operation());
      if (snapshot.summary() != null) {
        for (Map.Entry<String, String> entry : snapshot.summary().entrySet()) {
          // only write operation once
          if (OPERATION.equals(entry.getKey())) {
            continue;
          }
          generator.writeStringField(entry.getKey(), entry.getValue());
        }
      }
      generator.writeEndObject();
    }

    String manifestList = snapshot.manifestListLocation();
    if (manifestList != null) {
      // write just the location. manifests should not be embedded in JSON along with a list
      generator.writeStringField(MANIFEST_LIST, manifestList);
    } else {
      // embed the manifest list in the JSON, v1 only
      JsonUtil.writeStringArray(
          MANIFESTS,
          Iterables.transform(snapshot.allManifests(DUMMY_FILE_IO), ManifestFile::path),
          generator);
    }

    // schema ID might be null for snapshots written by old writers
    if (snapshot.schemaId() != null) {
      generator.writeNumberField(SCHEMA_ID, snapshot.schemaId());
    }

    generator.writeEndObject();
  }

  /**
   * 将快照序列化为格式化（pretty）JSON 字符串。
   *
   * <p>默认 pretty=true 以保持向后兼容。
   *
   * @param snapshot 待序列化的快照
   * @return JSON 字符串
   */
  public static String toJson(Snapshot snapshot) {
    // Use true as default value of pretty for backwards compatibility
    return toJson(snapshot, true);
  }

  /**
   * 将快照序列化为 JSON 字符串，可控制是否格式化输出。
   *
   * @param snapshot 待序列化的快照
   * @param pretty 是否格式化输出
   * @return JSON 字符串
   */
  public static String toJson(Snapshot snapshot, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(snapshot, gen), pretty);
  }

  /**
   * 从 JSON 节点反序列化为 {@link Snapshot} 实例。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验节点为对象类型；
   *   <li>解析 sequence-number（缺省为初始值）、snapshot-id、parent-snapshot-id、timestamp-ms；
   *   <li>解析 summary 对象，将 operation 字段单独提取，其余键值对放入 summary map；
   *   <li>解析 schema-id（可能为 null）；
   *   <li>若存在 manifest-list 字段，创建引用 manifest 列表文件的 {@link BaseSnapshot}； 否则回退到 v1 内嵌 manifest
   *       路径数组模式。
   * </ol>
   *
   * @param node JSON 节点
   * @return 反序列化得到的 {@link Snapshot}
   */
  static Snapshot fromJson(JsonNode node) {
    Preconditions.checkArgument(
        node.isObject(), "Cannot parse table version from a non-object: %s", node);

    long sequenceNumber = TableMetadata.INITIAL_SEQUENCE_NUMBER;
    if (node.has(SEQUENCE_NUMBER)) {
      sequenceNumber = JsonUtil.getLong(SEQUENCE_NUMBER, node);
    }
    long snapshotId = JsonUtil.getLong(SNAPSHOT_ID, node);
    Long parentId = null;
    if (node.has(PARENT_SNAPSHOT_ID)) {
      parentId = JsonUtil.getLong(PARENT_SNAPSHOT_ID, node);
    }
    long timestamp = JsonUtil.getLong(TIMESTAMP_MS, node);

    Map<String, String> summary = null;
    String operation = null;
    if (node.has(SUMMARY)) {
      JsonNode sNode = node.get(SUMMARY);
      Preconditions.checkArgument(
          sNode != null && !sNode.isNull() && sNode.isObject(),
          "Cannot parse summary from non-object value: %s",
          sNode);

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      Iterator<String> fields = sNode.fieldNames();
      while (fields.hasNext()) {
        String field = fields.next();
        if (field.equals(OPERATION)) {
          operation = JsonUtil.getString(OPERATION, sNode);
        } else {
          builder.put(field, JsonUtil.getString(field, sNode));
        }
      }
      summary = builder.build();
    }

    Integer schemaId = JsonUtil.getIntOrNull(SCHEMA_ID, node);

    if (node.has(MANIFEST_LIST)) {
      // the manifest list is stored in a manifest list file
      String manifestList = JsonUtil.getString(MANIFEST_LIST, node);
      return new BaseSnapshot(
          sequenceNumber,
          snapshotId,
          parentId,
          timestamp,
          operation,
          summary,
          schemaId,
          manifestList);

    } else {
      // fall back to an embedded manifest list. pass in the manifest's InputFile so length can be
      // loaded lazily, if it is needed
      return new BaseSnapshot(
          sequenceNumber,
          snapshotId,
          parentId,
          timestamp,
          operation,
          summary,
          schemaId,
          JsonUtil.getStringList(MANIFESTS, node).toArray(new String[0]));
    }
  }

  /**
   * 从 JSON 字符串反序列化为 {@link Snapshot} 实例。
   *
   * @param json JSON 字符串
   * @return 反序列化得到的 {@link Snapshot}
   */
  public static Snapshot fromJson(String json) {
    return JsonUtil.parse(json, SnapshotParser::fromJson);
  }

  /**
   * 占位 {@link FileIO} 实现（iceberg-core 元数据层内部类）。
   *
   * <p>职责：仅在 v1 快照序列化时惰性获取 manifest 文件路径，不提供任何实际 I/O 能力。
   *
   * <p>设计意图：v1 快照将 manifest 路径内嵌在 JSON 中，序列化时需通过 {@link Snapshot#allManifests(FileIO)} 获取路径列表，该方法需要
   * FileIO 参数。 此类仅返回路径而不读取文件，避免不必要的 I/O 操作。
   */
  private static class DummyFileIO implements FileIO {
    /**
     * 返回仅支持 location() 的占位 {@link InputFile}。
     *
     * @param path 文件路径
     * @return 占位 InputFile，其 getLength/newStream 会抛出异常
     */
    @Override
    public InputFile newInputFile(String path) {
      return new InputFile() {
        @Override
        public long getLength() {
          throw new UnsupportedOperationException();
        }

        @Override
        public SeekableInputStream newStream() {
          throw new UnsupportedOperationException();
        }

        @Override
        public String location() {
          return path;
        }

        @Override
        public boolean exists() {
          return true;
        }
      };
    }

    /** 不支持输出文件创建，抛出 {@link UnsupportedOperationException}。 */
    @Override
    public OutputFile newOutputFile(String path) {
      throw new UnsupportedOperationException();
    }

    /** 不支持文件删除，抛出 {@link UnsupportedOperationException}。 */
    @Override
    public void deleteFile(String path) {
      throw new UnsupportedOperationException();
    }
  }
}
