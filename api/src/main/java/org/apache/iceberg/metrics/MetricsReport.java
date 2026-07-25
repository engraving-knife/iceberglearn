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
package org.apache.iceberg.metrics;

/**
 * 指标报告接口：作为一次操作产生的指标集合的载体，由 {@link MetricsReporter} 上报。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：作为指标数据的标记接口，定义"可被上报的指标集合"这一抽象，不约束具体字段， 由具体实现（如 core 模块中的 commit/scan 指标报告）填充细节。
 *
 * <p>设计意图：采用空标记接口而非具体类，使不同操作类型可携带各自结构的指标数据， 同时让 {@link MetricsReporter#report(MetricsReport)}
 * 的签名保持稳定，解耦上报方与生成方。
 *
 * <p>上下游关系：由 core 模块在操作完成时构造；交由 {@link MetricsReporter} 实现上报到 日志或外部监控系统。
 */
public interface MetricsReport {}
