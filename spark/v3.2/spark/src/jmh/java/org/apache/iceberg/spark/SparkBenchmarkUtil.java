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

import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.catalyst.expressions.Attribute;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.UnsafeProjection;
import org.apache.spark.sql.types.StructType;
import scala.collection.JavaConverters;

/**
 * 文件级说明：SparkBenchmarkUtil 基准测试辅助类。
 *
 * <p>所属模块：iceberg-spark（v3.2）。职责：为基准测试提供 Spark基准测试 相关工具与辅助逻辑。
 *
 * <p>设计要点：作为 JMH 基准测试的基础设施，被各 Benchmark 子类复用，提供数据准备、配置管理与结果物化等通用能力。
 */
public class SparkBenchmarkUtil {

  /** 构造方法：SparkBenchmarkUtil。 */
  private SparkBenchmarkUtil() {}

  /** 辅助方法：投影。 */
  public static UnsafeProjection projection(Schema expectedSchema, Schema actualSchema) {
    StructType struct = SparkSchemaUtil.convert(actualSchema);

    List<AttributeReference> refs =
        JavaConverters.seqAsJavaListConverter(struct.toAttributes()).asJava();
    List<Attribute> attrs = Lists.newArrayListWithExpectedSize(struct.fields().length);
    List<Expression> exprs = Lists.newArrayListWithExpectedSize(struct.fields().length);

    for (AttributeReference ref : refs) {
      attrs.add(ref.toAttribute());
    }

    for (Types.NestedField field : expectedSchema.columns()) {
      int indexInIterSchema = struct.fieldIndex(field.name());
      exprs.add(refs.get(indexInIterSchema));
    }

    return UnsafeProjection.create(
        JavaConverters.asScalaBufferConverter(exprs).asScala().toSeq(),
        JavaConverters.asScalaBufferConverter(attrs).asScala().toSeq());
  }
}
