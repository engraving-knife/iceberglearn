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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.HasTableOperations;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件重写结果协调器基类：在 Spark 数据源写完成后收集并暂存重写产物。
 *
 * <p>所属模块：iceberg-spark（核心包，作为数据文件重写动作与 Spark 数据源写之间的桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>暂存每个文件组重写后生成的新文件集合（stageRewrite）。
 *   <li>按表与文件组 ID 查询、清理已暂存的重写结果。
 * </ul>
 *
 * <p>设计意图：Spark 数据源写无法直接把产物回传给发起重写的动作，因此通过此类作为 侧道（side-effect）收集器：写完成后调用 stageRewrite 暂存结果，动作方再通过
 * fetchNewFiles 取回。使用并发 Map 保证多任务并发写入安全，按 (表UUID, 文件组ID) 做键。
 *
 * <p>上下游关系：被具体重写协调器（如 FileRewriteCoordinator）继承； 由 Spark 数据源写端在写出后调用暂存，由重写动作在提交前读取结果。
 *
 * @param <F> 内容文件类型
 */
abstract class BaseFileRewriteCoordinator<F extends ContentFile<F>> {

  private static final Logger LOG = LoggerFactory.getLogger(BaseFileRewriteCoordinator.class);

  private final Map<Pair<String, String>, Set<F>> resultMap = Maps.newConcurrentMap();

  /**
   * 暂存某个文件组重写后的新文件集合。
   *
   * <p>设计要点：由于写入由 Spark 数据源完成，需通过此侧道调用把结果回传给重写动作。
   *
   * @param table 正在执行重写的表
   * @param fileSetId 标识被重写源文件集的 ID
   * @param newFiles 已写出的新文件集合
   */
  public void stageRewrite(Table table, String fileSetId, Set<F> newFiles) {
    LOG.debug(
        "Staging the output for {} - fileset {} with {} files",
        table.name(),
        fileSetId,
        newFiles.size());
    Pair<String, String> id = toId(table, fileSetId);
    resultMap.put(id, newFiles);
  }

  /**
   * 获取指定文件组重写后的新文件集合。
   *
   * @param table 正在执行重写的表
   * @param fileSetId 文件组 ID
   * @return 暂存的新文件集合
   * @throws ValidationException 当该文件组没有暂存结果时抛出
   */
  public Set<F> fetchNewFiles(Table table, String fileSetId) {
    Pair<String, String> id = toId(table, fileSetId);
    Set<F> result = resultMap.get(id);
    ValidationException.check(
        result != null, "No results for rewrite of file set %s in table %s", fileSetId, table);

    return result;
  }

  /**
   * 清除指定文件组的暂存结果。
   *
   * @param table 正在执行重写的表
   * @param fileSetId 文件组 ID
   */
  public void clearRewrite(Table table, String fileSetId) {
    LOG.debug("Removing entry for {} - id {}", table.name(), fileSetId);
    Pair<String, String> id = toId(table, fileSetId);
    resultMap.remove(id);
  }

  /**
   * 返回指定表当前所有已暂存的文件组 ID。
   *
   * @param table 目标表
   * @return 文件组 ID 集合
   */
  public Set<String> fetchSetIds(Table table) {
    return resultMap.keySet().stream()
        .filter(e -> e.first().equals(tableUUID(table)))
        .map(Pair::second)
        .collect(Collectors.toSet());
  }

  /** 以 (表UUID, 文件组ID) 构造结果映射的键。 */
  private Pair<String, String> toId(Table table, String setId) {
    String tableUUID = tableUUID(table);
    return Pair.of(tableUUID, setId);
  }

  /** 通过 {@link TableOperations} 获取表 UUID 作为唯一标识。 */
  private String tableUUID(Table table) {
    TableOperations ops = ((HasTableOperations) table).operations();
    return ops.current().uuid();
  }
}
