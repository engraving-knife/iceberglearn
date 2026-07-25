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
package org.apache.iceberg.flink.source.assigner;

import java.util.Collection;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;

/**
 * 文件级说明：简单 split 分配器工厂，不保证顺序与本地性。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/assigner 子包）。
 *
 * <p>职责：创建 {@link DefaultSplitAssigner}，不传入 comparator， split 按添加顺序（FIFO）分配给 reader。
 *
 * <p>设计意图：作为默认实现，提供最简单的分配策略， 适用于对读取顺序无要求的场景。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.IcebergSource}， 下游为 {@link
 * DefaultSplitAssigner}。
 */
public class SimpleSplitAssignerFactory implements SplitAssignerFactory {
  /** 默认构造。 */
  public SimpleSplitAssignerFactory() {}

  /** 创建不带状态的简单分配器。 */
  @Override
  public SplitAssigner createAssigner() {
    return new DefaultSplitAssigner(null);
  }

  /** 从 checkpoint 状态恢复创建简单分配器。 */
  @Override
  public SplitAssigner createAssigner(Collection<IcebergSourceSplitState> assignerState) {
    return new DefaultSplitAssigner(null, assignerState);
  }
}
