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

import org.roaringbitmap.longlong.Roaring64Bitmap;

/**
 * 基于 Roaring64 位图的行位置删除索引实现。
 *
 * <p>所属模块：iceberg-core，定位为 deletes 包内位置删除索引的默认实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>使用 {@link Roaring64Bitmap} 存储被删除的数据行位置（64 位长整型）。
 *   <li>提供单点删除、区间删除、删除判定与空判定能力，是 {@link PositionDeleteIndex} 的内存实现。
 * </ul>
 *
 * <p>设计意图：RoaringBitmap 是一种高效的压缩位图结构，对稀疏/密集的 long 集合均能保持较低的
 * 内存占用与快速的查找性能，适合存放数据文件中需要删除的行号集合。该实现非线程安全， 通常在单个读取任务内构建并使用。
 *
 * <p>上下游关系：实现 {@link PositionDeleteIndex} 接口；由 {@link Deletes#toPositionIndex} 构建，
 * 被读取侧用于判断某行是否在删除集合中。
 */
class BitmapPositionDeleteIndex implements PositionDeleteIndex {
  private final Roaring64Bitmap roaring64Bitmap;

  BitmapPositionDeleteIndex() {
    roaring64Bitmap = new Roaring64Bitmap();
  }

  /**
   * 标记单个行位置为已删除。
   *
   * @param position 被删除的行位置
   */
  @Override
  public void delete(long position) {
    roaring64Bitmap.add(position);
  }

  /**
   * 标记一段连续的行位置区间为已删除。
   *
   * @param posStart 区间起始位置（含）
   * @param posEnd 区间结束位置（不含）
   */
  @Override
  public void delete(long posStart, long posEnd) {
    roaring64Bitmap.add(posStart, posEnd);
  }

  /**
   * 判断指定行位置是否被标记为已删除。
   *
   * @param position 待判定的行位置
   * @return 若该位置在删除集合中返回 true
   */
  @Override
  public boolean isDeleted(long position) {
    return roaring64Bitmap.contains(position);
  }

  /** 判断当前删除索引是否为空（不包含任何已删除位置）。 */
  @Override
  public boolean isEmpty() {
    return roaring64Bitmap.isEmpty();
  }
}
