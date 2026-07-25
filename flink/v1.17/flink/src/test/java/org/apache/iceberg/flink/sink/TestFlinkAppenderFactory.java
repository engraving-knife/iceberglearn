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

import java.util.List;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.flink.SimpleDataUtil;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.TestAppenderFactory;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.StructLikeSet;

/**
 * 文件级说明：测试 TestFlinkAppenderFactory 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkAppenderFactory 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkAppenderFactory extends TestAppenderFactory<RowData> {

  private final RowType rowType;

  /** 辅助方法：TestFlinkAppenderFactory，Flink Appender Factory。 */
  public TestFlinkAppenderFactory(String fileFormat, boolean partitioned) {
    super(fileFormat, partitioned);
    this.rowType = FlinkSchemaUtil.convert(SCHEMA);
  }

  /** 辅助方法：createAppenderFactory，create Appender Factory。 */
  @Override
  protected FileAppenderFactory<RowData> createAppenderFactory(
      List<Integer> equalityFieldIds, Schema eqDeleteSchema, Schema posDeleteRowSchema) {
    return new FlinkAppenderFactory(
        table,
        table.schema(),
        rowType,
        table.properties(),
        table.spec(),
        ArrayUtil.toIntArray(equalityFieldIds),
        eqDeleteSchema,
        posDeleteRowSchema);
  }

  /** 辅助方法：createRow，create Row。 */
  @Override
  protected RowData createRow(Integer id, String data) {
    return SimpleDataUtil.createRowData(id, data);
  }

  /** 辅助方法：expectedRowSet，expected Row Set。 */
  @Override
  protected StructLikeSet expectedRowSet(Iterable<RowData> rows) {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    for (RowData row : rows) {
      RowDataWrapper wrapper = new RowDataWrapper(rowType, table.schema().asStruct());
      set.add(wrapper.wrap(row));
    }
    return set;
  }
}
