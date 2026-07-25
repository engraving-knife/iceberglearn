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
package org.apache.iceberg.mapping;

import static org.apache.iceberg.types.Types.NestedField.required;

import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TableTestBase;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.types.Types;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestMappingUpdates，用于验证 Mapping Updates 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Mapping Updates 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestMappingUpdates extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：mapping updates。 */
  public TestMappingUpdates(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：add column mapping update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddColumnMappingUpdate() {
    NameMapping mapping = MappingUtil.create(table.schema());
    table
        .updateProperties()
        .set(TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(mapping))
        .commit();

    Assert.assertEquals(
        MappedFields.of(MappedField.of(1, "id"), MappedField.of(2, "data")),
        mapping.asMappedFields());

    table.updateSchema().addColumn("ts", Types.TimestampType.withZone()).commit();

    NameMapping updated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"), MappedField.of(2, "data"), MappedField.of(3, "ts")),
        updated.asMappedFields());
  }

  /**
   * 测试场景：add nested column mapping update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddNestedColumnMappingUpdate() {
    NameMapping mapping = MappingUtil.create(table.schema());
    table
        .updateProperties()
        .set(TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(mapping))
        .commit();

    Assert.assertEquals(
        MappedFields.of(MappedField.of(1, "id"), MappedField.of(2, "data")),
        mapping.asMappedFields());

    table
        .updateSchema()
        .addColumn(
            "point",
            Types.StructType.of(
                required(1, "x", Types.DoubleType.get()), required(2, "y", Types.DoubleType.get())))
        .commit();

    NameMapping updated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3, "point", MappedFields.of(MappedField.of(4, "x"), MappedField.of(5, "y")))),
        updated.asMappedFields());

    table.updateSchema().addColumn("point", "z", Types.DoubleType.get()).commit();

    NameMapping pointUpdated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3,
                "point",
                MappedFields.of(
                    MappedField.of(4, "x"), MappedField.of(5, "y"), MappedField.of(6, "z")))),
        pointUpdated.asMappedFields());
  }

  /**
   * 测试场景：rename mapping update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameMappingUpdate() {
    NameMapping mapping = MappingUtil.create(table.schema());
    table
        .updateProperties()
        .set(TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(mapping))
        .commit();

    Assert.assertEquals(
        MappedFields.of(MappedField.of(1, "id"), MappedField.of(2, "data")),
        mapping.asMappedFields());

    table.updateSchema().renameColumn("id", "object_id").commit();

    NameMapping updated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, ImmutableList.of("id", "object_id")), MappedField.of(2, "data")),
        updated.asMappedFields());
  }

  /**
   * 测试场景：rename nested field mapping update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameNestedFieldMappingUpdate() {
    NameMapping mapping = MappingUtil.create(table.schema());
    table
        .updateProperties()
        .set(TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(mapping))
        .commit();

    table
        .updateSchema()
        .addColumn(
            "point",
            Types.StructType.of(
                required(1, "x", Types.DoubleType.get()), required(2, "y", Types.DoubleType.get())))
        .commit();

    NameMapping updated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3, "point", MappedFields.of(MappedField.of(4, "x"), MappedField.of(5, "y")))),
        updated.asMappedFields());

    table.updateSchema().renameColumn("point.x", "X").renameColumn("point.y", "Y").commit();

    NameMapping pointUpdated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3,
                "point",
                MappedFields.of(
                    MappedField.of(4, ImmutableList.of("x", "X")),
                    MappedField.of(5, ImmutableList.of("y", "Y"))))),
        pointUpdated.asMappedFields());
  }

  /**
   * 测试场景：rename complex field mapping update。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameComplexFieldMappingUpdate() {
    NameMapping mapping = MappingUtil.create(table.schema());
    table
        .updateProperties()
        .set(TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(mapping))
        .commit();

    table
        .updateSchema()
        .addColumn(
            "point",
            Types.StructType.of(
                required(1, "x", Types.DoubleType.get()), required(2, "y", Types.DoubleType.get())))
        .commit();

    NameMapping updated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3, "point", MappedFields.of(MappedField.of(4, "x"), MappedField.of(5, "y")))),
        updated.asMappedFields());

    table.updateSchema().renameColumn("point", "p2").commit();

    NameMapping pointUpdated =
        NameMappingParser.fromJson(table.properties().get(TableProperties.DEFAULT_NAME_MAPPING));

    Assert.assertEquals(
        MappedFields.of(
            MappedField.of(1, "id"),
            MappedField.of(2, "data"),
            MappedField.of(
                3,
                ImmutableList.of("point", "p2"),
                MappedFields.of(MappedField.of(4, "x"), MappedField.of(5, "y")))),
        pointUpdated.asMappedFields());
  }
}
