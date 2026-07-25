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

import static org.apache.spark.sql.functions.date_add;
import static org.apache.spark.sql.functions.expr;

import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.spark.SparkTestBaseWithCatalog;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 文件级说明：测试 TestSparkScan 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 Spark扫描 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
@RunWith(Parameterized.class)
public class TestSparkScan extends SparkTestBaseWithCatalog {

  private final String format;

  /** 参数。 */
  @Parameterized.Parameters(name = "format = {0}")
  public static Object[] parameters() {
    return new Object[] {"parquet", "avro", "orc"};
  }

  /** 测试Spark扫描。 */
  public TestSparkScan(String format) {
    this.format = format;
  }

  /** 移除表。 */
  @After
  public void removeTables() {
    sql("DROP TABLE IF EXISTS %s", tableName);
  }

  /** 测试estimated行计数场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testEstimatedRowCount() throws NoSuchTableException {
    sql(
        "CREATE TABLE %s (id BIGINT, date DATE) USING iceberg TBLPROPERTIES('%s' = '%s')",
        tableName, TableProperties.DEFAULT_FILE_FORMAT, format);

    Dataset<Row> df =
        spark
            .range(10000)
            .withColumn("date", date_add(expr("DATE '1970-01-01'"), expr("CAST(id AS INT)")))
            .select("id", "date");

    df.coalesce(1).writeTo(tableName).append();

    Table table = validationCatalog.loadTable(tableIdent);
    SparkScanBuilder scanBuilder =
        new SparkScanBuilder(spark, table, CaseInsensitiveStringMap.empty());
    SparkScan scan = (SparkScan) scanBuilder.build();
    Statistics stats = scan.estimateStatistics();

    Assert.assertEquals(10000L, stats.numRows().getAsLong());
  }
}
