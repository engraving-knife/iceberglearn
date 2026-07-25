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
package org.apache.iceberg.flink.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.SerializableTable;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.TableLoader;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestCachingTableSupplier 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.16）。职责：验证 TestCachingTableSupplier 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestCachingTableSupplier {

  /**
   * 测试场景：Check Arguments。
   *
   * <p>验证该方法在 Check Arguments 条件下的行为是否符合预期。
   */
  @Test
  public void testCheckArguments() {
    SerializableTable initialTable = mock(SerializableTable.class);

    Table loadedTable = mock(Table.class);
    TableLoader tableLoader = mock(TableLoader.class);
    when(tableLoader.loadTable()).thenReturn(loadedTable);

    new CachingTableSupplier(initialTable, tableLoader, Duration.ofMillis(100));

    assertThatThrownBy(() -> new CachingTableSupplier(initialTable, tableLoader, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tableRefreshInterval cannot be null");
    assertThatThrownBy(() -> new CachingTableSupplier(null, tableLoader, Duration.ofMillis(100)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("initialTable cannot be null");
    assertThatThrownBy(() -> new CachingTableSupplier(initialTable, null, Duration.ofMillis(100)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tableLoader cannot be null");
  }

  /**
   * 测试场景：Table Reload。
   *
   * <p>验证该方法在 Table Reload 条件下的行为是否符合预期。
   */
  @Test
  public void testTableReload() {
    SerializableTable initialTable = mock(SerializableTable.class);

    Table loadedTable = mock(Table.class);
    TableLoader tableLoader = mock(TableLoader.class);
    when(tableLoader.loadTable()).thenReturn(loadedTable);

    CachingTableSupplier cachingTableSupplier =
        new CachingTableSupplier(initialTable, tableLoader, Duration.ofMillis(100));

    // refresh shouldn't do anything as the min reload interval hasn't passed
    cachingTableSupplier.refreshTable();
    assertThat(cachingTableSupplier.get()).isEqualTo(initialTable);

    // refresh after waiting past the min reload interval
    Awaitility.await()
        .atLeast(100, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              cachingTableSupplier.refreshTable();
              assertThat(cachingTableSupplier.get()).isEqualTo(loadedTable);
            });
  }
}
