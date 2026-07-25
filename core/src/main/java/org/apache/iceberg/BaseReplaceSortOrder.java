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
import org.apache.iceberg.expressions.Term;
import org.apache.iceberg.util.Tasks;

/**
 * 替换表排序规则（{@link SortOrder}）的更新操作实现。
 *
 * <p>所属模块：iceberg-core。属于 Iceberg 表更新 API 体系，对应 {@link Table#replaceSortOrder()}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>基于当前表 schema 构造新的 {@link SortOrder.Builder}，供调用方链式声明排序字段。
 *   <li>提交时在重试机制下刷新 metadata、构建新 SortOrder 并通过 {@link TableOperations#commit} 落盘。
 * </ul>
 *
 * <p>设计意图：使用 {@link Tasks} 的指数退避重试，应对并发提交冲突 （{@link CommitFailedException}）；构建器基于 base.schema()
 * 而非最新 schema，保证排序字段 引用的字段 ID 在本次替换语义内一致。{@link #apply()} 与 {@link #commit()} 分离， 允许调用方先预览再提交。
 *
 * <p>上下游关系：由 {@link BaseTable#replaceSortOrder()} 创建；提交后由 {@link TableMetadata#replaceSortOrder}
 * 生成新 metadata。
 */
public class BaseReplaceSortOrder implements ReplaceSortOrder {
  private final TableOperations ops;
  private final SortOrder.Builder builder;
  private TableMetadata base;

  BaseReplaceSortOrder(TableOperations ops) {
    this.ops = ops;
    this.base = ops.current();
    this.builder = SortOrder.builderFor(base.schema());
  }

  /**
   * 返回当前构建器中尚未提交的 SortOrder 预览，不修改表元数据。
   *
   * @return 待应用的新排序规则
   */
  @Override
  public SortOrder apply() {
    return builder.build();
  }

  /**
   * 提交排序规则替换：在指数退避重试下刷新 base、构建新 SortOrder 并提交。
   *
   * <p>逻辑：每次重试都先 {@code ops.refresh()} 拿到最新 metadata 作为 base， 再调用 {@link #apply()} 构建新
   * SortOrder，最后通过 {@link TableMetadata#replaceSortOrder} 生成新 metadata 并提交； 仅在 {@link
   * CommitFailedException} 时重试。
   */
  @Override
  public void commit() {
    Tasks.foreach(ops)
        .retry(base.propertyAsInt(COMMIT_NUM_RETRIES, COMMIT_NUM_RETRIES_DEFAULT))
        .exponentialBackoff(
            base.propertyAsInt(COMMIT_MIN_RETRY_WAIT_MS, COMMIT_MIN_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_MAX_RETRY_WAIT_MS, COMMIT_MAX_RETRY_WAIT_MS_DEFAULT),
            base.propertyAsInt(COMMIT_TOTAL_RETRY_TIME_MS, COMMIT_TOTAL_RETRY_TIME_MS_DEFAULT),
            2.0 /* exponential */)
        .onlyRetryOn(CommitFailedException.class)
        .run(
            taskOps -> {
              this.base = ops.refresh();
              SortOrder newOrder = apply();
              TableMetadata updated = base.replaceSortOrder(newOrder);
              taskOps.commit(base, updated);
            });
  }

  @Override
  public ReplaceSortOrder asc(Term term, NullOrder nullOrder) {
    builder.asc(term, nullOrder);
    return this;
  }

  @Override
  public ReplaceSortOrder desc(Term term, NullOrder nullOrder) {
    builder.desc(term, nullOrder);
    return this;
  }

  @Override
  public ReplaceSortOrder caseSensitive(boolean caseSensitive) {
    builder.caseSensitive(caseSensitive);
    return this;
  }
}
