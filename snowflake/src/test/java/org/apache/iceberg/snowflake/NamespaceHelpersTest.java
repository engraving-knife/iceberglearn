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
package org.apache.iceberg.snowflake;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 NamespaceHelpersTest 的功能。
 *
 * <p>所属模块：iceberg-snowflake。职责：验证 NamespaceHelpersTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class NamespaceHelpersTest {
  /**
   * 测试场景：Round Trip Root。
   *
   * <p>验证该方法在 Round Trip Root 条件下的行为是否符合预期。
   */
  @Test
  public void testRoundTripRoot() {
    Namespace icebergNamespace = Namespace.empty();
    SnowflakeIdentifier snowflakeIdentifier =
        NamespaceHelpers.toSnowflakeIdentifier(icebergNamespace);
    Assertions.assertThat(snowflakeIdentifier).isEqualTo(SnowflakeIdentifier.ofRoot());
    Assertions.assertThat(NamespaceHelpers.toIcebergNamespace(snowflakeIdentifier))
        .isEqualTo(icebergNamespace);
  }

  /**
   * 测试场景：Round Trip Database。
   *
   * <p>验证该方法在 Round Trip Database 条件下的行为是否符合预期。
   */
  @Test
  public void testRoundTripDatabase() {
    Namespace icebergNamespace = Namespace.of("DB1");
    SnowflakeIdentifier snowflakeIdentifier =
        NamespaceHelpers.toSnowflakeIdentifier(icebergNamespace);
    Assertions.assertThat(snowflakeIdentifier).isEqualTo(SnowflakeIdentifier.ofDatabase("DB1"));
    Assertions.assertThat(NamespaceHelpers.toIcebergNamespace(snowflakeIdentifier))
        .isEqualTo(icebergNamespace);
  }

  /**
   * 测试场景：Round Trip Schema。
   *
   * <p>验证该方法在 Round Trip Schema 条件下的行为是否符合预期。
   */
  @Test
  public void testRoundTripSchema() {
    Namespace icebergNamespace = Namespace.of("DB1", "SCHEMA1");
    SnowflakeIdentifier snowflakeIdentifier =
        NamespaceHelpers.toSnowflakeIdentifier(icebergNamespace);
    Assertions.assertThat(snowflakeIdentifier)
        .isEqualTo(SnowflakeIdentifier.ofSchema("DB1", "SCHEMA1"));
    Assertions.assertThat(NamespaceHelpers.toIcebergNamespace(snowflakeIdentifier))
        .isEqualTo(icebergNamespace);
  }

  /**
   * 测试场景：Round Trip Table。
   *
   * <p>验证该方法在 Round Trip Table 条件下的行为是否符合预期。
   */
  @Test
  public void testRoundTripTable() {
    TableIdentifier icebergTable = TableIdentifier.of("DB1", "SCHEMA1", "TABLE1");
    SnowflakeIdentifier snowflakeIdentifier = NamespaceHelpers.toSnowflakeIdentifier(icebergTable);
    Assertions.assertThat(snowflakeIdentifier)
        .isEqualTo(SnowflakeIdentifier.ofTable("DB1", "SCHEMA1", "TABLE1"));
    Assertions.assertThat(NamespaceHelpers.toIcebergTableIdentifier(snowflakeIdentifier))
        .isEqualTo(icebergTable);
  }

  /**
   * 测试场景：To Snowflake Identifier Max Namespace Level。
   *
   * <p>验证该方法在 To Snowflake Identifier Max Namespace Level 条件下的行为是否符合预期。
   */
  @Test
  public void testToSnowflakeIdentifierMaxNamespaceLevel() {
    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                NamespaceHelpers.toSnowflakeIdentifier(
                    Namespace.of("DB1", "SCHEMA1", "THIRD_NS_LVL")))
        .withMessageContaining("max namespace level");
  }

  /**
   * 测试场景：To Snowflake Identifier Table Bad Namespace。
   *
   * <p>验证该方法在 To Snowflake Identifier Table Bad Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testToSnowflakeIdentifierTableBadNamespace() {
    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                NamespaceHelpers.toSnowflakeIdentifier(
                    TableIdentifier.of(Namespace.of("DB1_WITHOUT_SCHEMA"), "TABLE1")))
        .withMessageContaining("must be at the SCHEMA level");
  }

  /**
   * 测试场景：To Iceberg Namespace Table Fails。
   *
   * <p>验证该方法在 To Iceberg Namespace Table Fails 条件下的行为是否符合预期。
   */
  @Test
  public void testToIcebergNamespaceTableFails() {
    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                NamespaceHelpers.toIcebergNamespace(
                    SnowflakeIdentifier.ofTable("DB1", "SCHEMA1", "TABLE1")))
        .withMessageContaining("Cannot convert identifier");
  }

  /**
   * 测试场景：To Iceberg Table Identifier。
   *
   * <p>验证该方法在 To Iceberg Table Identifier 条件下的行为是否符合预期。
   */
  @Test
  public void testToIcebergTableIdentifier() {
    Assertions.assertThat(
            NamespaceHelpers.toIcebergTableIdentifier(
                SnowflakeIdentifier.ofTable("DB1", "SCHEMA1", "TABLE1")))
        .isEqualTo(TableIdentifier.of("DB1", "SCHEMA1", "TABLE1"));
  }

  /**
   * 测试场景：To Iceberg Table Identifier Wrong Type。
   *
   * <p>验证该方法在 To Iceberg Table Identifier Wrong Type 条件下的行为是否符合预期。
   */
  @Test
  public void testToIcebergTableIdentifierWrongType() {
    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                NamespaceHelpers.toIcebergTableIdentifier(
                    SnowflakeIdentifier.ofSchema("DB1", "SCHEMA1")))
        .withMessageContaining("must be type TABLE");
  }
}
