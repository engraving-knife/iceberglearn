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

/**
 * 用新建排序替换表当前 sort order 的 API。
 *
 * <p>所属模块：iceberg-api（表更新操作接口层）。
 *
 * <p>职责：在表元数据中用新的 {@link SortOrder} 替换原排序，用于指导支持排序的引擎在写入 时对记录排序。同时继承 {@link
 * SortOrderBuilder}，可通过链式方法构建新排序。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>{@code apply()} 返回新的 sort order 供校验。
 *   <li>提交时将变更应用到当前表元数据；若发生冲突，则把待提交变更应用到新的表元数据后 重试。
 * </ul>
 *
 * <p>上下游关系：继承 {@link PendingUpdate} 与 {@link SortOrderBuilder}；由 core 模块实现， 被引擎/用户调用以更新排序。
 */
public interface ReplaceSortOrder
    extends PendingUpdate<SortOrder>, SortOrderBuilder<ReplaceSortOrder> {}
