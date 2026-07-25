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

import java.util.List;
import org.apache.flink.types.Row;
import org.apache.iceberg.flink.FlinkReadOptions;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestFlinkSourceConfig 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkSourceConfig 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkSourceConfig extends TestFlinkTableSource {
  private static final String TABLE = "test_table";

  /**
   * 测试场景：Flink Session Config。
   *
   * <p>验证该方法在 Flink Session Config 条件下的行为是否符合预期。
   */
  @Test
  public void testFlinkSessionConfig() {
    getTableEnv().getConfig().set(FlinkReadOptions.STREAMING_OPTION, true);
    Assertions.assertThatThrownBy(
            () -> sql("SELECT * FROM %s /*+ OPTIONS('as-of-timestamp'='1')*/", TABLE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot set as-of-timestamp option for streaming reader");
  }

  /**
   * 测试场景：Flink Hint Config。
   *
   * <p>验证该方法在 Flink Hint Config 条件下的行为是否符合预期。
   */
  @Test
  public void testFlinkHintConfig() {
    List<Row> result =
        sql(
            "SELECT * FROM %s /*+ OPTIONS('as-of-timestamp'='%d','streaming'='false')*/",
            TABLE, System.currentTimeMillis());
    Assert.assertEquals(3, result.size());
  }

  /**
   * 测试场景：Read Option Hierarchy。
   *
   * <p>验证该方法在 Read Option Hierarchy 条件下的行为是否符合预期。
   */
  @Test
  public void testReadOptionHierarchy() {
    getTableEnv().getConfig().set(FlinkReadOptions.LIMIT_OPTION, 1L);
    List<Row> result = sql("SELECT * FROM %s", TABLE);
    Assert.assertEquals(1, result.size());

    result = sql("SELECT * FROM %s /*+ OPTIONS('limit'='3')*/", TABLE);
    Assert.assertEquals(3, result.size());
  }
}
