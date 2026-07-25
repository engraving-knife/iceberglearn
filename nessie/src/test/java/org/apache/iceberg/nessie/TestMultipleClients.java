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
package org.apache.iceberg.nessie;

import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.IOException;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Collections;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectnessie.client.ext.NessieClientFactory;
import org.projectnessie.client.ext.NessieClientUri;

/**
 * 文件级说明：测试 TestMultipleClients 的功能。
 *
 * <p>所属模块：iceberg-nessie。职责：验证 TestMultipleClients 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestMultipleClients extends BaseTestIceberg {

  private static final String BRANCH = "multiple-clients-test";
  private static final Schema schema =
      new Schema(Types.StructType.of(required(1, "id", Types.LongType.get())).fields());

  /** 辅助方法：TestMultipleClients。 */
  public TestMultipleClients() {
    super(BRANCH);
  }

  // another client that connects to the same nessie server.
  NessieCatalog anotherCatalog;

  /** 辅助方法：beforeEach。 */
  @Override
  @BeforeEach
  public void beforeEach(NessieClientFactory clientFactory, @NessieClientUri URI nessieUri)
      throws IOException {
    super.beforeEach(clientFactory, nessieUri);
    anotherCatalog = initCatalog(branch);
  }

  /** 辅助方法：afterEach。 */
  @AfterEach
  public void afterEach() throws Exception {
    anotherCatalog.close();
  }

  /**
   * 测试场景：List Namespaces。
   *
   * <p>验证该方法在 List Namespaces 条件下的行为是否符合预期。
   */
  @Test
  public void testListNamespaces() {
    catalog.createNamespace(Namespace.of("db1"), Collections.emptyMap());
    Assertions.assertThat(catalog.listNamespaces()).containsExactlyInAnyOrder(Namespace.of("db1"));

    // another client creates a namespace with the same nessie server
    anotherCatalog.createNamespace(Namespace.of("db2"), Collections.emptyMap());
    Assertions.assertThat(anotherCatalog.listNamespaces())
        .containsExactlyInAnyOrder(Namespace.of("db1"), Namespace.of("db2"));

    Assertions.assertThat(catalog.listNamespaces())
        .containsExactlyInAnyOrder(Namespace.of("db1"), Namespace.of("db2"));
  }

  /**
   * 测试场景：Load Namespace Metadata。
   *
   * <p>验证该方法在 Load Namespace Metadata 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadNamespaceMetadata() {
    catalog.createNamespace(Namespace.of("namespace1"), Collections.emptyMap());
    Assertions.assertThat(catalog.listNamespaces())
        .containsExactlyInAnyOrder(Namespace.of("namespace1"));

    // another client adds a metadata to the same namespace
    anotherCatalog.setProperties(Namespace.of("namespace1"), Collections.singletonMap("k1", "v1"));
    AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>("k1", "v1");
    Assertions.assertThat(anotherCatalog.loadNamespaceMetadata(Namespace.of("namespace1")))
        .containsExactly(entry);

    Assertions.assertThat(catalog.loadNamespaceMetadata(Namespace.of("namespace1")))
        .containsExactly(entry);
  }

  /**
   * 测试场景：List Tables。
   *
   * <p>验证该方法在 List Tables 条件下的行为是否符合预期。
   */
  @Test
  public void testListTables() {
    createTable(TableIdentifier.parse("foo.tbl1"), schema);
    Assertions.assertThat(catalog.listTables(Namespace.of("foo")))
        .containsExactlyInAnyOrder(TableIdentifier.parse("foo.tbl1"));

    // another client creates a table with the same nessie server
    anotherCatalog.createTable(TableIdentifier.parse("foo.tbl2"), schema);
    Assertions.assertThat(anotherCatalog.listTables(Namespace.of("foo")))
        .containsExactlyInAnyOrder(
            TableIdentifier.parse("foo.tbl1"), TableIdentifier.parse("foo.tbl2"));

    Assertions.assertThat(catalog.listTables(Namespace.of("foo")))
        .containsExactlyInAnyOrder(
            TableIdentifier.parse("foo.tbl1"), TableIdentifier.parse("foo.tbl2"));
  }

  /**
   * 测试场景：Commits。
   *
   * <p>验证该方法在 Commits 条件下的行为是否符合预期。
   */
  @Test
  public void testCommits() {
    TableIdentifier identifier = TableIdentifier.parse("foo.tbl1");
    createTable(identifier, schema);
    Table tableFromCatalog = catalog.loadTable(identifier);
    tableFromCatalog.updateSchema().addColumn("x1", Types.LongType.get()).commit();

    Table tableFromAnotherCatalog = anotherCatalog.loadTable(identifier);
    tableFromAnotherCatalog.updateSchema().addColumn("x2", Types.LongType.get()).commit();

    tableFromCatalog.updateSchema().addColumn("x3", Types.LongType.get()).commit();
    tableFromAnotherCatalog.updateSchema().addColumn("x4", Types.LongType.get()).commit();

    Assertions.assertThat(catalog.loadTable(identifier).schema().columns()).hasSize(5);
    Assertions.assertThat(anotherCatalog.loadTable(identifier).schema().columns()).hasSize(5);
  }

  /**
   * 测试场景：Concurrent Commits With Refresh。
   *
   * <p>验证该方法在 Concurrent Commits With Refresh 条件下的行为是否符合预期。
   */
  @Test
  public void testConcurrentCommitsWithRefresh() {
    TableIdentifier identifier = TableIdentifier.parse("foo.tbl1");
    createTable(identifier, schema);

    String hashBefore = catalog.currentHash();

    TableOperations ops1 = catalog.newTableOps(identifier);
    TableMetadata metadata1 =
        TableMetadata.buildFrom(ops1.current()).setProperties(ImmutableMap.of("k1", "v1")).build();

    // commit should succeed
    TableOperations ops2 = catalog.newTableOps(identifier);
    TableMetadata metadata2 =
        TableMetadata.buildFrom(ops2.current()).setProperties(ImmutableMap.of("k2", "v2")).build();
    ops2.commit(ops2.current(), metadata2);

    // refresh the catalog's client.
    String hashAfter = catalog.currentHash();
    Assertions.assertThat(hashBefore).isNotEqualTo(hashAfter);

    // client refresh should not affect the ongoing commits (commit should still fail due staleness)
    Assertions.assertThatThrownBy(() -> ops1.commit(ops1.current(), metadata1))
        .isInstanceOf(CommitFailedException.class)
        .hasMessageContaining(
            "Cannot commit: Reference hash is out of date. Update the reference 'multiple-clients-test' and try again");
  }
}
