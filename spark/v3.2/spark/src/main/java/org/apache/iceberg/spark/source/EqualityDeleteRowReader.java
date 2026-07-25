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

import java.util.Map;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.spark.rdd.InputFileBlockHolder;
import org.apache.spark.sql.catalyst.InternalRow;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 EqualityDeleteRowReader。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class EqualityDeleteRowReader extends RowDataReader {
  /** 构造 EqualityDeleteRowReader 实例。 */
  public EqualityDeleteRowReader(
      CombinedScanTask task, Table table, Schema expectedSchema, boolean caseSensitive) {
    super(task, table, expectedSchema, caseSensitive);
  }

  /** 执行该方法的具体逻辑。 */
  @Override
  protected CloseableIterator<InternalRow> open(FileScanTask task) {
    SparkDeleteFilter matches =
        new SparkDeleteFilter(task.file().path().toString(), task.deletes(), counter());

    // schema or rows returned by readers
    Schema requiredSchema = matches.requiredSchema();
    Map<Integer, ?> idToConstant = constantsMap(task, expectedSchema());
    DataFile file = task.file();

    // update the current file for Spark's filename() function
    InputFileBlockHolder.set(file.path().toString(), task.start(), task.length());

    return matches.findEqualityDeleteRows(open(task, requiredSchema, idToConstant)).iterator();
  }
}
