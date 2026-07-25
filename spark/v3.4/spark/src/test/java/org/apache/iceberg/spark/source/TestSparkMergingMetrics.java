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
package org.apache.iceberg.spark.source;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.TestMergingMetrics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.spark.sql.catalyst.InternalRow;

/**
 * 文件级说明：测试 TestSparkMergingMetrics 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Sparkmerging指标 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkMergingMetrics extends TestMergingMetrics<InternalRow> {

  /** 测试Sparkmerging指标。 */
  public TestSparkMergingMetrics(FileFormat fileFormat) {
    super(fileFormat);
  }

  /** 写与获取appender。 */
  @Override
  protected FileAppender<InternalRow> writeAndGetAppender(List<Record> records) throws IOException {
    Table testTable =
        new BaseTable(null, "dummy") {
          @Override
          public Map<String, String> properties() {
            return Collections.emptyMap();
          }

          @Override
          public SortOrder sortOrder() {
            return SortOrder.unsorted();
          }

          @Override
          public PartitionSpec spec() {
            return PartitionSpec.unpartitioned();
          }
        };

    FileAppender<InternalRow> appender =
        SparkAppenderFactory.builderFor(testTable, SCHEMA, SparkSchemaUtil.convert(SCHEMA))
            .build()
            .newAppender(org.apache.iceberg.Files.localOutput(temp.newFile()), fileFormat);
    try (FileAppender<InternalRow> fileAppender = appender) {
      records.stream()
          .map(r -> new StructInternalRow(SCHEMA.asStruct()).setStruct(r))
          .forEach(fileAppender::add);
    }
    return appender;
  }
}
