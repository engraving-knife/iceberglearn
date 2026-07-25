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
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：去除 carryover 行的迭代器，过滤 changelog 中同时出现 delete+insert 的过渡行（旧版语义）。
 *
 * <p>设计意图：在行级 changelog 合并后清理冗余过渡行，避免下游重复处理。
 *
 * <p>上下游关系：由 ChangelogRowReader 使用。
 */
class RemoveCarryoverIterator extends ChangelogIterator {
  private final int[] indicesToIdentifySameRow;

  private Row cachedDeletedRow = null;
  private long deletedRowCount = 0;
  private Row cachedNextRecord = null;

  RemoveCarryoverIterator(Iterator<Row> rowIterator, StructType rowType) {
    super(rowIterator, rowType);
    this.indicesToIdentifySameRow = generateIndicesToIdentifySameRow();
  }
  /** 判断是否有下一个元素。 */
  @Override
  public boolean hasNext() {
    if (hasCachedDeleteRow() || cachedNextRecord != null) {
      return true;
    }
    return rowIterator().hasNext();
  }
  /** 返回下一个元素。 */
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
    if (changeType(currentRow).equals(DELETE) && rowIterator().hasNext()) {
      cachedDeletedRow = currentRow;
      deletedRowCount = 1;

      Row nextRow = rowIterator().next();

      // drain all identical delete rows when there is at least one cached delete row and the next
      // row is the same record
      while (nextRow != null
          && cachedDeletedRow != null
          && isSameRecord(cachedDeletedRow, nextRow, indicesToIdentifySameRow)) {
        if (changeType(nextRow).equals(INSERT)) {
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

  /**
   * The iterator returns a cached delete row if there are delete rows cached and the next row is
   * not the same record or there is no next row.
   */
  private boolean returnCachedDeleteRow() {
    return hitBoundary() && hasCachedDeleteRow();
  }
  /** 执行 hitBoundary 相关操作。 */
  private boolean hitBoundary() {
    return !rowIterator().hasNext() || cachedNextRecord != null;
  }
  /** 判断是否存在 CachedDeleteRow。 */
  private boolean hasCachedDeleteRow() {
    return cachedDeletedRow != null;
  }
  /** 执行 generateIndicesToIdentifySameRow 相关操作。 */
  private int[] generateIndicesToIdentifySameRow() {
    Set<Integer> metadataColumnIndices = Sets.newHashSet(changeTypeIndex());
    return generateIndicesToIdentifySameRow(rowType().size(), metadataColumnIndices);
  }
}
