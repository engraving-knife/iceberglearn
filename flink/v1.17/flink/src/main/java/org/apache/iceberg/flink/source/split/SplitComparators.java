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
package org.apache.iceberg.flink.source.split;

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：提供 {@link SerializableComparator} 的实现，用于对 split 排序。
 *
 * <p>所属模块：iceberg-flink（source/split 子包），被有序分配器和 reader 使用。
 *
 * <p>职责：提供按文件序列号排序的 split 比较器，确保 split 按写入顺序被读取。
 *
 * <p>设计意图：将 split 排序策略集中管理，支持按不同维度排序。 fileSequenceNumber 比较器要求数据文件格式为 V2（含 fileSequenceNumber）。
 *
 * <p>上下游关系：被 {@link OrderedSplitAssignerFactory} 和 {@link IcebergSourceReader} 使用。
 */
public class SplitComparators {
  private SplitComparators() {}

  /**
   * 创建按数据文件序列号排序的比较器。
   *
   * <p>逻辑：取两个 split 各自唯一数据文件的 fileSequenceNumber 进行比较； 若序列号相同则按 splitId 比较。要求 split 只包含单个文件（不支持
   * CombinedScanTask）。
   *
   * @return 文件序列号比较器
   */
  public static SerializableComparator<IcebergSourceSplit> fileSequenceNumber() {
    return (IcebergSourceSplit o1, IcebergSourceSplit o2) -> {
      Preconditions.checkArgument(
          o1.task().files().size() == 1 && o2.task().files().size() == 1,
          "Could not compare combined task. Please use 'split-open-file-cost' to prevent combining multiple files to a split");

      Long seq1 = o1.task().files().iterator().next().file().fileSequenceNumber();
      Long seq2 = o2.task().files().iterator().next().file().fileSequenceNumber();

      Preconditions.checkNotNull(
          seq1,
          "Invalid file sequence number: null. Doesn't support splits written with V1 format: %s",
          o1);
      Preconditions.checkNotNull(
          seq2,
          "IInvalid file sequence number: null. Doesn't support splits written with V1 format: %s",
          o2);

      int temp = Long.compare(seq1, seq2);
      if (temp != 0) {
        return temp;
      } else {
        return o1.splitId().compareTo(o2.splitId());
      }
    };
  }
}
