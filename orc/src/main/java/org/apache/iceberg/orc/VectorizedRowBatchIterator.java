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
package org.apache.iceberg.orc;

import java.io.IOException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.util.Pair;
import org.apache.orc.RecordReader;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * ORC {@link RecordReader} 到 {@link CloseableIterator} 的适配器。
 *
 * <p>所属模块：iceberg-orc。把 ORC 的 nextBatch 逐批读取封装为迭代器接口， 供 {@link OrcIterable} 使用。
 *
 * <p>职责：每次 next() 返回一个 (VectorizedRowBatch, batch 在文件中的行偏移) 对。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>延迟推进（lazy advance）：hasNext 时才调 nextBatch 读取下一批，next 时标记已消费， 避免提前读取。
 *   <li>VectorizedRowBatch 复用：ORC 在每次 nextBatch 时复用同一个 batch 对象， 调用方需在下次 next 前消费完当前 batch。
 *   <li>batchOffsetInFile 记录当前 batch 起始的行号（getRowNumber）， 供 RowPositionReader 计算行绝对位置。
 * </ul>
 *
 * <p>上下游关系：被 {@link OrcIterable#iterator()} 创建和使用。
 */
public class VectorizedRowBatchIterator
    implements CloseableIterator<Pair<VectorizedRowBatch, Long>> {
  private final String fileLocation;
  private final RecordReader rows;
  private final VectorizedRowBatch batch;
  private boolean advanced = false;
  private long batchOffsetInFile = 0;

  /**
   * 构造迭代器。
   *
   * @param fileLocation 文件路径（用于错误信息）
   * @param schema ORC 读取 schema
   * @param rows ORC RecordReader
   * @param recordsPerBatch 每批最大行数
   */
  VectorizedRowBatchIterator(
      String fileLocation, TypeDescription schema, RecordReader rows, int recordsPerBatch) {
    this.fileLocation = fileLocation;
    this.rows = rows;
    this.batch = schema.createRowBatch(recordsPerBatch);
  }

  @Override
  public void close() throws IOException {
    rows.close();
  }

  /**
   * 延迟读取下一批数据。
   *
   * <p>逻辑：若尚未推进（advanced=false），记录当前行号作为 batch 偏移， 调 nextBatch 读取数据，标记 advanced=true。已推进时直接返回。
   *
   * @throws RuntimeIOException 读取失败
   */
  private void advance() {
    if (!advanced) {
      try {
        batchOffsetInFile = rows.getRowNumber();
        rows.nextBatch(batch);
      } catch (IOException ioe) {
        throw new RuntimeIOException(ioe, "Problem reading ORC file %s", fileLocation);
      }
      advanced = true;
    }
  }

  @Override
  public boolean hasNext() {
    advance();
    return batch.size > 0;
  }

  @Override
  public Pair<VectorizedRowBatch, Long> next() {
    // make sure we have the next batch
    advance();
    // mark it as used
    advanced = false;
    return Pair.of(batch, batchOffsetInFile);
  }
}
