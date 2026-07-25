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

import static org.apache.iceberg.types.Types.NestedField.optional;

import java.util.Collections;
import java.util.function.Supplier;
import org.apache.avro.SchemaBuilder;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestBuildAvroProjection，用于验证 Build Avro Projection 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Build Avro Projection 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestBuildAvroProjection {

  /**
   * 测试场景：project array with element schema unchanged。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectArrayWithElementSchemaUnchanged() {

    final Type icebergType =
        Types.ListType.ofRequired(
            0,
            Types.StructType.of(
                optional(1, "int1", Types.IntegerType.get()),
                optional(2, "string1", Types.StringType.get())));

    final org.apache.avro.Schema expected =
        SchemaBuilder.array()
            .prop(AvroSchemaUtil.ELEMENT_ID_PROP, "0")
            .items(
                SchemaBuilder.record("elem")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getElementType;

    final org.apache.avro.Schema actual = testSubject.array(expected, supplier);

    Assertions.assertThat(actual)
        .as("Array projection produced undesired array schema")
        .isEqualTo(expected);
    Assertions.assertThat(
            Integer.valueOf(actual.getProp(AvroSchemaUtil.ELEMENT_ID_PROP)).intValue())
        .as("Unexpected element ID discovered on the projected array schema")
        .isEqualTo(0);
  }

  /**
   * 测试场景：project array with extra field in element schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectArrayWithExtraFieldInElementSchema() {

    final Type icebergType =
        Types.ListType.ofRequired(
            0,
            Types.StructType.of(
                optional(1, "int1", Types.IntegerType.get()),
                optional(2, "string1", Types.StringType.get())));

    final org.apache.avro.Schema extraField =
        SchemaBuilder.array()
            .prop(AvroSchemaUtil.ELEMENT_ID_PROP, "0")
            .items(
                SchemaBuilder.record("elem")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .name("float1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "3")
                    .type()
                    .nullable()
                    .floatType()
                    .noDefault()
                    .endRecord());

    // once projected onto iceberg schema, the avro schema will lose the extra float field
    final org.apache.avro.Schema expected =
        SchemaBuilder.array()
            .prop(AvroSchemaUtil.ELEMENT_ID_PROP, "0")
            .items(
                SchemaBuilder.record("elem")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getElementType;

    final org.apache.avro.Schema actual = testSubject.array(extraField, supplier);

    Assertions.assertThat(actual)
        .as("Array projection produced undesired array schema")
        .isEqualTo(expected);
    Assertions.assertThat(
            Integer.valueOf(actual.getProp(AvroSchemaUtil.ELEMENT_ID_PROP)).intValue())
        .as("Unexpected element ID discovered on the projected array schema")
        .isEqualTo(0);
  }

  /**
   * 测试场景：project array with less field in element schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectArrayWithLessFieldInElementSchema() {

    final Type icebergType =
        Types.ListType.ofRequired(
            0,
            Types.StructType.of(
                optional(1, "int1", Types.IntegerType.get()),
                optional(2, "string1", Types.StringType.get())));

    final org.apache.avro.Schema lessField =
        SchemaBuilder.array()
            .prop(AvroSchemaUtil.ELEMENT_ID_PROP, "0")
            .items(
                SchemaBuilder.record("elem")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .endRecord());

    // once projected onto iceberg schema, the avro schema will have an extra string column
    final org.apache.avro.Schema expected =
        SchemaBuilder.array()
            .prop(AvroSchemaUtil.ELEMENT_ID_PROP, "0")
            .items(
                SchemaBuilder.record("elem")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1_r")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getElementType;

    final org.apache.avro.Schema actual = testSubject.array(lessField, supplier);

    Assertions.assertThat(actual)
        .as("Array projection produced undesired array schema")
        .isEqualTo(expected);
    Assertions.assertThat(
            Integer.valueOf(actual.getProp(AvroSchemaUtil.ELEMENT_ID_PROP)).intValue())
        .as("Unexpected element ID discovered on the projected array schema")
        .isEqualTo(0);
  }

  /**
   * 测试场景：project map with value schema unchanged。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectMapWithValueSchemaUnchanged() {

    final Type icebergType =
        Types.MapType.ofRequired(
            0,
            1,
            Types.StringType.get(),
            Types.StructType.of(
                optional(2, "int1", Types.IntegerType.get()),
                optional(3, "string1", Types.StringType.get())));

    final org.apache.avro.Schema expected =
        SchemaBuilder.map()
            .prop(AvroSchemaUtil.KEY_ID_PROP, "0")
            .prop(AvroSchemaUtil.VALUE_ID_PROP, "1")
            .values(
                SchemaBuilder.record("value")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getValueType;

    final org.apache.avro.Schema actual = testSubject.map(expected, supplier);

    Assertions.assertThat(actual)
        .as("Map projection produced undesired map schema")
        .isEqualTo(expected);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.KEY_ID_PROP)).intValue())
        .as("Unexpected key ID discovered on the projected map schema")
        .isEqualTo(0);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.VALUE_ID_PROP)).intValue())
        .as("Unexpected value ID discovered on the projected map schema")
        .isEqualTo(1);
  }

  /**
   * 测试场景：project map with extra field in value schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectMapWithExtraFieldInValueSchema() {

    final Type icebergType =
        Types.MapType.ofRequired(
            0,
            1,
            Types.StringType.get(),
            Types.StructType.of(
                optional(2, "int1", Types.IntegerType.get()),
                optional(3, "string1", Types.StringType.get())));

    final org.apache.avro.Schema extraField =
        SchemaBuilder.map()
            .prop(AvroSchemaUtil.KEY_ID_PROP, "0")
            .prop(AvroSchemaUtil.VALUE_ID_PROP, "1")
            .values(
                SchemaBuilder.record("value")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .name("float1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "3")
                    .type()
                    .nullable()
                    .floatType()
                    .noDefault()
                    .endRecord());

    // once projected onto iceberg schema, the avro schema will lose the extra float field
    final org.apache.avro.Schema expected =
        SchemaBuilder.map()
            .prop(AvroSchemaUtil.KEY_ID_PROP, "0")
            .prop(AvroSchemaUtil.VALUE_ID_PROP, "1")
            .values(
                SchemaBuilder.record("value")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getValueType;

    final org.apache.avro.Schema actual = testSubject.map(extraField, supplier);

    Assertions.assertThat(actual)
        .as("Map projection produced undesired map schema")
        .isEqualTo(expected);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.KEY_ID_PROP)).intValue())
        .as("Unexpected key ID discovered on the projected map schema")
        .isEqualTo(0);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.VALUE_ID_PROP)).intValue())
        .as("Unexpected value ID discovered on the projected map schema")
        .isEqualTo(1);
  }

  /**
   * 测试场景：project map with less field in value schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void projectMapWithLessFieldInValueSchema() {

    final Type icebergType =
        Types.MapType.ofRequired(
            0,
            1,
            Types.StringType.get(),
            Types.StructType.of(
                optional(2, "int1", Types.IntegerType.get()),
                optional(3, "string1", Types.StringType.get())));

    final org.apache.avro.Schema lessField =
        SchemaBuilder.map()
            .prop(AvroSchemaUtil.KEY_ID_PROP, "0")
            .prop(AvroSchemaUtil.VALUE_ID_PROP, "1")
            .values(
                SchemaBuilder.record("value")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .endRecord());

    // once projected onto iceberg schema, the avro schema will have an extra string column
    final org.apache.avro.Schema expected =
        SchemaBuilder.map()
            .prop(AvroSchemaUtil.KEY_ID_PROP, "0")
            .prop(AvroSchemaUtil.VALUE_ID_PROP, "1")
            .values(
                SchemaBuilder.record("value")
                    .namespace("unit.test")
                    .fields()
                    .name("int1")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "1")
                    .type()
                    .nullable()
                    .intType()
                    .noDefault()
                    .name("string1_r2")
                    .prop(AvroSchemaUtil.FIELD_ID_PROP, "2")
                    .type()
                    .nullable()
                    .stringType()
                    .noDefault()
                    .endRecord());

    final BuildAvroProjection testSubject =
        new BuildAvroProjection(icebergType, Collections.emptyMap());

    final Supplier<org.apache.avro.Schema> supplier = expected::getValueType;

    final org.apache.avro.Schema actual = testSubject.map(lessField, supplier);

    Assertions.assertThat(actual)
        .as("Map projection produced undesired map schema")
        .isEqualTo(expected);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.KEY_ID_PROP)).intValue())
        .as("Unexpected key ID discovered on the projected map schema")
        .isEqualTo(0);
    Assertions.assertThat(Integer.valueOf(actual.getProp(AvroSchemaUtil.VALUE_ID_PROP)).intValue())
        .as("Unexpected value ID discovered on the projected map schema")
        .isEqualTo(1);
  }
}
