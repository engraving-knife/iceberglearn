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
 * 基于 Spark 执行的 Iceberg 表维护动作，执行快照过期、文件清理、数据压缩等表维护操作。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 BaseSnapshotUpdateSparkAction。
 *
 * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
abstract class BaseSnapshotUpdateSparkAction<ThisT> extends BaseSparkAction<ThisT> {

  private final Map<String, String> summary = Maps.newHashMap();

  /** 构造 BaseSnapshotUpdateSparkAction 实例。 */
  protected BaseSnapshotUpdateSparkAction(SparkSession spark) {
    super(spark);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param property 参数
   * @param value 参数
   * @return 结果对象
   */
  public ThisT snapshotProperty(String property, String value) {
    summary.put(property, value);
    return self();
  }

  /** 提交事务或写入结果。 */
  protected void commit(org.apache.iceberg.SnapshotUpdate<?> update) {
    summary.forEach(update::set);
    update.commit();
  }
}
