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
package org.apache.iceberg.exceptions;

/**
 * 提交失败"可清理"标记接口：实现该接口的提交异常表示提交状态已确定为失败， 调用方可安全地清理本次提交产生的未提交元数据/文件。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>设计意图：采用标记接口（marker interface）模式而非枚举字段，让异常类型自身在 {@code instanceof}
 * 判断中即可表达"可安全清理"语义。提交路径上常见两类失败：
 *
 * <ul>
 *   <li>状态明确的失败（如 {@link CommitFailedException}、{@link ValidationException}）——可清理。
 *   <li>状态未知的失败（如 {@link CommitStateUnknownException}）——不可清理，否则可能误删有效数据。
 * </ul>
 *
 * 该接口仅被前者实现，使清理逻辑可基于类型精确判断。
 *
 * <p>上下游关系：被 core 模块的提交/清理逻辑通过 {@code instanceof CleanableFailure} 判定； 实现者包括 {@link
 * CommitFailedException}、{@link BadRequestException}、{@link ForbiddenException}、 {@link
 * NoSuchNamespaceException}、{@link NoSuchTableException}、{@link ValidationException}、 {@link
 * ServiceUnavailableException}、{@link NotAuthorizedException}、{@link NoSuchIcebergTableException}
 * 等。
 */
public interface CleanableFailure {}
