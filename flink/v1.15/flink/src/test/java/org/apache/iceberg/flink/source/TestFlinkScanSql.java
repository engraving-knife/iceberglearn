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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.config.TableConfigOptions;
import org.apache.flink.types.Row;
import org.junit.Before;

/**
 * 文件级说明：测试 TestFlinkScanSql 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFlinkScanSql 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkScanSql extends TestFlinkSource {

  private volatile TableEnvironment tEnv;

  /** 辅助方法：TestFlinkScanSql，Flink Scan Sql。 */
  public TestFlinkScanSql(String fileFormat) {
    super(fileFormat);
  }

  /** 辅助方法：before，before。 */
  @Before
  public void before() throws IOException {
    SqlHelpers.sql(
        getTableEnv(),
        "create catalog iceberg_catalog with ('type'='iceberg', 'catalog-type'='hadoop', 'warehouse'='%s')",
        catalogResource.warehouse());
    SqlHelpers.sql(getTableEnv(), "use catalog iceberg_catalog");
    getTableEnv()
        .getConfig()
        .getConfiguration()
        .set(TableConfigOptions.TABLE_DYNAMIC_TABLE_OPTIONS_ENABLED, true);
  }

  /** 辅助方法：getTableEnv，get Table Env。 */
  private TableEnvironment getTableEnv() {
    if (tEnv == null) {
      synchronized (this) {
        if (tEnv == null) {
          this.tEnv =
              TableEnvironment.create(EnvironmentSettings.newInstance().inBatchMode().build());
        }
      }
    }
    return tEnv;
  }

  /** 辅助方法：run，run。 */
  @Override
  protected List<Row> run(
      FlinkSource.Builder formatBuilder,
      Map<String, String> sqlOptions,
      String sqlFilter,
      String... sqlSelectedFields) {
    String select = String.join(",", sqlSelectedFields);
    String optionStr = SqlHelpers.sqlOptionsToString(sqlOptions);
    return SqlHelpers.sql(getTableEnv(), "select %s from t %s %s", select, optionStr, sqlFilter);
  }
}
