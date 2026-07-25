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
package org.apache.iceberg.flink.source.enumerator;

import java.io.Closeable;
import org.apache.flink.annotation.Internal;

/**
 * 文件级说明：流式 split 规划器接口，用于持续模式下发现新增文件并规划 split。
 *
 * <p>所属模块：iceberg-flink（source/enumerator 子包），供流式 source 使用。
 *
 * <p>职责：从上次枚举位置（lastPosition）到当前表快照之间，发现新增的追加文件并规划为 split。
 *
 * <p>设计意图：提取为接口以便单元测试中替换不同的 split 规划实现。
 *
 * <p>上下游关系：被流式 enumerator 调用；实现类（如 ContinuousSplitPlannerImpl）负责具体的 文件扫描和 split 生成。
 */
@Internal
public interface ContinuousSplitPlanner extends Closeable {

  /**
   * 发现 lastPosition 到当前表快照之间追加的文件并规划 split。
   *
   * @param lastPosition 上次枚举的位置（snapshotId）
   * @return 连续枚举结果
   */
  ContinuousEnumerationResult planSplits(IcebergEnumeratorPosition lastPosition);
}
