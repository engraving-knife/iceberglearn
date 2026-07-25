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
package org.apache.iceberg.flink.sink;

import java.time.Duration;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.util.DateTimeUtil;
import org.apache.iceberg.util.SerializableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 带刷新间隔缓存的表加载器。
 *
 * <p>所属模块：iceberg-flink（sink 侧），实现 {@link SerializableSupplier}。
 *
 * <p>职责：在内存中缓存 Iceberg {@link Table} 实例，仅当距上次加载超过指定间隔时才重新加载， 避免每次写入都访问 catalog。
 *
 * <p>设计意图：写入任务高频访问表元数据，直接每次加载会冲击 catalog；通过时间窗口缓存降低负载。 警告：writer 数量很多时仍可能对 catalog
 * 造成较大压力，需谨慎配置刷新间隔。
 *
 * <p>上下游关系：被 sink writer 用于获取/刷新表实例；上游依赖 {@link TableLoader}。
 */
class CachingTableSupplier implements SerializableSupplier<Table> {

  private static final Logger LOG = LoggerFactory.getLogger(CachingTableSupplier.class);

  private final Table initialTable;
  private final TableLoader tableLoader;
  private final Duration tableRefreshInterval;
  private long lastLoadTimeMillis;
  private transient Table table;

  /**
   * 构造缓存供应器。
   *
   * @param initialTable 初始表实例（不可为 null）
   * @param tableLoader 表加载器（不可为 null）
   * @param tableRefreshInterval 刷新间隔（不可为 null）
   */
  CachingTableSupplier(
      SerializableTable initialTable, TableLoader tableLoader, Duration tableRefreshInterval) {
    Preconditions.checkArgument(initialTable != null, "initialTable cannot be null");
    Preconditions.checkArgument(tableLoader != null, "tableLoader cannot be null");
    Preconditions.checkArgument(
        tableRefreshInterval != null, "tableRefreshInterval cannot be null");
    this.initialTable = initialTable;
    this.table = initialTable;
    this.tableLoader = tableLoader;
    this.tableRefreshInterval = tableRefreshInterval;
    this.lastLoadTimeMillis = System.currentTimeMillis();
  }

  /** 返回当前缓存的表实例；若被序列化清空则回退到初始表。 */
  @Override
  public Table get() {
    if (table == null) {
      this.table = initialTable;
    }
    return table;
  }

  /** 返回构造时传入的初始表实例。 */
  Table initialTable() {
    return initialTable;
  }

  /**
   * 在超过刷新间隔时重新加载表。
   *
   * <p>逻辑：比较当前时间与上次加载时间，超时则打开 tableLoader 并 loadTable，更新缓存与时间戳； 加载异常时仅告警不抛出，避免影响写入。
   */
  void refreshTable() {
    if (System.currentTimeMillis() > lastLoadTimeMillis + tableRefreshInterval.toMillis()) {
      try {
        if (!tableLoader.isOpen()) {
          tableLoader.open();
        }

        this.table = tableLoader.loadTable();
        this.lastLoadTimeMillis = System.currentTimeMillis();

        LOG.info(
            "Table {} reloaded, next min load time threshold is {}",
            table.name(),
            DateTimeUtil.formatTimestampMillis(
                lastLoadTimeMillis + tableRefreshInterval.toMillis()));
      } catch (Exception e) {
        LOG.warn("An error occurred reloading table {}, table was not reloaded", table.name(), e);
      }
    }
  }
}
