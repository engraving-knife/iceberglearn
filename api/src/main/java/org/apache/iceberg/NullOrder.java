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
 * 文件级说明：空值排序位置枚举。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：定义排序时 null 值相对于非 null 值的位置——{@link #NULLS_FIRST}（排在最前）或 {@link #NULLS_LAST}（排在最后）。
 *
 * <p>设计意图：排序序号（{@link SortOrder}）中每个排序字段都需指定空值位置，以枚举形式固化 两种选择，并通过 {@link #toString()} 输出 SQL
 * 风格的字符串（"NULLS FIRST" / "NULLS LAST"）， 便于序列化与可读性。
 *
 * <p>上下游关系：被 {@link SortOrder}、{@link SortOrderBuilder}、{@link UnboundSortOrder} 等 排序相关类使用。
 */
public enum NullOrder {
  /** 空值排在最前。 */
  NULLS_FIRST,
  /** 空值排在最后。 */
  NULLS_LAST;

  /**
   * 返回该空值排序位置的 SQL 风格字符串表示。
   *
   * <p>逻辑：按枚举值返回 "NULLS FIRST" 或 "NULLS LAST"；未知值抛出 {@link IllegalArgumentException}。
   *
   * @return SQL 风格字符串
   */
  @Override
  public String toString() {
    switch (this) {
      case NULLS_FIRST:
        return "NULLS FIRST";
      case NULLS_LAST:
        return "NULLS LAST";
      default:
        throw new IllegalArgumentException("Unexpected null order: " + this);
    }
  }
}
