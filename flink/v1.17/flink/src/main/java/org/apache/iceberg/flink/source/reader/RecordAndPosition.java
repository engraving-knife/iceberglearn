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

import org.apache.flink.annotation.Internal;

/**
 * 记录及其读取位置的组合，用于 checkpoint 持久化。
 *
 * <p>所属模块：iceberg-flink（source reader 侧）。
 *
 * <p>职责：携带一条记录与该记录之后的读取位置（fileOffset/recordOffset），\n * 保证记录处理与 checkpoint 状态更新原子化；位置指向该记录处理后
 * reader 应恢复的起点。
 *
 * <p>设计意图：可变对象，便于在仅需单实例的场景下复用，减少对象分配。
 *
 * @param <T> 记录类型
 */
@Internal
public class RecordAndPosition<T> {
  private T record;
  private int fileOffset;
  private long recordOffset;

  /** 构造并指定记录与位置。 */
  public RecordAndPosition(T record, int fileOffset, long recordOffset) {
    this.record = record;
    this.fileOffset = fileOffset;
    this.recordOffset = recordOffset;
  }

  /** 构造空对象，后续通过 set 填充。 */
  public RecordAndPosition() {}

  // ------------------------------------------------------------------------

  /** 返回记录。 */
  public T record() {
    return record;
  }

  /** 返回文件偏移。 */
  public int fileOffset() {
    return fileOffset;
  }

  /** 返回记录偏移。 */
  public long recordOffset() {
    return recordOffset;
  }

  /** 更新本对象的记录与位置。 */
  public void set(T newRecord, int newFileOffset, long newRecordOffset) {
    this.record = newRecord;
    this.fileOffset = newFileOffset;
    this.recordOffset = newRecordOffset;
  }

  /** 设置序列中的下一条记录，并将 recordOffset 加一。 */
  public void record(T nextRecord) {
    this.record = nextRecord;
    this.recordOffset++;
  }

  @Override
  public String toString() {
    return String.format("%s @ %d + %d", record, fileOffset, recordOffset);
  }
}
