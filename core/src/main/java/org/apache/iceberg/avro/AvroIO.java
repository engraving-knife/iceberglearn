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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.avro.InvalidAvroMagicException;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.iceberg.common.DynClasses;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.DelegatingInputStream;
import org.apache.iceberg.io.SeekableInputStream;

/**
 * Avro 文件 IO 适配与底层读取工具：把 Iceberg 的 {@link SeekableInputStream} 适配为 Avro 库所需的 {@link
 * SeekableInput}，并提供按字节位置定位起始行号的能力。
 *
 * <p>所属模块：iceberg-core（avro 包，Avro 读取链路的基础 IO 工具）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #stream(SeekableInputStream, long)}：把 Iceberg 输入流包装为 Avro 的 {@link
 *       SeekableInput}；优先复用 Hadoop 的 {@code AvroFSInput} 以获得更好的 seek 性能，否则使用 {@link
 *       AvroInputStreamAdapter} 适配。
 *   <li>{@link #findStartingRowPos(Supplier, long)}：扫描 Avro 文件的 block 结构，
 *       累计到达指定字节位置之前的总行数，用于分片读取时定位起始行号。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>解耦 Iceberg 的 IO 抽象与 Avro 库的输入接口，使 Avro 读取可在不同存储后端复用。
 *   <li>对 Hadoop {@code FSDataInputStream} 做特殊处理：若 Avro 库未 relocate 且存在 Hadoop 类，则构造 {@code
 *       AvroFSInput}，避免额外的字节拷贝/seek 限制。
 *   <li>定位行号时使用 directBinaryDecoder（不缓冲），保证输入流位置与解码进度一致， 从而准确累计 block 行数。
 * </ul>
 *
 * <p>上下游关系：被 {@link AvroIterable}、{@link Avro#rowCount(InputFile)} 等调用； 依赖 Avro 解码器与 Iceberg IO 抽象。
 */
class AvroIO {
  /** Avro 文件 magic 字节："Obj" + 版本号 1。 */
  private static final byte[] AVRO_MAGIC = new byte[] {'O', 'b', 'j', 1};

  private static final ValueReader<byte[]> MAGIC_READER = ValueReaders.fixed(AVRO_MAGIC.length);
  private static final ValueReader<Map<String, String>> META_READER =
      ValueReaders.map(ValueReaders.strings(), ValueReaders.strings());
  /** Avro 文件每个 block 末尾的 sync 标记长度固定为 16 字节。 */
  private static final ValueReader<byte[]> SYNC_READER = ValueReaders.fixed(16);

  private AvroIO() {}

  private static final Class<?> fsDataInputStreamClass =
      DynClasses.builder().impl("org.apache.hadoop.fs.FSDataInputStream").orNull().build();

  /** Avro 库是否已被 Iceberg relocate（relocate 后 SeekableInput 的包名会变化）。 */
  private static final boolean relocated =
      "org.apache.avro.file.SeekableInput".equals(SeekableInput.class.getName());

  /**
   * Hadoop {@code AvroFSInput} 的构造器（仅当 Avro 未 relocate 且 Hadoop 在类路径上时可用）。 用于把 {@code
   * FSDataInputStream} 包装为 Avro {@link SeekableInput}，获得原生 seek 能力。
   */
  private static final DynConstructors.Ctor<SeekableInput> avroFsInputCtor =
      !relocated && fsDataInputStreamClass != null
          ? DynConstructors.builder(SeekableInput.class)
              .impl("org.apache.hadoop.fs.AvroFSInput", fsDataInputStreamClass, Long.TYPE)
              .build()
          : null;

  /**
   * 把 Iceberg 的 {@link SeekableInputStream} 适配为 Avro {@link SeekableInput}。
   *
   * <p>逻辑：若流是 {@link DelegatingInputStream} 且其委托对象是 Hadoop {@code FSDataInputStream}，且 Avro 未
   * relocate，则构造 Hadoop 的 {@code AvroFSInput}（性能更优）；否则使用 {@link AvroInputStreamAdapter} 纯适配。
   *
   * @param stream Iceberg 可定位输入流
   * @param length 文件长度
   * @return Avro 可定位输入
   */
  static SeekableInput stream(SeekableInputStream stream, long length) {
    if (stream instanceof DelegatingInputStream) {
      InputStream wrapped = ((DelegatingInputStream) stream).getDelegate();
      if (avroFsInputCtor != null
          && fsDataInputStreamClass != null
          && fsDataInputStreamClass.isInstance(wrapped)) {
        return avroFsInputCtor.newInstance(wrapped, length);
      }
    }
    return new AvroInputStreamAdapter(stream, length);
  }

