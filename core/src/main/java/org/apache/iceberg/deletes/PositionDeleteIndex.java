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
package org.apache.iceberg.deletes;

/**
 * 位置删除索引接口：表示某个数据文件中被删除的行位置集合。
 *
 * <p>所属模块：iceberg-core，deletes 包内位置删除的抽象。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义向索引中添加删除位置（单点或区间）的契约。
 *   <li>定义判断某个行位置是否被删除的查询契约。
 * </ul>
 *
 * <p>设计意图：将删除位置的存储与查询解耦，允许不同实现（如位图、有序集合等）按场景选择。 默认实现 {@link BitmapPositionDeleteIndex} 使用 Roaring64
 * 位图，兼顾内存与查询性能。
 *
 * <p>上下游关系：由 {@link Deletes#toPositionIndex} 从位置删除文件构建，被行扫描流程用于跳过已删除行。
 */
public interface PositionDeleteIndex {
  /**
   * 标记单个行位置为已删除。
   *
   * @param position 被删除的行位置
   */
  void delete(long position);

  /**
   * 标记一段连续的行位置区间为已删除。
   *
   * @param posStart 区间起始位置（含）
   * @param posEnd 区间结束位置（不含）
   */
  void delete(long posStart, long posEnd);

  /**
   * 判断指定行位置是否被标记为已删除。
   *
   * @param position 待判定的行位置
   * @return 若该位置在删除集合中返回 true
   */
  boolean isDeleted(long position);

  /** 判断当前索引是否不包含任何已删除位置。 */
  boolean isEmpty();
}
