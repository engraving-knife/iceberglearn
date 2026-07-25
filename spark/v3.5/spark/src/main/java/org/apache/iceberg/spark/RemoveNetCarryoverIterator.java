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
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.StructType;

/**
 * 跨多快照计算净变更的行迭代器。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：在 changelog 读取流程中，把跨多个快照对同一行的相反变更（一次 insert + 一次 delete） 抵消为净变更，避免输出无意义的中间状态。区别于 {@link
 * org.apache.iceberg.spark.RemoveCarryoverIterator} 只在单快照内去除 carry-over 行。
 *
 * <p>前置假设（由上游保证）：
 *
 * <ul>
 *   <li>行迭代器按所有列分区。
 *   <li>行迭代器按所有列、change order、change type 排序；change order 与 snapshot id 一一对应。
 * </ul>
 *
 * <p>设计意图：利用排序与分区不变量，只需缓存连续相同行的计数与下一行即可在单趟遍历内完成抵消， 无需额外数据结构；用 cachedRowCount
 * 正负表示净增/净删，当相邻两行变更类型相反时计数相互抵消。
 *
 * <p>上下游关系：被 Spark changelog 读取（{@code SparkChangelogScan}）用于净 changelog 输出； 继承 {@link
 * ChangelogIterator}。
 */
public class RemoveNetCarryoverIterator extends ChangelogIterator {

  private final int[] indicesToIdentifySameRow;

  private Row cachedNextRow;
  private Row cachedRow;
  private long cachedRowCount;

  /** 构造迭代器，生成用于判定"同一行"的列索引集合（排除元数据列与 change type 列）。 */
  protected RemoveNetCarryoverIterator(Iterator<Row> rowIterator, StructType rowType) {
    super(rowIterator, rowType);
    this.indicesToIdentifySameRow = generateIndicesToIdentifySameRow();
  }

  /** 是否还有下一行：缓存计数 > 0、缓存下一行非空、或底层迭代器有剩余。 */
  @Override
  public boolean hasNext() {
    if (cachedRowCount > 0) {
      return true;
    }

    if (cachedNextRow != null) {
      return true;
    }

    return rowIterator().hasNext();
  }

  /**
   * 返回下一行净变更行。
   *
   * <p>逻辑：若缓存计数 > 0 直接返回缓存行（计数递减）；否则取当前行， 拉取后续相同行并根据变更类型是否相反调整 cachedRowCount（相反则抵消，相同则累加），
   * 直到缓存耗尽、无后续或遇到不同行。返回 null 表示当前轮无输出（被抵消）。
   *
   * @return 下一行，或 null 表示该轮被抵消
   */
  @Override
  public Row next() {
    // if there are cached rows, return one of them from the beginning
    if (cachedRowCount > 0) {
      cachedRowCount--;
      return cachedRow;
    }

    cachedRow = getCurrentRow();
    // return it directly if there is no more rows
    if (!rowIterator().hasNext()) {
      return cachedRow;
    }
    cachedRowCount = 1;

    cachedNextRow = rowIterator().next();

    // pull rows from the iterator until two consecutive rows are different
    while (isSameRecord(cachedRow, cachedNextRow, indicesToIdentifySameRow)) {
      if (oppositeChangeType(cachedRow, cachedNextRow)) {
        // two rows with opposite change types means no net changes, remove both
        cachedRowCount--;
      } else {
        // two rows with same change types means potential net changes, cache the next row
        cachedRowCount++;
      }

      // stop pulling rows if there is no more rows or the next row is different
      if (cachedRowCount <= 0 || !rowIterator().hasNext()) {
        // reset the cached next row if there is no more rows
        cachedNextRow = null;
        break;
      }

      cachedNextRow = rowIterator().next();
    }

    return null;
  }

  /** 取当前行：优先用缓存的下一行，否则从底层迭代器取。 */
  private Row getCurrentRow() {
    Row currentRow;
    if (cachedNextRow != null) {
      currentRow = cachedNextRow;
      cachedNextRow = null;
    } else {
      currentRow = rowIterator().next();
    }
    return currentRow;
  }

  /** 判断两行变更类型是否相反（insert vs delete）。 */
  private boolean oppositeChangeType(Row currentRow, Row nextRow) {
    return (changeType(nextRow).equals(INSERT) && changeType(currentRow).equals(DELETE))
        || (changeType(nextRow).equals(DELETE) && changeType(currentRow).equals(INSERT));
  }

  /** 生成判定"同一行"的列索引（排除 CHANGE_ORDINAL、COMMIT_SNAPSHOT_ID、change_type 元数据列）。 */
  private int[] generateIndicesToIdentifySameRow() {
    Set<Integer> metadataColumnIndices =
        Sets.newHashSet(
            rowType().fieldIndex(MetadataColumns.CHANGE_ORDINAL.name()),
            rowType().fieldIndex(MetadataColumns.COMMIT_SNAPSHOT_ID.name()),
            changeTypeIndex());
    return generateIndicesToIdentifySameRow(rowType().size(), metadataColumnIndices);
  }
}
