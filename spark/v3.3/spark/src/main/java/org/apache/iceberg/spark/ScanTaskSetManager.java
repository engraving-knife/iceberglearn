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
 * Iceberg Spark 集成相关组件的扫描组件，负责构建和执行数据读取计划。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ScanTaskSetManager。
 */
public class ScanTaskSetManager {

  private static final ScanTaskSetManager INSTANCE = new ScanTaskSetManager();

  private final Map<Pair<String, String>, List<? extends ScanTask>> tasksMap =
      Maps.newConcurrentMap();

  /** 构造 ScanTaskSetManager 实例。 */
  private ScanTaskSetManager() {}

  /** 执行该方法的具体逻辑。 */
  public static ScanTaskSetManager get() {
    return INSTANCE;
  }

  /** 执行该方法的具体逻辑。 */
  public <T extends ScanTask> void stageTasks(Table table, String setId, List<T> tasks) {
    Preconditions.checkArgument(
        tasks != null && tasks.size() > 0, "Cannot stage null or empty tasks");
    Pair<String, String> id = toId(table, setId);
    tasksMap.put(id, tasks);
  }

  /** 执行该方法的具体逻辑。 */
  @SuppressWarnings("unchecked")
  public <T extends ScanTask> List<T> fetchTasks(Table table, String setId) {
    Pair<String, String> id = toId(table, setId);
    return (List<T>) tasksMap.get(id);
  }

  /** 移除元素或项。 */
  @SuppressWarnings("unchecked")
  public <T extends ScanTask> List<T> removeTasks(Table table, String setId) {
    Pair<String, String> id = toId(table, setId);
    return (List<T>) tasksMap.remove(id);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param table 参数
   * @return 结果对象
   */
  public Set<String> fetchSetIds(Table table) {
    return tasksMap.keySet().stream()
        .filter(e -> e.first().equals(tableUUID(table)))
        .map(Pair::second)
        .collect(Collectors.toSet());
  }

  /** 执行该方法的具体逻辑。 */
  private String tableUUID(Table table) {
    TableOperations ops = ((HasTableOperations) table).operations();
    return ops.current().uuid();
  }

  /** 转换为id。 */
  private Pair<String, String> toId(Table table, String setId) {
    return Pair.of(tableUUID(table), setId);
  }
}
