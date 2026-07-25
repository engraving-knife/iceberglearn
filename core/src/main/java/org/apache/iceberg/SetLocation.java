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

import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MAX_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS;
import static org.apache.iceberg.TableProperties.COMMIT_MIN_RETRY_WAIT_MS_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES;
import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES_DEFAULT;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS;
import static org.apache.iceberg.TableProperties.COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT;

import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.util.Tasks;

/**
 * 设置表存储位置操作（{@link UpdateLocation} 的 core 实现）。
 *
 * <p>所属模块：iceberg-core。职责：把表的 location 属性更新为新路径并提交。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>链式 API：{@link #setLocation(String)} 设置后返回 this，{@link #apply()} 返回新元数据， {@link #commit()}
 *       提交。
 *   <li>提交前 refresh：在 commit 时重新读取元数据，避免基于过期元数据提交。
 *   <li>按表属性配置重试：复用 {@link Tasks} 的重试参数。
 * </ul>
 *
 * <p>上下游关系：上游为 {@link Table#updateLocation()}；下游通过 {@link TableOperations} 提交。
 */
public class SetLocation implements UpdateLocation {
  private final TableOperations ops;
  private String newLocation;

  /**
   * 构造方法：传入 TableOperations，初始化 newLocation 为 null。
   *
   * @param ops 表操作接口
   */
  public SetLocation(TableOperations ops) {
    this.ops = ops;
    this.newLocation = null;
  }

  /**
   * 设置新的表存储位置，仅记录不提交。
   *
   * @param location 新的表存储路径
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateLocation setLocation(String location) {
    this.newLocation = location;
    return this;
  }

  /**
   * 返回待设置的新 location（未触发元数据生成）。
   *
   * @return 新的表存储路径，未设置时为 null
   */
  @Override
  public String apply() {
    return newLocation;
  }

  /**
   * 提交 location 变更到 TableOperations。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>refresh 当前元数据，避免基于过期版本提交；
   *   <li>用 {@link Tasks} 按表属性配置的指数退避策略重试，仅在 {@link CommitFailedException} 时重试；
   *   <li>用 {@link TableMetadata#updateLocation(String)} 生成新元数据并提交。
   * </ol>
   */
  @Override
  public void commit() {
    TableMetadata base = ops.refresh();
    Tasks.foreach(ops)
        .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
        .exponentialBackoff(
            base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
            2.0 /* exponential */)
        .onlyRetryOn(CommitFailedException.class)
        .run(taskOps -> taskOps.commit(base, base.updateLocation(newLocation)));
  }
}
