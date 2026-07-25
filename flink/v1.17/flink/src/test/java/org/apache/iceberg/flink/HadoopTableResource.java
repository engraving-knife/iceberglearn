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
package org.apache.iceberg.flink;

import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.junit.rules.TemporaryFolder;

/**
 * 文件级说明：测试 HadoopTableResource 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 HadoopTableResource 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class HadoopTableResource extends HadoopCatalogResource {
  private final Schema schema;
  private final PartitionSpec partitionSpec;

  private Table table;

  /** 辅助方法：HadoopTableResource，Hadoop Table Resource。 */
  public HadoopTableResource(
      TemporaryFolder temporaryFolder, String database, String tableName, Schema schema) {
    this(temporaryFolder, database, tableName, schema, null);
  }

  /** 辅助方法：HadoopTableResource，Hadoop Table Resource。 */
  public HadoopTableResource(
      TemporaryFolder temporaryFolder,
      String database,
      String tableName,
      Schema schema,
      PartitionSpec partitionSpec) {
    super(temporaryFolder, database, tableName);
    this.schema = schema;
    this.partitionSpec = partitionSpec;
  }

  /** 辅助方法：before，before。 */
  @Override
  protected void before() throws Throwable {
    super.before();
    if (partitionSpec == null) {
      this.table = catalog.createTable(TableIdentifier.of(database, tableName), schema);
    } else {
      this.table =
          catalog.createTable(TableIdentifier.of(database, tableName), schema, partitionSpec);
    }
    tableLoader.open();
  }

  /** 辅助方法：table，table。 */
  public Table table() {
    return table;
  }
}
