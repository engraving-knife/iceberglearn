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

import java.util.Map;
import java.util.Set;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;

/**
 * 将等值删除转换为位置删除的策略接口。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义将等值删除文件（equality delete）转换为位置删除文件（positional delete）的策略契约。
 *   <li>提供选项配置、选择待转换文件、分组规划以及执行转换的统一方法。
 * </ul>
 *
 * <p>设计意图：等值删除在读取时需要与数据文件做关联匹配，读放大； 位置删除只标记被删行的位置，读取效率更高。本接口定义从等值删除到位置删除转换的策略抽象， 具体实现由各引擎模块提供。
 *
 * <p>上下游关系：被具体引擎模块的转换等值删除动作使用；依赖 {@link Table}、{@link DeleteFile}、{@link FileScanTask}。
 */
public interface ConvertEqualityDeleteStrategy {

  /** 返回本转换删除策略的名称。 */
  String name();

  /** 返回本转换策略所操作的目标表。 */
  Table table();

  /** 返回本策略可接受的选项白名单。这是一个允许列表， 未列出的选项会在运行时被拒绝。 */
  Set<String> validOptions();

  /** 设置本策略使用的选项。 */
  ConvertEqualityDeleteStrategy options(Map<String, String> options);

  /**
   * 选择需要转换的删除文件。
   *
   * @param deleteFiles 一个分组中的删除文件迭代器
   * @return 需转换的原始删除文件迭代器
   */
  Iterable<DeleteFile> selectDeleteFiles(Iterable<DeleteFile> deleteFiles);

  /**
   * 将删除文件分组为若干可执行单元，每组将作为一次独立的提交。 这些分组最终会由底层 Action 作为任务运行。
   *
   * @param dataFiles 包含待转换 DeleteFile 的数据文件迭代器
   * @return FileScanTask 列表的迭代器，每个列表代表一个一起处理的分组
   */
  Iterable<Iterable<FileScanTask>> planDeleteFileGroups(Iterable<FileScanTask> dataFiles);

  /**
   * 定义如何执行删除转换。
   *
   * @param deleteFilesToConvert 待一起转换的一组文件
   * @return 用于替换原始删除文件的新删除文件迭代器
   */
  Iterable<DeleteFile> convertDeleteFiles(Iterable<DeleteFile> deleteFilesToConvert);
}
