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
package org.apache.iceberg.rest;

import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestResourcePaths，用于验证 Resource Paths 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Resource Paths 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestResourcePaths {
  private final String prefix = "ws/catalog";
  private final ResourcePaths withPrefix =
      ResourcePaths.forCatalogProperties(ImmutableMap.of("prefix", prefix));
  private final ResourcePaths withoutPrefix = ResourcePaths.forCatalogProperties(ImmutableMap.of());

  /**
   * 测试场景：config path。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testConfigPath() {
    // prefix does not affect the config route because config is merged into catalog properties
    Assertions.assertThat(ResourcePaths.config()).isEqualTo("v1/config");
  }

  /**
   * 测试场景：namespaces。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespaces() {
    Assertions.assertThat(withPrefix.namespaces()).isEqualTo("v1/ws/catalog/namespaces");
    Assertions.assertThat(withoutPrefix.namespaces()).isEqualTo("v1/namespaces");
  }

  /**
   * 测试场景：namespace。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespace() {
    Namespace ns = Namespace.of("ns");
    Assertions.assertThat(withPrefix.namespace(ns)).isEqualTo("v1/ws/catalog/namespaces/ns");
    Assertions.assertThat(withoutPrefix.namespace(ns)).isEqualTo("v1/namespaces/ns");
  }

  /**
   * 测试场景：namespace with slash。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespaceWithSlash() {
    Namespace ns = Namespace.of("n/s");
    Assertions.assertThat(withPrefix.namespace(ns)).isEqualTo("v1/ws/catalog/namespaces/n%2Fs");
    Assertions.assertThat(withoutPrefix.namespace(ns)).isEqualTo("v1/namespaces/n%2Fs");
  }

  /**
   * 测试场景：namespace with multipart namespace。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespaceWithMultipartNamespace() {
    Namespace ns = Namespace.of("n", "s");
    Assertions.assertThat(withPrefix.namespace(ns)).isEqualTo("v1/ws/catalog/namespaces/n%1Fs");
    Assertions.assertThat(withoutPrefix.namespace(ns)).isEqualTo("v1/namespaces/n%1Fs");
  }

  /**
   * 测试场景：namespace properties。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespaceProperties() {
    Namespace ns = Namespace.of("ns");
    Assertions.assertThat(withPrefix.namespaceProperties(ns))
        .isEqualTo("v1/ws/catalog/namespaces/ns/properties");
    Assertions.assertThat(withoutPrefix.namespaceProperties(ns))
        .isEqualTo("v1/namespaces/ns/properties");
  }

  /**
   * 测试场景：namespace properties with slash。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespacePropertiesWithSlash() {
    Namespace ns = Namespace.of("n/s");
    Assertions.assertThat(withPrefix.namespaceProperties(ns))
        .isEqualTo("v1/ws/catalog/namespaces/n%2Fs/properties");
    Assertions.assertThat(withoutPrefix.namespaceProperties(ns))
        .isEqualTo("v1/namespaces/n%2Fs/properties");
  }

  /**
   * 测试场景：namespace properties with multipart namespace。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNamespacePropertiesWithMultipartNamespace() {
    Namespace ns = Namespace.of("n", "s");
    Assertions.assertThat(withPrefix.namespaceProperties(ns))
        .isEqualTo("v1/ws/catalog/namespaces/n%1Fs/properties");
    Assertions.assertThat(withoutPrefix.namespaceProperties(ns))
        .isEqualTo("v1/namespaces/n%1Fs/properties");
  }

  /**
   * 测试场景：tables。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTables() {
    Namespace ns = Namespace.of("ns");
    Assertions.assertThat(withPrefix.tables(ns)).isEqualTo("v1/ws/catalog/namespaces/ns/tables");
    Assertions.assertThat(withoutPrefix.tables(ns)).isEqualTo("v1/namespaces/ns/tables");
  }

  /**
   * 测试场景：tables with slash。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTablesWithSlash() {
    Namespace ns = Namespace.of("n/s");
    Assertions.assertThat(withPrefix.tables(ns)).isEqualTo("v1/ws/catalog/namespaces/n%2Fs/tables");
    Assertions.assertThat(withoutPrefix.tables(ns)).isEqualTo("v1/namespaces/n%2Fs/tables");
  }

  /**
   * 测试场景：tables with multipart namespace。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTablesWithMultipartNamespace() {
    Namespace ns = Namespace.of("n", "s");
    Assertions.assertThat(withPrefix.tables(ns)).isEqualTo("v1/ws/catalog/namespaces/n%1Fs/tables");
    Assertions.assertThat(withoutPrefix.tables(ns)).isEqualTo("v1/namespaces/n%1Fs/tables");
  }

  /**
   * 测试场景：table。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTable() {
    TableIdentifier ident = TableIdentifier.of("ns", "table");
    Assertions.assertThat(withPrefix.table(ident))
        .isEqualTo("v1/ws/catalog/namespaces/ns/tables/table");
    Assertions.assertThat(withoutPrefix.table(ident)).isEqualTo("v1/namespaces/ns/tables/table");
  }

  /**
   * 测试场景：table with slash。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTableWithSlash() {
    TableIdentifier ident = TableIdentifier.of("n/s", "tab/le");
    Assertions.assertThat(withPrefix.table(ident))
        .isEqualTo("v1/ws/catalog/namespaces/n%2Fs/tables/tab%2Fle");
    Assertions.assertThat(withoutPrefix.table(ident))
        .isEqualTo("v1/namespaces/n%2Fs/tables/tab%2Fle");
  }

  /**
   * 测试场景：table with multipart namespace。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTableWithMultipartNamespace() {
    TableIdentifier ident = TableIdentifier.of("n", "s", "table");
    Assertions.assertThat(withPrefix.table(ident))
        .isEqualTo("v1/ws/catalog/namespaces/n%1Fs/tables/table");
    Assertions.assertThat(withoutPrefix.table(ident)).isEqualTo("v1/namespaces/n%1Fs/tables/table");
  }

  /**
   * 测试场景：register。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRegister() {
    Namespace ns = Namespace.of("ns");
    Assertions.assertThat(withPrefix.register(ns))
        .isEqualTo("v1/ws/catalog/namespaces/ns/register");
    Assertions.assertThat(withoutPrefix.register(ns)).isEqualTo("v1/namespaces/ns/register");
  }
}
