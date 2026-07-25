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

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 扫描接口：定义对表进行不可变、可链式配置的扫描契约。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供投影、列选择、过滤、大小写敏感性、列统计、残留处理等扫描配置方法。
 *   <li>提供 {@link #planFiles()} 与 {@link #planTasks()} 两类任务规划入口。
 *   <li>暴露分片大小、回看、打开文件成本等执行参数。
 * </ul>
 *
 * <p>设计意图：Scan 对象不可变且线程安全，所有"配置类"方法（{@link #select(Collection)}、 {@link #filter(Expression)}
 * 等）都返回新的 Scan 实例，便于在多线程间共享与缓存。 泛型 {@code ThisT} 让链式调用返回类型收敛到具体子类型；{@code T}/{@code G} 分别
 * 约束任务与任务组的类型。
 *
 * <p>上下游关系：由 {@link Table#newScan()} 等方法创建；下游由 core 模块实现任务规划， 被引擎层消费以生成输入分片。
 *
 * @param <ThisT> 子类型自身，用于链式调用返回类型收敛
 * @param <T> 本扫描产出的任务类型
 * @param <G> 本扫描产出的任务组类型
 */
public interface Scan<ThisT, T extends ScanTask, G extends ScanTaskGroup<T>> {
  /**
   * 基于当前扫描配置创建一个新扫描，并覆盖 {@link Table} 的某个属性。
   *
   * <p>未知属性会被忽略。
   *
   * @param property 待覆盖的表属性名
   * @param value 覆盖值
   * @return 含该属性覆盖的新扫描
   */
  ThisT option(String property, String value);

  /**
   * 基于当前扫描创建一个使用指定 {@link Schema} 作为投影的新扫描。
   *
   * @param schema 投影 schema
   * @return 含该投影的新扫描
   */
  ThisT project(Schema schema);

  /**
   * 基于当前扫描创建一个指定列名大小写敏感性的新扫描。
   *
   * <p>仅在通过 {@link #select(java.util.Collection)} 选列时生效，默认为 true。
   *
   * @param caseSensitive 是否大小写敏感
   * @return 含该设置的新扫描
   */
  ThisT caseSensitive(boolean caseSensitive);

  /**
   * 返回本扫描是否对列名大小写敏感。
   *
   * @return 大小写敏感返回 true
   */
  boolean isCaseSensitive();

  /**
   * 基于当前扫描创建一个会在读取数据文件时一并加载列统计的新扫描。
   *
   * <p>列统计包括：值计数、null 值计数、下界、上界等。
   *
   * @return 含列统计加载的新扫描
   */
  ThisT includeColumnStats();

  /**
   * 基于当前扫描创建一个只读取指定数据列的新扫描。
   *
   * <p>逻辑：生成的预期 schema 包含所有被选中的列，以及过滤表达式中引用到的列。
   *
   * @param columns 表 schema 中的列名集合
   * @return 含指定投影列的新扫描
   */
  ThisT select(Collection<String> columns);

  /**
   * {@link #select(Collection)} 的变参便捷重载。
   *
   * @param columns 列名
   * @return 含指定投影列的新扫描
   */
  default ThisT select(String... columns) {
    return select(Lists.newArrayList(columns));
  }

  /**
   * 基于当前扫描创建一个用给定 {@link Expression} 过滤结果的新扫描。
   *
   * @param expr 过滤表达式
   * @return 含该过滤的新扫描
   */
  ThisT filter(Expression expr);

  /**
   * 返回本扫描当前的过滤表达式。
   *
   * @return 过滤表达式
   */
  Expression filter();

  /**
   * 基于当前扫描创建一个只对文件做过滤、不对文件内行做残留过滤的新扫描。
   *
   * @return 不做行级残留过滤的新扫描
   */
  ThisT ignoreResiduals();

  /**
   * 基于当前扫描创建一个使用指定线程池规划任务的新扫描。
   *
   * <p>未指定时使用默认工作线程池。
   *
   * @param executorService 任务规划使用的线程池
   * @return 使用该线程池的新扫描
   */
  ThisT planWith(ExecutorService executorService);

  /**
   * 返回本扫描的投影 schema。
   *
   * <p>逻辑：若通过 {@link #project(Schema)} 直接设置则返回该 schema；若通过 {@link #select(Collection)}
   * 设置，则返回包含被选列与过滤表达式中引用列的投影 schema。
   *
   * @return 投影 schema
   */
  Schema schema();

  /**
   * 规划任务：每个任务读取一整个文件。
   *
   * <p>如需规划"按字节切分大文件/合并小文件"的均衡任务，请使用 {@link #planTasks()}。
   *
   * @return 整文件扫描任务的可关闭迭代器
   */
  CloseableIterable<T> planFiles();

  /**
   * 规划均衡任务组：通过对大任务切分、小任务合并得到大小均衡的任务组。
   *
   * <p>任务组可能读取部分文件、多个文件或两者兼有。
   *
   * @return 均衡任务组的可关闭迭代器
   */
  CloseableIterable<G> planTasks();

  /** 返回本扫描的目标分片大小（字节）。 */
  long targetSplitSize();

  /** 返回本扫描的分片回看（split lookback）数量。 */
  int splitLookback();

  /** 返回本扫描的打开文件成本阈值（字节）。 */
  long splitOpenFileCost();

  /**
   * 基于当前扫描创建一个把扫描指标额外上报给指定 {@link MetricsReporter} 的新扫描。
   *
   * <p>默认实现抛出 {@link UnsupportedOperationException}，由具体子类提供实现。
   */
  default ThisT metricsReporter(MetricsReporter reporter) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " doesn't implement metricsReporter");
  }
}
