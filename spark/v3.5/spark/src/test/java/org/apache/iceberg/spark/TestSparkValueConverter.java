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
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkValueConverter 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Spark值转换器 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkValueConverter {
  /** 测试Spark空值映射转换场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkNullMapConvert() {
    Schema schema =
        new Schema(
            Types.NestedField.required(0, "id", Types.LongType.get()),
            Types.NestedField.optional(
                5,
                "locations",
                Types.MapType.ofOptional(
                    6,
                    7,
                    Types.StringType.get(),
                    Types.StructType.of(
                        Types.NestedField.required(1, "lat", Types.FloatType.get()),
                        Types.NestedField.required(2, "long", Types.FloatType.get())))));

    assertCorrectNullConversion(schema);
  }

  /** 测试Spark空值列表转换场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkNullListConvert() {
    Schema schema =
        new Schema(
            Types.NestedField.required(0, "id", Types.LongType.get()),
            Types.NestedField.optional(
                5, "locations", Types.ListType.ofOptional(6, Types.StringType.get())));

    assertCorrectNullConversion(schema);
  }

  /** 测试Spark空值结构体转换场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkNullStructConvert() {
    Schema schema =
        new Schema(
            Types.NestedField.required(0, "id", Types.LongType.get()),
            Types.NestedField.optional(
                5,
                "location",
                Types.StructType.of(
                    Types.NestedField.required(1, "lat", Types.FloatType.get()),
                    Types.NestedField.required(2, "long", Types.FloatType.get()))));

    assertCorrectNullConversion(schema);
  }

  /** 测试Spark空值基础转换场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSparkNullPrimitiveConvert() {
    Schema schema =
        new Schema(
            Types.NestedField.required(0, "id", Types.LongType.get()),
            Types.NestedField.optional(5, "location", Types.StringType.get()));
    assertCorrectNullConversion(schema);
  }

  /** 断言correct空值conversion。 */
  private void assertCorrectNullConversion(Schema schema) {
    Row sparkRow = RowFactory.create(1, null);
    Record record = GenericRecord.create(schema);
    record.set(0, 1);
    Assert.assertEquals(
        "Round-trip conversion should produce original value",
        record,
        SparkValueConverter.convert(schema, sparkRow));
  }
}
