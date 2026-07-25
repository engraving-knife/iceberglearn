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
package org.apache.iceberg.io;

import org.apache.iceberg.metrics.MetricsContext;

/**
 * 文件级说明：FileIO 专用的指标上下文扩展接口，定义 FileIO 实现应上报的标准指标。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：在 {@link MetricsContext} 基础上声明 FileIO 读写相关的标准指标名常量 （读字节、读操作数、写字节、写操作数）。
 *
 * <p>设计意图：统一指标命名，使不同 FileIO 实现上报的同名指标可以被统一的指标收集体系 （MetricsContext 实现侧）正确识别与聚合，便于跨实现做可观测性统计。
 *
 * <p>上下游关系：由 FileIO 实现依赖以注册指标；被 {@link MetricsContext} 体系消费并对外 暴露给监控后端。
 */
public interface FileIOMetricsContext extends MetricsContext {
  /** 读字节指标名。 */
  String READ_BYTES = "read.bytes";
  /** 读操作次数指标名。 */
  String READ_OPERATIONS = "read.operations";
  /** 写字节指标名。 */
  String WRITE_BYTES = "write.bytes";
  /** 写操作次数指标名。 */
  String WRITE_OPERATIONS = "write.operations";
}
