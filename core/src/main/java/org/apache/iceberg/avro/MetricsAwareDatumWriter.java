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

import java.util.stream.Stream;
import org.apache.avro.io.DatumWriter;
import org.apache.iceberg.FieldMetrics;

/**
 * 在 {@link DatumWriter} 基础上增加字段指标收集能力的包装接口。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 写出与指标统计的桥接接口）。
 *
 * <p>职责：在写出 Avro 数据的同时，跟踪并暴露各字段的 {@link FieldMetrics}（如上下界、null 计数、 值计数等），供写入器在落盘时生成文件级
 * metrics，加速后续查询过滤。
 *
 * <p>设计意图：以接口扩展方式增强 Avro 原生 {@link DatumWriter}，不改变其写出契约，仅追加 指标查询入口；实现类负责在写出过程中累计指标。
 *
 * <p>上下游关系：被 Avro 写入器（如 {@link org.apache.iceberg.io.DataWriter}）持有并调用， 上游为具体的 Avro 数据编码实现。
 *
 * @param <D> 数据类型
 */
public interface MetricsAwareDatumWriter<D> extends DatumWriter<D> {

  /**
   * 返回本 writer 跟踪的 {@link FieldMetrics} 流。
   *
   * @return 字段指标流
   */
  Stream<FieldMetrics> metrics();
}
