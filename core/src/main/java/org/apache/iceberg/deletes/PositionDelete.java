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

import org.apache.iceberg.StructLike;

/**
 * 位置删除记录：表示一条按行位置删除的记录，实现 {@link StructLike} 以便作为列式写入的行。
 *
 * <p>所属模块：iceberg-core，deletes 包内位置删除的数据载体。
 *
 * <p>职责：承载一条位置删除的三元组信息——数据文件路径（path）、行位置（pos）、被删除的行数据（row， 可为 null）。以 StructLike
 * 形式暴露三个字段，便于直接写入位置删除文件。
 *
 * <p>设计意图：位置删除文件按规范需包含 file_path、pos 两列，可选地携带被删除的行数据。 本类通过实现 StructLike
 * 复用底层列式写入器（FileAppender），避免为删除记录单独实现写路径。 字段顺序与 {@link
 * org.apache.iceberg.MetadataColumns#DELETE_FILE_PATH}、 {@link
 * org.apache.iceberg.MetadataColumns#DELETE_FILE_POS} 对应：0=path, 1=pos, 2=row。
 *
 * <p>上下游关系：被 {@link PositionDeleteWriter}、{@link SortingPositionOnlyDeleteWriter} 写入； 由读取侧的 {@link
 * Deletes} 解析位置删除文件时使用。
 *
 * @param <R> 被删除行数据的类型
 */
public class PositionDelete<R> implements StructLike {
  /**
   * 创建一个新的空的位置删除记录实例。
   *
   * @param <T> 被删除行数据的类型
   * @return 新的 PositionDelete 实例
   */
  public static <T> PositionDelete<T> create() {
    return new PositionDelete<>();
  }

  private CharSequence path;
  private long pos;
  private R row;

  private PositionDelete() {}

  /**
   * 设置位置删除的三元组值并返回本对象（便于链式调用）。
   *
   * @param newPath 数据文件路径
   * @param newPos 行位置
   * @param newRow 被删除的行数据，可为 null（仅写位置删除时）
   * @return 本对象
   */
  public PositionDelete<R> set(CharSequence newPath, long newPos, R newRow) {
    this.path = newPath;
    this.pos = newPos;
    this.row = newRow;
    return this;
  }

  /** 返回字段数，固定为 3（path/pos/row）。 */
  @Override
  public int size() {
    return 3;
  }

  /** 返回数据文件路径。 */
  public CharSequence path() {
    return path;
  }

  /** 返回行位置。 */
  public long pos() {
    return pos;
  }

  /** 返回被删除的行数据，可能为 null。 */
  public R row() {
    return row;
  }

  /**
   * 按列位置读取字段值。
   *
   * <p>逻辑：0 返回 path，1 返回 pos（装箱为 Long），2 返回 row；其余位置抛出 {@link IllegalArgumentException}。
   *
   * @param colPos 列位置（0/1/2）
   * @param javaClass 期望的 Java 类型（仅用于泛型擦除后强转）
   * @return 对应字段值
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T get(int colPos, Class<T> javaClass) {
    switch (colPos) {
      case 0:
        return (T) path;
      case 1:
        return (T) (Long) pos;
      case 2:
        return (T) row;
      default:
        throw new IllegalArgumentException("No column at position " + colPos);
    }
  }

  /**
   * 按列位置写入字段值。
   *
   * <p>逻辑：0 写 path，1 写 pos（拆箱 Long），2 写 row；其余位置抛出 {@link IllegalArgumentException}。
   *
   * @param colPos 列位置（0/1/2）
   * @param value 字段值
   */
  @Override
  public <T> void set(int colPos, T value) {
    switch (colPos) {
      case 0:
        this.path = (CharSequence) value;
        break;
      case 1:
        this.pos = (Long) value;
        break;
      case 2:
        this.row = (R) value;
        break;
      default:
        throw new IllegalArgumentException("No column at position " + colPos);
    }
  }
}
