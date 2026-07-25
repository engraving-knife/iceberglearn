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
package org.apache.iceberg.aws.glue;

import java.util.Map;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchIcebergTableException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.Table;

/**
 * 文件级说明：测试 TestGlueToIcebergConverter 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestGlueToIcebergConverter 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestGlueToIcebergConverter {

  /**
   * 测试场景：To Namespace。
   *
   * <p>验证该方法在 To Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testToNamespace() {
    Database database = Database.builder().name("db").build();
    Namespace namespace = Namespace.of("db");
    Assertions.assertThat(GlueToIcebergConverter.toNamespace(database)).isEqualTo(namespace);
  }

  /**
   * 测试场景：To Table Id。
   *
   * <p>验证该方法在 To Table Id 条件下的行为是否符合预期。
   */
  @Test
  public void testToTableId() {
    Table table = Table.builder().databaseName("db").name("name").build();
    TableIdentifier icebergId = TableIdentifier.of("db", "name");
    Assertions.assertThat(GlueToIcebergConverter.toTableId(table)).isEqualTo(icebergId);
  }

  /**
   * 测试场景：Validate Table Iceberg Property Not Found。
   *
   * <p>验证该方法在 Validate Table Iceberg Property Not Found 条件下的行为是否符合预期。
   */
  @Test
  public void testValidateTableIcebergPropertyNotFound() {
    Table table = Table.builder().parameters(ImmutableMap.of()).build();

    Assertions.assertThatThrownBy(() -> GlueTableOperations.checkIfTableIsIceberg(table, "name"))
        .isInstanceOf(NoSuchIcebergTableException.class)
        .hasMessage("Input Glue table is not an iceberg table: name (type=null)");
  }

  /**
   * 测试场景：Validate Table Iceberg Property Value Wrong。
   *
   * <p>验证该方法在 Validate Table Iceberg Property Value Wrong 条件下的行为是否符合预期。
   */
  @Test
  public void testValidateTableIcebergPropertyValueWrong() {
    Map<String, String> properties =
        ImmutableMap.of(BaseMetastoreTableOperations.TABLE_TYPE_PROP, "other");
    Table table = Table.builder().parameters(properties).build();

    Assertions.assertThatThrownBy(() -> GlueTableOperations.checkIfTableIsIceberg(table, "name"))
        .isInstanceOf(NoSuchIcebergTableException.class)
        .hasMessage("Input Glue table is not an iceberg table: name (type=other)");
  }
}
