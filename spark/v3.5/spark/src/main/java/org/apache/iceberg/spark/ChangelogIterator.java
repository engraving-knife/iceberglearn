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

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import org.apache.iceberg.ChangelogOperation;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：变更日志迭代器，将数据文件与删除文件按行序合并，产出 Spark 可读的 changelog 行（含 _change_type 等）。
 *
 * <p>设计意图：基于有序文件扫描与归并迭代，处理 insert/delete/update 的行级变更语义。
 *
 * <p>上下游关系：被 ChangelogRowReader / SparkChangelogScan 使用；依赖 Iceberg FileScanTask 与删除文件。
 */
public abstract class ChangelogIterator implements Iterator<Row> {
  protected static final String DELETE = ChangelogOperation.DELETE.name();
  protected static final String INSERT = ChangelogOperation.INSERT.name();
  protected static final String UPDATE_BEFORE = ChangelogOperation.UPDATE_BEFORE.name();
  protected static final String UPDATE_AFTER = ChangelogOperation.UPDATE_AFTER.name();

  private final Iterator<Row> rowIterator;
  private final int changeTypeIndex;
  private final StructType rowType;

  protected ChangelogIterator(Iterator<Row> rowIterator, StructType rowType) {
    this.rowIterator = rowIterator;
    this.rowType = rowType;
    this.changeTypeIndex = rowType.fieldIndex(MetadataColumns.CHANGE_TYPE.name());
  }
  /** 执行 changeTypeIndex 相关操作。 */
  protected int changeTypeIndex() {
    return changeTypeIndex;
  }
  /** 执行 rowType 相关操作。 */
  protected StructType rowType() {
    return rowType;
  }
  /** 执行 changeType 相关操作。 */
  protected String changeType(Row row) {
    String changeType = row.getString(changeTypeIndex());
    Preconditions.checkNotNull(changeType, "Change type should not be null");
    return changeType;
  }
  /** 执行 rowIterator 相关操作。 */
  protected Iterator<Row> rowIterator() {
    return rowIterator;
  }

  /**
   * Creates an iterator composing {@link RemoveCarryoverIterator} and {@link ComputeUpdateIterator}
   * to remove carry-over rows and compute update rows
   *
   * @param rowIterator the iterator of rows from a changelog table
   * @param rowType the schema of the rows
   * @param identifierFields the names of the identifier columns, which determine if rows are the
   *     same
   * @return a new iterator instance
   */
  public static Iterator<Row> computeUpdates(
      Iterator<Row> rowIterator, StructType rowType, String[] identifierFields) {
    Iterator<Row> carryoverRemoveIterator = removeCarryovers(rowIterator, rowType);
    ChangelogIterator changelogIterator =
        new ComputeUpdateIterator(carryoverRemoveIterator, rowType, identifierFields);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }

  /**
   * Creates an iterator that removes carry-over rows from a changelog table.
   *
   * @param rowIterator the iterator of rows from a changelog table
   * @param rowType the schema of the rows
   * @return a new iterator instance
   */
  public static Iterator<Row> removeCarryovers(Iterator<Row> rowIterator, StructType rowType) {
    RemoveCarryoverIterator changelogIterator = new RemoveCarryoverIterator(rowIterator, rowType);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }
  /** 执行 removeNetCarryovers 相关操作。 */
  public static Iterator<Row> removeNetCarryovers(Iterator<Row> rowIterator, StructType rowType) {
    ChangelogIterator changelogIterator = new RemoveNetCarryoverIterator(rowIterator, rowType);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }
  /** 判断是否 SameRecord。 */
  protected boolean isSameRecord(Row currentRow, Row nextRow, int[] indicesToIdentifySameRow) {
    for (int idx : indicesToIdentifySameRow) {
      if (isDifferentValue(currentRow, nextRow, idx)) {
        return false;
      }
    }

    return true;
  }
  /** 判断是否 DifferentValue。 */
  protected boolean isDifferentValue(Row currentRow, Row nextRow, int idx) {
    return !Objects.equals(nextRow.get(idx), currentRow.get(idx));
  }
  /** 执行 generateIndicesToIdentifySameRow 相关操作。 */
  protected static int[] generateIndicesToIdentifySameRow(
      int totalColumnCount, Set<Integer> metadataColumnIndices) {
    int[] indices = new int[totalColumnCount - metadataColumnIndices.size()];

    for (int i = 0, j = 0; i < indices.length; i++) {
      if (!metadataColumnIndices.contains(i)) {
        indices[j] = i;
        j++;
      }
    }
    return indices;
  }
}
