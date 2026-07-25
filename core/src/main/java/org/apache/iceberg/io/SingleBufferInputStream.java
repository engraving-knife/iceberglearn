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
import java.util.List;

/**
 * 文件级说明：单 ByteBuffer 实现的 {@link ByteBufferInputStream}。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：对单个 {@link ByteBuffer} 进行包装，提供切片、跳跃、mark/reset 等流式读取语义， 但不消费（consume）原始缓冲区，而是通过 {@link
 * ByteBuffer#duplicate()} 创建视图，原始缓冲区 的 position/limit 不受影响。
 *
 * <p>设计意图：当数据全部位于一个 ByteBuffer（如内存中缓存的整个文件）时，相比多缓冲实现可省去 迭代器与跨缓冲区合并的开销，所有切片操作直接基于 duplicate 视图，零拷贝。
 *
 * <p>上下游关系：由 {@link ByteBufferInputStream#wrap(ByteBuffer...)} 在缓冲区数量为 1 时创建； 被 Parquet 等读取器使用。
 */
class SingleBufferInputStream extends ByteBufferInputStream {

  private final ByteBuffer original;
  private final long startPosition;
  private final int length;
  private ByteBuffer buffer;
  private int mark;

  /**
   * 构造方法：记录原始缓冲区与其起始位置/长度，并创建一个 duplicate 视图用于读写。
   *
   * <p>设计要点：保留 original 引用是为了在 backward seek 时能重新 duplicate 回初始状态， 因为 ByteBuffer 视图无法单独回退 position
   * 到比当前更早的任意点。
   *
   * @param buffer 待包装的 ByteBuffer，调用后该缓冲区的 position/limit 不被修改
   */
  SingleBufferInputStream(ByteBuffer buffer) {
    // duplicate the buffer because its state will be modified
    this.original = buffer;
    this.startPosition = buffer.position();
    this.length = original.remaining();
    initFromBuffer();
  }

  /** 重置内部视图到原始缓冲区的初始状态，并清除 mark。 */
  private void initFromBuffer() {
    this.mark = -1;
    this.buffer = original.duplicate();
  }

  /**
   * 返回相对流起始的字节位置。
   *
   * <p>设计要点：position 是相对流起始而非底层缓冲区起始，需减去 startPosition。
   *
   * @return 当前流位置
   */
  @Override
  public long getPos() {
    // position is relative to the start of the stream, not the buffer
    return buffer.position() - startPosition;
  }

  /**
   * 读取单个字节（无符号 0~255）。
   *
   * @return 0~255 的字节值
   * @throws EOFException 已到流尾
   */
  @Override
  public int read() throws IOException {
    if (!buffer.hasRemaining()) {
      throw new EOFException();
    }
    return buffer.get() & 0xFF; // as unsigned
  }

  /**
   * 将最多 len 字节读入 bytes 数组。
   *
   * @param bytes 目标数组
   * @param off 起始偏移
   * @param len 期望读取长度
   * @return 实际读取字节数；到达流尾返回 -1；len 为 0 返回 0
   */
  @Override
  public int read(byte[] bytes, int off, int len) throws IOException {
    if (len == 0) {
      return 0;
    }

    int remaining = buffer.remaining();
    if (remaining <= 0) {
      return -1;
    }

    int bytesToRead = Math.min(buffer.remaining(), len);
    buffer.get(bytes, off, bytesToRead);

    return bytesToRead;
  }

  /**
   * 跳转到流中 newPosition。
   *
   * <p>逻辑：若 newPosition 超过流长则抛 EOFException；若需回退则调用 {@link #initFromBuffer()} 重新 duplicate
   * 出初始视图；之后通过 skipFully 前进到目标位置。
   *
   * @param newPosition 目标位置（相对流起始）
   * @throws EOFException 越过流尾
   * @throws IOException skip 过程中发生 IO 错误
   */
  @Override
  public void seek(long newPosition) throws IOException {
    if (newPosition > length) {
      throw new EOFException(
          String.format("Cannot seek to position after end of file: %s", newPosition));
    }

    if (getPos() > newPosition) {
      // backwards seek requires returning to the initial state
      initFromBuffer();
    }

    long bytesToSkip = newPosition - getPos();
    skipFully(bytesToSkip);
  }

