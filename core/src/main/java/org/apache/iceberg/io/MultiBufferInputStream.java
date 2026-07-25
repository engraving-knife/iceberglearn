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
package org.apache.iceberg.io;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterators;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：多 ByteBuffer 实现的 {@link ByteBufferInputStream}。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：将多个 {@link ByteBuffer} 串接为单一逻辑流，支持跨缓冲区读取、跳跃、切片与 mark/reset。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>底层缓冲区以迭代器形式串接，每次仅持有"当前缓冲区"，避免一次性合并带来的内存拷贝。
 *   <li>backward seek 通过 {@link #initFromBuffers()} 重新生成迭代器并 duplicate 各缓冲区实现， 因为视图无法回退 position
 *       到任意早于当前的位置。
 *   <li>mark/reset 通过在 mark 之后跨缓冲区保存副本视图（markBuffers），reset 时把保存的视图 拼到迭代器前部，从而支持跨多缓冲区的回退。
 * </ul>
 *
 * <p>上下游关系：由 {@link ByteBufferInputStream#wrap(ByteBuffer...)} 在缓冲区数量大于 1 时创建；
 * 通常用于跨多个网络/磁盘读取块拼成的逻辑文件流。
 */
class MultiBufferInputStream extends ByteBufferInputStream {
  private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

  private final List<ByteBuffer> buffers;
  private final long length;

  private Iterator<ByteBuffer> iterator;
  private ByteBuffer current = EMPTY;
  private long position;

  private long mark;
  private long markLimit;
  private List<ByteBuffer> markBuffers;

  /**
   * 构造方法：拷贝所有缓冲区的剩余字节总和作为流长度，并为每个缓冲区生成 duplicate 视图。
   *
   * @param buffers 待串接的 ByteBuffer 列表
   */
  MultiBufferInputStream(List<ByteBuffer> buffers) {
    this.buffers = buffers;

    long totalLen = 0;
    for (ByteBuffer buffer : buffers) {
      totalLen += buffer.remaining();
    }
    this.length = totalLen;

    initFromBuffers();
  }

  /** 重置到初始状态：清空 mark、重置位置、重新生成 duplicate 视图迭代器并取出第一个缓冲区。 */
  private void initFromBuffers() {
    discardMark();
    this.position = 0;
    this.iterator = buffers.stream().map(ByteBuffer::duplicate).iterator();
    nextBuffer();
  }

  /** 返回当前流位置。 */
  @Override
  public long getPos() {
    return position;
  }

  /**
   * 跳转到流中 newPosition。
   *
   * <p>逻辑：若 newPosition 超过流长抛 EOFException；若需回退则重新初始化迭代器；之后用 skipFully 前进到目标位置。
   *
   * @param newPosition 目标位置
   * @throws EOFException 越过流尾
   * @throws IOException skip 过程中发生 IO 错误
   */
  @Override
  public void seek(long newPosition) throws IOException {
    if (newPosition > length) {
      throw new EOFException(
          String.format("Cannot seek to position after end of file: %s", newPosition));
    }

    if (position > newPosition) {
      // backward seek requires returning to the initial state
      initFromBuffers();
    }

    long bytesToSkip = newPosition - position;
    skipFully(bytesToSkip);
  }

  /**
   * 跳过最多 n 字节，返回实际跳过字节数；到达流尾返回 -1。
   *
   * <p>逻辑：在当前缓冲区中按剩余字节量向前推进 position，当前缓冲区耗尽时调用 {@link #nextBuffer()} 取下一个；若没有更多缓冲区则返回已跳过字节数或 -1。
   *
   * @param n 期望跳过的字节数
   * @return 实际跳过字节数，或 -1
   */
  @Override
  public long skip(long n) {
    if (n <= 0) {
      return 0;
    }

    if (current == null) {
      return -1;
    }

    long bytesSkipped = 0;
    while (bytesSkipped < n) {
      if (current.remaining() > 0) {
        long bytesToSkip = Math.min(n - bytesSkipped, current.remaining());
        current.position(current.position() + (int) bytesToSkip);
        bytesSkipped += bytesToSkip;
        this.position += bytesToSkip;
      } else if (!nextBuffer()) {
        // there are no more buffers
        return bytesSkipped > 0 ? bytesSkipped : -1;
      }
    }

    return bytesSkipped;
  }

  /**
   * 把流中数据填入 out 缓冲区，可能跨多个底层缓冲区。
   *
   * <p>逻辑：循环从当前缓冲区向 out 复制，复制时若当前缓冲区剩余不超过 out 容量则整体复制， 否则仅复制 out 容量大小的切片；当前缓冲区耗尽时切换到下一个缓冲区。
   *
   * @param out 目标缓冲区
   * @return 实际写入字节数；到达流尾返回 -1
   */
  @Override
  public int read(ByteBuffer out) {
    int len = out.remaining();
    if (len <= 0) {
      return 0;
    }

    if (current == null) {
      return -1;
    }

    int bytesCopied = 0;
    while (bytesCopied < len) {
      if (current.remaining() > 0) {
        int bytesToCopy;
        ByteBuffer copyBuffer;
        if (current.remaining() <= out.remaining()) {
          // copy all of the current buffer
          bytesToCopy = current.remaining();
          copyBuffer = current;
        } else {
          // copy a slice of the current buffer
          bytesToCopy = out.remaining();
          copyBuffer = current.duplicate();
          copyBuffer.limit(copyBuffer.position() + bytesToCopy);
          current.position(copyBuffer.position() + bytesToCopy);
        }

        out.put(copyBuffer);
        bytesCopied += bytesToCopy;
        this.position += bytesToCopy;

      } else if (!nextBuffer()) {
        // there are no more buffers
        return bytesCopied > 0 ? bytesCopied : -1;
      }
    }

    return bytesCopied;
  }

  /**
   * 切出 len 字节的 ByteBuffer。
   *
   * <p>逻辑：若 len 在当前缓冲区内则直接切片返回（零拷贝）；否则需 allocate 新缓冲区并跨多个 缓冲区拷贝合并为单个 ByteBuffer 返回，剩余不足抛
   * EOFException。
   *
   * @param len 切片长度
   * @return 切片 ByteBuffer
   * @throws EOFException 剩余不足时抛出
   */
  @Override
  public ByteBuffer slice(int len) throws EOFException {
    if (len <= 0) {
      return EMPTY;
    }

    if (current == null) {
      throw new EOFException();
    }

    ByteBuffer slice;
    if (len > current.remaining()) {
      // a copy is needed to return a single buffer
      slice = ByteBuffer.allocate(len);
      int bytesCopied = read(slice);
      slice.flip();
      if (bytesCopied < len) {
        throw new EOFException();
      }
    } else {
      slice = current.duplicate();
      slice.limit(slice.position() + len);
      current.position(slice.position() + len);
      this.position += len;
    }

    return slice;
  }

  /**
   * 切出 len 字节的缓冲区列表，可能跨多个底层缓冲区但保持零拷贝（每个切片是某个底层缓冲区的视图）。
   *
   * <p>逻辑：循环从当前缓冲区切出不超过剩余需求长度的视图，加入返回列表；当前缓冲区耗尽则 切换到下一个；总剩余不足抛 EOFException。
   *
   * @param len 需要切出的总字节数
   * @return ByteBuffer 列表
   * @throws EOFException 剩余不足时抛出
   */
  @Override
  public List<ByteBuffer> sliceBuffers(long len) throws EOFException {
    if (len <= 0) {
      return ImmutableList.of();
    }

    if (current == null) {
      throw new EOFException();
    }

    List<ByteBuffer> sliceBuffers = Lists.newArrayList();
    long bytesAccumulated = 0;
    while (bytesAccumulated < len) {
      if (current.remaining() > 0) {
        // get a slice of the current buffer to return
        // always fits in an int because remaining returns an int that is >= 0
        int bufLen = (int) Math.min(len - bytesAccumulated, current.remaining());
        ByteBuffer slice = current.duplicate();
        slice.limit(slice.position() + bufLen);
        sliceBuffers.add(slice);
        bytesAccumulated += bufLen;

        // update state; the bytes are considered read
        current.position(current.position() + bufLen);
        this.position += bufLen;
      } else if (!nextBuffer()) {
        // there are no more buffers
        throw new EOFException();
      }
    }

    return sliceBuffers;
  }

  /**
   * 返回剩余全部字节的缓冲区列表，并将流位置推进到末尾。
   *
   * <p>逻辑：委托 {@link #sliceBuffers(long)}，长度为 length-position。理论上不会 EOF， 若发生则视为内部状态错误并包装为
   * RuntimeException。
   *
   * @return 剩余缓冲区列表，可能为空
   */
  @Override
  public List<ByteBuffer> remainingBuffers() {
    if (position >= length) {
      return Collections.emptyList();
    }

    try {
      return sliceBuffers(length - position);
    } catch (EOFException e) {
      throw new RuntimeException(
          "[Parquet bug] Stream is bad: incorrect bytes remaining " + (length - position));
    }
  }

  /**
   * 将最多 len 字节读入 bytes 数组，可能跨多个缓冲区。
   *
   * @param bytes 目标数组
   * @param off 起始偏移
   * @param len 期望读取长度
   * @return 实际读取字节数；到达流尾返回 -1
   */
  @Override
  public int read(byte[] bytes, int off, int len) {
    if (len <= 0) {
      if (len < 0) {
        throw new IndexOutOfBoundsException("Read length must be greater than 0: " + len);
      }
      return 0;
    }

    if (current == null) {
      return -1;
    }

    int bytesRead = 0;
    while (bytesRead < len) {
      if (current.remaining() > 0) {
        int bytesToRead = Math.min(len - bytesRead, current.remaining());
        current.get(bytes, off + bytesRead, bytesToRead);
        bytesRead += bytesToRead;
        this.position += bytesToRead;
      } else if (!nextBuffer()) {
        // there are no more buffers
        return bytesRead > 0 ? bytesRead : -1;
      }
    }

    return bytesRead;
  }

  /** 等价于 {@link #read(byte[], int, int)} 偏移为 0、长度为 bytes.length。 */
  @Override
  public int read(byte[] bytes) {
    return read(bytes, 0, bytes.length);
  }

  /**
   * 读取单个字节（无符号 0~255），可能跨缓冲区。
   *
   * @return 0~255 的字节值
   * @throws EOFException 已到流尾
   */
  @Override
  public int read() throws IOException {
    if (current == null) {
      throw new EOFException();
    }

    while (true) {
      if (current.remaining() > 0) {
        this.position += 1;
        return current.get() & 0xFF; // as unsigned
      } else if (!nextBuffer()) {
        // there are no more buffers
        throw new EOFException();
      }
    }
  }

  /** 返回剩余可读字节数（不超过 Integer.MAX_VALUE）。 */
  @Override
  public int available() {
    long remaining = length - position;
    if (remaining > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return (int) remaining;
    }
  }

  /**
   * 标记当前位置，并保存当前缓冲区的副本视图，供后续 reset 跨缓冲区回退使用。
   *
   * <p>逻辑：若已存在 mark 则先 discard；记录 mark 位置与 readlimit 边界 markLimit； 将 current 的 duplicate 加入
   * markBuffers。
   *
   * @param readlimit 在 mark 失效前可读取的最大字节数
   */
  @Override
  public void mark(int readlimit) {
    if (mark >= 0) {
      discardMark();
    }
    this.mark = position;
    this.markLimit = mark + readlimit + 1;
    if (current != null) {
      markBuffers.add(current.duplicate());
    }
  }

  /**
   * 回退到 {@link #mark(int)} 标记的位置。
   *
   * <p>逻辑：若 mark 有效且未越过 markLimit，将 markBuffers 拼到迭代器前部，重新初始化 当前缓冲区到标记点；否则抛 IOException。
   *
   * @throws IOException 未设置 mark 或已越过 markLimit 时抛出
   */
  @Override
  public void reset() throws IOException {
    if (mark >= 0 && position < markLimit) {
      this.position = mark;
      // replace the current iterator with one that adds back the buffers that
      // have been used since mark was called.
      this.iterator = Iterators.concat(markBuffers.iterator(), iterator);
      discardMark();
      nextBuffer(); // go back to the marked buffers
    } else {
      throw new IOException("No mark defined or has read past the previous mark limit");
    }
  }

  /** 清除当前 mark 及其相关状态。 */
  private void discardMark() {
    this.mark = -1;
    this.markLimit = 0;
    markBuffers = Lists.newArrayList();
  }

  /** 本流支持 mark/reset。 */
  @Override
  public boolean markSupported() {
    return true;
  }

  /**
   * 切换到下一个底层缓冲区。
   *
   * <p>逻辑：从迭代器取下一个缓冲区并 duplicate；若 mark 有效且未越过 markLimit，则把当前 缓冲区的副本加入 markBuffers 以便后续 reset；若已越过
   * markLimit 则丢弃 mark。
   *
   * @return 是否成功切到下一个缓冲区；迭代器耗尽返回 false
   */
  private boolean nextBuffer() {
    if (!iterator.hasNext()) {
      this.current = null;
      return false;
    }

    this.current = iterator.next().duplicate();

    if (mark >= 0) {
      if (position < markLimit) {
        // the mark is defined and valid. save the new buffer
        markBuffers.add(current.duplicate());
      } else {
        // the mark has not been used and is no longer valid
        discardMark();
      }
    }

    return true;
  }
}
