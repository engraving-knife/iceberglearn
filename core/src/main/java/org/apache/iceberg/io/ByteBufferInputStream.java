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
import java.util.Arrays;
import java.util.List;

/**
 * 文件级说明：基于 {@link ByteBuffer} 的可寻址输入流抽象基类。
 *
 * <p>所属模块：iceberg-core（Iceberg 核心实现层，提供读写引擎与文件格式集成的基础设施）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>扩展 {@link SeekableInputStream}，在普通字节流语义之上提供"按 ByteBuffer 切片读取"能力， 支持零拷贝地返回底层缓冲区视图，避免不必要的
 *       byte[] 拷贝。
 *   <li>提供工厂方法 {@link #wrap(ByteBuffer...)} / {@link #wrap(List)} 根据缓冲区数量自动 选择单缓冲（{@link
 *       SingleBufferInputStream}）或多缓冲（{@link MultiBufferInputStream}）实现。
 *   <li>统一暴露 {@link #slice(int)}、{@link #sliceBuffers(long)}、{@link #remainingBuffers()} 等
 *       面向列式文件读取的切片接口。
 * </ul>
 *
 * <p>设计意图：Parquet/ORC 等列式格式在读取 Page/ColumnChunk 时频繁需要"借出一段字节"， 通过 slice 系列方法可以直接返回 ByteBuffer
 * 视图而非拷贝，减少 GC 压力。抽象类由两个具体实现 分别覆盖单缓冲与多缓冲场景，调用方通过工厂方法无感知地获得最优实现。
 *
 * <p>上下游关系：被 Parquet/ORC 等 reader、{@link ContentCache} 中的 CachingInputFile、 以及 core
 * 中各类解码器使用；上游数据通常来自 {@link SeekableInputStream} 包装的 FileIO 输入流。
 */
public abstract class ByteBufferInputStream extends SeekableInputStream {

  /**
   * 用一组 ByteBuffer 构建输入流；仅一个缓冲区时返回轻量的 {@link SingleBufferInputStream}， 否则返回 {@link
   * MultiBufferInputStream}。
   *
   * @param buffers 一个或多个 ByteBuffer
   * @return 包装后的 {@link ByteBufferInputStream}
   */
  public static ByteBufferInputStream wrap(ByteBuffer... buffers) {
    if (buffers.length == 1) {
      return new SingleBufferInputStream(buffers[0]);
    } else {
      return new MultiBufferInputStream(Arrays.asList(buffers));
    }
  }

  /**
   * 用 ByteBuffer 列表构建输入流；逻辑同 {@link #wrap(ByteBuffer...)}，便于直接接收 List。
   *
   * @param buffers ByteBuffer 列表
   * @return 包装后的 {@link ByteBufferInputStream}
   */
  public static ByteBufferInputStream wrap(List<ByteBuffer> buffers) {
    if (buffers.size() == 1) {
      return new SingleBufferInputStream(buffers.get(0));
    } else {
      return new MultiBufferInputStream(buffers);
    }
  }

  /**
   * 跳过指定字节数，若剩余不足则抛出 EOFException。
   *
   * <p>逻辑：调用 {@link #skip(long)} 取实际跳过字节数，与期望长度比较，不足即视为遇到流尾。
   *
   * @param length 需要跳过的字节数
   * @throws EOFException 当剩余字节不足以跳过 length 时抛出
   * @throws IOException 读取过程中发生 IO 错误
   */
  public void skipFully(long length) throws IOException {
    long skipped = skip(length);
    if (skipped < length) {
      throw new EOFException("Not enough bytes to skip: " + skipped + " < " + length);
    }
  }

  /**
   * 将流中可读字节填入 out 缓冲区，返回实际写入字节数。
   *
   * @param out 目标缓冲区，方法返回时其 position 已 flip 到写入起点
   * @return 实际写入的字节数；到达流尾返回 -1
   */
  public abstract int read(ByteBuffer out);

  /**
   * 从当前位置切出指定长度的 ByteBuffer 视图，并推进流位置。
   *
   * @param length 切片长度
   * @return 切片后的 ByteBuffer（可能是底层缓冲区的视图，零拷贝）
   * @throws EOFException 剩余字节不足时抛出
   */
  public abstract ByteBuffer slice(int length) throws EOFException;

  /**
   * 切出指定长度的 ByteBuffer 列表，可能跨多个底层缓冲区。
   *
   * @param length 需要切出的总字节数
   * @return ByteBuffer 列表，顺序对应流中字节顺序
   * @throws EOFException 剩余字节不足时抛出
   */
  public abstract List<ByteBuffer> sliceBuffers(long length) throws EOFException;

  /**
   * 切出指定长度的子流，等价于对 {@link #sliceBuffers(long)} 的结果再次 wrap。
   *
   * @param length 子流长度
   * @return 子 {@link ByteBufferInputStream}
   * @throws EOFException 剩余字节不足时抛出
   */
  public ByteBufferInputStream sliceStream(long length) throws EOFException {
    return ByteBufferInputStream.wrap(sliceBuffers(length));
  }

  /**
   * 返回流中剩余的全部 ByteBuffer，并将流位置推进到末尾。
   *
   * @return 剩余缓冲区列表，可能为空列表
   */
  public abstract List<ByteBuffer> remainingBuffers();

  /**
   * 返回由剩余字节构成的新 {@link ByteBufferInputStream}。
   *
   * @return 剩余字节构成的子流
   */
  public ByteBufferInputStream remainingStream() {
    return ByteBufferInputStream.wrap(remainingBuffers());
  }
}
