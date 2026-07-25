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

import java.util.List;
import org.apache.iceberg.Schema;
import org.apache.iceberg.io.FileAppenderFactory;
import org.apache.iceberg.io.TestAppenderFactory;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.StructLikeSet;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * 文件级说明：测试 TestSparkAppenderFactory 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Sparkappenderfactory
 * 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkAppenderFactory extends TestAppenderFactory<InternalRow> {

  private final StructType sparkType;

  /** 测试Sparkappenderfactory。 */
  public TestSparkAppenderFactory(String fileFormat, boolean partitioned) {
    super(fileFormat, partitioned);
    this.sparkType = SparkSchemaUtil.convert(SCHEMA);
  }

  /** 创建appenderfactory。 */
  @Override
  protected FileAppenderFactory<InternalRow> createAppenderFactory(
      List<Integer> equalityFieldIds, Schema eqDeleteSchema, Schema posDeleteRowSchema) {
    return SparkAppenderFactory.builderFor(table, table.schema(), sparkType)
        .equalityFieldIds(ArrayUtil.toIntArray(equalityFieldIds))
        .eqDeleteRowSchema(eqDeleteSchema)
        .posDelRowSchema(posDeleteRowSchema)
        .build();
  }

  /** 创建行。 */
  @Override
  protected InternalRow createRow(Integer id, String data) {
    InternalRow row = new GenericInternalRow(2);
    row.update(0, id);
    row.update(1, UTF8String.fromString(data));
    return row;
  }

  /** 期望行集合。 */
  @Override
  protected StructLikeSet expectedRowSet(Iterable<InternalRow> rows) {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    for (InternalRow row : rows) {
      InternalRowWrapper wrapper = new InternalRowWrapper(sparkType);
      set.add(wrapper.wrap(row));
    }
    return set;
  }
}
