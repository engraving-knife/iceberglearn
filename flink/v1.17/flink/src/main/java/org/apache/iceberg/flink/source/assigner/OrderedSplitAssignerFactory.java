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
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;
import org.apache.iceberg.flink.source.split.SerializableComparator;

/**
 * 创建带排序的默认 split assigner 的工厂。
 *
 * <p>所属模块：iceberg-flink（source assigner 侧）。
 *
 * <p>职责：使用给定的 {@link SerializableComparator} 创建 {@link DefaultSplitAssigner}， 使 split 按比较器定义的顺序分发给
 * reader。
 *
 * <p>设计意图：通过工厂模式解耦 assigner 创建与排序策略，便于扩展不同分配语义。
 *
 * <p>上下游关系：被 {@link IcebergSource} 在需要有序分配时使用。
 */
public class OrderedSplitAssignerFactory implements SplitAssignerFactory {
  private final SerializableComparator<IcebergSourceSplit> comparator;

  /**
   * 构造工厂。
   *
   * @param comparator split 排序比较器
   */
  public OrderedSplitAssignerFactory(SerializableComparator<IcebergSourceSplit> comparator) {
    this.comparator = comparator;
  }

  /** 创建无初始状态的有序 assigner。 */
  @Override
  public SplitAssigner createAssigner() {
    return new DefaultSplitAssigner(comparator);
  }

  /** 从已有状态恢复创建有序 assigner。 */
  @Override
  public SplitAssigner createAssigner(Collection<IcebergSourceSplitState> assignerState) {
    return new DefaultSplitAssigner(comparator, assignerState);
  }
}
