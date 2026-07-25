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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：基于快照更新的动作基类，提供执行快照变更并可选执行（execute/executeWith）的公共逻辑。
 *
 * <p>设计意图：在 BaseSparkAction 之上增加快照提交与结果统计的通用流程。
 *
 * <p>上下游关系：被 ExpireSnapshotsSparkAction / RewriteDataFilesSparkAction 等继承。
 */
abstract class BaseSnapshotUpdateSparkAction<ThisT> extends BaseSparkAction<ThisT> {

  private final Map<String, String> summary = Maps.newHashMap();

  protected BaseSnapshotUpdateSparkAction(SparkSession spark) {
    super(spark);
  }
  /** 执行 snapshotProperty 相关操作。 */
  public ThisT snapshotProperty(String property, String value) {
    summary.put(property, value);
    return self();
  }
  /** 提交写入。 */
  protected void commit(org.apache.iceberg.SnapshotUpdate<?> update) {
    summary.forEach(update::set);
    update.commit();
  }
}
