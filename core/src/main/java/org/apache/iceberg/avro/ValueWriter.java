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
package org.apache.iceberg.avro;

import java.io.IOException;
import java.util.stream.Stream;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.FieldMetrics;

/**
 * 文件级说明：Avro 值写入器接口，定义把单个值编码到 Avro {@link org.apache.avro.io.Encoder} 的基本契约。
 *
 * <p>所属模块：iceberg-core（avro 子包）。职责：声明 {@link #write(Object, Encoder)} 方法 和可选的 {@link #metrics()}
 * 方法，由各类型的具体实现（见 {@link ValueWriters}）完成编码。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口编程：读写栈只依赖此接口，具体实现可替换。
 *   <li>metrics() 默认返回空流，允许子类按需提供字段级指标（如 min/max/null 计数）， 供 manifest 写入时收集统计信息。
 * </ul>
 *
 * <p>上下游关系：由 {@link GenericAvroWriter} 等组合使用；具体实现在 {@link ValueWriters} 中。
 *
 * @param <D> 待写入的数据类型
 */
public interface ValueWriter<D> {
  /**
   * 把一个值编码写入 Avro 编码器。
   *
   * @param datum 待写入数据
   * @param encoder Avro 编码器
   * @throws IOException 写入失败时抛出
   */
  void write(D datum, Encoder encoder) throws IOException;

  /**
   * 返回本次写入累积的字段指标流。
   *
   * <p>设计要点：默认返回空流；支持指标采集的实现（如数值/字符串 writer）覆盖此方法， 把累计的 min/max/null 计数等返回，供 manifest 统计使用。
   *
   * @return 字段指标流（默认空）
   */
  default Stream<FieldMetrics> metrics() {
    return Stream.empty(); // TODO will populate in following PRs
  }
}
