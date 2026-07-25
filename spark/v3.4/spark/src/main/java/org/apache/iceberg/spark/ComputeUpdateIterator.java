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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.catalyst.expressions.GenericRow;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：更新计算迭代器，针对 COPY-ON-WRITE 更新操作，根据待更新行与删除行集合计算最终的更新后行。
 *
 * <p>设计意图：以归并方式流式处理匹配行，避免全量物化中间结果。
 *
 * <p>上下游关系：由 SparkCopyOnWriteScan 的更新读取链路使用。
 */
public class ComputeUpdateIterator extends ChangelogIterator {

  private final String[] identifierFields;
  private final List<Integer> identifierFieldIdx;

  private Row cachedRow = null;

  ComputeUpdateIterator(Iterator<Row> rowIterator, StructType rowType, String[] identifierFields) {
    super(rowIterator, rowType);
    this.identifierFieldIdx =
        Arrays.stream(identifierFields).map(rowType::fieldIndex).collect(Collectors.toList());
    this.identifierFields = identifierFields;
  }
  /** 判断是否有下一个元素。 */
  @Override
  public boolean hasNext() {
    if (cachedRow != null) {
      return true;
    }
    return rowIterator().hasNext();
  }
  /** 返回下一个元素。 */
  @Override
  public Row next() {
    // if there is an updated cached row, return it directly
    if (cachedUpdateRecord()) {
      Row row = cachedRow;
      cachedRow = null;
      return row;
    }

    // either a cached record which is not an UPDATE or the next record in the iterator.
    Row currentRow = currentRow();

    if (changeType(currentRow).equals(DELETE) && rowIterator().hasNext()) {
      Row nextRow = rowIterator().next();
      cachedRow = nextRow;

      if (sameLogicalRow(currentRow, nextRow)) {
        Preconditions.checkState(
            changeType(nextRow).equals(INSERT),
            "Cannot compute updates because there are multiple rows with the same identifier"
                + " fields([%s]). Please make sure the rows are unique.",
            String.join(",", identifierFields));

        currentRow = modify(currentRow, changeTypeIndex(), UPDATE_BEFORE);
        cachedRow = modify(nextRow, changeTypeIndex(), UPDATE_AFTER);
      }
    }

    return currentRow;
  }
  /** 执行 modify 相关操作。 */
  private Row modify(Row row, int valueIndex, Object value) {
    if (row instanceof GenericRow) {
      GenericRow genericRow = (GenericRow) row;
      genericRow.values()[valueIndex] = value;
      return genericRow;
    } else {
      Object[] values = new Object[row.size()];
      for (int index = 0; index < row.size(); index++) {
        values[index] = row.get(index);
      }
      values[valueIndex] = value;
      return RowFactory.create(values);
    }
  }
  /** 执行 cachedUpdateRecord 相关操作。 */
  private boolean cachedUpdateRecord() {
    return cachedRow != null && changeType(cachedRow).equals(UPDATE_AFTER);
  }
  /** 执行 currentRow 相关操作。 */
  private Row currentRow() {
    if (cachedRow != null) {
      Row row = cachedRow;
      cachedRow = null;
      return row;
    } else {
      return rowIterator().next();
    }
  }
  /** 执行 sameLogicalRow 相关操作。 */
  private boolean sameLogicalRow(Row currentRow, Row nextRow) {
    for (int idx : identifierFieldIdx) {
      if (isDifferentValue(currentRow, nextRow, idx)) {
        return false;
      }
    }
    return true;
  }
}
