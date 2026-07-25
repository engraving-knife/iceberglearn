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
package org.apache.iceberg.mr.hive.serde.objectinspector;

/**
 * 文件级说明：写入路径的类型转换接口。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）；为 Hive -> Iceberg 写入路径 提供“Hive 基本对象 ->
 * Iceberg Record 可接受对象”的转换契约。
 *
 * <p>职责：定义 {@link #convert(Object)} 方法，由各 Iceberg 自定义 ObjectInspector 实现， 把 Hive 侧取到的 Java 基本对象转换为
 * Iceberg 期望的类型（如 java.sql.Date -> LocalDate）。
 *
 * <p>设计意图：Iceberg 自定义的 ObjectInspector 同时承担“读取呈现”与“写入转换”两种职责； 通过该接口区分写入路径。若 ObjectInspector
 * 未实现本接口，写入时直接使用 Hive 原始对象。
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.mr.hive.Deserializer} 在写入时按需调用。
 */
public interface WriteObjectInspector {
  /**
   * 把 Hive 侧的基本对象转换为 Iceberg Record 可接受的对应类型对象。
   *
   * @param value Hive 侧对象
   * @return 转换后的对象；value 为 null 时返回 null
   */
  Object convert(Object value);
}
