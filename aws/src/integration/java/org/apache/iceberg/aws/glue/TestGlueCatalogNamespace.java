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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.services.glue.model.CreateTableRequest;
import software.amazon.awssdk.services.glue.model.Database;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetDatabaseRequest;
import software.amazon.awssdk.services.glue.model.TableInput;

/**
 * 文件级说明：TestGlueCatalogNamespace 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 Glue目录命名空间 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestGlueCatalogNamespace extends GlueTestBase {

  /**
   * 测试场景：创建命名空间。
   *
   * <p>验证该方法在 创建命名空间 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateNamespace() {
    String namespace = getRandomName();
    namespaces.add(namespace);
    AssertHelpers.assertThrows(
        "namespace does not exist before create",
        EntityNotFoundException.class,
        "not found",
        () -> glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()));
    Map<String, String> properties =
        ImmutableMap.of(
            IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY,
            "description",
            IcebergToGlueConverter.GLUE_DB_LOCATION_KEY,
            "s3://location",
            "key",
            "val");
    Namespace ns = Namespace.of(namespace);
    glueCatalog.createNamespace(ns, properties);
    Database database =
        glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()).database();
    Assert.assertEquals("namespace must equal database name", namespace, database.name());
    Assert.assertEquals(
        "namespace description should be set", "description", database.description());
    Assert.assertEquals(
        "namespace location should be set", "s3://location", database.locationUri());
    Assert.assertEquals(
        "namespace parameters should be set", ImmutableMap.of("key", "val"), database.parameters());
    Assert.assertEquals(properties, glueCatalog.loadNamespaceMetadata(ns));
  }

  /**
   * 测试场景：创建duplicate。
   *
   * <p>验证该方法在 创建duplicate 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateDuplicate() {
    String namespace = createNamespace();
    AssertHelpers.assertThrows(
        "should not create namespace with the same name",
        AlreadyExistsException.class,
        "it already exists in Glue",
        () -> glueCatalog.createNamespace(Namespace.of(namespace)));
  }

  /**
   * 测试场景：创建badname。
   *
   * <p>验证该方法在 创建badname 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateBadName() {
    List<Namespace> invalidNamespaces =
        Lists.newArrayList(Namespace.of("db-1"), Namespace.of("db", "db2"));

    for (Namespace namespace : invalidNamespaces) {
      AssertHelpers.assertThrows(
          "should not create namespace with invalid or nested names",
          ValidationException.class,
          "Cannot convert namespace",
          () -> glueCatalog.createNamespace(namespace));
    }
  }

  /**
   * 测试场景：命名空间存在。
   *
   * <p>验证该方法在 命名空间存在 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNamespaceExists() {
    String namespace = createNamespace();
    Assert.assertTrue(glueCatalog.namespaceExists(Namespace.of(namespace)));
  }

  /**
   * 测试场景：列表命名空间。
   *
   * <p>验证该方法在 列表命名空间 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testListNamespace() {
    String namespace = createNamespace();
    List<Namespace> namespaceList = glueCatalog.listNamespaces();
    Assert.assertTrue(namespaceList.size() > 0);
    Assert.assertTrue(namespaceList.contains(Namespace.of(namespace)));
    namespaceList = glueCatalog.listNamespaces(Namespace.of(namespace));
    Assert.assertTrue(namespaceList.isEmpty());
  }

  /**
   * 测试场景：命名空间属性。
   *
   * <p>验证该方法在 命名空间属性 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testNamespaceProperties() {
    String namespace = createNamespace();
    // set properties
    Map<String, String> properties = Maps.newHashMap();
    properties.put("key", "val");
    properties.put("key2", "val2");
    properties.put(IcebergToGlueConverter.GLUE_DB_LOCATION_KEY, "s3://test");
    properties.put(IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY, "description");
    glueCatalog.setProperties(Namespace.of(namespace), properties);
    Database database =
        glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()).database();
    Assert.assertTrue(database.parameters().containsKey("key"));
    Assert.assertEquals("val", database.parameters().get("key"));
    Assert.assertTrue(database.parameters().containsKey("key2"));
    Assert.assertEquals("val2", database.parameters().get("key2"));
    Assert.assertEquals("s3://test", database.locationUri());
    Assert.assertEquals("description", database.description());
    // remove properties
    glueCatalog.removeProperties(
        Namespace.of(namespace),
        Sets.newHashSet(
            "key",
            IcebergToGlueConverter.GLUE_DB_LOCATION_KEY,
            IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY));
    database = glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()).database();
    Assert.assertFalse(database.parameters().containsKey("key"));
    Assert.assertTrue(database.parameters().containsKey("key2"));
    Assert.assertEquals("val2", database.parameters().get("key2"));
    Assert.assertNull(database.locationUri());
    Assert.assertNull(database.description());
    // add back
    properties = Maps.newHashMap();
    properties.put("key", "val");
    properties.put(IcebergToGlueConverter.GLUE_DB_LOCATION_KEY, "s3://test2");
    properties.put(IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY, "description2");
    glueCatalog.setProperties(Namespace.of(namespace), properties);
    database = glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()).database();
    Assert.assertTrue(database.parameters().containsKey("key"));
    Assert.assertEquals("val", database.parameters().get("key"));
    Assert.assertTrue(database.parameters().containsKey("key2"));
    Assert.assertEquals("val2", database.parameters().get("key2"));
    Assert.assertEquals("s3://test2", database.locationUri());
    Assert.assertEquals("description2", database.description());
  }

  /**
   * 测试场景：删除命名空间。
   *
   * <p>验证该方法在 删除命名空间 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropNamespace() {
    String namespace = createNamespace();
    glueCatalog.dropNamespace(Namespace.of(namespace));
    AssertHelpers.assertThrows(
        "namespace should not exist after deletion",
        EntityNotFoundException.class,
        "not found",
        () -> glue.getDatabase(GetDatabaseRequest.builder().name(namespace).build()));
  }

  /**
   * 测试场景：删除命名空间thatcontainsonlyIceberg表。
   *
   * <p>验证该方法在 删除命名空间thatcontainsonlyIceberg表 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropNamespaceThatContainsOnlyIcebergTable() {
    String namespace = createNamespace();
    createTable(namespace);
    AssertHelpers.assertThrows(
        "namespace should not be dropped when still has Iceberg table",
        NamespaceNotEmptyException.class,
        "still contains Iceberg tables",
        () -> glueCatalog.dropNamespace(Namespace.of(namespace)));
  }

  /**
   * 测试场景：删除命名空间thatcontains不存在的Iceberg表。
   *
   * <p>验证该方法在 删除命名空间thatcontains不存在的Iceberg表 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropNamespaceThatContainsNonIcebergTable() {
    String namespace = createNamespace();
    glue.createTable(
        CreateTableRequest.builder()
            .databaseName(namespace)
            .tableInput(TableInput.builder().name(UUID.randomUUID().toString()).build())
            .build());
    AssertHelpers.assertThrows(
        "namespace should not be dropped when still has non-Iceberg table",
        NamespaceNotEmptyException.class,
        "still contains non-Iceberg tables",
        () -> glueCatalog.dropNamespace(Namespace.of(namespace)));
  }
}
