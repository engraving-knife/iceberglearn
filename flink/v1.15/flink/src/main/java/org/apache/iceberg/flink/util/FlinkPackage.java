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
 * Flink 版本信息工具类。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：从 Flink 核心包的 manifest 中读取版本号， 供 Iceberg 在 commit 元数据中记录引擎版本。
 *
 * <p>设计意图：工具类 + 静态方法；通过反射读取 jar manifest，避免硬编码。
 */
public class FlinkPackage {
  /** 选择 {@link DataStream} 类，因为它是 Flink 核心 API 之一。 */
  private static final String VERSION = DataStream.class.getPackage().getImplementationVersion();

  private FlinkPackage() {}

  /** 返回形如 x.y.z 的 Flink 版本字符串。 */
  public static String version() {
    return VERSION;
  }
}
