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

import java.io.Closeable;
import java.io.IOException;

/**
 * 文件级说明：按位置范围读取的接口，允许 {@link InputFile} 的流实现执行基于位置的范围读， 在许多云对象存储上比无界顺序读更高效。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #readFully(long, byte[], int, int)} 从指定位置读取定长数据填入缓冲区。
 *   <li>通过 {@link #readTail(byte[], int, int)} 读取文件末尾定长字节。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>云对象存储（S3/GCS/Azure 等）通常对范围 GET 请求有原生优化，本接口允许实现直接 发起 range request，避免拉取不必要的数据。
 *   <li>线程安全并非接口要求，由实现自行决定。
 *   <li>若实现同时也是 {@link SeekableInputStream}，本接口的范围读不要求同步更新流位置； 调用方在切回 {@link java.io.InputStream}
 *       顺序读时应主动 seek 到正确位置。
 * </ul>
 *
 * <p>上下游关系：通常由 {@link InputFile#newStream()} 返回的流实现本接口；被列式读取器 （如读取 Parquet footer / 列块）使用以按需取数。
 */
public interface RangeReadable extends Closeable {

  /**
   * 从输入源的 {@code position} 处读取 {@code length} 字节，填入 {@code buffer} 的 {@code offset} 起始位置。
   *
   * @param position 读取起始位置
   * @param buffer 目标缓冲区
   * @param offset 写入缓冲区的起始偏移
   * @param length 要读取的字节数
   */
  void readFully(long position, byte[] buffer, int offset, int length) throws IOException;

  /**
   * 从输入源的 {@code position} 处读取数据填满整个 {@code buffer}。
   *
   * <p>逻辑：委托给 {@link #readFully(long, byte[], int, int)}，偏移为 0，长度为 buffer 长度。
   *
   * @param position 读取起始位置
   * @param buffer 目标缓冲区
   */
  default void readFully(long position, byte[] buffer) throws IOException {
    readFully(position, buffer, 0, buffer.length);
  }

  /**
   * 读取文件末尾 {@code length} 字节，写入 {@code buffer} 的 {@code offset} 起始位置。
   *
   * @param buffer 目标缓冲区
   * @param offset 写入缓冲区的起始偏移
   * @param length 从文件末尾算起要读取的字节数
   * @return 实际读取到的字节数
   * @throws IOException 若读取过程中发生错误
   */
  int readTail(byte[] buffer, int offset, int length) throws IOException;

  /**
   * 读取文件末尾数据填满整个 {@code buffer}。
   *
   * <p>逻辑：委托给 {@link #readTail(byte[], int, int)}，偏移为 0，长度为 buffer 长度。
   *
   * @param buffer 目标缓冲区
   * @return 实际读取到的字节数
   * @throws IOException 若读取过程中发生错误
   */
  default int readTail(byte[] buffer) throws IOException {
    return readTail(buffer, 0, buffer.length);
  }
}
