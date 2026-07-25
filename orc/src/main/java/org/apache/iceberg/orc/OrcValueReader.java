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
package org.apache.iceberg.orc;

import org.apache.orc.storage.ql.exec.vector.ColumnVector;

/**
 * ORC 字段值读取器接口：从 ORC {@link ColumnVector} 中读取单个字段值。
 *
 * <p>所属模块：iceberg-orc。是字段级读取的契约，由具体实现把列向量中的指定行解码为 Java 值。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #read}：处理 null 与 repeating 向量后委托 {@link #nonNullRead} 读取非空值。
 *   <li>{@link #nonNullRead}：由子类实现具体的非空值解码逻辑。
 *   <li>{@link #setBatchContext}：透传 batch 偏移（默认空实现）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法模式：read() 在 default 方法中统一处理 null/repeating，子类只需实现 nonNullRead。
 *   <li>repeating 向量优化：当 isRepeating 为 true 时所有行共用 row 0 的值，避免重复读取。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.data.orc.GenericOrcReaders} 的各 reader 实现； 被 {@link
 * OrcValueReaders.StructReader} 组合为字段级读取树。
 */
public interface OrcValueReader<T> {
  /**
   * 读取字段值：处理 null 和 repeating 后委托 nonNullRead。
   *
   * <p>逻辑：repeating 向量取 row 0；非 noNulls 时检查 isNull 返回 null；否则调 nonNullRead。
   *
   * @param vector ORC 列向量
   * @param row 行号
   * @return 字段值，null 表示该行为空
   */
  default T read(ColumnVector vector, int row) {
    int rowIndex = vector.isRepeating ? 0 : row;
    if (!vector.noNulls && vector.isNull[rowIndex]) {
      return null;
    } else {
      return nonNullRead(vector, rowIndex);
    }
  }

  /**
   * 读取非空字段值，由子类实现具体解码逻辑。
   *
   * @param vector ORC 列向量
   * @param row 行号（已处理 repeating）
   * @return 解码后的字段值
   */
  T nonNullRead(ColumnVector vector, int row);

  /** 设置当前 batch 在文件中的偏移（默认空实现，需要文件位置的 reader 覆写）。 */
  default void setBatchContext(long batchOffsetInFile) {}
}
