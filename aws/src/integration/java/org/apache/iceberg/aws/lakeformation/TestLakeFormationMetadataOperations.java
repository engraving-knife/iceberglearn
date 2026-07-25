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
package org.apache.iceberg.aws.lakeformation;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.AssertHelpers;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.UpdateProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.ForbiddenException;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.services.glue.model.AccessDeniedException;
import software.amazon.awssdk.services.lakeformation.model.Permission;

/**
 * 文件级说明：TestLakeFormationMetadataOperations 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 lakeformation元数据操作 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class TestLakeFormationMetadataOperations extends LakeFormationTestBase {
  /**
   * 测试场景：创建and删除数据库successful。
   *
   * <p>验证该方法在 创建and删除数据库successful 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateAndDropDatabaseSuccessful() {
    String testDbName = getRandomDbName();

    grantCreateDbPermission();
    glueCatalogPrivilegedRole.createNamespace(Namespace.of(testDbName));

    grantDatabasePrivileges(testDbName, Permission.DROP);
    glueCatalogPrivilegedRole.dropNamespace(Namespace.of(testDbName));
  }

  /**
   * 测试场景：创建数据库noprivileges。
   *
   * <p>验证该方法在 创建数据库noprivileges 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateDatabaseNoPrivileges() {
    String testDbName = getRandomDbName();
    AssertHelpers.assertThrows(
        "attempt to create a database without CREATE_DATABASE permission should fail",
        AccessDeniedException.class,
        "Insufficient Lake Formation permission(s)",
        () -> glueCatalogPrivilegedRole.createNamespace(Namespace.of(testDbName)));
  }

  /**
   * 测试场景：删除数据库noprivileges。
   *
   * <p>验证该方法在 删除数据库noprivileges 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropDatabaseNoPrivileges() {
    String testDbName = getRandomDbName();
    lfRegisterPathRoleCreateDb(testDbName);
    try {
      AssertHelpers.assertThrows(
          "attempt to drop a database without DROP permission should fail",
          AccessDeniedException.class,
          "Insufficient Lake Formation permission(s)",
          () -> glueCatalogPrivilegedRole.dropNamespace(Namespace.of(testDbName)));
    } finally {
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：show数据库successful。
   *
   * <p>验证该方法在 show数据库successful 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testShowDatabasesSuccessful() {
    String testDbName = getRandomDbName();
    lfRegisterPathRoleCreateDb(testDbName);
    grantDatabasePrivileges(testDbName, Permission.ALTER);
    try {
      List<Namespace> namespaces = glueCatalogPrivilegedRole.listNamespaces();
      Assert.assertTrue(namespaces.contains(Namespace.of(testDbName)));
    } finally {
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：创建表no创建表permission。
   *
   * <p>验证该方法在 创建表no创建表permission 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateTableNoCreateTablePermission() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    grantCreateDbPermission();
    lfRegisterPathRoleCreateDb(testDbName);
    String tableLocation = getTableLocation(testTableName);
    grantDataPathPrivileges(tableLocation);
    try {
      AssertHelpers.assertThrows(
          "attempt to create a table without CREATE_TABLE permission should fail",
          AccessDeniedException.class,
          "Insufficient Lake Formation permission(s)",
          () ->
              glueCatalogPrivilegedRole.createTable(
                  TableIdentifier.of(testDbName, testTableName),
                  schema,
                  partitionSpec,
                  tableLocation,
                  null));
    } finally {
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：show表successful。
   *
   * <p>验证该方法在 show表successful 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testShowTablesSuccessful() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    grantTablePrivileges(testDbName, testTableName, Permission.ALTER);
    try {
      List<TableIdentifier> tables = glueCatalogPrivilegedRole.listTables(Namespace.of(testDbName));
      Assert.assertTrue(
          tables.contains(TableIdentifier.of(Namespace.of(testDbName), testTableName)));
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：show表noprivileges。
   *
   * <p>验证该方法在 show表noprivileges 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testShowTablesNoPrivileges() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    try {
      AssertHelpers.assertThrows(
          "attempt to show tables without any permissions should fail",
          AccessDeniedException.class,
          "Insufficient Lake Formation permission(s)",
          () -> glueCatalogPrivilegedRole.listTables(Namespace.of(testDbName)));
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：创建表no数据路径permission。
   *
   * <p>验证该方法在 创建表no数据路径permission 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateTableNoDataPathPermission() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    grantDatabasePrivileges(testDbName, Permission.CREATE_TABLE);
    try {
      AssertHelpers.assertThrows(
          "attempt to create a table without DATA_LOCATION_ACCESS permission should fail",
          ForbiddenException.class,
          "Glue cannot access the requested resources",
          () ->
              glueCatalogPrivilegedRole.createTable(
                  TableIdentifier.of(testDbName, testTableName),
                  schema,
                  partitionSpec,
                  getTableLocation(testTableName),
                  null));
    } finally {
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：创建表success。
   *
   * <p>验证该方法在 创建表success 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testCreateTableSuccess() {
    String testDbName = getRandomDbName();
    lfRegisterPathRoleCreateDb(testDbName);
    String testTableName = getRandomTableName();
    String tableLocation = getTableLocation(testTableName);
    grantDataPathPrivileges(tableLocation);
    grantDatabasePrivileges(testDbName, Permission.CREATE_TABLE);
    try {
      glueCatalogPrivilegedRole.createTable(
          TableIdentifier.of(testDbName, testTableName),
          schema,
          partitionSpec,
          tableLocation,
          null);
    } finally {
      grantTablePrivileges(testDbName, testTableName, Permission.DELETE, Permission.DROP);
      glueCatalogPrivilegedRole.dropTable(TableIdentifier.of(testDbName, testTableName), false);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：删除表success当purgeisfalse。
   *
   * <p>验证该方法在 删除表success当purgeisfalse 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropTableSuccessWhenPurgeIsFalse() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    grantTablePrivileges(testDbName, testTableName, Permission.DROP, Permission.SELECT);
    try {
      glueCatalogPrivilegedRole.dropTable(TableIdentifier.of(testDbName, testTableName), false);
    } finally {
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：删除表no删除permission。
   *
   * <p>验证该方法在 删除表no删除permission 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testDropTableNoDropPermission() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    grantTablePrivileges(testDbName, testTableName, Permission.SELECT);
    try {
      AssertHelpers.assertThrows(
          "attempt to drop a table without DROP permission should fail",
          AccessDeniedException.class,
          "Insufficient Lake Formation permission(s)",
          () ->
              glueCatalogPrivilegedRole.dropTable(
                  TableIdentifier.of(testDbName, testTableName), false));
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：修改表集合属性successful。
   *
   * <p>验证该方法在 修改表集合属性successful 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAlterTableSetPropertiesSuccessful() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    Map<String, String> properties = Maps.newHashMap();
    grantTablePrivileges(testDbName, testTableName, Permission.ALTER, Permission.INSERT);
    grantDataPathPrivileges(getTableLocation(testTableName));
    try {
      Table table =
          glueCatalogPrivilegedRole.loadTable(
              TableIdentifier.of(Namespace.of(testDbName), testTableName));
      properties.putAll(table.properties());
      properties.put(
          TableProperties.DEFAULT_FILE_FORMAT, TableProperties.DEFAULT_FILE_FORMAT_DEFAULT);
      UpdateProperties updateProperties = table.updateProperties();
      properties.forEach(updateProperties::set);
      updateProperties.commit();
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：修改表集合属性no数据路径access。
   *
   * <p>验证该方法在 修改表集合属性no数据路径access 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAlterTableSetPropertiesNoDataPathAccess() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    Map<String, String> properties = Maps.newHashMap();
    grantTablePrivileges(testDbName, testTableName, Permission.ALTER, Permission.INSERT);
    try {
      Table table =
          glueCatalogPrivilegedRole.loadTable(
              TableIdentifier.of(Namespace.of(testDbName), testTableName));
      properties.putAll(table.properties());
      properties.put(
          TableProperties.DEFAULT_FILE_FORMAT, TableProperties.DEFAULT_FILE_FORMAT_DEFAULT);
      UpdateProperties updateProperties = table.updateProperties();
      properties.forEach(updateProperties::set);
      AssertHelpers.assertThrows(
          "attempt to alter a table without ALTER permission should fail",
          ForbiddenException.class,
          "Glue cannot access the requested resources",
          updateProperties::commit);
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：修改表集合属性noprivileges。
   *
   * <p>验证该方法在 修改表集合属性noprivileges 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAlterTableSetPropertiesNoPrivileges() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    grantDataPathPrivileges(getTableLocation(testTableName));
    try {
      AssertHelpers.assertThrows(
          "attempt to alter a table without ALTER permission should fail",
          AccessDeniedException.class,
          "Insufficient Lake Formation permission(s)",
          () ->
              glueCatalogPrivilegedRole.loadTable(
                  TableIdentifier.of(Namespace.of(testDbName), testTableName)));
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }

  /**
   * 测试场景：修改表集合属性no修改permission。
   *
   * <p>验证该方法在 修改表集合属性no修改permission 条件下的行为与断言结果是否符合预期。
   */
  @Test
  public void testAlterTableSetPropertiesNoAlterPermission() {
    String testDbName = getRandomDbName();
    String testTableName = getRandomTableName();
    lfRegisterPathRoleCreateDb(testDbName);
    lfRegisterPathRoleCreateTable(testDbName, testTableName);
    Map<String, String> properties = Maps.newHashMap();
    grantTablePrivileges(testDbName, testTableName, Permission.SELECT, Permission.INSERT);
    try {
      Table table =
          glueCatalogPrivilegedRole.loadTable(
              TableIdentifier.of(Namespace.of(testDbName), testTableName));
      properties.putAll(table.properties());
      properties.put(
          TableProperties.DEFAULT_FILE_FORMAT, TableProperties.DEFAULT_FILE_FORMAT_DEFAULT);
      UpdateProperties updateProperties = table.updateProperties();
      properties.forEach(updateProperties::set);
      AssertHelpers.assertThrows(
          "attempt to alter a table without ALTER privileges should fail",
          ForbiddenException.class,
          "Glue cannot access the requested resources",
          updateProperties::commit);
    } finally {
      lfRegisterPathRoleDeleteTable(testDbName, testTableName);
      lfRegisterPathRoleDeleteDb(testDbName);
    }
  }
}
