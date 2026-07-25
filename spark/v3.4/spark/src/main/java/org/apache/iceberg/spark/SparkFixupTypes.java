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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 类型修正访问器，在 Iceberg Schema 与 Spark StructType 互转时修正类型差异（如 decimal、timestamp）。
 *
 * <p>设计意图：以访问者模式递归修正字段类型，保证两端类型语义一致。
 *
 * <p>上下游关系：由 SparkSchemaUtil / SparkScanBuilder 等在类型转换后调用。
 */
class SparkFixupTypes extends FixupTypes {

  private SparkFixupTypes(Schema referenceSchema) {
    super(referenceSchema);
  }
  /** 执行 fixup 相关操作。 */
  static Schema fixup(Schema schema, Schema referenceSchema) {
    return new Schema(
        TypeUtil.visit(schema, new SparkFixupTypes(referenceSchema)).asStructType().fields());
  }
  /** 执行 fixupPrimitive 相关操作。 */
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
