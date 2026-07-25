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
package org.apache.iceberg.data;

import java.io.File;
import java.io.IOException;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TestTables;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.util.StructLikeSet;
import org.junit.Assert;

/**
 * 文件级说明：测试 TestGenericReaderDeletes 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 TestGenericReaderDeletes 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestGenericReaderDeletes extends DeleteReadTests {

  /** 辅助方法：createTable。 */
  @Override
  protected Table createTable(String name, Schema schema, PartitionSpec spec) throws IOException {
    File tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete());

    return TestTables.create(tableDir, name, schema, spec, 2);
  }

  /** 辅助方法：dropTable。 */
  @Override
  protected void dropTable(String name) {
    TestTables.clearTables();
  }

  /** 辅助方法：rowSet。 */
  @Override
  public StructLikeSet rowSet(String name, Table table, String... columns) throws IOException {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    try (CloseableIterable<Record> reader = IcebergGenerics.read(table).select(columns).build()) {
      Iterables.addAll(
          set,
          CloseableIterable.transform(
              reader, record -> new InternalRecordWrapper(table.schema().asStruct()).wrap(record)));
    }
    return set;
  }

  /** 辅助方法：expectPruned。 */
  @Override
  protected boolean expectPruned() {
    return false;
  }
}
