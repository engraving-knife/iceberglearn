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
 * Iceberg 表在 Spark DataSource V2 中的实现的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 PositionDeletesRowReader。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class PositionDeletesRowReader extends BaseRowReader<PositionDeletesScanTask>
    implements PartitionReader<InternalRow> {

  private static final Logger LOG = LoggerFactory.getLogger(PositionDeletesRowReader.class);

  PositionDeletesRowReader(SparkInputPartition partition) {
    this(
        partition.table(),
        partition.taskGroup(),
        SnapshotUtil.schemaFor(partition.table(), partition.branch()),
        partition.expectedSchema(),
        partition.isCaseSensitive());
  }

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

  /** 执行该方法的具体逻辑。 */
  @Override
  protected Stream<ContentFile<?>> referencedFiles(PositionDeletesScanTask task) {
    return Stream.of(task.file());
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private Set<Integer> nonConstantFieldIds(Map<Integer, ?> idToConstant) {
    Set<Integer> fields = expectedSchema().idToName().keySet();
    return fields.stream()
        .filter(id -> expectedSchema().findField(id).type().isPrimitiveType())
        .filter(id -> !idToConstant.containsKey(id))
        .collect(Collectors.toSet());
  }
}
