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
package org.apache.iceberg.flink.source.reader;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.conversion.DataStructureConverter;
import org.apache.flink.table.data.conversion.DataStructureConverters;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;
import org.apache.flink.types.Row;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.PlaintextEncryptionManager;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.TestFixtures;
import org.apache.iceberg.flink.TestHelpers;
import org.apache.iceberg.hadoop.HadoopFileIO;

/**
 * 文件级说明：测试 TestRowDataReaderFunction 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestRowDataReaderFunction 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestRowDataReaderFunction extends ReaderFunctionTestBase<RowData> {

  protected static final RowType rowType = FlinkSchemaUtil.convert(TestFixtures.SCHEMA);
  private static final DataStructureConverter<Object, Object> rowDataConverter =
      DataStructureConverters.getConverter(TypeConversions.fromLogicalToDataType(rowType));

  /** 辅助方法：TestRowDataReaderFunction，Row Data Reader Function。 */
  public TestRowDataReaderFunction(FileFormat fileFormat) {
    super(fileFormat);
  }

  /** 辅助方法：readerFunction，reader Function。 */
  @Override
  protected ReaderFunction<RowData> readerFunction() {
    return new RowDataReaderFunction(
        new Configuration(),
        TestFixtures.SCHEMA,
        TestFixtures.SCHEMA,
        null,
        true,
        new HadoopFileIO(new org.apache.hadoop.conf.Configuration()),
        new PlaintextEncryptionManager(),
        Collections.emptyList());
  }

  /** 辅助方法：assertRecords，assert Records。 */
  @Override
  protected void assertRecords(List<Record> expected, List<RowData> actual, Schema schema) {
    List<Row> rows = toRows(actual);
    TestHelpers.assertRecords(rows, expected, TestFixtures.SCHEMA);
  }

  /** 辅助方法：toRows，to Rows。 */
  private List<Row> toRows(List<RowData> actual) {
    return actual.stream()
        .map(rowData -> (Row) rowDataConverter.toExternal(rowData))
        .collect(Collectors.toList());
  }
}
