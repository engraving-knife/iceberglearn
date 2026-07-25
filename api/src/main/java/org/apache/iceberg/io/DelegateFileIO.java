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

/**
 * 文件级说明：可作为委托目标的 FileIO 扩展接口。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：组合 {@link FileIO}、{@link SupportsPrefixOperations} 与 {@link SupportsBulkOperations}
 * 三者的能力，标记实现既支持按前缀操作也支持批量删除，可作为完整的委托 IO 目标。
 *
 * <p>设计意图：通过接口组合而非在 FileIO 中堆叠方法，保持单一职责；调用方可以通过 {@code instanceof DelegateFileIO} 检测当前 FileIO
 * 是否具备全部增强能力，从而决定是否走 批量/前缀优化路径。
 *
 * <p>上下游关系：由具备完整对象存储能力的 FileIO 实现标记实现；被需要批量删除 / 前缀列举 的上层流程（如 expire snapshots、drop table）调用。
 */
public interface DelegateFileIO extends FileIO, SupportsPrefixOperations, SupportsBulkOperations {}
