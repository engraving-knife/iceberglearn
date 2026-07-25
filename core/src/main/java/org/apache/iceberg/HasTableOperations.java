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
 * 暴露表底层 {@link TableOperations} 的访问接口。
 *
 * <p>所属模块：iceberg-core（表内部能力暴露层）。
 *
 * <p>职责：为需要直接访问表操作对象（如元数据读写、IO、加密等）的内部调用方 提供受控的访问入口，而不必把这些方法暴露到公共 {@link Table} 接口上。
 *
 * <p>设计意图：{@link Table} 接口面向用户，故意隐藏 {@link TableOperations}； 但 core 内部模块或部分引擎集成需要拿到 operations
 * 才能完成底层操作， 因此通过本接口在内部强转获取。
 *
 * <p>上下游关系：被 {@code BaseTable} 等表实现类实现；被 core 内部工具与 部分引擎集成模块通过强转为 {@link HasTableOperations} 调用。
 */
public interface HasTableOperations {
  /**
   * 返回该表关联的 {@link TableOperations} 实例。
   *
   * @return 表操作对象
   */
  TableOperations operations();
}
