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
 * 文件级说明：支持批量删除操作的 FileIO 扩展接口。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：在 {@link FileIO} 基础上增加 {@link #deleteFiles(Iterable)} 方法，允许一次性删除 多个文件。
 *
 * <p>设计意图：许多对象存储（S3 等）提供批量删除 API，相比逐个删除可显著降低请求次数与 延迟。本接口允许实现利用底层批量 API；调用方通过 {@code instanceof}
 * 检测后可走批量路径， 否则回退到逐个删除。
 *
 * <p>上下游关系：由具备批量删除能力的 FileIO 实现标记实现；被快照过期、删除数据文件等 上层流程调用。
 */
public interface SupportsBulkOperations extends FileIO {
  /**
   * 批量删除给定路径集合对应的文件。
   *
   * @param pathsToDelete 要删除的文件路径集合
   * @throws BulkDeletionFailureException 当至少 1 个文件删除失败时抛出
   */
  void deleteFiles(Iterable<String> pathsToDelete) throws BulkDeletionFailureException;
}
