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
package org.apache.iceberg.actions;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;

/**
 * 文件重写策略接口。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义重写数据文件的策略契约：选择待重写文件、规划文件分组、执行实际重写。
 *   <li>提供策略可用的选项白名单（{@link #validOptions()}）与选项设置入口。
 * </ul>
 *
 * <p>设计意图：将"如何挑选文件"和"如何分组、如何重写"解耦为策略，使 {@link RewriteDataFiles}
 * 动作能根据不同目标（合并小文件、按列排序等）切换策略。策略实例可序列化，便于在分布式引擎中分发。
 *
 * <p>上下游关系：被 {@link BinPackStrategy}、{@link SortStrategy} 等抽象策略实现；由具体 Action
 * （Spark/Presto/Flink）在执行重写时调用。
 *
 * @deprecated since 1.3.0, will be removed in 1.4.0; use {@link FileRewriter} instead. Note: This
 *     can only be removed once Spark 3.2 isn't using this API anymore.
 */
@Deprecated
public interface RewriteStrategy extends Serializable {
  /**
   * 返回本重写策略的名称。
   *
   * @return 策略名（如 "BINPACK"、"SORT"）
   */
  String name();

  /**
   * 返回本策略所操作的目标表。
   *
   * @return 目标 {@link Table}
   */
  Table table();

  /**
   * 返回本策略可接受的选项白名单。
   *
   * <p>设计要点：这是一个允许列表，运行期会拒绝任何不在其中的选项，避免无效配置。
   *
   * @return 合法选项名集合
   */
  Set<String> validOptions();

  /**
   * 设置策略运行选项并返回策略自身。
   *
   * @param options 选项键值对
   * @return 当前策略实例
   */
  RewriteStrategy options(Map<String, String> options);

  /**
   * 从给定文件中筛选出本策略认为需要重写的目标文件。
   *
   * @param dataFiles 某分区内文件的 {@link FileScanTask} 迭代器
   * @return 仅包含待重写文件的 {@link FileScanTask} 迭代器
   */
  Iterable<FileScanTask> selectFilesToRewrite(Iterable<FileScanTask> dataFiles);

  /**
   * 将待重写的文件扫描任务规划为若干分组，每组作为一个独立可执行单元处理并独立提交。
   *
   * <p>设计意图：分组决定并发粒度与提交粒度，每组最终作为一次独立变更提交，避免单组过大或 单次提交影响过多文件。
   *
   * @param dataFiles 待重写文件的 {@link FileScanTask} 迭代器
   * @return 文件分组列表的迭代器
   */
  Iterable<List<FileScanTask>> planFileGroups(Iterable<FileScanTask> dataFiles);

  /**
   * 依据本策略算法重写一组文件，返回新写入的文件集合。
   *
   * <p>说明：具体实现通常与引擎相关（Spark/Presto/Flink 各自提供写入能力）。
   *
   * @param filesToRewrite 需要一起重写的文件分组
   * @return 新写入的 {@link DataFile} 集合
   */
  Set<DataFile> rewriteFiles(List<FileScanTask> filesToRewrite);
}
