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
package org.apache.iceberg.flink.data;

import org.apache.flink.table.data.RowData;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.RandomGenericData;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.flink.RowDataConverter;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 文件级说明：测试 RandomRowData 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 RandomRowData 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class RandomRowData {
  /** 辅助方法：RandomRowData，Random Row Data。 */
  private RandomRowData() {}

  /** 辅助方法：generate，generate。 */
  public static Iterable<RowData> generate(Schema schema, int numRecords, long seed) {
    return convert(schema, RandomGenericData.generate(schema, numRecords, seed));
  }

  /** 辅助方法：convert，convert。 */
  public static Iterable<RowData> convert(Schema schema, Iterable<Record> records) {
    return Iterables.transform(records, record -> RowDataConverter.convert(schema, record));
  }
}
