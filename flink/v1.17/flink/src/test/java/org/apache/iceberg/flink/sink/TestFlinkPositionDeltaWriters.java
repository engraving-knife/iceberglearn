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
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.flink.RowDataWrapper;
import org.apache.iceberg.flink.SimpleDataUtil;
import org.apache.iceberg.io.FileWriterFactory;
import org.apache.iceberg.io.TestPositionDeltaWriters;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.StructLikeSet;

/**
 * 文件级说明：测试 TestFlinkPositionDeltaWriters 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestFlinkPositionDeltaWriters 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFlinkPositionDeltaWriters extends TestPositionDeltaWriters<RowData> {

  /** 辅助方法：TestFlinkPositionDeltaWriters，Flink Position Delta Writers。 */
  public TestFlinkPositionDeltaWriters(FileFormat fileFormat) {
    super(fileFormat);
  }

  /** 辅助方法：newWriterFactory，new Writer Factory。 */
  @Override
  protected FileWriterFactory<RowData> newWriterFactory(
      Schema dataSchema,
      List<Integer> equalityFieldIds,
      Schema equalityDeleteRowSchema,
      Schema positionDeleteRowSchema) {
    return FlinkFileWriterFactory.builderFor(table)
        .dataSchema(table.schema())
        .dataFileFormat(format())
        .deleteFileFormat(format())
        .equalityFieldIds(ArrayUtil.toIntArray(equalityFieldIds))
        .equalityDeleteRowSchema(equalityDeleteRowSchema)
        .positionDeleteRowSchema(positionDeleteRowSchema)
        .build();
  }

  /** 辅助方法：toRow，to Row。 */
  @Override
  protected RowData toRow(Integer id, String data) {
    return SimpleDataUtil.createRowData(id, data);
  }

  /** 辅助方法：toSet，to Set。 */
  @Override
  protected StructLikeSet toSet(Iterable<RowData> rows) {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    RowType flinkType = FlinkSchemaUtil.convert(table.schema());
    for (RowData row : rows) {
      RowDataWrapper wrapper = new RowDataWrapper(flinkType, table.schema().asStruct());
      set.add(wrapper.wrap(row));
    }
    return set;
  }
}
