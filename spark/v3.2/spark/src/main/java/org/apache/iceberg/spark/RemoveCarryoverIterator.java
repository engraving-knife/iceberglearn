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
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

/**
 * Iceberg Spark 集成相关组件的迭代器，按行或按批产出数据。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 RemoveCarryoverIterator。
 *
 * <p>设计意图：迭代器模式，统一遍历接口。
 */
class RemoveCarryoverIterator extends ChangelogIterator {
  private final int[] indicesToIdentifySameRow;

  private Row cachedDeletedRow = null;
  private long deletedRowCount = 0;
  private Row cachedNextRecord = null;

  RemoveCarryoverIterator(Iterator<Row> rowIterator, StructType rowType) {
    super(rowIterator, rowType);
    this.indicesToIdentifySameRow = generateIndicesToIdentifySameRow(rowType.size());
  }

  /** 判断是否包含next。 */
  @Override
  public boolean hasNext() {
    if (hasCachedDeleteRow() || cachedNextRecord != null) {
      return true;
    }
    return rowIterator().hasNext();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 对应结果
   */
  @Override
  public Row next() {
    Row currentRow;

    if (returnCachedDeleteRow()) {
      // Non-carryover delete rows found. One or more identical delete rows were seen followed by a
      // non-identical row. This means none of the delete rows were carry over rows. Emit one
      // delete row and decrease the amount of delete rows seen.
      deletedRowCount--;
      currentRow = cachedDeletedRow;
      if (deletedRowCount == 0) {
        cachedDeletedRow = null;
      }
      return currentRow;
    } else if (cachedNextRecord != null) {
      currentRow = cachedNextRecord;
      cachedNextRecord = null;
    } else {
      currentRow = rowIterator().next();
    }

    // If the current row is a delete row, drain all identical delete rows
    if (currentRow.getString(changeTypeIndex()).equals(DELETE) && rowIterator().hasNext()) {
      cachedDeletedRow = currentRow;
      deletedRowCount = 1;

      Row nextRow = rowIterator().next();

      // drain all identical delete rows when there is at least one cached delete row and the next
      // row is the same record
      while (nextRow != null
          && cachedDeletedRow != null
          && isSameRecord(cachedDeletedRow, nextRow)) {
        if (nextRow.getString(changeTypeIndex()).equals(INSERT)) {
          deletedRowCount--;
          if (deletedRowCount == 0) {
            cachedDeletedRow = null;
          }
        } else {
          deletedRowCount++;
        }

        if (rowIterator().hasNext()) {
          nextRow = rowIterator().next();
        } else {
          nextRow = null;
        }
      }

      cachedNextRecord = nextRow;
      return null;
    } else {
      // either there is no cached delete row or the current row is not a delete row
      return currentRow;
    }
  }

  /** 执行该方法的具体逻辑。 */
  private boolean returnCachedDeleteRow() {
    return hitBoundary() && hasCachedDeleteRow();
  }

  /** 执行该方法的具体逻辑。 */
  private boolean hitBoundary() {
    return !rowIterator().hasNext() || cachedNextRecord != null;
  }

  /** 判断是否包含cacheddeleterow。 */
  private boolean hasCachedDeleteRow() {
    return cachedDeletedRow != null;
  }

  /** 执行该方法的具体逻辑。 */
  private int[] generateIndicesToIdentifySameRow(int columnSize) {
    int[] indices = new int[columnSize - 1];
    for (int i = 0; i < indices.length; i++) {
      if (i < changeTypeIndex()) {
        indices[i] = i;
      } else {
        indices[i] = i + 1;
      }
    }
    return indices;
  }

  /** 判断是否samerecord。 */
  private boolean isSameRecord(Row currentRow, Row nextRow) {
    for (int idx : indicesToIdentifySameRow) {
      if (isDifferentValue(currentRow, nextRow, idx)) {
        return false;
      }
    }

    return true;
  }
}
