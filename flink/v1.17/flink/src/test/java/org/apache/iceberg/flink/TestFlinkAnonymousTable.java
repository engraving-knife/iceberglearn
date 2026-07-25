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

import java.io.File;
import java.util.concurrent.TimeUnit;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.TableEnvironment;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Test;

/**
 * 文件级说明：测试 TestFlinkAnonymousTable 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkAnonymousTable 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkAnonymousTable extends FlinkTestBase {

  /**
   * 测试场景：Write Anonymous Table。
   *
   * <p>验证该方法在 Write Anonymous Table 条件下的行为是否符合预期。
   */
  @Test
  public void testWriteAnonymousTable() throws Exception {
    File warehouseDir = TEMPORARY_FOLDER.newFolder();
    TableEnvironment tEnv = getTableEnv();
    Table table =
        tEnv.from(
            TableDescriptor.forConnector("datagen")
                .schema(Schema.newBuilder().column("f0", DataTypes.STRING()).build())
                .option("number-of-rows", "3")
                .build());

    TableDescriptor descriptor =
        TableDescriptor.forConnector("iceberg")
            .schema(Schema.newBuilder().column("f0", DataTypes.STRING()).build())
            .option("catalog-name", "hadoop_test")
            .option("catalog-type", "hadoop")
            .option("catalog-database", "test_db")
            .option("catalog-table", "test")
            .option("warehouse", warehouseDir.getAbsolutePath())
            .build();

    table.insertInto(descriptor).execute();
    Awaitility.await()
        .atMost(3, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                Assertions.assertThat(
                        warehouseDir.toPath().resolve("test_db").resolve("test").toFile())
                    .exists());
  }
}
