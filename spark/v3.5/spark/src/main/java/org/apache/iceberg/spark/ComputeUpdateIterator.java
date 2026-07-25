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
 * 变更日志更新行计算迭代器。
 *
 * <p>所属模块：iceberg-spark。在单个 Spark 任务内，将代表更新操作的"删除+插入"行对识别并 转换为 UPDATE_BEFORE/UPDATE_AFTER
 * 更新记录。要求输入行已按标识列与变更类型排序。
 *
 * <p>职责：逐行扫描，当遇到 DELETE 且下一行为相同逻辑行（标识列相同）的 INSERT 时，将二者 标记为一次更新；INSERT 行缓存后于下次返回。
 *
 * <p>设计意图：变更日志（CDC）场景下，Iceberg 的 equality delete 与对应 insert 在按标识列
 * 排序后会相邻出现，本迭代器把它们规整为成对的更新前后镜像，便于下游消费。
 *
 * <p>示例：(id=1,data='a',DELETE) 与 (id=1,data='b',INSERT) → (id=1,data='a',UPDATE_BEFORE) 与
 * (id=1,data='b',UPDATE_AFTER)。
 */
public class ComputeUpdateIterator extends ChangelogIterator {

  private final String[] identifierFields;
  private final List<Integer> identifierFieldIdx;

  private Row cachedRow = null;

  /**
   * 构造迭代器。
   *
   * @param rowIterator 已按标识列与变更类型排序的行迭代器
   * @param rowType 行结构类型
   * @param identifierFields 标识列名数组
   */
  ComputeUpdateIterator(Iterator<Row> rowIterator, StructType rowType, String[] identifierFields) {
    super(rowIterator, rowType);
    this.identifierFieldIdx =
        Arrays.stream(identifierFields).map(rowType::fieldIndex).collect(Collectors.toList());
    this.identifierFields = identifierFields;
  }

  /** 是否还有下一行：有缓存行或底层迭代器有剩余即返回 true。 */
  @Override
  public boolean hasNext() {
    if (cachedRow != null) {
      return true;
    }
    return rowIterator().hasNext();
  }

  /**
   * 返回下一行。
   *
   * <p>逻辑：若缓存的为 UPDATE_AFTER 行则直接返回；否则取当前行，若其为 DELETE 且仍有下一行， 读取下一行并缓存；当两行为同一逻辑行（标识列相同）时，校验下一行必须为
   * INSERT， 将当前行改写为 UPDATE_BEFORE、缓存行改写为 UPDATE_AFTER。最终返回当前行。
   */
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

  /** 原地修改行中指定列为新值：GenericRow 直接改数组，其它行重建为 RowFactory.create。 */
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

  /** 判断缓存行是否为 UPDATE_AFTER（需优先返回）。 */
  private boolean cachedUpdateRecord() {
    return cachedRow != null && changeType(cachedRow).equals(UPDATE_AFTER);
  }

  /** 取当前行：有缓存则返回并清空缓存，否则取底层迭代器下一行。 */
  private Row currentRow() {
    if (cachedRow != null) {
      Row row = cachedRow;
      cachedRow = null;
      return row;
    } else {
      return rowIterator().next();
    }
  }

  /** 判断两行是否为同一逻辑行（所有标识列值均相同）。 */
  private boolean sameLogicalRow(Row currentRow, Row nextRow) {
    for (int idx : identifierFieldIdx) {
      if (isDifferentValue(currentRow, nextRow, idx)) {
        return false;
      }
    }
    return true;
  }
}
