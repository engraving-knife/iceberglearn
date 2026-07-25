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

import java.util.UUID;
import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericFixed;
import org.apache.iceberg.util.UUIDUtil;

/**
 * 文件级说明：Avro UUID 逻辑类型转换器，负责 Iceberg 的 {@link java.util.UUID} 与 Avro 的 fixed(16) 字节布局之间的双向转换。
 *
 * <p>所属模块：iceberg-core。职责：实现 Avro {@link org.apache.avro.Conversion} 接口， 把 Iceberg 中以 16 字节定长存储的
 * UUID 在读写时透明转换为 {@link UUID} 对象。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Iceberg 在 Avro 中用 logicalType="uuid" 标注 16 字节 fixed 类型来存储 UUID， 本类作为 Avro
 *       反射/通用读写框架的钩子，让上层使用者直接拿到 {@link UUID} 而非裸字节。
 *   <li>具体字节编解码委托给 {@link org.apache.iceberg.util.UUIDUtil}，本类只做 Avro Conversion 协议适配，避免编解码逻辑散落。
 * </ul>
 *
 * <p>上下游关系：被 Iceberg 的 Avro 读写栈（如 {@code GenericAvroReader/Writer}、反射读写器） 通过 Avro 的 Conversion
 * 注册机制发现并调用；底层依赖 {@link UUIDUtil}。
 */
public class UUIDConversion extends Conversion<UUID> {
  /**
   * 返回本 Conversion 转换后的 Java 类型。
   *
   * <p>设计要点：固定返回 {@link UUID}，供 Avro 框架在匹配逻辑类型时确定目标 Java 类。
   *
   * @return {@link UUID} 的 Class 对象
   */
  @Override
  public Class<UUID> getConvertedType() {
    return UUID.class;
  }

  /**
   * 返回本 Conversion 处理的 Avro 逻辑类型名称。
   *
   * <p>设计要点：取 {@link org.apache.avro.LogicalTypes#uuid()} 的标准名称 "uuid"， 与 Avro 规范保持一致，确保 Avro
   * 框架能按逻辑类型名路由到本转换器。
   *
   * @return 逻辑类型名称字符串 "uuid"
   */
  @Override
  public String getLogicalTypeName() {
    return LogicalTypes.uuid().getName();
  }

  /**
   * 将 Avro 定长字节（fixed(16)）反序列化为 {@link UUID}。
   *
   * <p>设计要点：直接把 {@link GenericFixed#bytes()} 交给 {@link UUIDUtil#convert(byte[])} 完成大端字节序到 UUID 的解析。
   *
   * @param value Avro 端的 fixed 值，长度必须为 16 字节
   * @param schema 该字段对应的 Avro schema
   * @param type 该字段对应的逻辑类型实例
   * @return 解析后的 {@link UUID} 对象
   */
  @Override
  public UUID fromFixed(GenericFixed value, Schema schema, LogicalType type) {
    return UUIDUtil.convert(value.bytes());
  }

  /**
   * 将 {@link UUID} 序列化为 Avro 的 fixed(16) 值。
   *
   * <p>设计要点：通过 {@link UUIDUtil#convert(UUID)} 取出 16 字节大端表示， 再包装为 {@link GenericData.Fixed}，保证写入磁盘时与
   * Iceberg 的存储约定一致。
   *
   * @param value 待序列化的 UUID
   * @param schema 该字段对应的 Avro schema
   * @param type 该字段对应的逻辑类型实例
   * @return Avro 端的 fixed 值
   */
  @Override
  public GenericFixed toFixed(UUID value, Schema schema, LogicalType type) {
    return new GenericData.Fixed(schema, UUIDUtil.convert(value));
  }
}
