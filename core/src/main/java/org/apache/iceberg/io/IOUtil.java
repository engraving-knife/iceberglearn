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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * 文件级说明：IO 读写工具类（静态方法集合）。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：提供"循环读/写直到完成"的语义包装，弥补 {@link InputStream#read(byte[], int, int)} 与 {@link
 * OutputStream#write(byte[], int, int)} 单次调用不保证读/写全部请求字节的语义缺口。
 *
 * <p>设计意图：JDK 的 read/write 不保证一次调用读写完整长度，调用方需自行循环。本工具类 封装循环逻辑，并提供"必须读满否则抛
 * EOFException"的严格版本（readFully）与"读多少算多少" 的宽松版本（readRemaining），降低上层重复代码。
 *
 * <p>上下游关系：被 core 中各类 reader/writer、{@link ContentCache} 的 CachingInputFile、 以及 Parquet/ORC 集成代码使用。
 */
public class IOUtil {
  // not meant to be instantiated
  private IOUtil() {}

  private static final int WRITE_CHUNK_SIZE = 8192;

  /**
   * 从流中读取恰好 length 字节到 bytes 的 offset 起始位置。
   *
   * <p>逻辑：委托 {@link #readRemaining(InputStream, byte[], int, int)} 循环读取；若读取字节数 小于 length 则抛
   * EOFException。
   *
   * @param stream 输入流
   * @param bytes 目标缓冲区
   * @param offset 起始偏移
   * @param length 期望读取的字节数
   * @throws EOFException 流尾提前到来，未读到 length 字节
   * @throws IOException 读取过程中发生 IO 错误
   */
  public static void readFully(InputStream stream, byte[] bytes, int offset, int length)
      throws IOException {
    int bytesRead = readRemaining(stream, bytes, offset, length);
    if (bytesRead < length) {
      throw new EOFException(
          "Reached the end of stream with " + (length - bytesRead) + " bytes left to read");
    }
  }

  /**
   * 将 ByteBuffer 全部内容写入输出流，按 8KB 分块多次 write。
   *
   * <p>设计要点：分块避免一次性大数组分配，并适配部分 OutputStream 对单次写入长度的限制。
   *
   * @param outputStream 输出流
   * @param buffer 待写入的 ByteBuffer，调用后 position 推进到 limit
   * @throws IOException 写入过程中发生 IO 错误
   */
  public static void writeFully(OutputStream outputStream, ByteBuffer buffer) throws IOException {
    if (!buffer.hasRemaining()) {
      return;
    }
    byte[] chunk = new byte[WRITE_CHUNK_SIZE];
    while (buffer.hasRemaining()) {
      int chunkSize = Math.min(chunk.length, buffer.remaining());
      buffer.get(chunk, 0, chunkSize);
      outputStream.write(chunk, 0, chunkSize);
    }
  }

  /**
   * 从流中循环读取最多 length 字节，返回实际读取字节数（到达流尾时停止）。
   *
   * <p>逻辑：循环调用 stream.read 直到 remaining 为 0 或读到流尾（返回 -1）。
   *
   * @param stream 输入流
   * @param bytes 目标缓冲区
   * @param offset 起始偏移
   * @param length 期望读取的字节数
   * @return 实际读取字节数（0~length）
   * @throws IOException 读取过程中发生 IO 错误
   */
  public static int readRemaining(InputStream stream, byte[] bytes, int offset, int length)
      throws IOException {
    int pos = offset;
    int remaining = length;
    while (remaining > 0) {
      int bytesRead = stream.read(bytes, pos, remaining);
      if (bytesRead < 0) {
        break;
      }

      remaining -= bytesRead;
      pos += bytesRead;
    }

    return length - remaining;
  }
}
