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
package org.apache.iceberg.avro;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.FileReader;
import org.apache.avro.io.DatumReader;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * Avro 文件可迭代读取器：把 Avro {@link DataFileReader} 适配为 Iceberg 的 {@link
 * CloseableIterable}，支持区间读取、容器复用与文件元数据访问。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 读取链路对上层的返回类型，由 {@link Avro.ReadBuilder#build()} 构造）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>创建并管理 {@link DataFileReader}，按需读取 Avro 文件。
 *   <li>支持按字节区间 [{@code start}, {@code end}) 读取（{@link AvroRangeIterator}）， 用于并行分片。
 *   <li>支持容器对象复用（{@link AvroReuseIterator}），减少 GC 压力。
 *   <li>若 reader 实现 {@link SupportsRowPosition}，则注入行号 supplier， 供下游获取当前分片的起始行号。
 *   <li>懒加载并缓存 Avro 文件元数据（key-value）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link CloseableGroup} 以统一管理 reader 等可关闭资源，避免资源泄漏。
 *   <li>把区间读取与容器复用解耦为独立的内部迭代器，便于组合（区间读 + 复用可叠加）。
 *   <li>元数据懒加载：仅在首次 {@link #getMetadata()} 或 {@link #iterator()} 时读取文件头， 避免不必要的 IO。
 * </ul>
 *
 * <p>上下游关系：由 {@link Avro.ReadBuilder#build()} 构造；上层（引擎的扫描任务、 manifest 读取等）通过迭代器消费记录；底层依赖 {@link
 * AvroIO} 与 Avro {@link DataFileReader}。
 *
 * @param <D> 读取的数据类型
 */
public class AvroIterable<D> extends CloseableGroup implements CloseableIterable<D> {
  private final InputFile file;
  private final DatumReader<D> reader;
  private final Long start;
  private final Long end;
  private final boolean reuseContainers;
  private Map<String, String> metadata = null;

  /**
   * 构造 AvroIterable。
   *
   * @param file 输入文件
   * @param reader DatumReader（通常为 {@link ProjectionDatumReader}）
   * @param start 区间起始字节位置，null 表示从文件头读
   * @param length 区间长度，start 为 null 时忽略
   * @param reuseContainers 是否复用容器对象以减少 GC
   */
  AvroIterable(
      InputFile file, DatumReader<D> reader, Long start, Long length, boolean reuseContainers) {
    this.file = file;
    this.reader = reader;
    this.start = start;
    this.end = start != null ? start + length : null;
    this.reuseContainers = reuseContainers;
  }

  /**
   * 懒加载 Avro 文件元数据（仅首次调用时从 reader 读取，后续返回缓存）。
   *
   * @param metadataReader 已打开的 {@link DataFileReader}
   * @return 同一 reader（便于链式调用）
   */
  private DataFileReader<D> initMetadata(DataFileReader<D> metadataReader) {
    if (metadata == null) {
      this.metadata = Maps.newHashMap();
      for (String key : metadataReader.getMetaKeys()) {
        metadata.put(key, metadataReader.getMetaString(key));
      }
    }
    return metadataReader;
  }

  /**
   * 返回 Avro 文件元数据（懒加载，必要时单独打开一次 reader 读取文件头）。
   *
   * @return 文件元数据键值对
   */
  public Map<String, String> getMetadata() {
    if (metadata == null) {
      try (DataFileReader<D> reader = newFileReader()) {
        initMetadata(reader);
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to read metadata for file: %s", file);
      }
    }
    return metadata;
  }

  /**
   * 返回可关闭迭代器，按配置（区间/复用）读取 Avro 记录。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>打开 {@link DataFileReader} 并初始化元数据缓存。
   *   <li>若设置了区间（start 非空）：若 reader 支持 {@link SupportsRowPosition}，注入 行号 supplier（{@link
   *       AvroIO#findStartingRowPos}）；用 {@link AvroRangeIterator} 包装以限制读取到 {@code [start, end)} 区间。
   *   <li>否则若 reader 支持 {@link SupportsRowPosition}，注入起始行号 0。
   *   <li>把 reader 加入 {@link CloseableGroup} 以统一关闭。
   *   <li>若启用 reuseContainers，返回 {@link AvroReuseIterator}，否则返回普通 {@link CloseableIterator}。
   * </ol>
   *
   * @return 可关闭迭代器
   */
  @Override
  public CloseableIterator<D> iterator() {
    FileReader<D> fileReader = initMetadata(newFileReader());

    if (start != null) {
      if (reader instanceof SupportsRowPosition) {
        ((SupportsRowPosition) reader)
            .setRowPositionSupplier(() -> AvroIO.findStartingRowPos(file::newStream, start));
      }
      fileReader = new AvroRangeIterator<>(fileReader, start, end);
    } else if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(() -> 0L);
    }

    addCloseable(fileReader);

    if (reuseContainers) {
      return new AvroReuseIterator<>(fileReader);
    }

    return CloseableIterator.withClose(fileReader);
  }

  /**
   * 创建新的 {@link DataFileReader}，把 Iceberg 输入流经 {@link AvroIO#stream} 适配后 交给 Avro；IOException 包装为
   * {@link RuntimeIOException}。
   */
  private DataFileReader<D> newFileReader() {
    try {
      return (DataFileReader<D>)
          DataFileReader.openReader(AvroIO.stream(file.newStream(), file.getLength()), reader);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to open file: %s", file);
    }
  }

  /**
   * 区间读取迭代器：包装 {@link FileReader}，仅产出位于字节区间 [{@code ?}, {@code end}) 内的记录。
   *
   * <p>设计要点：构造时调用 {@code sync(start)} 把 reader 定位到区间起始 sync 点； {@link #hasNext()} 同时检查 reader
   * 是否还有数据且未越过 end sync。
   */
  private static class AvroRangeIterator<D> implements FileReader<D> {
    private final FileReader<D> reader;
    private final long end;

    /**
     * 构造区间迭代器，并 sync 到 start 之后的第一个 block 边界。
     *
     * @param reader 底层 FileReader
     * @param start 区间起始字节
     * @param end 区间结束字节
     */
    AvroRangeIterator(FileReader<D> reader, long start, long end) {
      this.reader = reader;
      this.end = end;

      try {
        reader.sync(start);
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to find sync past position %d", start);
      }
    }

    @Override
    public Schema getSchema() {
      return reader.getSchema();
    }

    /**
     * 是否还有下一条记录：底层 reader 有数据且未越过 end sync。
     *
     * @return true 表示可继续读取
     */
    @Override
    public boolean hasNext() {
      try {
        return reader.hasNext() && !reader.pastSync(end);
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to check range end: %d", end);
      }
    }

    /** 读取下一条记录；无数据时抛出 {@link NoSuchElementException}。 */
    @Override
    public D next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return reader.next();
    }

    /**
     * 读取下一条记录并复用给定对象（若 reader 支持）。
     *
     * @param reuse 可复用对象
     * @return 读取到的记录
     */
    @Override
    public D next(D reuse) {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        return reader.next(reuse);
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to read next record");
      }
    }

    @Override
    public void sync(long position) throws IOException {
      reader.sync(position);
    }

    @Override
    public boolean pastSync(long position) throws IOException {
      return reader.pastSync(position);
    }

    @Override
    public long tell() throws IOException {
      return reader.tell();
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }

    @Override
    public Iterator<D> iterator() {
      return this;
    }
  }

  /** 容器复用迭代器：每次 {@link #next()} 把上次返回的对象传回 reader 复用， 减少 GC 压力。代价是迭代过程中持有的对象会被覆盖，调用方不能保留旧引用。 */
  private static class AvroReuseIterator<D> implements CloseableIterator<D> {
    private final FileReader<D> reader;
    private D reused = null;

    AvroReuseIterator(FileReader<D> reader) {
      this.reader = reader;
    }

    @Override
    public boolean hasNext() {
      return reader.hasNext();
    }

    /**
     * 读取下一条记录，复用上次返回的对象作为容器。
     *
     * @return 读取到的记录（可能与上一次是同一对象实例）
     */
    @Override
    public D next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      try {
        this.reused = reader.next(reused);
        return reused;
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to read next record");
      }
    }

    @Override
    public void close() throws IOException {
      reader.close();
    }
  }
}
