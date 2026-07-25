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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.LongType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.StructType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 DataTest 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 DataTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public abstract class DataTest {

  /** 辅助方法：writeAndValidate。 */
  protected abstract void writeAndValidate(Schema schema) throws IOException;

  private static final StructType SUPPORTED_PRIMITIVES =
      StructType.of(
          required(100, "id", LongType.get()),
          optional(101, "data", Types.StringType.get()),
          required(102, "b", Types.BooleanType.get()),
          optional(103, "i", Types.IntegerType.get()),
          required(104, "l", LongType.get()),
          optional(105, "f", Types.FloatType.get()),
          required(106, "d", Types.DoubleType.get()),
          optional(107, "date", Types.DateType.get()),
          required(108, "ts_tz", Types.TimestampType.withZone()),
          required(109, "ts", Types.TimestampType.withoutZone()),
          required(110, "s", Types.StringType.get()),
          required(112, "fixed", Types.FixedType.ofLength(7)),
          optional(113, "bytes", Types.BinaryType.get()),
          required(114, "dec_9_0", Types.DecimalType.of(9, 0)),
          required(115, "dec_11_2", Types.DecimalType.of(11, 2)),
          required(116, "dec_38_10", Types.DecimalType.of(38, 10)), // maximum precision
          required(117, "time", Types.TimeType.get()));

  @Rule public TemporaryFolder temp = new TemporaryFolder();

  /**
   * 测试场景：Simple Struct。
   *
   * <p>验证该方法在 Simple Struct 条件下的行为是否符合预期。
   */
  @Test
  public void testSimpleStruct() throws IOException {
    writeAndValidate(new Schema(SUPPORTED_PRIMITIVES.fields()));
  }

  /**
   * 测试场景：Array。
   *
   * <p>验证该方法在 Array 条件下的行为是否符合预期。
   */
  @Test
  public void testArray() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(1, "data", ListType.ofOptional(2, Types.StringType.get())));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Array Of Structs。
   *
   * <p>验证该方法在 Array Of Structs 条件下的行为是否符合预期。
   */
  @Test
  public void testArrayOfStructs() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(1, "data", ListType.ofOptional(2, SUPPORTED_PRIMITIVES)));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Map。
   *
   * <p>验证该方法在 Map 条件下的行为是否符合预期。
   */
  @Test
  public void testMap() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(
                1,
                "data",
                MapType.ofOptional(2, 3, Types.StringType.get(), Types.StringType.get())));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Numeric Map Key。
   *
   * <p>验证该方法在 Numeric Map Key 条件下的行为是否符合预期。
   */
  @Test
  public void testNumericMapKey() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(1, "data", MapType.ofOptional(2, 3, LongType.get(), Types.StringType.get())));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Complex Map Key。
   *
   * <p>验证该方法在 Complex Map Key 条件下的行为是否符合预期。
   */
  @Test
  public void testComplexMapKey() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(
                1,
                "data",
                MapType.ofOptional(
                    2,
                    3,
                    StructType.of(
                        required(4, "i", Types.IntegerType.get()),
                        optional(5, "s", Types.StringType.get())),
                    Types.StringType.get())));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Map Of Structs。
   *
   * <p>验证该方法在 Map Of Structs 条件下的行为是否符合预期。
   */
  @Test
  public void testMapOfStructs() throws IOException {
    Schema schema =
        new Schema(
            required(0, "id", LongType.get()),
            optional(
                1, "data", MapType.ofOptional(2, 3, Types.StringType.get(), SUPPORTED_PRIMITIVES)));

    writeAndValidate(schema);
  }

  /**
   * 测试场景：Mixed Types。
   *
   * <p>验证该方法在 Mixed Types 条件下的行为是否符合预期。
   */
  @Test
  public void testMixedTypes() throws IOException {
    StructType structType =
        StructType.of(
            required(0, "id", LongType.get()),
            optional(
                1,
                "list_of_maps",
                ListType.ofOptional(
                    2, MapType.ofOptional(3, 4, Types.StringType.get(), SUPPORTED_PRIMITIVES))),
            optional(
                5,
                "map_of_lists",
                MapType.ofOptional(
                    6, 7, Types.StringType.get(), ListType.ofOptional(8, SUPPORTED_PRIMITIVES))),
            required(
                9,
                "list_of_lists",
                ListType.ofOptional(10, ListType.ofOptional(11, SUPPORTED_PRIMITIVES))),
            required(
                12,
                "map_of_maps",
                MapType.ofOptional(
                    13,
                    14,
                    Types.StringType.get(),
                    MapType.ofOptional(15, 16, Types.StringType.get(), SUPPORTED_PRIMITIVES))),
            required(
                17,
                "list_of_struct_of_nested_types",
                ListType.ofOptional(
                    19,
                    StructType.of(
                        Types.NestedField.required(
                            20,
                            "m1",
                            MapType.ofOptional(
                                21, 22, Types.StringType.get(), SUPPORTED_PRIMITIVES)),
                        Types.NestedField.optional(
                            23, "l1", ListType.ofRequired(24, SUPPORTED_PRIMITIVES)),
                        Types.NestedField.required(
                            25, "l2", ListType.ofRequired(26, SUPPORTED_PRIMITIVES)),
                        Types.NestedField.optional(
                            27,
                            "m2",
                            MapType.ofOptional(
                                28, 29, Types.StringType.get(), SUPPORTED_PRIMITIVES))))));

    Schema schema =
        new Schema(
            TypeUtil.assignFreshIds(structType, new AtomicInteger(0)::incrementAndGet)
                .asStructType()
                .fields());

    writeAndValidate(schema);
  }
}
