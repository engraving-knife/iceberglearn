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
 * Float 类型字段的指标统计：Iceberg 内部追踪的字段级指标，仅由 Parquet/ORC 写入器使用。
 *
 * <p>所属模块：iceberg-core，继承 {@link FieldMetrics}，是浮点字段统计的具体实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在写入数据文件时累计 Float 字段的值计数、NaN 计数、上下界。
 *   <li>由于 Parquet/ORC 文件本身已记录大部分统计，写入器实际只追踪 NaN 计数； 其他指标通过抛异常保证不被误用。
 * </ul>
 *
 * <p>设计意图：Float 的 NaN 无法直接比较大小，需单独计数；上下界仅在存在非 NaN 值时才有意义。 {@link Builder} 模式累加值后统一构建，避免中间状态不一致。
 *
 * <p>上下游关系：被 Parquet/ORC 写入器在写文件时调用收集统计，最终写入 manifest 供读取层过滤。
 */
public class FloatFieldMetrics extends FieldMetrics<Float> {

  private FloatFieldMetrics(
      int id, long valueCount, long nanValueCount, Float lowerBound, Float upperBound) {
    super(id, valueCount, 0L, nanValueCount, lowerBound, upperBound);
  }

  /**
   * 为指定字段 ID 创建指标构建器。
   *
   * @param id 字段 ID
   * @return 新的 {@link Builder}
   */
  public Builder builderFor(int id) {
    return new Builder(id);
  }

  /** Float 字段指标构建器：累加值并维护上下界与 NaN 计数。 */
  public static class Builder {
    private final int id;
    private long valueCount = 0;
    private long nanValueCount = 0;
    private float lowerBound = Float.POSITIVE_INFINITY;
    private float upperBound = Float.NEGATIVE_INFINITY;

    public Builder(int id) {
      this.id = id;
    }

    /**
     * 累加一个值：更新计数、NaN 计数与上下界。
     *
     * <p>逻辑：NaN 只增加 nanValueCount 不参与上下界比较；非 NaN 值用 {@link Float#compare} 比较更新
     * lowerBound/upperBound（处理 -0.0f 与 0.0f）。
     *
     * @param value 待累加的值
     */
    public void addValue(float value) {
      this.valueCount++;
      if (Float.isNaN(value)) {
        this.nanValueCount++;
      } else {
        if (Float.compare(value, lowerBound) < 0) {
          this.lowerBound = value;
        }
        if (Float.compare(value, upperBound) > 0) {
          this.upperBound = value;
        }
      }
    }

    /**
     * 构建最终指标：仅当存在非 NaN 值时才设置上下界，否则上下界为 null。
     *
     * @return 不可变的 {@link FloatFieldMetrics}
     */
    public FloatFieldMetrics build() {
      boolean hasBound = valueCount - nanValueCount > 0;
      return new FloatFieldMetrics(
          id,
          valueCount,
          nanValueCount,
          hasBound ? lowerBound : null,
          hasBound ? upperBound : null);
    }
  }
}