  /**
   * 把 Iceberg {@link SeekableInputStream} 适配为 Avro {@link SeekableInput} 的纯 Java 实现。
   *
   * <p>设计要点：所有方法直接委托给底层 Iceberg 流，仅补充 Avro 要求的 {@link #tell()} 与 {@link #length()}
   * 方法。不引入额外缓冲，保证位置准确。
   */
  private static class AvroInputStreamAdapter extends SeekableInputStream implements SeekableInput {
    private final SeekableInputStream stream;
    private final long length;

    AvroInputStreamAdapter(SeekableInputStream stream, long length) {
      this.stream = stream;
      this.length = length;
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }

    @Override
    public long getPos() throws IOException {
      return stream.getPos();
    }

    @Override
    public void seek(long newPos) throws IOException {
      stream.seek(newPos);
    }

    @Override
    public long tell() throws IOException {
      return getPos();
    }

    @Override
    public long length() throws IOException {
      return length;
    }

    @Override
    public int read() throws IOException {
      return stream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return stream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return stream.skip(n);
    }

    @Override
    public int available() throws IOException {
      return stream.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
      stream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      stream.reset();
    }

    @Override
    public boolean markSupported() {
      return stream.markSupported();
    }
  }

  /**
   * 扫描 Avro 文件，返回到达字节位置 {@code start} 之前的累计行数。
   *
   * <p>Avro 文件布局：{@code header|block|block|...}，header 含 {@code magic|string-map|sync}， 每个 block 含
   * {@code row-count|compressed-size|block-bytes|sync}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>用 directBinaryDecoder（不缓冲）读取 header：校验 magic、跳过 metadata、读取 文件 sync 标记。
   *   <li>循环读取每个 block 的 row-count 与 compressed-size，累计 row-count 到 {@code totalRows}，并按
   *       compressed-size 跳过 block 字节，直到下一个 sync 位置 {@code >= start}。
   *   <li>遇到 EOF 时返回当前累计行数；遇到 sync 不匹配则抛出 RuntimeIOException。
   * </ol>
   *
   * <p>设计意图：用于分片读取时把“字节起始位置”换算为“行起始位置”，供 {@link SupportsRowPosition} 等机制向 reader 报告行号。
   *
   * @param open 创建输入流的工厂（每次调用产生新流）
   * @param start 目标字节位置
   * @return 累计行数
   */
  static long findStartingRowPos(Supplier<SeekableInputStream> open, long start) {
    long totalRows = 0;
    try (SeekableInputStream in = open.get()) {
      // use a direct decoder that will not buffer so the position of the input stream is accurate
      BinaryDecoder decoder = DecoderFactory.get().directBinaryDecoder(in, null);

      // an Avro file's layout looks like this:
      //   header|block|block|...
      // the header contains:
      //   magic|string-map|sync
      // each block consists of:
      //   row-count|compressed-size-in-bytes|block-bytes|sync

      // it is necessary to read the header here because this is the only way to get the expected
      // file sync bytes
      byte[] magic = MAGIC_READER.read(decoder, null);
      if (!Arrays.equals(AVRO_MAGIC, magic)) {
        throw new InvalidAvroMagicException("Not an Avro file");
      }

      META_READER.read(decoder, null); // ignore the file metadata, it isn't needed
      byte[] fileSync = SYNC_READER.read(decoder, null);

      // the while loop reads row counts and seeks past the block bytes until the next sync pos is
      // >= start, which
      // indicates that the next sync is the start of the split.
      byte[] blockSync = new byte[16];
      long nextSyncPos = in.getPos();

      while (nextSyncPos < start) {
        if (nextSyncPos != in.getPos()) {
          in.seek(nextSyncPos);
          SYNC_READER.read(decoder, blockSync);

          if (!Arrays.equals(fileSync, blockSync)) {
            throw new RuntimeIOException("Invalid sync at %s", nextSyncPos);
          }
        }

        long rowCount = decoder.readLong();
        long compressedBlockSize = decoder.readLong();

        totalRows += rowCount;
        nextSyncPos = in.getPos() + compressedBlockSize;
      }

      return totalRows;

    } catch (EOFException e) {
      return totalRows;

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to read stream while finding starting row position");
    }
  }
}
