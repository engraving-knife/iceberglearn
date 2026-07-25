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
 * 向 Iceberg {@link EnvironmentContext} 注册 Flink 引擎信息的初始化器。
 *
 * <p>所属模块：iceberg-flink，用于在 Flink 运行时标记当前引擎名称与版本。
 *
 * <p>职责：将引擎名 "flink" 与 Flink 版本号写入 Iceberg 的环境上下文，供指标、审计等场景区分引擎来源。
 *
 * <p>设计意图：包级可见，仅由 Flink 集成入口在初始化时调用一次，避免重复注册。
 *
 * <p>上下游关系：被 Flink source/sink 初始化流程调用；上游依赖 {@link FlinkPackage} 获取版本。
 */
class FlinkEnvironmentContext {
  private FlinkEnvironmentContext() {}

  /** 注册 Flink 引擎名与版本到 Iceberg 环境上下文。 */
  public static void init() {
    EnvironmentContext.put(EnvironmentContext.ENGINE_NAME, "flink");
    EnvironmentContext.put(EnvironmentContext.ENGINE_VERSION, FlinkPackage.version());
  }
}
