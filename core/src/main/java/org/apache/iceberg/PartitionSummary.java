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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.ManifestFile.PartitionFieldSummary;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.NaNUtil;

/**
 * 单 manifest 的分区字段统计聚合器。
 *
 * <p>所属模块：iceberg-core。职责：在写入 manifest 时收集每个分区字段的最小值/最大值/是否存在 null 等 统计信息，最终输出 {@link
 * ManifestFile.PartitionFieldSummary} 列表，写入 manifest 文件 footer。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>按分区列类型分别构建 {@link PartitionFieldStats}，利用泛型保证类型安全。
 *   <li>NaN 特殊处理：通过 {@link NaNUtil} 把 NaN 视为可比较值，正确计算 min/max。
 *   <li>仅包级可见：作为 manifest 写入器的内部协作类，不对外暴露。
 * </ul>
 *
 * <p>上下游关系：被 {@link ManifestWriter} 在 flush manifest 时调用；产物被扫描层用于分区裁剪。
 */
class PartitionSummary {
  private final PartitionFieldStats<?>[] fields;
  private final Class<?>[] javaClasses;

  PartitionSummary(PartitionSpec spec) {
    this.javaClasses = spec.javaClasses();
    this.fields = new PartitionFieldStats[javaClasses.length];
    List<Types.NestedField> partitionFields = spec.partitionType().fields();
    for (int i = 0; i < fields.length; i += 1) {
      this.fields[i] = new PartitionFieldStats<>(partitionFields.get(i).type());
    }
  }

  /**
   * 把当前累积的各分区字段统计转换为 {@link PartitionFieldSummary} 列表，用于写入 manifest footer。
   *
   * @return 每个分区字段对应的统计摘要列表
   */
  List<PartitionFieldSummary> summaries() {
    return Arrays.stream(fields).map(PartitionFieldStats::toSummary).collect(Collectors.toList());
  }

  /**
   * 用一条分区键值更新所有分区字段的统计（min/max/containsNull/containsNaN）。
   *
   * @param partitionKey 单条数据的分区值
   */
  public void update(StructLike partitionKey) {
    updateFields(partitionKey);
  }

  /**
   * 遍历所有分区字段，按对应 Java 类型从 key 中取出值并更新统计。
   *
   * <p>使用泛型 {@code <T>} 是为了类型安全地把 {@link PartitionFieldStats} 与 {@link Class} 关联起来，避免显式强制转换散落。
   *
   * @param key 单条数据的分区值
   */
  @SuppressWarnings("unchecked")
  private <T> void updateFields(StructLike key) {
    for (int i = 0; i < javaClasses.length; i += 1) {
      PartitionFieldStats<T> stats = (PartitionFieldStats<T>) fields[i];
      Class<T> javaClass = (Class<T>) javaClasses[i];
      stats.update(key.get(i, javaClass));
    }
  }

  private static class PartitionFieldStats<T> {
    private final Type type;
    private final Comparator<T> comparator;

    private boolean containsNull = false;
    private boolean containsNaN = false;
    private T min = null;
    private T max = null;

    private PartitionFieldStats(Type type) {
      this.type = type;
      this.comparator = Comparators.forType(type.asPrimitiveType());
    }

    public PartitionFieldSummary toSummary() {
      return new GenericPartitionFieldSummary(
          containsNull,
          containsNaN,
          min != null ? Conversions.toByteBuffer(type, min) : null,
          max != null ? Conversions.toByteBuffer(type, max) : null);
    }

    void update(T value) {
      if (value == null) {
        this.containsNull = true;
      } else if (NaNUtil.isNaN(value)) {
        this.containsNaN = true;
      } else if (min == null) {
        this.min = value;
        this.max = value;
      } else {
        if (comparator.compare(value, min) < 0) {
          this.min = value;
        }
        if (comparator.compare(max, value) < 0) {
          this.max = value;
        }
      }
    }
  }
}
