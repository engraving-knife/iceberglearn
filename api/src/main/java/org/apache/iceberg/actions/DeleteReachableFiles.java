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
package org.apache.iceberg.actions;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsBulkOperations;

/**
 * 删除某个表元数据文件所引用的全部文件的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，用于表被删除后的存储层彻底清理。
 *
 * <p>职责：根据给定元数据位置，不可逆地删除所有可达文件（数据文件、清单文件、清单列表等）。 适用于表已删除且不再需要时清理底层存储。
 *
 * <p>设计意图：与 {@link DeleteOrphanFiles}（基于表对象、扫描存储找孤儿）不同，本动作基于
 * 一个独立的元数据位置直接删除其可达文件，常用于表已不存在但元数据文件仍残留的场景。实现可 借助查询引擎分布式执行删除工作。
 *
 * <p>上下游关系：由引擎模块实现；删除执行依赖 {@link FileIO} 或自定义 deleteFunc；结果通过 {@link Result} 返回各类文件删除计数。
 */
public interface DeleteReachableFiles
    extends Action<DeleteReachableFiles, DeleteReachableFiles.Result> {

  /**
   * 指定用于删除文件的自定义删除函数。
   *
   * @param deleteFunc 接收文件路径的删除函数
   * @return this，便于链式调用
   */
  DeleteReachableFiles deleteWith(Consumer<String> deleteFunc);

  /**
   * 指定用于删除文件的替代执行器服务。
   *
   * <p>仅当通过 {@link #deleteWith(Consumer)} 提供自定义删除函数、或 FileIO 不 {@link SupportsBulkOperations
   * 支持批量删除}时才会使用该执行器；否则并行度由 IO 专属的 {@link SupportsBulkOperations#deleteFiles(Iterable) deleteFiles}
   * 控制。
   *
   * @param executorService 使用的执行器服务
   * @return this，便于链式调用
   */
  DeleteReachableFiles executeDeleteWith(ExecutorService executorService);

  /**
   * 设置用于删除文件的 {@link FileIO}。
   *
   * @param io 用于删除文件的 FileIO
   * @return this，便于链式调用
   */
  DeleteReachableFiles io(FileIO io);

  /** 动作执行结果，包含执行摘要统计。 */
  interface Result {

    /**
     * 返回已删除的数据文件数量。
     *
     * @return 已删除数据文件数
     */
    long deletedDataFilesCount();

    /**
     * 返回已删除的等值删除文件数量。
     *
     * @return 已删除等值删除文件数
     */
    long deletedEqualityDeleteFilesCount();

    /**
     * 返回已删除的位置删除文件数量。
     *
     * @return 已删除位置删除文件数
     */
    long deletedPositionDeleteFilesCount();

    /**
     * 返回已删除的清单（manifest）文件数量。
     *
     * @return 已删除清单文件数
     */
    long deletedManifestsCount();

    /**
     * 返回已删除的清单列表（manifest list）数量。
     *
     * @return 已删除清单列表数
     */
    long deletedManifestListsCount();

    /**
     * 返回已删除的其他文件数量（metadata json、version hint 等）。
     *
     * @return 已删除其他文件数
     */
    long deletedOtherFilesCount();
  }
}
