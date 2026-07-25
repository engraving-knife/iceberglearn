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
package org.apache.iceberg.flink.source;

import java.util.Map;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.data.DeleteReadTests;
import org.apache.iceberg.hive.HiveCatalog;
import org.apache.iceberg.hive.TestHiveMetastore;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestFlinkReaderDeletesBase 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFlinkReaderDeletesBase 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public abstract class TestFlinkReaderDeletesBase extends DeleteReadTests {

  @ClassRule public static final TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

  protected static String databaseName = "default";

  protected static HiveConf hiveConf = null;
  protected static HiveCatalog catalog = null;
  private static TestHiveMetastore metastore = null;

  protected final FileFormat format;

  /** 辅助方法：parameters，parameters。 */
  @Parameterized.Parameters(name = "fileFormat={0}")
  public static Object[][] parameters() {
    return new Object[][] {
      new Object[] {FileFormat.PARQUET},
      new Object[] {FileFormat.AVRO},
      new Object[] {FileFormat.ORC}
    };
  }

  TestFlinkReaderDeletesBase(FileFormat fileFormat) {
    this.format = fileFormat;
  }

  /** 辅助方法：startMetastore，start Metastore。 */
  @BeforeClass
  public static void startMetastore() {
    metastore = new TestHiveMetastore();
    metastore.start();
    hiveConf = metastore.hiveConf();
    catalog =
        (HiveCatalog)
            CatalogUtil.loadCatalog(
                HiveCatalog.class.getName(), "hive", ImmutableMap.of(), hiveConf);
  }

  /** 辅助方法：stopMetastore，stop Metastore。 */
  @AfterClass
  public static void stopMetastore() throws Exception {
    metastore.stop();
    catalog = null;
  }

  /** 辅助方法：createTable，create Table。 */
  @Override
  protected Table createTable(String name, Schema schema, PartitionSpec spec) {
    Map<String, String> props = Maps.newHashMap();
    props.put(TableProperties.DEFAULT_FILE_FORMAT, format.name());

    Table table = catalog.createTable(TableIdentifier.of(databaseName, name), schema, spec, props);
    TableOperations ops = ((BaseTable) table).operations();
    TableMetadata meta = ops.current();
    ops.commit(meta, meta.upgradeToFormatVersion(2));

    return table;
  }

  /** 辅助方法：dropTable，drop Table。 */
  @Override
  protected void dropTable(String name) {
    catalog.dropTable(TableIdentifier.of(databaseName, name));
  }

  /** 辅助方法：expectPruned，expect Pruned。 */
  @Override
  protected boolean expectPruned() {
    return false;
  }
}
