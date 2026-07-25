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
package org.apache.iceberg.parquet;

import java.io.IOException;
import java.util.NoSuchElementException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.parquet.hadoop.ParquetReader;

/**
 * 文件级说明：Parquet 文件的 {@link CloseableIterable} 适配器。
 *
 * <p>所属模块：iceberg-parquet（读取入口，位于 org.apache.iceberg.parquet 包）。
 *
 * <p>职责：包装 Parquet {@link ParquetReader.Builder}，提供迭代器接口逐行读取 Parquet 文件。
 *
 * <p>设计意图：通过 CloseableGroup 管理底层 ParquetReader 的生命周期， 确保迭代结束后正确释放资源。内部 {@link ParquetIterator}
 * 实现"预读下一行"语义， 支持复用容器对象（reuseContainers）。
 *
 * <p>上下游关系：被上层读取入口调用；依赖 ParquetReader（Parquet 读取器）。
 *
 * @param <T> 读取的记录类型
 */
public class ParquetIterable<T> extends CloseableGroup implements CloseableIterable<T> {
  private final ParquetReader.Builder<T> builder;

  ParquetIterable(ParquetReader.Builder<T> builder) {
    this.builder = builder;
  }

  /**
   * 创建迭代器：构建 ParquetReader 并包装为 ParquetIterator。
   *
   * <p>逻辑：通过 builder.build() 创建 ParquetReader，加入 CloseableGroup 管理， 然后包装为 ParquetIterator 返回。
   *
   * @return Parquet 行迭代器
   * @throws RuntimeIOException 若创建 reader 失败
   */
  @Override
  public CloseableIterator<T> iterator() {
    try {
      ParquetReader<T> reader = builder.build();
      addCloseable(reader);
      return new ParquetIterator<>(reader);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to create Parquet reader");
    }
  }

  /**
   * Parquet 行迭代器：实现预读（lookahead）语义。
   *
   * <p>设计要点：构造时立即预读第一行；hasNext 时按需推进；next 返回已预读的行。 needsAdvance 标记避免重复读取。
   */
  private static class ParquetIterator<T> implements CloseableIterator<T> {
    private final ParquetReader<T> parquet;
    private boolean needsAdvance = false;
    private boolean hasNext = false;
    private T next;

    ParquetIterator(ParquetReader<T> parquet) {
      this.parquet = parquet;
      this.next = advance();
    }

    @Override
    public boolean hasNext() {
      if (needsAdvance) {
        this.next = advance();
      }
      return hasNext;
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      this.needsAdvance = true;

      return next;
    }

    /**
     * 预读下一条记录。
     *
     * <p>注意：必须在 hasNext 中调用（因为可能复用 UnsafeRow 等容器对象）。
     *
     * @return 下一条记录，若到文件末尾则返回 null
     * @throws RuntimeIOException 若读取失败
     */
    private T advance() {
      // this must be called in hasNext because it reuses an UnsafeRow
      try {
        T nextRecord = parquet.read();
        this.needsAdvance = false;
        this.hasNext = nextRecord != null;
        return nextRecord;
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Remove is not supported");
    }

    @Override
    public void close() throws IOException {
      parquet.close();
    }
  }
}
