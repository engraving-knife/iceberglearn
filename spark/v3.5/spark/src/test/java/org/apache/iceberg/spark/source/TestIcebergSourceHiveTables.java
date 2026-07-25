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
package org.apache.iceberg.spark.source;

import java.io.IOException;
import java.util.Map;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.junit.After;
import org.junit.BeforeClass;

/**
 * 文件级说明：测试 TestIcebergSourceHiveTables 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Iceberg源hive表 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestIcebergSourceHiveTables extends TestIcebergSourceTablesBase {

  private static TableIdentifier currentIdentifier;

  /** 启动。 */
  @BeforeClass
  public static void start() {
    Namespace db = Namespace.of("db");
    if (!catalog.namespaceExists(db)) {
      catalog.createNamespace(db);
    }
  }

  /** 删除表。 */
  @After
  public void dropTable() throws IOException {
    if (!catalog.tableExists(currentIdentifier)) {
      return;
    }

    dropTable(currentIdentifier);
  }

  /** 创建表。 */
  @Override
  public Table createTable(
      TableIdentifier ident, Schema schema, PartitionSpec spec, Map<String, String> properties) {
    TestIcebergSourceHiveTables.currentIdentifier = ident;
    return TestIcebergSourceHiveTables.catalog.createTable(ident, schema, spec, properties);
  }

  /** 删除表。 */
  @Override
  public void dropTable(TableIdentifier ident) throws IOException {
    Table table = catalog.loadTable(ident);
    Path tablePath = new Path(table.location());
    FileSystem fs = tablePath.getFileSystem(spark.sessionState().newHadoopConf());
    fs.delete(tablePath, true);
    catalog.dropTable(ident, false);
  }

  /** 加载表。 */
  @Override
  public Table loadTable(TableIdentifier ident, String entriesSuffix) {
    TableIdentifier identifier =
        TableIdentifier.of(ident.namespace().level(0), ident.name(), entriesSuffix);
    return TestIcebergSourceHiveTables.catalog.loadTable(identifier);
  }

  /** 加载路径。 */
  @Override
  public String loadLocation(TableIdentifier ident, String entriesSuffix) {
    return String.format("%s.%s", loadLocation(ident), entriesSuffix);
  }

  /** 加载路径。 */
  @Override
  public String loadLocation(TableIdentifier ident) {
    return ident.toString();
  }
}
