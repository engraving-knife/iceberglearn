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
package org.apache.iceberg.flink.source.reader;

import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.typeutils.InternalSerializers;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.flink.data.RowDataUtil;

/**
 * RowData 记录工厂，用于数组池批处理场景创建批次与克隆记录。
 *
 * <p>所属模块：iceberg-flink（source reader 侧），实现 {@link RecordFactory}。
 *
 * <p>职责：创建由 {@link GenericRowData} 组成的批次数组，并提供按字段序列化器克隆 RowData 的能力。
 *
 * <p>设计意图：克隆时若目标为 GenericRowData 则复用，否则分配新对象，因此总是把克隆返回值写回数组。
 *
 * <p>上下游关系：被 {@link ArrayPoolDataIteratorBatcher} 用于创建与填充批次。
 */
class RowDataRecordFactory implements RecordFactory<RowData> {
  private final RowType rowType;
  private final TypeSerializer[] fieldSerializers;

  /**
   * 构造工厂。
   *
   * @param rowType Flink RowType
   */
  RowDataRecordFactory(RowType rowType) {
    this.rowType = rowType;
    this.fieldSerializers = createFieldSerializers(rowType);
  }

  /** 为 RowType 各字段创建对应的 {@link TypeSerializer}。 */
  static TypeSerializer[] createFieldSerializers(RowType rowType) {
    return rowType.getChildren().stream()
        .map(InternalSerializers::create)
        .toArray(TypeSerializer[]::new);
  }

  /**
   * 创建由 GenericRowData 组成的批次数组。
   *
   * @param batchSize 批次大小
   * @return RowData 数组
   */
  @Override
  public RowData[] createBatch(int batchSize) {
    RowData[] arr = new RowData[batchSize];
    for (int i = 0; i < batchSize; ++i) {
      arr[i] = new GenericRowData(rowType.getFieldCount());
    }
    return arr;
  }

  /**
   * 将 from 克隆到批次指定位置。
   *
   * <p>逻辑：通过 RowDataUtil.clone 克隆；因返回值可能为新对象或复用对象，需写回数组对应位置。
   */
  @Override
  public void clone(RowData from, RowData[] batch, int position) {
    // Set the return value from RowDataUtil.clone back to the array.
    // Clone method returns same clone target object (reused) if it is a GenericRowData.
    // Clone method will allocate a new GenericRowData object
    // if the target object is NOT a GenericRowData.
    // So we should always set the clone return value back to the array.
    batch[position] = RowDataUtil.clone(from, batch[position], rowType, fieldSerializers);
  }
}
