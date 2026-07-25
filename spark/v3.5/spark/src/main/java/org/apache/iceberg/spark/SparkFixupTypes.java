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
package org.apache.iceberg.spark;

import org.apache.iceberg.Schema;
import org.apache.iceberg.types.FixupTypes;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;

/**
 * Spark 类型修正器：处理 Spark -> Iceberg 类型反向转换时的歧义。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：某些 Iceberg 类型（如 binary 与 fixed、string 与 uuid）会被映射为同一个 Spark 类型， 反向转换时只能得到其中一个默认类型。本类根据参考
 * schema 把这些类型修正回原始 Iceberg 类型。
 *
 * <p>设计意图：继承 {@link FixupTypes}，仅覆盖 fixupPrimitive 处理特定类型对
 * （STRING<-UUID、BINARY<-FIXED、TIMESTAMP<-TIMESTAMP），其余由父类处理。
 *
 * <p>上下游关系：被 {@link SparkSchemaUtil} 等在 schema 转换后调用；依赖 iceberg-core 的 TypeUtil。
 */
class SparkFixupTypes extends FixupTypes {

  private SparkFixupTypes(Schema referenceSchema) {
    super(referenceSchema);
  }

  /** 对 schema 做类型修正，参考 referenceSchema 还原原始类型。 */
  static Schema fixup(Schema schema, Schema referenceSchema) {
    return new Schema(
        TypeUtil.visit(schema, new SparkFixupTypes(referenceSchema)).asStructType().fields());
  }

  /** 判断是否需要把 type 修正为 source 类型：STRING<-UUID、BINARY<-FIXED、TIMESTAMP<-TIMESTAMP。 */
  @Override
  protected boolean fixupPrimitive(Type.PrimitiveType type, Type source) {
    switch (type.typeId()) {
      case STRING:
        if (source.typeId() == Type.TypeID.UUID) {
          return true;
        }
        break;
      case BINARY:
        if (source.typeId() == Type.TypeID.FIXED) {
          return true;
        }
        break;
      case TIMESTAMP:
        if (source.typeId() == Type.TypeID.TIMESTAMP) {
          return true;
        }
        break;
      default:
    }
    return false;
  }
}
