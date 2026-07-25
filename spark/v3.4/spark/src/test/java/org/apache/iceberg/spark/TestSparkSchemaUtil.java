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
package org.apache.iceberg.spark;

import static org.apache.iceberg.types.Types.NestedField.optional;

import java.io.IOException;
import java.util.List;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.catalyst.expressions.AttributeReference;
import org.apache.spark.sql.catalyst.expressions.MetadataAttribute;
import org.apache.spark.sql.types.StructType;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkSchemaUtil 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Spark模式工具 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkSchemaUtil {
  private static final Schema TEST_SCHEMA =
      new Schema(
          optional(1, "id", Types.IntegerType.get()), optional(2, "data", Types.StringType.get()));

  private static final Schema TEST_SCHEMA_WITH_METADATA_COLS =
      new Schema(
          optional(1, "id", Types.IntegerType.get()),
          optional(2, "data", Types.StringType.get()),
          MetadataColumns.FILE_PATH,
          MetadataColumns.ROW_POSITION);

  /** 测试estimatesize最大值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEstimateSizeMaxValue() throws IOException {
    Assert.assertEquals(
        "estimateSize returns Long max value",
        Long.MAX_VALUE,
        SparkSchemaUtil.estimateSize(null, Long.MAX_VALUE));
  }

  /** 测试estimatesize带overflow场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEstimateSizeWithOverflow() throws IOException {
    long tableSize =
        SparkSchemaUtil.estimateSize(SparkSchemaUtil.convert(TEST_SCHEMA), Long.MAX_VALUE - 1);
    Assert.assertEquals("estimateSize handles overflow", Long.MAX_VALUE, tableSize);
  }

  /** 测试 testEstimateSize 场景：验证 EstimateSize 相关操作的行为与结果。 */
  @Test
  public void testEstimateSize() throws IOException {
    long tableSize = SparkSchemaUtil.estimateSize(SparkSchemaUtil.convert(TEST_SCHEMA), 1);
    Assert.assertEquals("estimateSize matches with expected approximation", 24, tableSize);
  }

  /** 测试模式conversion带meta数据列模式场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSchemaConversionWithMetaDataColumnSchema() {
    StructType structType = SparkSchemaUtil.convert(TEST_SCHEMA_WITH_METADATA_COLS);
    List<AttributeReference> attrRefs =
        scala.collection.JavaConverters.seqAsJavaList(structType.toAttributes());
    for (AttributeReference attrRef : attrRefs) {
      if (MetadataColumns.isMetadataColumn(attrRef.name())) {
        Assert.assertTrue(
            "metadata columns should have __metadata_col in attribute metadata",
            MetadataAttribute.unapply(attrRef).isDefined());
      } else {
        Assert.assertFalse(
            "non metadata columns should not have __metadata_col in attribute metadata",
            MetadataAttribute.unapply(attrRef).isDefined());
      }
    }
  }
}
