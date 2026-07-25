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

import java.io.Serializable;
import org.apache.iceberg.io.TaskWriter;

/**
 * 文件级说明：{@link TaskWriter} 工厂接口。
 *
 * <p>所属模块：iceberg-flink（sink 子包），定义创建 Iceberg 任务写入器的契约。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以 taskId 和 attemptId 初始化工厂（用于生成唯一文件名）。
 *   <li>创建新的 {@link TaskWriter} 实例（每个 checkpoint 周期创建一个）。
 * </ul>
 *
 * <p>设计意图：实现 Serializable 以支持 Flink 序列化分发到各 TaskManager。 每次 checkpoint 后由 {@link
 * IcebergStreamWriter} 调用 create() 重建 writer。
 *
 * @param <T> 记录数据类型
 */
public interface TaskWriterFactory<T> extends Serializable {

  /**
   * 以 taskId 和 attemptId 初始化工厂。
   *
   * @param taskId 任务 id
   * @param attemptId 任务尝试 id（用于区分重试）
   */
  void initialize(int taskId, int attemptId);

  /**
   * 创建新的 {@link TaskWriter} 实例。
   *
   * <p>设计要点：每个 checkpoint 周期调用一次，创建全新的 writer。
   *
   * @return 新创建的任务写入器
   */
  TaskWriter<T> create();
}
