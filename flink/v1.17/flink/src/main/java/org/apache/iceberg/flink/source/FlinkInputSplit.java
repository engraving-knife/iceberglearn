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
package org.apache.iceberg.flink.source;

import java.util.Arrays;
import javax.annotation.Nullable;
import org.apache.flink.core.io.LocatableInputSplit;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;

/**
 * Flink 输入分片，包装 Iceberg {@link CombinedScanTask} 与所在主机信息。
 *
 * <p>所属模块：iceberg-flink（source 侧），继承 Flink {@link LocatableInputSplit} 以支持本地性。
 *
 * <p>职责：作为旧版 source 接口的分片单元，携带合并扫描任务与可选主机列表。
 *
 * <p>设计意图：复用 LocatableInputSplit 使 Flink 能基于主机做本地性调度。
 *
 * <p>上下游关系：被旧版 FlinkIcebergSource 的枚举器产出、reader 消费。
 */
public class FlinkInputSplit extends LocatableInputSplit {

  private final CombinedScanTask task;

  /**
   * 构造输入分片。
   *
   * @param splitNumber 分片序号
   * @param task 合并扫描任务
   * @param hostnames 所在主机名（可空）
   */
  FlinkInputSplit(int splitNumber, CombinedScanTask task, @Nullable String[] hostnames) {
    super(splitNumber, hostnames);
    this.task = task;
  }

  /** 返回分片对应的合并扫描任务。 */
  CombinedScanTask getTask() {
    return task;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("splitNumber", getSplitNumber())
        .add("task", task)
        .add("hosts", Arrays.toString(getHostnames()))
        .toString();
  }
}
