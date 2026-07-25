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

import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.FileWriterFactory;
import org.apache.iceberg.io.TestWriterMetrics;

/**
 * 文件级说明：测试 TestFlinkWriterMetrics 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkWriterMetrics 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkWriterMetrics extends TestWriterMetrics<RowData> {

  /** 辅助方法：TestFlinkWriterMetrics，Flink Writer Metrics。 */
  public TestFlinkWriterMetrics(FileFormat fileFormat) {
    super(fileFormat);
  }

  /** 辅助方法：newWriterFactory，new Writer Factory。 */
  @Override
  protected FileWriterFactory<RowData> newWriterFactory(Table sourceTable) {
    return FlinkFileWriterFactory.builderFor(sourceTable)
        .dataSchema(sourceTable.schema())
        .dataFileFormat(fileFormat)
        .deleteFileFormat(fileFormat)
        .positionDeleteRowSchema(sourceTable.schema())
        .build();
  }

  /** 辅助方法：toRow，to Row。 */
  @Override
  protected RowData toRow(Integer id, String data, boolean boolValue, Long longValue) {
    GenericRowData nested = GenericRowData.of(boolValue, longValue);
    GenericRowData row = GenericRowData.of(id, StringData.fromString(data), nested);
    return row;
  }

  /** 辅助方法：toGenericRow，to Generic Row。 */
  @Override
  public RowData toGenericRow(int value, int repeated) {
    GenericRowData row = new GenericRowData(repeated);
    for (int i = 0; i < repeated; i++) {
      row.setField(i, value);
    }
    return row;
  }
}
