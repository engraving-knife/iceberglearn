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
package org.apache.iceberg.flink;

import org.apache.iceberg.Schema;
import org.apache.iceberg.types.FixupTypes;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：修复 Flink 类型转换中 UUID/Fixed 类型歧义的工具类。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），继承 Iceberg core 的 {@link FixupTypes}。
 *
 * <p>职责：在 Iceberg → Flink → Iceberg 的双向类型转换中，修复因类型映射不唯一导致的类型偏差。
 *
 * <p>设计意图：Iceberg 的 UUID（16 字节）和 Fixed(16) 都映射为 Flink 的 BinaryType(16)， 反向转换时无法区分。本类通过参考
 * schema（referenceSchema）来恢复正确的原始类型： 当 Flink 类型源是 UUID 且 Fixed 长度为 16 时，将其修正回 Fixed。
 *
 * <p>上下游关系：被 {@link FlinkSchemaUtil#convert(Schema, TableSchema)} 调用， 用于基于已有 schema 重新分配字段 id
 * 后的类型修正。
 */
class FlinkFixupTypes extends FixupTypes {

  private FlinkFixupTypes(Schema referenceSchema) {
    super(referenceSchema);
  }

  /**
   * 修复 schema 中的类型歧义。
   *
   * <p>逻辑：以 referenceSchema 为参考，访问 schema 中每个类型节点，对 Fixed(16) 类型 检查参考类型是否为 UUID，若匹配则标记需要修正。
   *
   * @param schema 待修复的 schema
   * @param referenceSchema 参考_schema（提供正确的类型信息）
   * @return 修复后的 schema
   */
  static Schema fixup(Schema schema, Schema referenceSchema) {
    return new Schema(
        TypeUtil.visit(schema, new FlinkFixupTypes(referenceSchema)).asStructType().fields());
  }

  /**
   * 判断是否需要修复基础类型。
   *
   * <p>逻辑：当 type 是 FixedType 且长度为 16，同时 source 是 UUID 类型时返回 true， 表示该 Fixed 应修正为 UUID。
   *
   * @param type 当前类型
   * @param source 参考类型
   * @return true 表示需要修复
   */
  @Override
  protected boolean fixupPrimitive(Type.PrimitiveType type, Type source) {
    if (type instanceof Types.FixedType) {
      int length = ((Types.FixedType) type).length();
      return source.typeId() == Type.TypeID.UUID && length == 16;
    }
    return false;
  }
}
