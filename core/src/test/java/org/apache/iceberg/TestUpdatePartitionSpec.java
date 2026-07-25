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

import static org.apache.iceberg.expressions.Expressions.bucket;
import static org.apache.iceberg.expressions.Expressions.day;
import static org.apache.iceberg.expressions.Expressions.hour;
import static org.apache.iceberg.expressions.Expressions.month;
import static org.apache.iceberg.expressions.Expressions.ref;
import static org.apache.iceberg.expressions.Expressions.truncate;
import static org.apache.iceberg.expressions.Expressions.year;

import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestUpdatePartitionSpec，用于验证 Update Partition Spec 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Update Partition Spec 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestUpdatePartitionSpec extends TableTestBase {
  private static final Schema SCHEMA =
      new Schema(
          Types.NestedField.required(1, "id", Types.LongType.get()),
          Types.NestedField.required(2, "ts", Types.TimestampType.withZone()),
          Types.NestedField.required(3, "category", Types.StringType.get()),
          Types.NestedField.optional(4, "data", Types.StringType.get()));

  private static final PartitionSpec UNPARTITIONED = PartitionSpec.builderFor(SCHEMA).build();
  private static final PartitionSpec PARTITIONED =
      PartitionSpec.builderFor(SCHEMA)
          .identity("category")
          .day("ts")
          .bucket("id", 16, "shard")
          .build();

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：update partition spec。 */
  public TestUpdatePartitionSpec(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：add identity by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddIdentityByName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField("category").apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).identity("category").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add identity by term。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddIdentityByTerm() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(ref("category")).apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).identity("category").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add year。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddYear() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(year("ts")).apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).year("ts").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add month。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddMonth() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(month("ts")).apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).month("ts").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add day。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddDay() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(day("ts")).apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).day("ts").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add hour。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddHour() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(hour("ts")).apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).hour("ts").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add bucket。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddBucket() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField(bucket("id", 16))
            .apply();

    // added fields have different default names to avoid conflicts
    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA).bucket("id", 16, "id_bucket_16").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add truncate。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddTruncate() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField(truncate("data", 4))
            .apply();

    // added fields have different default names to avoid conflicts
    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA).truncate("data", 4, "data_trunc_4").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add named partition。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddNamedPartition() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField("shard", bucket("id", 16))
            .apply();

    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).bucket("id", 16, "shard").build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add to existing。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddToExisting() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .addField(truncate("data", 4))
            .apply();

    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .day("ts")
            .bucket("id", 16, "shard")
            .truncate("data", 4, "data_trunc_4")
            .build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：multiple adds。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMultipleAdds() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField("category")
            .addField(day("ts"))
            .addField("shard", bucket("id", 16))
            .addField("prefix", truncate("data", 4))
            .apply();

    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .day("ts")
            .bucket("id", 16, "shard")
            .truncate("data", 4, "prefix")
            .build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：add hour to day。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddHourToDay() {
    // multiple partitions for the same source with different time granularity is not allowed by the
    // builder, but is
    // allowed when updating a spec so that existing columns in metadata continue to work.
    PartitionSpec byDay =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED).addField(day("ts")).apply();

    PartitionSpec byHour =
        new BaseUpdatePartitionSpec(formatVersion, byDay).addField(hour("ts")).apply();

    Assert.assertEquals(
        "Should have a day and an hour time field",
        ImmutableList.of(
            new PartitionField(2, 1000, "ts_day", Transforms.day()),
            new PartitionField(2, 1001, "ts_hour", Transforms.hour())),
        byHour.fields());
  }

  /**
   * 测试场景：add multiple buckets。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddMultipleBuckets() {
    PartitionSpec bucket16 =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField(bucket("id", 16))
            .apply();

    PartitionSpec bucket8 =
        new BaseUpdatePartitionSpec(formatVersion, bucket16).addField(bucket("id", 8)).apply();

    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA)
            .bucket("id", 16, "id_bucket_16")
            .bucket("id", 8, "id_bucket_8")
            .build();

    Assert.assertEquals("Should have a day and an hour time field", expected, bucket8);
  }

  /**
   * 测试场景：remove identity by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveIdentityByName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).removeField("category").apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .alwaysNull("category", "category")
            .day("ts")
            .bucket("id", 16, "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA)
            .add(id("ts"), 1001, "ts_day", Transforms.day())
            .add(id("id"), 1002, "shard", Transforms.bucket(16))
            .build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove bucket by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveBucketByName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).removeField("shard").apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .day("ts")
            .alwaysNull("id", "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA)
            .add(id("category"), 1000, "category", Transforms.identity())
            .add(id("ts"), 1001, "ts_day", Transforms.day())
            .build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove identity by equivalent。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveIdentityByEquivalent() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField(ref("category"))
            .apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .alwaysNull("category", "category")
            .day("ts")
            .bucket("id", 16, "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA)
            .add(id("ts"), 1001, "ts_day", Transforms.day())
            .add(id("id"), 1002, "shard", Transforms.bucket(16))
            .build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove day by equivalent。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveDayByEquivalent() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).removeField(day("ts")).apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .alwaysNull("ts", "ts_day")
            .bucket("id", 16, "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA)
            .add(id("category"), 1000, "category", Transforms.identity())
            .add(id("id"), 1002, "shard", Transforms.bucket(16))
            .build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove bucket by equivalent。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveBucketByEquivalent() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField(bucket("id", 16))
            .apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .day("ts")
            .alwaysNull("id", "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA).identity("category").day("ts").build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：rename。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRename() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .renameField("shard", "id_bucket") // rename back to default
            .apply();

    PartitionSpec expected =
        PartitionSpec.builderFor(SCHEMA).identity("category").day("ts").bucket("id", 16).build();

    Assert.assertEquals("Should match expected spec", expected, updated);
  }

  /**
   * 测试场景：multiple changes。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMultipleChanges() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .renameField("shard", "id_bucket") // rename back to default
            .removeField(day("ts"))
            .addField("prefix", truncate("data", 4))
            .apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .alwaysNull("ts", "ts_day")
            .bucket("id", 16)
            .truncate("data", 4, "prefix")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA)
            .add(id("category"), 1000, "category", Transforms.identity())
            .add(id("id"), 1002, "id_bucket", Transforms.bucket(16))
            .add(id("data"), 1003, "prefix", Transforms.truncate(4))
            .build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：add deleted name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddDeletedName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField(bucket("id", 16))
            .apply();

    PartitionSpec v1Expected =
        PartitionSpec.builderFor(SCHEMA)
            .identity("category")
            .day("ts")
            .alwaysNull("id", "shard")
            .build();

    V1Assert.assertEquals("Should match expected spec", v1Expected, updated);

    PartitionSpec v2Expected =
        PartitionSpec.builderFor(SCHEMA).identity("category").day("ts").build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove newly added field by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveNewlyAddedFieldByName() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("prefix", truncate("data", 4))
                    .removeField("prefix"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot delete newly added field");
  }

  /**
   * 测试场景：remove newly added field by transform。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveNewlyAddedFieldByTransform() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("prefix", truncate("data", 4))
                    .removeField(truncate("data", 4)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot delete newly added field");
  }

  /**
   * 测试场景：add already added field by transform。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddAlreadyAddedFieldByTransform() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("prefix", truncate("data", 4))
                    .addField(truncate("data", 4)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：add already added field by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddAlreadyAddedFieldByName() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("prefix", truncate("data", 4))
                    .addField("prefix", truncate("data", 6)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：add redundant time partition。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddRedundantTimePartition() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
                    .addField(day("ts"))
                    .addField(hour("ts"))) // conflicts with hour
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add redundant partition field");

    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField(hour("ts")) // does not conflict with day because day already exists
                    .addField(month("ts"))) // conflicts with hour
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add redundant partition");
  }

  /**
   * 测试场景：no effect add deleted same field with same name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNoEffectAddDeletedSameFieldWithSameName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField("shard")
            .addField("shard", bucket("id", 16))
            .apply();
    Assert.assertEquals(PARTITIONED, updated);
    updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField("shard")
            .addField(bucket("id", 16))
            .apply();
    Assert.assertEquals(PARTITIONED, updated);
  }

  /**
   * 测试场景：generate new spec add deleted same field with different name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGenerateNewSpecAddDeletedSameFieldWithDifferentName() {
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
            .removeField("shard")
            .addField("new_shard", bucket("id", 16))
            .apply();
    Assert.assertEquals("Should match expected field size", 3, updated.fields().size());
    Assert.assertEquals(
        "Should match expected field name", "category", updated.fields().get(0).name());
    Assert.assertEquals(
        "Should match expected field name", "ts_day", updated.fields().get(1).name());
    Assert.assertEquals(
        "Should match expected field name", "new_shard", updated.fields().get(2).name());
    Assert.assertEquals(
        "Should match expected field transform",
        "identity",
        updated.fields().get(0).transform().toString());
    Assert.assertEquals(
        "Should match expected field transform",
        "day",
        updated.fields().get(1).transform().toString());
    Assert.assertEquals(
        "Should match expected field transform",
        "bucket[16]",
        updated.fields().get(2).transform().toString());
  }

  /**
   * 测试场景：add duplicate by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddDuplicateByName() {
    Assertions.assertThatThrownBy(
            () -> new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).addField("category"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：add duplicate by ref。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddDuplicateByRef() {
    Assertions.assertThatThrownBy(
            () -> new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).addField(ref("category")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：add duplicate transform。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddDuplicateTransform() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).addField(bucket("id", 16)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：add named duplicate。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAddNamedDuplicate() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("b16", bucket("id", 16)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot add duplicate partition field");
  }

  /**
   * 测试场景：remove unknown field by name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveUnknownFieldByName() {
    Assertions.assertThatThrownBy(
            () -> new BaseUpdatePartitionSpec(formatVersion, PARTITIONED).removeField("moon"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot find partition field to remove");
  }

  /**
   * 测试场景：remove unknown field by equivalent。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveUnknownFieldByEquivalent() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .removeField(hour("ts")) // day(ts) exists
            )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot find partition field to remove");
  }

  /**
   * 测试场景：rename unknown field。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameUnknownField() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .renameField("shake", "seal"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot find partition field to rename: shake");
  }

  /**
   * 测试场景：rename after add。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameAfterAdd() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .addField("data_trunc", truncate("data", 4))
                    .renameField("data_trunc", "prefix"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot rename newly added partition field: data_trunc");
  }

  /**
   * 测试场景：delete and rename。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeleteAndRename() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .renameField("shard", "id_bucket")
                    .removeField(bucket("id", 16)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot rename and delete partition field: shard");
  }

  /**
   * 测试场景：rename and delete。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRenameAndDelete() {
    Assertions.assertThatThrownBy(
            () ->
                new BaseUpdatePartitionSpec(formatVersion, PARTITIONED)
                    .removeField(bucket("id", 16))
                    .renameField("shard", "id_bucket"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot delete and rename partition field: shard");
  }

  /**
   * 测试场景：remove and add multi times。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveAndAddMultiTimes() {
    PartitionSpec addFirstTime =
        new BaseUpdatePartitionSpec(formatVersion, UNPARTITIONED)
            .addField("ts_date", day("ts"))
            .apply();
    PartitionSpec removeFirstTime =
        new BaseUpdatePartitionSpec(formatVersion, addFirstTime).removeField(day("ts")).apply();
    PartitionSpec addSecondTime =
        new BaseUpdatePartitionSpec(formatVersion, removeFirstTime)
            .addField("ts_date", day("ts"))
            .apply();
    PartitionSpec removeSecondTime =
        new BaseUpdatePartitionSpec(formatVersion, addSecondTime).removeField(day("ts")).apply();
    PartitionSpec addThirdTime =
        new BaseUpdatePartitionSpec(formatVersion, removeSecondTime).addField(month("ts")).apply();
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, addThirdTime)
            .renameField("ts_month", "ts_date")
            .apply();

    if (formatVersion == 1) {
      Assert.assertEquals("Should match expected spec field size", 3, updated.fields().size());

      Assert.assertTrue(
          "Should match expected field name",
          updated.fields().get(0).name().matches("^ts_date(?:_\\d+)+$"));
      Assert.assertTrue(
          "Should match expected field name",
          updated.fields().get(1).name().matches("^ts_date_(?:\\d+)+$"));
      Assert.assertEquals(
          "Should match expected field name", "ts_date", updated.fields().get(2).name());

      Assert.assertEquals(
          "Should match expected field transform",
          "void",
          updated.fields().get(0).transform().toString());
      Assert.assertEquals(
          "Should match expected field transform",
          "void",
          updated.fields().get(1).transform().toString());
      Assert.assertEquals(
          "Should match expected field transform",
          "month",
          updated.fields().get(2).transform().toString());
    }

    PartitionSpec v2Expected = PartitionSpec.builderFor(SCHEMA).month("ts", "ts_date").build();

    V2Assert.assertEquals("Should match expected spec", v2Expected, updated);
  }

  /**
   * 测试场景：remove and update with different transformation。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemoveAndUpdateWithDifferentTransformation() {
    PartitionSpec expected = PartitionSpec.builderFor(SCHEMA).month("ts", "ts_transformed").build();
    PartitionSpec updated =
        new BaseUpdatePartitionSpec(formatVersion, expected)
            .removeField("ts_transformed")
            .addField("ts_transformed", day("ts"))
            .apply();

    if (formatVersion == 1) {
      Assert.assertEquals("Should match expected spec field size", 2, updated.fields().size());
      Assert.assertEquals(
          "Should match expected field name",
          "ts_transformed_1000",
          updated.fields().get(0).name());
      Assert.assertEquals(
          "Should match expected field name", "ts_transformed", updated.fields().get(1).name());

      Assert.assertEquals(
          "Should match expected field transform",
          "void",
          updated.fields().get(0).transform().toString());
      Assert.assertEquals(
          "Should match expected field transform",
          "day",
          updated.fields().get(1).transform().toString());
    } else {
      Assert.assertEquals("Should match expected spec field size", 1, updated.fields().size());
      Assert.assertEquals(
          "Should match expected field name", "ts_transformed", updated.fields().get(0).name());
      Assert.assertEquals(
          "Should match expected field transform",
          "day",
          updated.fields().get(0).transform().toString());
    }
  }

  /** 辅助方法：id。 */
  private static int id(String name) {
    return SCHEMA.findField(name).fieldId();
  }
}
