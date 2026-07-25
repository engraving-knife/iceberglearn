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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.flink.table.api.TableColumn;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.types.Row;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.TestFixtures;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：测试 TestFlinkSource 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFlinkSource 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public abstract class TestFlinkSource extends TestFlinkScan {

  TestFlinkSource(String fileFormat) {
    super(fileFormat);
  }

  /** 辅助方法：runWithProjection，run With Projection。 */
  @Override
  protected List<Row> runWithProjection(String... projected) throws Exception {
    TableSchema.Builder builder = TableSchema.builder();
    TableSchema schema =
        FlinkSchemaUtil.toSchema(
            FlinkSchemaUtil.convert(
                catalogResource.catalog().loadTable(TestFixtures.TABLE_IDENTIFIER).schema()));
    for (String field : projected) {
      TableColumn column = schema.getTableColumn(field).get();
      builder.field(column.getName(), column.getType());
    }
    return run(FlinkSource.forRowData().project(builder.build()), Maps.newHashMap(), "", projected);
  }

  /** 辅助方法：runWithFilter，run With Filter。 */
  @Override
  protected List<Row> runWithFilter(Expression filter, String sqlFilter, boolean caseSensitive)
      throws Exception {
    FlinkSource.Builder builder =
        FlinkSource.forRowData().filters(Collections.singletonList(filter));
    Map<String, String> options = Maps.newHashMap();
    options.put("case-sensitive", Boolean.toString(caseSensitive));
    return run(builder, options, sqlFilter, "*");
  }

  /** 辅助方法：runWithOptions，run With Options。 */
  @Override
  protected List<Row> runWithOptions(Map<String, String> options) throws Exception {
    FlinkSource.Builder builder = FlinkSource.forRowData();
    Optional.ofNullable(options.get("case-sensitive"))
        .ifPresent(value -> builder.caseSensitive(Boolean.getBoolean(value)));
    Optional.ofNullable(options.get("snapshot-id"))
        .ifPresent(value -> builder.snapshotId(Long.parseLong(value)));
    Optional.ofNullable(options.get("tag")).ifPresent(value -> builder.tag(value));
    Optional.ofNullable(options.get("branch")).ifPresent(value -> builder.branch(value));
    Optional.ofNullable(options.get("start-tag")).ifPresent(value -> builder.startTag(value));
    Optional.ofNullable(options.get("end-tag")).ifPresent(value -> builder.endTag(value));
    Optional.ofNullable(options.get("start-snapshot-id"))
        .ifPresent(value -> builder.startSnapshotId(Long.parseLong(value)));
    Optional.ofNullable(options.get("end-snapshot-id"))
        .ifPresent(value -> builder.endSnapshotId(Long.parseLong(value)));
    Optional.ofNullable(options.get("as-of-timestamp"))
        .ifPresent(value -> builder.asOfTimestamp(Long.parseLong(value)));
    return run(builder, options, "", "*");
  }

  /** 辅助方法：run，run。 */
  @Override
  protected List<Row> run() throws Exception {
    return run(FlinkSource.forRowData(), Maps.newHashMap(), "", "*");
  }

  /** 辅助方法：run，run。 */
  protected abstract List<Row> run(
      FlinkSource.Builder formatBuilder,
      Map<String, String> sqlOptions,
      String sqlFilter,
      String... sqlSelectedFields)
      throws Exception;
}
