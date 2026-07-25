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
package org.apache.iceberg;

import java.util.Iterator;

/**
 * 扫描任务拆分迭代器接口。
 *
 * <p>所属模块：iceberg-core，定位为扫描任务（ScanTask）切分层的核心抽象。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义将一个较大的扫描任务拆分为多个子任务的迭代器契约；
 *   <li>通过 {@link SplitScanTaskCreator} 函数式接口解耦"如何根据父任务、偏移量与长度构造子任务"；
 *   <li>以 {@link Iterator} 形式按需产出子任务，支持流式切分而无需一次性物化全部结果。
 * </ul>
 *
 * <p>设计意图：抽象出统一的拆分迭代器类型，便于不同 {@link ScanTask} 子类（如文件扫描任务、合并任务）
 * 复用相同的拆分与遍历逻辑，避免在每个具体任务类型中重复实现迭代器样板代码；同时通过泛型参数 T 保证类型安全。
 *
 * <p>上下游关系：被扫描任务自身（如 {@code FileScanTask}、{@code CombinedScanTask}）的 split 方法
 * 使用，产生切分后的任务交给具体的执行引擎消费。
 *
 * @param <T> 该迭代器产出的扫描任务的具体 Java 类型
 */
interface SplitScanTaskIterator<T extends ScanTask> extends Iterator<T> {

  /**
   * 子任务创建器函数式接口，根据父任务及目标数据范围构造一个新的子任务。
   *
   * <p>设计意图：通过函数式接口将"拆分粒度"与"任务构造方式"解耦，调用方传入具体的 create 实现 即可复用通用的拆分算法；标注 {@link FunctionalInterface}
   * 以便使用 lambda 表达式。
   *
   * @param <T> 父任务与子任务的具体类型
   */
  @FunctionalInterface
  interface SplitScanTaskCreator<T extends ScanTask> {
    /**
     * 基于父任务、起始偏移量与读取长度构造一个子任务。
     *
     * @param parentTask 被拆分的父扫描任务，提供文件路径、剩余上下文等基础信息
     * @param offset 子任务在父任务数据范围内相对起始的字节偏移量
     * @param length 子任务覆盖的字节长度
     * @return 新构造的子扫描任务
     */
    T create(T parentTask, long offset, long length);
  }
}
