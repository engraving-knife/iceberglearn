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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.PositionDeletesScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.primitives.Ints;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.rdd.InputFileBlockHolder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 读取 position 删除文件的行读取器。
 *
 * <p>所属模块：iceberg-spark（source 子包）。继承 {@link BaseRowReader}，实现 Spark {@link PartitionReader}，读取
 * Iceberg position 删除文件（按文件内行号标记删除），用于 position deletes 重写等场景。
 *
 * <p>设计意图：position 删除文件本身即为要消费的数据，读取时需设置 Spark input file 块以支持 filename()，并将残差过滤中非常量字段下推到行读取器。
 *
 * <p>上下游关系：由 position deletes 重写读取路径构造；通过 {@link BaseRowReader#newIterable} 生成迭代器。
 */
class PositionDeletesRowReader extends BaseRowReader<PositionDeletesScanTask>
    implements PartitionReader<InternalRow> {

  private static final Logger LOG = LoggerFactory.getLogger(PositionDeletesRowReader.class);

  /** 由输入分区构造，按分支取对应 schema。 */
  PositionDeletesRowReader(SparkInputPartition partition) {
    this(
        partition.table(),
        partition.taskGroup(),
        SnapshotUtil.schemaFor(partition.table(), partition.branch()),
        partition.expectedSchema(),
        partition.isCaseSensitive());
  }

  /** 全参数构造，记录读取的分区数日志。 */
  PositionDeletesRowReader(
      Table table,
      ScanTaskGroup<PositionDeletesScanTask> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {

    super(table, taskGroup, tableSchema, expectedSchema, caseSensitive);

    int numSplits = taskGroup.tasks().size();
    LOG.debug("Reading {} position delete file split(s) for table {}", numSplits, table.name());
  }

  /** 返回该任务引用的文件流（单个 position 删除文件）。 */
  @Override
  protected Stream<ContentFile<?>> referencedFiles(PositionDeletesScanTask task) {
    return Stream.of(task.file());
  }

  /**
   * 打开单个 position 删除文件。
   *
   * <p>逻辑：设置 Spark input file 块；取输入文件与常量映射；将残差过滤中非常量字段提取后下推， 通过 newIterable 生成行迭代器。
   */
  @Override
  protected CloseableIterator<InternalRow> open(PositionDeletesScanTask task) {
    String filePath = task.file().path().toString();
    LOG.debug("Opening position delete file {}", filePath);

    // update the current file for Spark's filename() function
    InputFileBlockHolder.set(filePath, task.start(), task.length());

    InputFile inputFile = getInputFile(task.file().path().toString());
    Preconditions.checkNotNull(inputFile, "Could not find InputFile associated with %s", task);

    // select out constant fields when pushing down filter to row reader
    Map<Integer, ?> idToConstant = constantsMap(task, expectedSchema());
    Set<Integer> nonConstantFieldIds = nonConstantFieldIds(idToConstant);
    Expression residualWithoutConstants =
        ExpressionUtil.extractByIdInclusive(
            task.residual(), expectedSchema(), caseSensitive(), Ints.toArray(nonConstantFieldIds));

    return newIterable(
            inputFile,
            task.file().format(),
            task.start(),
            task.length(),
            residualWithoutConstants,
            expectedSchema(),
            idToConstant)
        .iterator();
  }

  /** 返回预期 schema 中非常量的基本类型字段 ID 集合。 */
  private Set<Integer> nonConstantFieldIds(Map<Integer, ?> idToConstant) {
    Set<Integer> fields = expectedSchema().idToName().keySet();
    return fields.stream()
        .filter(id -> expectedSchema().findField(id).type().isPrimitiveType())
        .filter(id -> !idToConstant.containsKey(id))
        .collect(Collectors.toSet());
  }
}
