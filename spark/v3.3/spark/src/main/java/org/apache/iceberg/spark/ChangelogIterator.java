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
 * Iceberg Spark 集成相关组件的迭代器，按行或按批产出数据。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ChangelogIterator。
 *
 * <p>设计意图：迭代器模式，统一遍历接口。
 */
public abstract class ChangelogIterator implements Iterator<Row> {
  protected static final String DELETE = ChangelogOperation.DELETE.name();
  protected static final String INSERT = ChangelogOperation.INSERT.name();
  protected static final String UPDATE_BEFORE = ChangelogOperation.UPDATE_BEFORE.name();
  protected static final String UPDATE_AFTER = ChangelogOperation.UPDATE_AFTER.name();

  private final Iterator<Row> rowIterator;
  private final int changeTypeIndex;
  private final StructType rowType;

  /** 构造 ChangelogIterator 实例。 */
  protected ChangelogIterator(Iterator<Row> rowIterator, StructType rowType) {
    this.rowIterator = rowIterator;
    this.rowType = rowType;
    this.changeTypeIndex = rowType.fieldIndex(MetadataColumns.CHANGE_TYPE.name());
  }

  /** 执行该方法的具体逻辑。 */
  protected int changeTypeIndex() {
    return changeTypeIndex;
  }

  /** 执行该方法的具体逻辑。 */
  protected StructType rowType() {
    return rowType;
  }

  /** 执行该方法的具体逻辑。 */
  protected String changeType(Row row) {
    String changeType = row.getString(changeTypeIndex());
    Preconditions.checkNotNull(changeType, "Change type should not be null");
    return changeType;
  }

  /** 执行该方法的具体逻辑。 */
  protected Iterator<Row> rowIterator() {
    return rowIterator;
  }

  /** 执行该方法的具体逻辑。 */
  public static Iterator<Row> computeUpdates(
      Iterator<Row> rowIterator, StructType rowType, String[] identifierFields) {
    Iterator<Row> carryoverRemoveIterator = removeCarryovers(rowIterator, rowType);
    ChangelogIterator changelogIterator =
        new ComputeUpdateIterator(carryoverRemoveIterator, rowType, identifierFields);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }

  /** 移除元素或项。 */
  public static Iterator<Row> removeCarryovers(Iterator<Row> rowIterator, StructType rowType) {
    RemoveCarryoverIterator changelogIterator = new RemoveCarryoverIterator(rowIterator, rowType);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }

  /** 移除元素或项。 */
  public static Iterator<Row> removeNetCarryovers(Iterator<Row> rowIterator, StructType rowType) {
    ChangelogIterator changelogIterator = new RemoveNetCarryoverIterator(rowIterator, rowType);
    return Iterators.filter(changelogIterator, Objects::nonNull);
  }

  /** 判断是否samerecord。 */
  protected boolean isSameRecord(Row currentRow, Row nextRow, int[] indicesToIdentifySameRow) {
    for (int idx : indicesToIdentifySameRow) {
      if (isDifferentValue(currentRow, nextRow, idx)) {
        return false;
      }
    }

    return true;
  }

  /** 判断是否differentvalue。 */
  protected boolean isDifferentValue(Row currentRow, Row nextRow, int idx) {
    return !Objects.equals(nextRow.get(idx), currentRow.get(idx));
  }

  /** 执行该方法的具体逻辑。 */
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
