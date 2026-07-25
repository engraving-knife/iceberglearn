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
package org.apache.iceberg.expressions;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.StructLike;

/**
 * 计数聚合基类：统计某个 term 的非空计数（COUNT）或行计数（COUNT_STAR）。
 *
 * <p>所属模块：iceberg-api（聚合分支的具体实现之一，对应 {@link Operation#COUNT}）。
 *
 * <p>职责：把行级/文件级计数语义收敛为对 {@link #countFor(StructLike)} / {@link #countFor(DataFile)}
 * 的统一调用，并把“按贡献值累加”实现交给 {@link CountAggregator}。
 *
 * <p>设计意图：把“如何取单点贡献值”与“如何累积”分离——子类只需覆盖 countFor 决定单点贡献 （COUNT 计非空、COUNT_STAR 计所有行），累加逻辑由本基类的
 * CountAggregator 统一实现。
 *
 * <p>上下游关系：被 {@link AggregateEvaluator} 通过 {@link #newAggregator()} 持有并驱动； 子类如 {@link CountStar}
 * 提供具体计数语义。
 *
 * @param <T> 计数输入值的 Java 类型
 */
public class CountAggregate<T> extends BoundAggregate<T, Long> {
  /**
   * 构造计数聚合。
   *
   * @param op 操作类型
   * @param term 已绑定 term
   */
  protected CountAggregate(Operation op, BoundTerm<T> term) {
    super(op, term);
  }

  /**
   * 行级求值：返回该行对计数的贡献值。
   *
   * <p>逻辑：委托 {@link #countFor(StructLike)}。
   *
   * @param struct 一行数据
   * @return 该行的计数贡献
   */
  @Override
  public Long eval(StructLike struct) {
    return countFor(struct);
  }

  /**
   * 文件级求值：返回该文件对计数的贡献值。
   *
   * <p>逻辑：委托 {@link #countFor(DataFile)}。
   *
   * @param file 数据文件
   * @return 该文件的计数贡献
   */
  @Override
  public Long eval(DataFile file) {
    return countFor(file);
  }

  /**
   * 计算一行数据对计数的贡献值（由子类覆盖）。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}。
   *
   * @param row 一行数据
   * @return 该行的计数贡献
   */
  protected Long countFor(StructLike row) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement countFor(StructLike)");
  }

  /**
   * 计算一个数据文件对计数的贡献值（由子类覆盖）。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}。
   *
   * @param file 数据文件
   * @return 该文件的计数贡献
   */
  protected Long countFor(DataFile file) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement countFor(DataFile)");
  }

  /**
   * 创建计数聚合器。
   *
   * @return 新的 {@link CountAggregator}
   */
  @Override
  public Aggregator<Long> newAggregator() {
    return new CountAggregator<>(this);
  }

  /**
   * 计数聚合器：把每个非 null 贡献值累加到内部计数器。
   *
   * <p>设计意图：复用 {@link NullSafeAggregator} 的 null 安全逻辑，自身只维护一个 long 计数器。
   *
   * @param <T> 聚合输入类型
   */
  private static class CountAggregator<T> extends NullSafeAggregator<T, Long> {
    private Long count = 0L;

    CountAggregator(BoundAggregate<T, Long> aggregate) {
      super(aggregate);
    }

    @Override
    protected void update(Long value) {
      count += value;
    }

    @Override
    protected Long current() {
      return count;
    }
  }
}
