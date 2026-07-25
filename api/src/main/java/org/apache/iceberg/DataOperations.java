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

/**
 * 文件级说明：产生快照的数据操作类型常量集合。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：定义快照所记录的数据操作类型字符串常量（append/replace/overwrite/delete）， 供 {@link Snapshot#operation()}
 * 返回，便于其他组件按操作类型过滤不需要处理的快照。
 *
 * <p>设计意图：以字符串常量而非枚举形式定义，便于在序列化元数据中持久化与跨版本兼容。 例如快照过期任务对 append 操作无需清理已删除文件（append
 * 不含删除文件），可据此跳过相关逻辑。
 *
 * <p>上下游关系：由 {@link AppendFiles}、{@link RewriteFiles}、{@link OverwriteFiles}、 {@link
 * ReplacePartitions}、{@link DeleteFiles} 等更新 API 在提交时设置；被快照过期、 增量扫描等组件消费。
 */
public class DataOperations {
  private DataOperations() {}

  /** 追加操作：向表追加新数据，不删除任何数据。由 {@link AppendFiles} 实现。 */
  public static final String APPEND = "append";

  /** 替换操作：移除并替换文件，但不改变表中的数据内容。由 {@link RewriteFiles} 实现。 */
  public static final String REPLACE = "replace";

  /** 覆写操作：新增数据并覆写已有数据。由 {@link OverwriteFiles} 和 {@link ReplacePartitions} 实现。 */
  public static final String OVERWRITE = "overwrite";

  /** 删除操作：从表中删除数据，不新增任何数据。由 {@link DeleteFiles} 实现。 */
  public static final String DELETE = "delete";
}
