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
package org.apache.iceberg.mr.hive;

import java.util.Collections;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hive.service.cli.CLIService;
import org.apache.hive.service.cli.HiveSQLException;
import org.apache.hive.service.cli.OperationHandle;
import org.apache.hive.service.cli.RowSet;
import org.apache.hive.service.cli.SessionHandle;
import org.apache.hive.service.cli.session.HiveSession;
import org.apache.hive.service.server.HiveServer2;
import org.apache.iceberg.hive.TestHiveMetastore;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：测试 TestHiveShell 的功能。
 *
 * <p>所属模块：iceberg-mr。职责：验证 HiveShell 相关功能，覆盖正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestHiveShell {

  private final TestHiveMetastore metastore;
  private final HiveServer2 hs2;
  private final HiveConf hs2Conf;
  private CLIService client;
  private HiveSession session;
  private boolean started;

  /** 构造方法：TestHiveShell。 */
  public TestHiveShell() {
    metastore = new TestHiveMetastore();
    hs2Conf = initializeConf();
    hs2 = new HiveServer2();
  }

  /** 辅助方法：集合Hive配置值。 */
  public void setHiveConfValue(String key, String value) {
    Preconditions.checkState(
        !started, "TestHiveShell has already been started. Cannot set Hive conf anymore.");
    hs2Conf.verifyAndSet(key, value);
  }

  /** 辅助方法：集合Hive会话值。 */
  public void setHiveSessionValue(String key, String value) {
    Preconditions.checkState(session != null, "There is no open session for setting variables.");
    try {
      session.getSessionConf().set(key, value);
    } catch (Exception e) {
      throw new RuntimeException("Unable to set Hive session variable: ", e);
    }
  }

  /** 辅助方法：集合Hive会话值。 */
  public void setHiveSessionValue(String key, boolean value) {
    setHiveSessionValue(key, Boolean.toString(value));
  }

  /** 辅助方法：启动。 */
  public void start() {
    // Create a copy of the HiveConf for the metastore
    metastore.start(new HiveConf(hs2Conf), 10);
    hs2Conf.setVar(
        HiveConf.ConfVars.METASTOREURIS,
        metastore.hiveConf().getVar(HiveConf.ConfVars.METASTOREURIS));
    hs2Conf.setVar(
        HiveConf.ConfVars.METASTOREWAREHOUSE,
        metastore.hiveConf().getVar(HiveConf.ConfVars.METASTOREWAREHOUSE));

    // Initializing RpcMetrics in a single JVM multiple times can cause issues
    DefaultMetricsSystem.setMiniClusterMode(true);

    hs2.init(hs2Conf);
    hs2.start();
    client =
        hs2.getServices().stream()
            .filter(CLIService.class::isInstance)
            .findFirst()
            .map(CLIService.class::cast)
            .get();
    started = true;
  }

  /** 辅助方法：停止。 */
  public void stop() throws Exception {
    if (client != null) {
      client.stop();
    }
    hs2.stop();
    metastore.stop();
    started = false;
  }

  /** 辅助方法：元存储。 */
  public TestHiveMetastore metastore() {
    return metastore;
  }

  /** 辅助方法：open会话。 */
  public void openSession() {
    Preconditions.checkState(
        started, "You have to start TestHiveShell first, before opening a session.");
    try {
      SessionHandle sessionHandle =
          client
              .getSessionManager()
              .openSession(CLIService.SERVER_VERSION, "", "", "127.0.0.1", Collections.emptyMap());
      session = client.getSessionManager().getSession(sessionHandle);
    } catch (Exception e) {
      throw new RuntimeException("Unable to open new Hive session: ", e);
    }
  }

  /** 辅助方法：close会话。 */
  public void closeSession() {
    Preconditions.checkState(session != null, "There is no open session to be closed.");
    try {
      session.close();
      session = null;
    } catch (Exception e) {
      throw new RuntimeException("Unable to close Hive session: ", e);
    }
  }

  /** 辅助方法：执行语句。 */
  public List<Object[]> executeStatement(String statement) {
    Preconditions.checkState(
        session != null,
        "You have to start TestHiveShell and open a session first, before running a query.");
    try {
      OperationHandle handle =
          client.executeStatement(session.getSessionHandle(), statement, Collections.emptyMap());
      List<Object[]> resultSet = Lists.newArrayList();
      if (handle.hasResultSet()) {
        RowSet rowSet;
        // keep fetching results until we can
        while ((rowSet = client.fetchResults(handle)) != null && rowSet.numRows() > 0) {
          for (Object[] row : rowSet) {
            resultSet.add(row.clone());
          }
        }
      }
      return resultSet;
    } catch (HiveSQLException e) {
      throw new IllegalArgumentException(
          "Failed to execute Hive query '" + statement + "': " + e.getMessage(), e);
    }
  }

  /** 辅助方法：获取Hive配置。 */
  public Configuration getHiveConf() {
    if (session != null) {
      return session.getHiveConf();
    } else {
      return hs2Conf;
    }
  }

  /** 辅助方法：initialize配置。 */
  private HiveConf initializeConf() {
    HiveConf hiveConf = new HiveConf();

    // Use ephemeral port to enable running tests in parallel
    hiveConf.setIntVar(HiveConf.ConfVars.HIVE_SERVER2_THRIFT_PORT, 0);
    // Disable the web UI
    hiveConf.setIntVar(HiveConf.ConfVars.HIVE_SERVER2_WEBUI_PORT, -1);

    // Switch off optimizers in order to contain the map reduction within this JVM
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVE_CBO_ENABLED, false);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVE_INFER_BUCKET_SORT, false);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVEMETADATAONLYQUERIES, false);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVEOPTINDEXFILTER, false);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVECONVERTJOIN, false);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVESKEWJOIN, false);

    // Speed up test execution
    hiveConf.setLongVar(HiveConf.ConfVars.HIVECOUNTERSPULLINTERVAL, 1L);
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVESTATSAUTOGATHER, false);

    // Resource configuration
    hiveConf.setInt("mapreduce.map.memory.mb", 1024);

    // Tez configuration
    hiveConf.setBoolean("tez.local.mode", true);

    // Disable vectorization for HiveIcebergInputFormat
    hiveConf.setBoolVar(HiveConf.ConfVars.HIVE_VECTORIZATION_ENABLED, false);

    // do not serialize the FileIO config
    hiveConf.set(InputFormatConfig.CONFIG_SERIALIZATION_DISABLED, "true");

    return hiveConf;
  }
}
