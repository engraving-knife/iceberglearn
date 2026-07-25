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
 * Double 类型字段的指标统计：用于 Parquet/ORC 写入器内部跟踪 NaN 计数与上下界。
 *
 * <p>所属模块：iceberg-core（写入期指标收集层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在写入数据时累计 Double 字段的值计数、NaN 计数、上下界。
 *   <li>区分 NaN 与普通数值：NaN 不参与上下界统计，但单独计数以便后续过滤优化。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Parquet/ORC 文件自身会通过文件统计信息保留大部分指标，但 NaN 计数需要写入器额外 跟踪（文件格式不直接提供）。本类专门负责这一场景，并在父类 {@link
 *       FieldMetrics} 中将未由写入器维护的指标访问设为抛异常，避免误用。
 *   <li>NaN 比较使用 {@link Double#compare} 而非直接比较运算符，确保与排序语义一致。
 *   <li>当所有值都是 NaN 时上下界返回 null，避免返回无意义的 POSITIVE/NEGATIVE_INFINITY。
 * </ul>
 *
 * <p>上下游关系：被 Parquet/ORC 写入器在写入每个值时调用 {@link Builder#addValue}； 最终通过 {@link Builder#build()}
 * 生成不可变指标对象，作为文件元数据的一部分。
 */
public class DoubleFieldMetrics extends FieldMetrics<Double> {

  /**
   * 私有构造：通过 {@link Builder} 创建实例。
   *
   * <p>设计要点：nullValueCount 固定为 0，因为 Parquet/ORC 文件统计已包含 null 计数， 写入器不需要重复跟踪。
   *
   * @param id 字段 id
   * @param valueCount 值总数（含 NaN）
   * @param nanValueCount NaN 值数量
   * @param lowerBound 下界；若全部为 NaN 则为 null
   * @param upperBound 上界；若全部为 NaN 则为 null
   */
  private DoubleFieldMetrics(
      int id, long valueCount, long nanValueCount, Double lowerBound, Double upperBound) {
    super(id, valueCount, 0L, nanValueCount, lowerBound, upperBound);
  }

  /**
   * Double 字段指标构建器：累计写入值并最终生成 {@link DoubleFieldMetrics}。
   *
   * <p>设计意图：上下界初值分别设为 {@link Double#POSITIVE_INFINITY} 和 {@link
   * Double#NEGATIVE_INFINITY}，使首个有效值即可更新边界；通过 hasBound 标志 在没有有效值时返回 null，避免暴露哨兵值。
   */
  public static class Builder {
    private final int id;
    private long valueCount = 0;
    private long nanValueCount = 0;
    private double lowerBound = Double.POSITIVE_INFINITY;
    private double upperBound = Double.NEGATIVE_INFINITY;

    /**
     * 构造指定字段的构建器。
     *
     * @param id 字段 id
     */
    public Builder(int id) {
      this.id = id;
    }

    /**
     * 累加一个 Double 值到指标统计。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>valueCount 自增。
     *   <li>若为 NaN，nanValueCount 自增，不参与上下界更新。
     *   <li>否则用 {@link Double#compare} 与当前上下界比较并更新。
     * </ul>
     *
     * @param value 待统计的 double 值
     */
    public void addValue(double value) {
      this.valueCount++;
      if (Double.isNaN(value)) {
        this.nanValueCount++;
      } else {
        if (Double.compare(value, lowerBound) < 0) {
          this.lowerBound = value;
        }
        if (Double.compare(value, upperBound) > 0) {
          this.upperBound = value;
        }
      }
    }

    /**
     * 构建最终的字段指标对象。
     *
     * <p>逻辑：若存在至少一个非 NaN 值（valueCount - nanValueCount > 0），则返回实际上下界； 否则上下界返回 null。
     *
     * @return 不可变的 {@link DoubleFieldMetrics}
     */
    public DoubleFieldMetrics build() {
      boolean hasBound = valueCount - nanValueCount > 0;
      return new DoubleFieldMetrics(
          id,
          valueCount,
          nanValueCount,
          hasBound ? lowerBound : null,
          hasBound ? upperBound : null);
    }
  }
}
