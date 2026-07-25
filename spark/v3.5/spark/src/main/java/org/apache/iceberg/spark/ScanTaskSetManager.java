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
package org.apache.iceberg.spark;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.Pair;

/**
 * 扫描任务集管理器：以单例方式在驱动端暂存一组扫描任务，供对应的数据源读消费。
 *
 * <p>所属模块：iceberg-spark（核心包，服务于文件重写等需要外部驱动扫描任务集的场景）。
 *
 * <p>职责：按 (表UUID, 任务集ID) 暂存、查询、移除扫描任务列表，使重写动作能够 先规划任务集，再由 Spark 数据源按 setId 读取执行。
 *
 * <p>设计意图：部分动作（如数据文件重写）需要先在驱动端规划好扫描任务集， 再让 Spark 数据源以特定 setId 读取这些任务。此类作为驱动端与数据源之间的任务传递中介， 使用并发 Map
 * 保证多任务并发安全，以表 UUID 隔离不同表。
 *
 * <p>上下游关系：单例，被重写动作调用 stageTasks 暂存任务，被 Spark 数据源读端 通过 {@link SparkScan#taskSetId} 读取任务。
 */
public class ScanTaskSetManager {

  private static final ScanTaskSetManager INSTANCE = new ScanTaskSetManager();

  private final Map<Pair<String, String>, List<? extends ScanTask>> tasksMap =
      Maps.newConcurrentMap();

  private ScanTaskSetManager() {}

  /** 返回全局唯一的 {@link ScanTaskSetManager} 实例。 */
  public static ScanTaskSetManager get() {
    return INSTANCE;
  }

  /**
   * 暂存指定表与任务集 ID 对应的扫描任务列表。
   *
   * @param table 目标表
   * @param setId 任务集标识
   * @param tasks 待暂存的扫描任务列表
   * @throws IllegalArgumentException 当 tasks 为 null 或空时抛出
   */
  public <T extends ScanTask> void stageTasks(Table table, String setId, List<T> tasks) {
    Preconditions.checkArgument(
        tasks != null && tasks.size() > 0, "Cannot stage null or empty tasks");
    Pair<String, String> id = toId(table, setId);
    tasksMap.put(id, tasks);
  }

  /**
   * 获取指定表与任务集 ID 对应的扫描任务列表。
   *
   * @param table 目标表
   * @param setId 任务集标识
   * @return 扫描任务列表，不存在时返回 null
   */
  @SuppressWarnings("unchecked")
  public <T extends ScanTask> List<T> fetchTasks(Table table, String setId) {
    Pair<String, String> id = toId(table, setId);
    return (List<T>) tasksMap.get(id);
  }

  /**
   * 移除并返回指定表与任务集 ID 对应的扫描任务列表。
   *
   * @param table 目标表
   * @param setId 任务集标识
   * @return 被移除的扫描任务列表，不存在时返回 null
   */
  @SuppressWarnings("unchecked")
  public <T extends ScanTask> List<T> removeTasks(Table table, String setId) {
    Pair<String, String> id = toId(table, setId);
    return (List<T>) tasksMap.remove(id);
  }

  /**
   * 返回指定表当前所有已暂存的 task set ID。
   *
   * @param table 目标表
   * @return task set ID 集合
   */
  public Set<String> fetchSetIds(Table table) {
    return tasksMap.keySet().stream()
        .filter(e -> e.first().equals(tableUUID(table)))
        .map(Pair::second)
        .collect(Collectors.toSet());
  }

  /** 通过 {@link TableOperations} 获取表 UUID 作为唯一标识。 */
  private String tableUUID(Table table) {
    TableOperations ops = ((HasTableOperations) table).operations();
    return ops.current().uuid();
  }

  /** 以 (表UUID, 任务集ID) 构造任务映射的键。 */
  private Pair<String, String> toId(Table table, String setId) {
    return Pair.of(tableUUID(table), setId);
  }
}
