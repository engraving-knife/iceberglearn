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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.ContentScanTask;

/**
 * 内容文件重写器接口。
 *
 * <p>所属模块：iceberg-core 的 actions 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义内容文件（数据文件/删除文件）重写的统一契约：选项校验、初始化、规划文件分组、执行重写。
 *   <li>整个重写按分区与基于大小的分组拆分为若干文件组（file group），每组由单个框架动作处理 （例如 Spark 中每组对应一个独立的 Spark job）。
 * </ul>
 *
 * <p>设计意图：取代旧版 {@link RewriteStrategy}，将选文件、分组与重写抽象为统一接口， 通过泛型同时支持数据文件与删除文件的重写，便于不同引擎实现复用 core
 * 侧的公共逻辑。
 *
 * <p>上下游关系：被 {@link SizeBasedFileRewriter} 等抽象实现；由具体引擎的重写动作调用。
 *
 * @param <T> 读取内容文件的任务类型
 * @param <F> 内容文件类型
 */
public interface FileRewriter<T extends ContentScanTask<F>, F extends ContentFile<F>> {

  /** 返回本重写器的描述信息，默认为类全限定名。 */
  default String description() {
    return getClass().getName();
  }

  /** 返回本重写器支持的选项白名单。运行期仅接受其中的选项，其余将被拒绝。 */
  Set<String> validOptions();

  /**
   * 使用给定选项初始化本重写器。
   *
   * @param options 初始化选项键值对
   */
  void init(Map<String, String> options);

  /**
   * 根据扫描任务挑选需要重写的文件，并将其划分为若干文件分组，每组作为一个可执行单元（如 Spark job）重写。
   *
   * @param tasks 某分区内文件的扫描任务迭代器
   * @return 文件分组列表的迭代器，每组单独重写
   */
  Iterable<List<T>> planFileGroups(Iterable<T> tasks);

  /**
   * 重写由给定扫描任务列表表示的一组文件。
   *
   * <p>实现通常与引擎相关（如 Spark、Flink、Trino）。
   *
   * @param group 需一起重写的文件扫描任务分组
   * @return 新写入的文件集合
   */
  Set<F> rewrite(List<T> group);
}
