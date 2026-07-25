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
package org.apache.iceberg;

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.apache.iceberg.TestHelpers.Row;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestAccessors 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestAccessors 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestAccessors {

  /** 辅助方法：direct。 */
  private static Accessor<StructLike> direct(Type type) {
    Schema schema = new Schema(required(17, "field_" + type.typeId(), type));
    return schema.accessorForField(17);
  }

  /** 辅助方法：nested1。 */
  private static Accessor<StructLike> nested1(Type type) {
    Schema schema =
        new Schema(
            required(
                11,
                "struct1",
                Types.StructType.of(
                    Types.NestedField.required(17, "field_" + type.typeId(), type))));
    return schema.accessorForField(17);
  }

  /** 辅助方法：nested2。 */
  private static Accessor<StructLike> nested2(Type type) {
    Schema schema =
        new Schema(
            required(
                11,
                "s",
                Types.StructType.of(
                    Types.NestedField.required(
                        22,
                        "s2",
                        Types.StructType.of(
                            Types.NestedField.required(17, "field_" + type.typeId(), type))))));
    return schema.accessorForField(17);
  }

  /** 辅助方法：nested3。 */
  private static Accessor<StructLike> nested3(Type type) {
    Schema schema =
        new Schema(
            required(
                11,
                "s",
                Types.StructType.of(
                    Types.NestedField.required(
                        22,
                        "s2",
                        Types.StructType.of(
                            Types.NestedField.required(
                                33,
                                "s3",
                                Types.StructType.of(
                                    Types.NestedField.required(
                                        17, "field_" + type.typeId(), type))))))));
    return schema.accessorForField(17);
  }

  /** 辅助方法：nested3optional。 */
  private static Accessor<StructLike> nested3optional(Type type) {
    Schema schema =
        new Schema(
            optional(
                11,
                "s",
                Types.StructType.of(
                    Types.NestedField.optional(
                        22,
                        "s2",
                        Types.StructType.of(
                            Types.NestedField.optional(
                                33,
                                "s3",
                                Types.StructType.of(
                                    Types.NestedField.optional(
                                        17, "field_" + type.typeId(), type))))))));
    return schema.accessorForField(17);
  }

  /** 辅助方法：nested4。 */
  private static Accessor<StructLike> nested4(Type type) {
    Schema schema =
        new Schema(
            required(
                11,
                "s",
                Types.StructType.of(
                    Types.NestedField.required(
                        22,
                        "s2",
                        Types.StructType.of(
                            Types.NestedField.required(
                                33,
                                "s3",
                                Types.StructType.of(
                                    Types.NestedField.required(
                                        44,
                                        "s4",
                                        Types.StructType.of(
                                            Types.NestedField.required(
                                                17, "field_" + type.typeId(), type))))))))));
    return schema.accessorForField(17);
  }

  /** 辅助方法：assertAccessorReturns。 */
  private void assertAccessorReturns(Type type, Object value) {
    assertThat(direct(type).get(Row.of(value))).isEqualTo(value);

    assertThat(nested1(type).get(Row.of(Row.of(value)))).isEqualTo(value);
    assertThat(nested2(type).get(Row.of(Row.of(Row.of(value))))).isEqualTo(value);
    assertThat(nested3(type).get(Row.of(Row.of(Row.of(Row.of(value)))))).isEqualTo(value);
    assertThat(nested4(type).get(Row.of(Row.of(Row.of(Row.of(Row.of(value))))))).isEqualTo(value);

    assertThat(nested3optional(type).get(Row.of(Row.of(Row.of(Row.of(value)))))).isEqualTo(value);
  }

  /**
   * 测试场景：Boolean。
   *
   * <p>验证该方法在 Boolean 条件下的行为是否符合预期。
   */
  @Test
  public void testBoolean() {
    assertAccessorReturns(Types.BooleanType.get(), true);
    assertAccessorReturns(Types.BooleanType.get(), false);
  }

  /**
   * 测试场景：Int。
   *
   * <p>验证该方法在 Int 条件下的行为是否符合预期。
   */
  @Test
  public void testInt() {
    assertAccessorReturns(Types.IntegerType.get(), 123);
  }

  /**
   * 测试场景：Long。
   *
   * <p>验证该方法在 Long 条件下的行为是否符合预期。
   */
  @Test
  public void testLong() {
    assertAccessorReturns(Types.LongType.get(), 123L);
  }

  /**
   * 测试场景：Float。
   *
   * <p>验证该方法在 Float 条件下的行为是否符合预期。
   */
  @Test
  public void testFloat() {
    assertAccessorReturns(Types.FloatType.get(), 1.23f);
  }

  /**
   * 测试场景：Double。
   *
   * <p>验证该方法在 Double 条件下的行为是否符合预期。
   */
  @Test
  public void testDouble() {
    assertAccessorReturns(Types.DoubleType.get(), 1.23d);
  }

  /**
   * 测试场景：Date。
   *
   * <p>验证该方法在 Date 条件下的行为是否符合预期。
   */
  @Test
  public void testDate() {
    assertAccessorReturns(Types.DateType.get(), 123);
  }

  /**
   * 测试场景：Time。
   *
   * <p>验证该方法在 Time 条件下的行为是否符合预期。
   */
  @Test
  public void testTime() {
    assertAccessorReturns(Types.TimeType.get(), 123L);
  }

  /**
   * 测试场景：Timestamp。
   *
   * <p>验证该方法在 Timestamp 条件下的行为是否符合预期。
   */
  @Test
  public void testTimestamp() {
    assertAccessorReturns(Types.TimestampType.withoutZone(), 123L);
    assertAccessorReturns(Types.TimestampType.withZone(), 123L);
  }

  /**
   * 测试场景：String。
   *
   * <p>验证该方法在 String 条件下的行为是否符合预期。
   */
  @Test
  public void testString() {
    assertAccessorReturns(Types.StringType.get(), "abc");
  }

  /**
   * 测试场景：Uuid。
   *
   * <p>验证该方法在 Uuid 条件下的行为是否符合预期。
   */
  @Test
  public void testUuid() {
    assertAccessorReturns(Types.UUIDType.get(), UUID.randomUUID());
  }

  /**
   * 测试场景：Fixed。
   *
   * <p>验证该方法在 Fixed 条件下的行为是否符合预期。
   */
  @Test
  public void testFixed() {
    assertAccessorReturns(Types.FixedType.ofLength(3), ByteBuffer.wrap(new byte[] {1, 2, 3}));
  }

  /**
   * 测试场景：Binary。
   *
   * <p>验证该方法在 Binary 条件下的行为是否符合预期。
   */
  @Test
  public void testBinary() {
    assertAccessorReturns(Types.BinaryType.get(), ByteBuffer.wrap(new byte[] {1, 2, 3}));
  }

  /**
   * 测试场景：Decimal。
   *
   * <p>验证该方法在 Decimal 条件下的行为是否符合预期。
   */
  @Test
  public void testDecimal() {
    assertAccessorReturns(Types.DecimalType.of(5, 7), BigDecimal.valueOf(123.456));
  }

  /**
   * 测试场景：List。
   *
   * <p>验证该方法在 List 条件下的行为是否符合预期。
   */
  @Test
  public void testList() {
    assertAccessorReturns(
        Types.ListType.ofRequired(18, Types.IntegerType.get()), ImmutableList.of(1, 2, 3));
    assertAccessorReturns(
        Types.ListType.ofRequired(18, Types.StringType.get()), ImmutableList.of("a", "b", "c"));
  }

  /**
   * 测试场景：Map。
   *
   * <p>验证该方法在 Map 条件下的行为是否符合预期。
   */
  @Test
  public void testMap() {
    assertAccessorReturns(
        Types.MapType.ofRequired(18, 19, Types.StringType.get(), Types.IntegerType.get()),
        ImmutableMap.of("a", 1, "b", 2));
  }

  /**
   * 测试场景：Struct As Object。
   *
   * <p>验证该方法在 Struct As Object 条件下的行为是否符合预期。
   */
  @Test
  public void testStructAsObject() {
    assertAccessorReturns(
        Types.StructType.of(
            Types.NestedField.optional(18, "str19", Types.StringType.get()),
            Types.NestedField.optional(19, "int19", Types.IntegerType.get())),
        Row.of("a", 1));
  }

  /**
   * 测试场景：Empty Struct As Object。
   *
   * <p>验证该方法在 Empty Struct As Object 条件下的行为是否符合预期。
   */
  @Test
  public void testEmptyStructAsObject() {
    assertAccessorReturns(
        Types.StructType.of(Types.NestedField.optional(19, "int19", Types.IntegerType.get())),
        Row.of());

    assertAccessorReturns(Types.StructType.of(), Row.of());
  }

  /**
   * 测试场景：Empty Schema。
   *
   * <p>验证该方法在 Empty Schema 条件下的行为是否符合预期。
   */
  @Test
  public void testEmptySchema() {
    Schema emptySchema = new Schema();
    assertThat(emptySchema.accessorForField(17)).isNull();
  }
}
