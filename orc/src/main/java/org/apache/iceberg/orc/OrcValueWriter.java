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

import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.orc.storage.ql.exec.vector.ColumnVector;

/**
 * ORC 字段值写入器接口：把单个字段值写入 ORC {@link ColumnVector}。
 *
 * <p>所属模块：iceberg-orc。是字段级写入的契约，由具体实现把 Java 值编码到列向量。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #write}：处理 null 后委托 {@link #nonNullWrite} 写入非空值。
 *   <li>{@link #nonNullWrite}：由子类实现具体的非空值编码逻辑。
 *   <li>{@link #nullWrite}：null 值的回调（默认空，带统计的 writer 覆写以计数 null）。
 *   <li>{@link #metrics}：返回字段统计流（默认空）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>模板方法模式：write() 在 default 方法中统一处理 null（设 isNull 标记），子类只需实现 nonNullWrite。
 *   <li>nullWrite 钩子让 float/double writer 可统计 null 值数量（ORC 不单独统计）。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.data.orc.GenericOrcWriters} 的各 writer 实现； 被 {@link
 * org.apache.iceberg.data.orc.GenericOrcWriters.StructWriter} 组合为字段级写入树。
 */
public interface OrcValueWriter<T> {

  /**
   * 把值写入 ORC 列向量。
   *
   * <p>逻辑：data 为 null 时设 noNulls=false 和 isNull 标记并调 nullWrite； 否则设 isNull=false 并调 nonNullWrite
   * 写入实际值。
   *
   * @param rowId 列向量中的行号
   * @param data 待写入值
   * @param output 目标列向量
   */
  default void write(int rowId, T data, ColumnVector output) {
    if (data == null) {
      output.noNulls = false;
      output.isNull[rowId] = true;
      nullWrite();
    } else {
      output.isNull[rowId] = false;
      nonNullWrite(rowId, data, output);
    }
  }

  /**
   * 写入非空值，由子类实现具体编码逻辑。
   *
   * @param rowId 列向量中的行号
   * @param data 待写入的非空值
   * @param output 目标列向量
   */
  void nonNullWrite(int rowId, T data, ColumnVector output);

  /** null 值的回调钩子（默认空，带统计的 writer 覆写以计数 null）。 */
  default void nullWrite() {
    // no op
  }

  /** 返回此写入器跟踪的 {@link FieldMetrics} 流（默认空，带统计的 writer 覆写）。 */
  default Stream<FieldMetrics<?>> metrics() {
    return Stream.empty();
  }
}
