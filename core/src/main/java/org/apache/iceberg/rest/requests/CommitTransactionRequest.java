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
package org.apache.iceberg.rest.requests;

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 所属模块：iceberg-core；REST Catalog 请求模型层。
 *
 * <p>职责：封装"事务性提交（commit transaction）"请求体，将多个表的变更（{@link UpdateTableRequest}） 打包为一次原子提交。具体包括：
 *
 * <ul>
 *   <li>持有多个 {@link UpdateTableRequest} 作为待提交的表变更
 *   <li>提供 {@link #validate()} 校验变更列表非空且每个变更都携带表标识
 *   <li>对外暴露不可变的变更列表
 * </ul>
 *
 * <p>设计意图：作为 {@link RESTRequest} 的实现，遵循 Iceberg REST Catalog 规范的事务提交协议；
 * 使用不可变列表保证请求体在传输过程中不被修改；构造时即校验，尽早暴露非法参数。
 *
 * <p>上下游关系：由 {@code RESTSessionCatalog} 在多表事务提交时构造；由 {@link CommitTransactionRequestParser} 序列化为
 * JSON；服务端接收后执行原子提交。
 */
public class CommitTransactionRequest implements RESTRequest {
  // 待提交的多个表变更请求
  private final List<UpdateTableRequest> tableChanges;

  /**
   * 构造事务提交请求，并立即执行 {@link #validate()} 校验参数合法性。
   *
   * @param tableChanges 表变更请求列表
   */
  public CommitTransactionRequest(List<UpdateTableRequest> tableChanges) {
    this.tableChanges = tableChanges;
    validate();
  }

  /**
   * 返回不可变的表变更请求列表。
   *
   * @return 表变更请求的不可变拷贝
   */
  public List<UpdateTableRequest> tableChanges() {
    return ImmutableList.copyOf(tableChanges);
  }

  /**
   * 校验请求体合法性：变更列表非 null 且非空，且每个变更都携带表标识。
   *
   * <p>逻辑：依次检查 tableChanges 不为 null、不为空，并遍历检查每个变更的 identifier 非空， 不满足时抛出 {@link
   * IllegalArgumentException}。
   */
  @Override
  public void validate() {
    Preconditions.checkArgument(null != tableChanges, "Invalid table changes: null");
    Preconditions.checkArgument(!tableChanges.isEmpty(), "Invalid table changes: empty");
    for (UpdateTableRequest tableChange : tableChanges) {
      Preconditions.checkArgument(
          null != tableChange.identifier(), "Invalid table changes: table identifier is required");
    }
  }
}
