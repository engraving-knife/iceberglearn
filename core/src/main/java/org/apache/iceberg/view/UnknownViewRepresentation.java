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
package org.apache.iceberg.view;

import org.immutables.value.Value;

/**
 * 文件级说明：未知类型视图表示的占位实现（Immutables 不可变接口）。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：当反序列化遇到无法识别的表示类型时，使用本类作为占位，保留原始信息而不丢失。
 *
 * <p>设计意图：作为前向兼容的兜底类型，避免因新增表示类型而导致旧版本读取失败； 接口体为空，仅承载 type 标识。
 *
 * <p>上下游关系：由 {@link ViewRepresentationParser} 在 type 无法匹配已知类型时构造。
 */
@Value.Immutable
public interface UnknownViewRepresentation extends ViewRepresentation {}
