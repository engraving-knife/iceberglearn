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
package org.apache.iceberg.arrow.vectorized;

import java.util.Arrays;

/**
 * 文件级说明：按行下标跟踪空值状态的轻量持有器。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路的空值元数据）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以字节数组记录每个下标是否为 null，并提供单点与区间设置接口。
 *   <li>维护空值计数，支持查询与重置。
 * </ul>
 *
 * <p>设计意图：为性能与简洁，要求各 set 方法的 index 参数单调递增；区间设置利用 {@link System#arraycopy} 拷贝预填充的 nulls/nonNulls
 * 模板数组，避免逐元素赋值。
 *
 * <p>上下游关系：由 {@link VectorHolder} 持有；被 {@link ColumnVector}、 {@link DictEncodedArrowConverter}
 * 查询空值状态。
 */
public class NullabilityHolder {
  private final byte[] isNull;
  private int numNulls;
  private final byte[] nonNulls;
  private final byte[] nulls;

  /**
   * 构造指定容量的空值持有器，预填充 nulls（1）与 nonNulls（0）模板数组。
   *
   * @param size 容量（行数）
   */
  public NullabilityHolder(int size) {
    this.isNull = new byte[size];
    this.nonNulls = new byte[size];
    Arrays.fill(nonNulls, (byte) 0);
    this.nulls = new byte[size];
    Arrays.fill(nulls, (byte) 1);
  }

  /**
   * 返回容量。
   *
   * @return 行数
   */
  public int size() {
    return isNull.length;
  }

  /**
   * 标记指定下标为 null 并递增空值计数。
   *
   * @param index 行下标（应单调递增）
   */
  public void setNull(int index) {
    isNull[index] = 1;
    numNulls++;
  }

  /**
   * 标记指定下标为非 null。
   *
   * @param index 行下标（应单调递增）
   */
  public void setNotNull(int index) {
    isNull[index] = 0;
  }

  /**
   * 批量标记从 startIndex 起连续 num 个下标为 null，并累加空值计数。
   *
   * @param startIndex 起始下标
   * @param num 数量
   */
  public void setNulls(int startIndex, int num) {
    System.arraycopy(nulls, 0, isNull, startIndex, num);
    numNulls += num;
  }

  /**
   * 批量标记从 startIndex 起连续 num 个下标为非 null。
   *
   * @param startIndex 起始下标
   * @param num 数量
   */
  public void setNotNulls(int startIndex, int num) {
    System.arraycopy(nonNulls, 0, isNull, startIndex, num);
  }

  /**
   * 判断指定下标是否为 null。
   *
   * @param index 行下标
   * @return 为 null 返回 1，否则 0
   */
  public byte isNullAt(int index) {
    return isNull[index];
  }

  /**
   * 是否存在空值。
   *
   * @return 存在空值返回 true
   */
  public boolean hasNulls() {
    return numNulls > 0;
  }

  /**
   * 返回空值数量。
   *
   * @return 空值数
   */
  public int numNulls() {
    return numNulls;
  }

  /** 重置空值计数为 0（供下一批复用）。 */
  public void reset() {
    numNulls = 0;
  }
}
