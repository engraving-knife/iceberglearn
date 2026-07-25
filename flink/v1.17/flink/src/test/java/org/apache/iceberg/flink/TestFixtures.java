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
package org.apache.iceberg.flink;

import static org.apache.iceberg.types.Types.NestedField.required;

import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：测试 TestFixtures 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFixtures 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFixtures {

  /** 辅助方法：TestFixtures，Fixtures。 */
  private TestFixtures() {}

  public static final Schema SCHEMA =
      new Schema(
          required(1, "data", Types.StringType.get()),
          required(2, "id", Types.LongType.get()),
          required(3, "dt", Types.StringType.get()));

  public static final PartitionSpec SPEC =
      PartitionSpec.builderFor(SCHEMA).identity("dt").bucket("id", 1).build();

  public static final RowType ROW_TYPE = FlinkSchemaUtil.convert(SCHEMA);

  public static final String DATABASE = "default";
  public static final String TABLE = "t";
  public static final String SINK_TABLE = "t_sink";

  public static final TableIdentifier TABLE_IDENTIFIER = TableIdentifier.of(DATABASE, TABLE);

  public static final Schema TS_SCHEMA =
      new Schema(
          required(1, "ts", Types.TimestampType.withoutZone()),
          required(2, "str", Types.StringType.get()));

  public static final PartitionSpec TS_SPEC =
      PartitionSpec.builderFor(TS_SCHEMA).hour("ts").build();

  public static final RowType TS_ROW_TYPE = FlinkSchemaUtil.convert(TS_SCHEMA);
}
