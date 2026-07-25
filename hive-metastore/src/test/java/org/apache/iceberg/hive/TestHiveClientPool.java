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
package org.apache.iceberg.hive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.FunctionType;
import org.apache.hadoop.hive.metastore.api.GetAllFunctionsResponse;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.PrincipalType;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.thrift.transport.TTransportException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 文件级说明：测试 TestHiveClientPool 的功能。
 *
 * <p>所属模块：iceberg-hive-metastore。职责：验证 TestHiveClientPool 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestHiveClientPool {

  private static final String HIVE_SITE_CONTENT =
      "<?xml version=\"1.0\"?>\n"
          + "<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>\n"
          + "<configuration>\n"
          + "  <property>\n"
          + "    <name>hive.metastore.sasl.enabled</name>\n"
          + "    <value>true</value>\n"
          + "  </property>\n"
          + "</configuration>\n";

  HiveClientPool clients;

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    HiveClientPool clientPool = new HiveClientPool(2, new Configuration());
    clients = Mockito.spy(clientPool);
  }

  /** 辅助方法：after。 */
  @AfterEach
  public void after() {
    clients.close();
    clients = null;
  }

  /**
   * 测试场景：Conf。
   *
   * <p>验证该方法在 Conf 条件下的行为是否符合预期。
   */
  @Test
  public void testConf() {
    HiveConf conf = createHiveConf();
    conf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, "file:/mywarehouse/");

    HiveClientPool clientPool = new HiveClientPool(10, conf);
    HiveConf clientConf = clientPool.hiveConf();

    assertThat(clientConf.get(HiveConf.ConfVars.METASTOREWAREHOUSE.varname))
        .isEqualTo(conf.get(HiveConf.ConfVars.METASTOREWAREHOUSE.varname));
    assertThat(clientPool.poolSize()).isEqualTo(10);

    // 'hive.metastore.sasl.enabled' should be 'true' as defined in xml
    assertThat(clientConf.get(HiveConf.ConfVars.METASTORE_USE_THRIFT_SASL.varname))
        .isEqualTo(conf.get(HiveConf.ConfVars.METASTORE_USE_THRIFT_SASL.varname));
    assertThat(clientConf.getBoolVar(HiveConf.ConfVars.METASTORE_USE_THRIFT_SASL)).isTrue();
  }

  /** 辅助方法：createHiveConf。 */
  private HiveConf createHiveConf() {
    HiveConf hiveConf = new HiveConf();
    try (InputStream inputStream =
        new ByteArrayInputStream(HIVE_SITE_CONTENT.getBytes(StandardCharsets.UTF_8))) {
      hiveConf.addResource(inputStream, "for_test");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return hiveConf;
  }

  /**
   * 测试场景：New Client Failure。
   *
   * <p>验证该方法在 New Client Failure 条件下的行为是否符合预期。
   */
  @Test
  public void testNewClientFailure() {
    Mockito.doThrow(new RuntimeException("Connection exception")).when(clients).newClient();
    assertThatThrownBy(() -> clients.run(Object::toString))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Connection exception");
  }

  /**
   * 测试场景：Get Tables Fails For Non Reconnectable Exception。
   *
   * <p>验证该方法在 Get Tables Fails For Non Reconnectable Exception 条件下的行为是否符合预期。
   */
  @Test
  public void testGetTablesFailsForNonReconnectableException() throws Exception {
    HiveMetaStoreClient hmsClient = Mockito.mock(HiveMetaStoreClient.class);
    Mockito.doReturn(hmsClient).when(clients).newClient();
    Mockito.doThrow(new MetaException("Another meta exception"))
        .when(hmsClient)
        .getTables(Mockito.anyString(), Mockito.anyString());
    assertThatThrownBy(() -> clients.run(client -> client.getTables("default", "t")))
        .isInstanceOf(MetaException.class)
        .hasMessage("Another meta exception");
  }

  /**
   * 测试场景：Connection Failure Restore For Meta Exception。
   *
   * <p>验证该方法在 Connection Failure Restore For Meta Exception 条件下的行为是否符合预期。
   */
  @Test
  public void testConnectionFailureRestoreForMetaException() throws Exception {
    HiveMetaStoreClient hmsClient = newClient();

    // Throwing an exception may trigger the client to reconnect.
    String metaMessage = "Got exception: org.apache.thrift.transport.TTransportException";
    Mockito.doThrow(new MetaException(metaMessage)).when(hmsClient).getAllDatabases();

    // Create a new client when the reconnect method is called.
    HiveMetaStoreClient newClient = reconnect(hmsClient);

    List<String> databases = Lists.newArrayList("db1", "db2");

    Mockito.doReturn(databases).when(newClient).getAllDatabases();
    // The return is OK when the reconnect method is called.
    assertThat((List<String>) clients.run(client -> client.getAllDatabases(), true))
        .isEqualTo(databases);

    // Verify that the method is called.
    Mockito.verify(clients).reconnect(hmsClient);
    Mockito.verify(clients, Mockito.never()).reconnect(newClient);
  }

  /**
   * 测试场景：Connection Failure Restore For T Transport Exception。
   *
   * <p>验证该方法在 Connection Failure Restore For T Transport Exception 条件下的行为是否符合预期。
   */
  @Test
  public void testConnectionFailureRestoreForTTransportException() throws Exception {
    HiveMetaStoreClient hmsClient = newClient();
    Mockito.doThrow(new TTransportException()).when(hmsClient).getAllFunctions();

    // Create a new client when getAllFunctions() failed.
    HiveMetaStoreClient newClient = reconnect(hmsClient);

    GetAllFunctionsResponse response = new GetAllFunctionsResponse();
    response.addToFunctions(
        new Function(
            "concat",
            "db1",
            "classname",
            "root",
            PrincipalType.USER,
            100,
            FunctionType.JAVA,
            null));
    Mockito.doReturn(response).when(newClient).getAllFunctions();
    assertThat((GetAllFunctionsResponse) clients.run(client -> client.getAllFunctions(), true))
        .isEqualTo(response);

    Mockito.verify(clients).reconnect(hmsClient);
    Mockito.verify(clients, Mockito.never()).reconnect(newClient);
  }

  /** 辅助方法：newClient。 */
  private HiveMetaStoreClient newClient() {
    HiveMetaStoreClient hmsClient = Mockito.mock(HiveMetaStoreClient.class);
    Mockito.doReturn(hmsClient).when(clients).newClient();
    return hmsClient;
  }

  /** 辅助方法：reconnect。 */
  private HiveMetaStoreClient reconnect(HiveMetaStoreClient obsoleteClient) {
    HiveMetaStoreClient newClient = Mockito.mock(HiveMetaStoreClient.class);
    Mockito.doReturn(newClient).when(clients).reconnect(obsoleteClient);
    return newClient;
  }
}