  /**
   * 跳过最多 len 字节，返回实际跳过字节数；到达流尾返回 -1。
   *
   * @param len 期望跳过的字节数
   * @return 实际跳过字节数，或 -1 表示已无剩余
   */
  @Override
  public long skip(long len) {
    if (len == 0) {
      return 0;
    }

    if (buffer.remaining() <= 0) {
      return -1;
    }

    // buffer.remaining is an int, so this will always fit in an int
    int bytesToSkip = (int) Math.min(buffer.remaining(), len);
    buffer.position(buffer.position() + bytesToSkip);

    return bytesToSkip;
  }

  /**
   * 把流中数据填入 out 缓冲区，返回实际写入字节数。
   *
   * <p>逻辑：若剩余字节不超过 out 容量，整体复制；否则仅复制 out 容量大小的切片， 并推进 buffer 的 position。最后对 out 做 flip 以便读取。
   *
   * @param out 目标缓冲区
   * @return 实际写入字节数
   */
  @Override
  public int read(ByteBuffer out) {
    int bytesToCopy;
    ByteBuffer copyBuffer;
    if (buffer.remaining() <= out.remaining()) {
      // copy all of the buffer
      bytesToCopy = buffer.remaining();
      copyBuffer = buffer;
    } else {
      // copy a slice of the current buffer
      bytesToCopy = out.remaining();
      copyBuffer = buffer.duplicate();
      copyBuffer.limit(buffer.position() + bytesToCopy);
      buffer.position(buffer.position() + bytesToCopy);
    }

    out.put(copyBuffer);
    out.flip();

    return bytesToCopy;
  }

  /**
   * 切出 len 字节的 ByteBuffer 视图，并推进流位置。
   *
   * @param len 切片长度
   * @return 切片视图（底层缓冲区的 duplicate）
   * @throws EOFException 剩余不足时抛出
   */
  @Override
  public ByteBuffer slice(int len) throws EOFException {
    if (buffer.remaining() < len) {
      throw new EOFException();
    }

    // length is less than remaining, so it must fit in an int
    ByteBuffer copy = buffer.duplicate();
    copy.limit(copy.position() + len);
    buffer.position(buffer.position() + len);

    return copy;
  }

  /**
   * 切出 len 字节的缓冲区列表（单缓冲场景下只有一个元素）。
   *
   * @param len 切片长度
   * @return 含单个 ByteBuffer 的列表
   * @throws EOFException 剩余不足时抛出
   */
  @Override
  public List<ByteBuffer> sliceBuffers(long len) throws EOFException {
    if (len == 0) {
      return Collections.emptyList();
    }

    if (len > buffer.remaining()) {
      throw new EOFException();
    }

    // length is less than remaining, so it must fit in an int
    return Collections.singletonList(slice((int) len));
  }

  /**
   * 返回剩余全部字节的缓冲区列表，并将流位置推进到末尾。
   *
   * @return 含剩余 ByteBuffer 的列表，无剩余时为空列表
   */
  @Override
  public List<ByteBuffer> remainingBuffers() {
    if (buffer.remaining() <= 0) {
      return Collections.emptyList();
    }

    ByteBuffer remaining = buffer.duplicate();
    buffer.position(buffer.limit());

    return Collections.singletonList(remaining);
  }

  /** 标记当前位置，供 {@link #reset()} 回退使用。 */
  @Override
  public void mark(int readlimit) {
    this.mark = buffer.position();
  }

  /**
   * 回退到 {@link #mark(int)} 标记的位置。
   *
   * @throws IOException 未设置 mark 时抛出
   */
  @Override
  public void reset() throws IOException {
    if (mark >= 0) {
      buffer.position(mark);
      this.mark = -1;
    } else {
      throw new IOException("No mark defined");
    }
  }

  /** 本流支持 mark/reset。 */
  @Override
  public boolean markSupported() {
    return true;
  }

  /** 返回剩余可读字节数。 */
  @Override
  public int available() {
    return buffer.remaining();
  }
}
