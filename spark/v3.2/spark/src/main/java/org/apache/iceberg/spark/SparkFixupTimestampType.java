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
import org.apache.iceberg.types.Types;

/**
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkFixupTimestampType。
 */
class SparkFixupTimestampType extends FixupTypes {

  /** 构造 SparkFixupTimestampType 实例。 */
  private SparkFixupTimestampType(Schema referenceSchema) {
    super(referenceSchema);
  }

  /** 执行该方法的具体逻辑。 */
  static Schema fixup(Schema schema) {
    /** 执行该方法的具体逻辑。 */
    return new Schema(
        TypeUtil.visit(schema, new SparkFixupTimestampType(schema)).asStructType().fields());
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param primitive 参数
   * @return 结果对象
   */
  @Override
  public Type primitive(Type.PrimitiveType primitive) {
    if (primitive.typeId() == Type.TypeID.TIMESTAMP) {
      return Types.TimestampType.withoutZone();
    }
    return primitive;
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected boolean fixupPrimitive(Type.PrimitiveType type, Type source) {
    return Type.TypeID.TIMESTAMP.equals(type.typeId());
  }
}
