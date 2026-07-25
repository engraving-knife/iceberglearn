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
package org.apache.iceberg.flink;

import org.apache.iceberg.EnvironmentContext;
import org.apache.iceberg.flink.util.FlinkPackage;

/**
 * Flink 环境上下文初始化工具类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在 Flink 端加载表/操作 Iceberg 时， 把引擎名（flink）和引擎版本写入 Iceberg 的 {@link
 * EnvironmentContext}， 用于 commit 元数据中的引擎标识。
 *
 * <p>设计意图：工具类 + 幂等初始化，由 {@link TableLoader#loadTable} 等入口调用。
 */
class FlinkEnvironmentContext {
  private FlinkEnvironmentContext() {}

  /** 将引擎名设为 flink，引擎版本设为 Flink 包版本。 */
  public static void init() {
    EnvironmentContext.put(EnvironmentContext.ENGINE_NAME, "flink");
    EnvironmentContext.put(EnvironmentContext.ENGINE_VERSION, FlinkPackage.version());
  }
}
