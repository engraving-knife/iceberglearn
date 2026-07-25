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

import static org.apache.iceberg.MetadataColumns.CHANGE_ORDINAL;
import static org.apache.iceberg.MetadataColumns.CHANGE_TYPE;
import static org.apache.iceberg.MetadataColumns.COMMIT_SNAPSHOT_ID;

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 变更日志（changelog）相关 schema 工具类。
 *
 * <p>所属模块：iceberg-core（CDC/增量读取支持工具层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造变更日志 schema：在原表 schema 上追加 {@link MetadataColumns#CHANGE_TYPE}、 {@link
 *       MetadataColumns#CHANGE_ORDINAL}、{@link MetadataColumns#COMMIT_SNAPSHOT_ID}
 *       三个元数据列，供下游识别每行记录的变更类型与来源提交。
 *   <li>反向操作：从变更日志 schema 中剥离这些元数据列，还原为原表 schema。
 * </ul>
 *
 * <p>设计意图：变更日志 schema 通过"原表 schema + 元数据列"的组合方式构造， 避免为每个表单独定义 changelog schema；同时维护字段 id 集合便于按 id
 * 反选。
 *
 * <p>上下游关系：被 {@code BaseIncrementalChangelogScan} 等扫描实现调用，用于生成 CDC 读取所需的 schema。
 */
public class ChangelogUtil {

  /** 变更日志附带的元数据列集合：变更类型、变更序号、提交快照 id。 */
  private static final Schema CHANGELOG_METADATA =
      new Schema(CHANGE_TYPE, CHANGE_ORDINAL, COMMIT_SNAPSHOT_ID);

  /** 变更日志元数据列的字段 id 集合，用于反向剥离。 */
  private static final Set<Integer> CHANGELOG_METADATA_FIELD_IDS =
      CHANGELOG_METADATA.columns().stream()
          .map(Types.NestedField::fieldId)
          .collect(Collectors.toSet());

  private ChangelogUtil() {}

  /**
   * 在原表 schema 上追加变更日志元数据列，返回新的 changelog schema。
   *
   * @param tableSchema 原表 schema
   * @return 含变更元数据列的 schema
   */
  public static Schema changelogSchema(Schema tableSchema) {
    return TypeUtil.join(tableSchema, CHANGELOG_METADATA);
  }

  /**
   * 从 changelog schema 中移除变更日志元数据列，返回原表 schema。
   *
   * @param changelogSchema 含变更元数据列的 schema
   * @return 仅保留原表字段的 schema
   */
  public static Schema dropChangelogMetadata(Schema changelogSchema) {
    return TypeUtil.selectNot(changelogSchema, CHANGELOG_METADATA_FIELD_IDS);
  }
}
