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
 * 读取 equality 删除文件并应用为行的读取器。
 *
 * <p>所属模块：iceberg-spark（source 子包）。继承 {@link RowDataReader}，针对 equality 删除 读取数据文件并借助 {@link
 * SparkDeleteFilter} 找出被 equality 删除的行。
 *
 * <p>设计意图：equality 删除以"按字段值匹配"方式标记删除行，读取时需将删除条件与数据行连接； 本类封装该连接逻辑并维护 Spark input file 信息以支持
 * filename() 函数。
 *
 * <p>上下游关系：由 {@link RowDataReaderFactory} 在遇到 equality 删除时构造。
 */
public class EqualityDeleteRowReader extends RowDataReader {
  /** 以组合扫描任务等参数构造，委托给父类。 */
  public EqualityDeleteRowReader(
      CombinedScanTask task,
      Table table,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {
    super(table, task, tableSchema, expectedSchema, caseSensitive);
  }

  /**
   * 打开单个文件扫描任务。
   *
   * <p>逻辑：构建 {@link SparkDeleteFilter}，取所需 schema 与常量映射，设置 Spark input file 块， 读取数据后返回匹配 equality
   * 删除的行迭代器。
   */
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
