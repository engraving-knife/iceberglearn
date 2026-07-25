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
package org.apache.iceberg.flink.util;

import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * 文件级说明：Flink 运行时版本信息工具类。
 *
 * <p>所属模块：iceberg-flink（util 子包），提供 Flink 版本号的获取。
 *
 * <p>职责：通过反射获取 Flink DataStream 包的实现版本，用于运行时版本检测。
 *
 * <p>设计意图：选择 {@link DataStream} 类获取版本，因为它是 Flink 核心 API 之一， 其包信息反映了 Flink
 * 运行时版本。版本信息用于在运行时做版本相关的兼容性判断。
 *
 * <p>上下游关系：被各需要 Flink 版本信息的场景调用。
 */
public class FlinkPackage {
  /** Choose {@link DataStream} class because it is one of the core Flink API. */
  private static final String VERSION = DataStream.class.getPackage().getImplementationVersion();

  private FlinkPackage() {}

  /** Returns Flink version string like x.y.z */
  public static String version() {
    return VERSION;
  }
}
