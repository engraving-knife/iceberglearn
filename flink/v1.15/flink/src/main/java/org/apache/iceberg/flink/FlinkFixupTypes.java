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
 * Flink 类型修正器，处理 Iceberg UUID 与 Fixed 类型在 Flink 中合并后无法区分的问题。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在 schema 转换中，UUID 与 16 字节 Fixed 都映射 到 Flink 的 BinaryType，反向转换时按参考
 * schema 修正回原始类型。
 *
 * <p>设计意图：继承 Iceberg {@link FixupTypes}，按参考 schema 决定应当还原为 Fixed 还是 UUID。
 */
class FlinkFixupTypes extends FixupTypes {

  private FlinkFixupTypes(Schema referenceSchema) {
    super(referenceSchema);
  }

  /** 按参考 schema 修正给定 schema 中的类型并返回新 Schema。 */
  static Schema fixup(Schema schema, Schema referenceSchema) {
    return new Schema(
        TypeUtil.visit(schema, new FlinkFixupTypes(referenceSchema)).asStructType().fields());
  }

  /** 若类型为 16 字节 Fixed 且源类型为 UUID，则修正为 UUID 类型。 */
  @Override
  protected boolean fixupPrimitive(Type.PrimitiveType type, Type source) {
    if (type instanceof Types.FixedType) {
      int length = ((Types.FixedType) type).length();
      return source.typeId() == Type.TypeID.UUID && length == 16;
    }
    return false;
  }
}
