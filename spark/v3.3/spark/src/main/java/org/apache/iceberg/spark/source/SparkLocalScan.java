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
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.LocalScan;
import org.apache.spark.sql.types.StructType;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的扫描组件，负责构建和执行数据读取计划。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkLocalScan。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkLocalScan implements LocalScan {

  private final Table table;
  private final StructType readSchema;
  private final InternalRow[] rows;
  private final List<Expression> filterExpressions;

  SparkLocalScan(
      Table table, StructType readSchema, InternalRow[] rows, List<Expression> filterExpressions) {
    this.table = table;
    this.readSchema = readSchema;
    this.rows = rows;
    this.filterExpressions = filterExpressions;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public InternalRow[] rows() {
    return rows;
  }

  /**
   * 读取数据。
   *
   * @return 结果对象
   */
  @Override
  public StructType readSchema() {
    return readSchema;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return String.format("%s [filters=%s]", table, Spark3Util.describe(filterExpressions));
  }

  /** 返回该对象的字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "IcebergLocalScan(table=%s, type=%s, filters=%s)",
        table, SparkSchemaUtil.convert(readSchema).asStruct(), filterExpressions);
  }
}
