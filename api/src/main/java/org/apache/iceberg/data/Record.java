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
package org.apache.iceberg.data;

import java.util.Map;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types.StructType;

/**
 * 文件级说明：Iceberg 通用记录接口，表示一行结构化数据。
 *
 * <p>所属模块：iceberg-api（最核心的对外 API 模块，定义表/数据/IO 的抽象契约，被 core 实现及各引擎集成模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>作为内存中的一行数据载体，提供按字段名或字段下标读写值的能力。
 *   <li>继承 {@link StructLike}，使本接口可与 Iceberg 的结构访问协议（按位置取值）互通。
 *   <li>提供 {@code struct()} 暴露该记录对应的 {@link StructType} 模式。
 *   <li>提供多种 {@code copy} 重载，便于以覆盖部分字段的方式生成新记录。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口而非具体实现：使得内存数据（GenericRecord）、Spark Row、Flink RowData 等 不同运行时表示可以通过实现同一接口被 Iceberg
 *       读写链路统一消费。
 *   <li>{@code copy} 默认方法重载：减少调用方在“仅覆盖 1~3 个字段”时的样板代码，同时内部 复用 {@link #copy(Map)}
 *       抽象方法，保持实现方只需实现一次核心拷贝逻辑。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的写入/读取路径、各引擎模块（Spark/Flink/Hive 等）以及 iceberg-data 的 {@code GenericRecord}
 * 实现广泛使用。
 */
public interface Record extends StructLike {

  /**
   * 返回该记录对应的 {@link StructType} 模式描述。
   *
   * @return 当前记录的字段结构
   */
  StructType struct();

  /**
   * 按字段名读取字段值。
   *
   * @param name 字段名
   * @return 该字段的值（可能为 null）
   */
  Object getField(String name);

  /**
   * 按字段名设置字段值。
   *
   * @param name 字段名
   * @param value 要写入的值
   */
  void setField(String name, Object value);

  /**
   * 按字段下标读取字段值（与 {@link StructLike} 协议一致）。
   *
   * @param pos 字段位置，从 0 开始
   * @return 该字段的值（可能为 null）
   */
  Object get(int pos);

  /**
   * 创建当前记录的深拷贝。
   *
   * @return 与当前记录字段值相同的新 {@link Record}
   */
  Record copy();

  /**
   * 创建当前记录的拷贝，并用 {@code overwriteValues} 中的值覆盖对应字段。
   *
   * @param overwriteValues 需要覆盖的字段名到新值的映射
   * @return 拷贝并覆盖后的新 {@link Record}
   */
  Record copy(Map<String, Object> overwriteValues);

  /**
   * 创建当前记录的拷贝，并覆盖单个字段。
   *
   * <p>逻辑：构造预期大小为 1 的 Map，委托给 {@link #copy(Map)} 实现。
   *
   * @param field 要覆盖的字段名
   * @param value 该字段的新值
   * @return 拷贝并覆盖后的新 {@link Record}
   */
  default Record copy(String field, Object value) {
    Map<String, Object> overwriteValues = Maps.newHashMapWithExpectedSize(1);
    overwriteValues.put(field, value);
    return copy(overwriteValues);
  }

  /**
   * 创建当前记录的拷贝，并覆盖两个字段。
   *
   * <p>逻辑：构造预期大小为 2 的 Map，委托给 {@link #copy(Map)} 实现。
   *
   * @param field1 第一个要覆盖的字段名
   * @param value1 第一个字段的新值
   * @param field2 第二个要覆盖的字段名
   * @param value2 第二个字段的新值
   * @return 拷贝并覆盖后的新 {@link Record}
   */
  default Record copy(String field1, Object value1, String field2, Object value2) {
    Map<String, Object> overwriteValues = Maps.newHashMapWithExpectedSize(2);
    overwriteValues.put(field1, value1);
    overwriteValues.put(field2, value2);
    return copy(overwriteValues);
  }

  /**
   * 创建当前记录的拷贝，并覆盖三个字段。
   *
   * <p>逻辑：构造预期大小为 3 的 Map，委托给 {@link #copy(Map)} 实现。
   *
   * @param field1 第一个要覆盖的字段名
   * @param value1 第一个字段的新值
   * @param field2 第二个要覆盖的字段名
   * @param value2 第二个字段的新值
   * @param field3 第三个要覆盖的字段名
   * @param value3 第三个字段的新值
   * @return 拷贝并覆盖后的新 {@link Record}
   */
  default Record copy(
      String field1, Object value1, String field2, Object value2, String field3, Object value3) {
    Map<String, Object> overwriteValues = Maps.newHashMapWithExpectedSize(3);
    overwriteValues.put(field1, value1);
    overwriteValues.put(field2, value2);
    overwriteValues.put(field3, value3);
    return copy(overwriteValues);
  }
}
