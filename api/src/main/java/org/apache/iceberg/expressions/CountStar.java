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
 * COUNT(*) 聚合：统计总行数，不关心具体字段值。
 *
 * <p>所属模块：iceberg-api（聚合分支具体实现之一，对应 {@link Operation#COUNT_STAR}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>行级求值固定返回 1L（每行计 1）。
 *   <li>文件级求值返回 {@link DataFile#recordCount()}，缺失（&lt;0）时返回 null。
 * </ul>
 *
 * <p>设计意图：复用 {@link CountAggregate} 的累加器，仅覆盖单点贡献逻辑；hasValue 校验 文件是否提供了有效 recordCount，避免静默计入错误统计。
 *
 * <p>上下游关系：被 {@link AggregateEvaluator} 通过 newAggregator 驱动；常用于表/分区的 行数统计。
 *
 * @param <T> term 的 Java 类型（COUNT(*) 下实际不使用具体值）
 */
public class CountStar<T> extends CountAggregate<T> {
  /**
   * 构造 COUNT(*) 聚合。
   *
   * @param term 已绑定 term（语义上不参与计数，仅用于与基类协议一致）
   */
  protected CountStar(BoundTerm<T> term) {
    super(Operation.COUNT_STAR, term);
  }

  /**
   * 行级贡献：每行固定计 1。
   *
   * @param row 一行数据
   * @return 1L
   */
  @Override
  protected Long countFor(StructLike row) {
    return 1L;
  }

  /**
   * 判断文件是否提供了有效行数。
   *
   * <p>逻辑：recordCount &gt;= 0 视为有效。
   *
   * @param file 数据文件
   * @return 有效返回 true
   */
  @Override
  protected boolean hasValue(DataFile file) {
    return file.recordCount() >= 0;
  }

  /**
   * 文件级贡献：返回文件记录数。
   *
   * <p>逻辑：recordCount &lt; 0 视为缺失，返回 null；否则返回 recordCount。
   *
   * @param file 数据文件
   * @return 文件记录数，缺失时返回 null
   */
  @Override
  protected Long countFor(DataFile file) {
    long count = file.recordCount();
    if (count < 0) {
      return null;
    }

    return count;
  }
}
