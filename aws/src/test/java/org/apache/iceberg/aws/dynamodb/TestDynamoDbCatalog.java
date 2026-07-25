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
package org.apache.iceberg.aws.dynamodb;

import static org.apache.iceberg.aws.dynamodb.DynamoDbCatalog.toPropertyCol;
import static org.mockito.ArgumentMatchers.any;

import org.apache.iceberg.aws.AwsProperties;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * 文件级说明：测试 TestDynamoDbCatalog 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestDynamoDbCatalog 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestDynamoDbCatalog {

  private static final String WAREHOUSE_PATH = "s3://bucket";
  private static final String CATALOG_NAME = "dynamodb";
  private static final TableIdentifier TABLE_IDENTIFIER = TableIdentifier.of("db", "table");

  private DynamoDbClient dynamo;
  private DynamoDbCatalog dynamoCatalog;

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    dynamo = Mockito.mock(DynamoDbClient.class);
    dynamoCatalog = new DynamoDbCatalog();
    dynamoCatalog.initialize(CATALOG_NAME, WAREHOUSE_PATH, new AwsProperties(), dynamo, null);
  }

  /**
   * 测试场景：Constructor Warehouse Path With End Slash。
   *
   * <p>验证该方法在 Constructor Warehouse Path With End Slash 条件下的行为是否符合预期。
   */
  @Test
  public void testConstructorWarehousePathWithEndSlash() {
    DynamoDbCatalog catalogWithSlash = new DynamoDbCatalog();
    catalogWithSlash.initialize(
        CATALOG_NAME, WAREHOUSE_PATH + "/", new AwsProperties(), dynamo, null);
    Mockito.doReturn(GetItemResponse.builder().item(Maps.newHashMap()).build())
        .when(dynamo)
        .getItem(any(GetItemRequest.class));
    String location = catalogWithSlash.defaultWarehouseLocation(TABLE_IDENTIFIER);
    Assertions.assertThat(location).isEqualTo(WAREHOUSE_PATH + "/db.db/table");
  }

  /**
   * 测试场景：Default Warehouse Location No Db Uri。
   *
   * <p>验证该方法在 Default Warehouse Location No Db Uri 条件下的行为是否符合预期。
   */
  @Test
  public void testDefaultWarehouseLocationNoDbUri() {
    Mockito.doReturn(GetItemResponse.builder().item(Maps.newHashMap()).build())
        .when(dynamo)
        .getItem(any(GetItemRequest.class));

    String warehousePath = WAREHOUSE_PATH + "/db.db/table";
    String defaultWarehouseLocation = dynamoCatalog.defaultWarehouseLocation(TABLE_IDENTIFIER);
    Assertions.assertThat(defaultWarehouseLocation).isEqualTo(warehousePath);
  }

  /**
   * 测试场景：Default Warehouse Location Db Uri。
   *
   * <p>验证该方法在 Default Warehouse Location Db Uri 条件下的行为是否符合预期。
   */
  @Test
  public void testDefaultWarehouseLocationDbUri() {
    String dbUri = "s3://bucket2/db";
    Mockito.doReturn(
            GetItemResponse.builder()
                .item(
                    ImmutableMap.of(
                        toPropertyCol(DynamoDbCatalog.defaultLocationProperty()),
                        AttributeValue.builder().s(dbUri).build()))
                .build())
        .when(dynamo)
        .getItem(any(GetItemRequest.class));

    String defaultWarehouseLocation = dynamoCatalog.defaultWarehouseLocation(TABLE_IDENTIFIER);
    Assertions.assertThat(defaultWarehouseLocation).isEqualTo("s3://bucket2/db/table");
  }

  /**
   * 测试场景：Default Warehouse Location No Namespace。
   *
   * <p>验证该方法在 Default Warehouse Location No Namespace 条件下的行为是否符合预期。
   */
  @Test
  public void testDefaultWarehouseLocationNoNamespace() {
    Mockito.doReturn(GetItemResponse.builder().build())
        .when(dynamo)
        .getItem(any(GetItemRequest.class));

    Assertions.assertThatThrownBy(() -> dynamoCatalog.defaultWarehouseLocation(TABLE_IDENTIFIER))
        .as("default warehouse can't be called on non existent namespace")
        .isInstanceOf(NoSuchNamespaceException.class)
        .hasMessageContaining("Cannot find default warehouse location:");
  }
}
