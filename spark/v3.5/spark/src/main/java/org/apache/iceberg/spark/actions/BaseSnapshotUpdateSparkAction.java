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
package org.apache.iceberg.spark.actions;

import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.spark.sql.SparkSession;

/**
 * 基于 Spark 的快照更新型 Action 抽象基类。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，提供 Spark 上的 Iceberg 表管理与数据操作能力）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为所有会产生新快照的 Spark Action（如迁移表、重写数据文件等）提供公共的 快照属性（summary）管理能力。
 *   <li>在提交快照更新时，将收集的自定义属性统一设置到 SnapshotUpdate 对象上。
 * </ul>
 *
 * <p>设计意图：将快照属性收集与提交这一横切关注点抽取到抽象基类中，避免每个 具体 Action 重复实现。使用泛型 {@code ThisT} 支持链式调用返回具体子类型。
 *
 * <p>上下游关系：继承自 {@link BaseSparkAction}；被 MigrateTableSparkAction、 RewriteDataFilesSparkAction 等具体
 * Action 继承；提交时调用 Iceberg 核心 {@link org.apache.iceberg.SnapshotUpdate#commit}。
 */
abstract class BaseSnapshotUpdateSparkAction<ThisT> extends BaseSparkAction<ThisT> {

  private final Map<String, String> summary = Maps.newHashMap();

  protected BaseSnapshotUpdateSparkAction(SparkSession spark) {
    super(spark);
  }

  /**
   * 添加一个将写入快照 summary 的自定义属性，支持链式调用。
   *
   * @param property 属性名
   * @param value 属性值
   * @return 当前 Action 实例（用于链式调用）
   */
  public ThisT snapshotProperty(String property, String value) {
    summary.put(property, value);
    return self();
  }

  /**
   * 提交快照更新：先将所有收集的 summary 属性设置到 update 对象上，再执行 commit。
   *
   * @param update 待提交的 Iceberg 快照更新对象
   */
  protected void commit(org.apache.iceberg.SnapshotUpdate<?> update) {
    summary.forEach(update::set);
    update.commit();
  }
}
