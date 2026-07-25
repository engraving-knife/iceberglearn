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

import java.util.Map;
import java.util.Set;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.Tasks;

/**
 * 表属性更新操作（{@link UpdateProperties} 的 core 实现）。
 *
 * <p>所属模块：iceberg-core。职责：累积对表属性的 set/remove 变更，生成新的 {@link TableMetadata} 并提交。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>暂存变更：先在内存中收集 updates/removals，调用 {@link #apply()} 时才生成新元数据，支持链式 API。
 *   <li>冲突校验：禁止同时对同一 key 既更新又删除。
 *   <li>提交重试：通过 {@link Tasks} 按表属性配置的重试参数处理 {@link CommitFailedException}。
 * </ul>
 *
 * <p>上下游关系：上游为用户/Catalog 调用 {@link Table#updateProperties()}；下游通过 {@link TableOperations} 提交元数据变更。
 */
class PropertiesUpdate implements UpdateProperties {
  private final TableOperations ops;
  private final Map<String, String> updates = Maps.newHashMap();
  private final Set<String> removals = Sets.newHashSet();
  private TableMetadata base;

  PropertiesUpdate(TableOperations ops) {
    this.ops = ops;
    this.base = ops.current();
  }

  /**
   * 设置或更新一个表属性。
   *
   * <p>校验：key/value 不能为 null；同一 key 不能同时被 remove。
   *
   * @param key 属性 key
   * @param value 属性 value
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateProperties set(String key, String value) {
    Preconditions.checkNotNull(key, "Key cannot be null");
    Preconditions.checkNotNull(value, "Value cannot be null");
    Preconditions.checkArgument(
        !removals.contains(key), "Cannot remove and update the same key: %s", key);

    updates.put(key, value);

    return this;
  }

  /**
   * 标记一个属性为待删除。
   *
   * <p>校验：key 不能为 null；同一 key 不能同时被 set。
   *
   * @param key 待删除的属性 key
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateProperties remove(String key) {
    Preconditions.checkNotNull(key, "Key cannot be null");
    Preconditions.checkArgument(
        !updates.keySet().contains(key), "Cannot remove and update the same key: %s", key);

    removals.add(key);

    return this;
  }

  /**
   * 设置默认文件格式（等价于 set(DEFAULT_FILE_FORMAT, format.name())）。
   *
   * @param format 文件格式
   * @return 当前 builder，支持链式调用
   */
  @Override
  public UpdateProperties defaultFormat(FileFormat format) {
    set(TableProperties.DEFAULT_FILE_FORMAT, format.name());
    return this;
  }

  /**
   * 计算应用所有变更后的新属性映射。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>刷新 base 元数据；
   *   <li>拷贝现有属性，剔除 removals；
   *   <li>叠加 updates；
   *   <li>校验 metrics 相关属性引用的列在 schema 中存在。
   * </ol>
   *
   * @return 新属性映射（未提交）
   */
  @Override
  public Map<String, String> apply() {
    this.base = ops.refresh();

    Map<String, String> newProperties = Maps.newHashMap();
    for (Map.Entry<String, String> entry : base.properties().entrySet()) {
      if (!removals.contains(entry.getKey())) {
        newProperties.put(entry.getKey(), entry.getValue());
      }
    }

    newProperties.putAll(updates);

    // Validate the metrics
    if (base != null && base.schema() != null) {
      MetricsConfig.fromProperties(newProperties).validateReferencedColumns(base.schema());
    }

    return newProperties;
  }

  /**
   * 提交属性变更到 TableOperations，带按表属性配置的重试。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>用 {@link Tasks} 框架按 {@code commit.retry-num-retries} 等属性配置指数退避重试；
   *   <li>仅在 {@link CommitFailedException} 时重试；
   *   <li>每次重试都重新调用 {@link #apply()} 以拿到最新的 base 元数据。
   * </ol>
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
              Map<String, String> newProperties = apply();
              TableMetadata updated = base.replaceProperties(newProperties);
              taskOps.commit(base, updated);
            });
  }
}
