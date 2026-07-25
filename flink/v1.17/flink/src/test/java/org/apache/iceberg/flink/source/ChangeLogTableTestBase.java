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
import java.util.stream.Collectors;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.types.RowKind;
import org.apache.iceberg.flink.FlinkTestBase;
import org.apache.iceberg.flink.MiniClusterResource;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestName;

/**
 * 文件级说明：测试 ChangeLogTableTestBase 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 ChangeLogTableTestBase 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class ChangeLogTableTestBase extends FlinkTestBase {
  private volatile TableEnvironment tEnv = null;

  @Rule public TestName name = new TestName();

  /** 辅助方法：clean，clean。 */
  @After
  public void clean() {
    sql("DROP TABLE IF EXISTS %s", name.getMethodName());
    BoundedTableFactory.clearDataSets();
  }

  /** 辅助方法：getTableEnv，get Table Env。 */
  @Override
  protected TableEnvironment getTableEnv() {
    if (tEnv == null) {
      synchronized (this) {
        if (tEnv == null) {
          EnvironmentSettings settings =
              EnvironmentSettings.newInstance().inStreamingMode().build();

          StreamExecutionEnvironment env =
              StreamExecutionEnvironment.getExecutionEnvironment(
                      MiniClusterResource.DISABLE_CLASSLOADER_CHECK_CONFIG)
                  .enableCheckpointing(400)
                  .setMaxParallelism(1)
                  .setParallelism(1);

          tEnv = StreamTableEnvironment.create(env, settings);
        }
      }
    }
    return tEnv;
  }

  /** 辅助方法：insertRow，insert Row。 */
  protected static Row insertRow(Object... values) {
    return Row.ofKind(RowKind.INSERT, values);
  }

  /** 辅助方法：deleteRow，delete Row。 */
  protected static Row deleteRow(Object... values) {
    return Row.ofKind(RowKind.DELETE, values);
  }

  /** 辅助方法：updateBeforeRow，update Before Row。 */
  protected static Row updateBeforeRow(Object... values) {
    return Row.ofKind(RowKind.UPDATE_BEFORE, values);
  }

  /** 辅助方法：updateAfterRow，update After Row。 */
  protected static Row updateAfterRow(Object... values) {
    return Row.ofKind(RowKind.UPDATE_AFTER, values);
  }

  /** 辅助方法：listJoin，list Join。 */
  protected static <T> List<T> listJoin(List<List<T>> lists) {
    return lists.stream().flatMap(List::stream).collect(Collectors.toList());
  }
}
