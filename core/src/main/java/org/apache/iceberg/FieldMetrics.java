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

/**
 * 单个字段的列级度量信息（值计数、null 计数、NaN 计数、上下界）。
 *
 * <p>所属模块：iceberg-core（核心实现层），是写入 manifest 时收集的列级统计载体。
 *
 * <p>职责：承载某个字段（由 id 标识）的 value/null/NaN 计数与上下界， 供 manifest 写入器与扫描规划器使用，以支持谓词下推与统计过滤。
 *
 * <p>设计意图：泛型 T 表示上下界值类型（如 Long/Double），由调用方按字段类型传入； 不可变对象，线程安全。
 *
 * <p>上下游关系：由写入器（如 Parquet 写入流程）收集并写入 manifest； 被扫描规划器读取用于过滤优化。
 */
public class FieldMetrics<T> {
  private final int id;
  private final long valueCount;
  private final long nullValueCount;
  private final long nanValueCount;
  private final T lowerBound;
  private final T upperBound;

  /**
   * 构造方法。
   *
   * @param id 字段 id
   * @param valueCount 值总数（含 null/NaN）
   * @param nullValueCount null 值数
   * @param nanValueCount NaN 值数（仅 double/float 字段非 0）
   * @param lowerBound 下界
   * @param upperBound 上界
   */
  public FieldMetrics(
      int id,
      long valueCount,
      long nullValueCount,
      long nanValueCount,
      T lowerBound,
      T upperBound) {
    this.id = id;
    this.valueCount = valueCount;
    this.nullValueCount = nullValueCount;
    this.nanValueCount = nanValueCount;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
  }

  /** 返回本度量所属字段的 id。 */
  public int id() {
    return id;
  }

  /** 返回该字段的值总数（含 null/NaN/重复值）。 */
  public long valueCount() {
    return valueCount;
  }

  /** 返回该字段的 null 值数。 */
  public long nullValueCount() {
    return nullValueCount;
  }

  /** 返回该字段的 NaN 值数；仅当字段类型为 double/float 时可能非 0。 */
  public long nanValueCount() {
    return nanValueCount;
  }

  /** 返回该字段的下界值。 */
  public T lowerBound() {
    return lowerBound;
  }

  /** 返回该字段的上界值。 */
  public T upperBound() {
    return upperBound;
  }

  /** 返回该字段是否存在有效上下界（即至少有一个非 null 值）。 */
  public boolean hasBounds() {
    return upperBound != null;
  }
}
