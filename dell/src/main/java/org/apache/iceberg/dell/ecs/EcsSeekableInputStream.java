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
package org.apache.iceberg.dell.ecs;

import com.emc.object.Range;
import com.emc.object.s3.S3Client;
import java.io.IOException;
import java.io.InputStream;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;

/**
 * 基于 ECS S3 range 请求的 {@link SeekableInputStream} 实现，支持按位置随机读取。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>包装 {@link S3Client#readObjectStream(String, String, Range)}，提供可定位的输入流。
 *   <li>在 {@link #seek(long)} 后按需重新打开指向新偏移的 range 流。
 *   <li>上报读取字节/操作计数。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>懒加载：流在首次读取时才真正建立，避免构造即发请求。
 *   <li>不缓存内容：仅维护逻辑位置，读取时按需发起 range 请求。S3 协议本身不支持 已打开流的随机定位，故每次 seek 到新位置都关闭旧流并以新偏移重开。
 *   <li>位置双值：{@code newPos} 记录 seek 的目标位置，{@code pos} 记录当前流实际位置， 通过 {@link #checkAndUseNewPos()}
 *       在读取前协调二者，避免无效重开。
 *   <li>非线程安全：流实例仅供单线程使用。
 * </ul>
 *
 * <p>上下游关系：由 {@link EcsInputFile#newStream()} 创建；底层依赖 {@link S3Client#readObjectStream}；被 Iceberg
 * 读取侧（如 Parquet/ORC reader）使用。
 */
class EcsSeekableInputStream extends SeekableInputStream {

  private final S3Client client;
  private final EcsURI uri;

  /** 由 {@link #seek(long)} 设置的待生效目标位置。 */
  private long newPos = 0;
  /** 当前已打开流对应的内容位置。 */
  private long pos = -1;

  private InputStream internalStream;

  private final Counter readBytes;
  private final Counter readOperations;

  EcsSeekableInputStream(S3Client client, EcsURI uri, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.readBytes = metrics.counter(FileIOMetricsContext.READ_BYTES, Unit.BYTES);
    this.readOperations = metrics.counter(FileIOMetricsContext.READ_OPERATIONS);
  }

  /**
   * 返回当前逻辑位置。
   *
   * <p>若有待生效的 seek（newPos >= 0）返回 newPos，否则返回当前流位置 pos。
   *
   * @return 当前读取位置
   */
  @Override
  public long getPos() {
    return newPos >= 0 ? newPos : pos;
  }

  /**
   * 设置读取位置。若与当前位置相同则忽略。
   *
   * <p>逻辑：仅记录到 {@code newPos}，真正的重开流推迟到下次读取时由 {@link #checkAndUseNewPos()} 执行，避免连续 seek 造成无谓请求。
   *
   * @param inputNewPos 目标位置
   */
  @Override
  public void seek(long inputNewPos) {
    if (pos == inputNewPos) {
      return;
    }

    newPos = inputNewPos;
  }

  /**
   * 读取单个字节。
   *
   * <p>逻辑：先 {@link #checkAndUseNewPos()} 确保流指向正确位置，再读取， 累加 pos 与指标计数。
   *
   * @return 读到的字节（0-255），到达末尾返回 -1
   * @throws IOException 当底层读取失败时抛出
   */
  @Override
  public int read() throws IOException {
    checkAndUseNewPos();
    pos += 1;
    readBytes.increment();
    readOperations.increment();
    return internalStream.read();
  }

  /**
   * 读取一段字节到缓冲。
   *
   * <p>逻辑：先 {@link #checkAndUseNewPos()} 对齐位置，再读取，按实际读到的字节数 累加 pos 与指标计数。
   *
   * @param b 目标缓冲
   * @param off 起始偏移
   * @param len 期望读取长度
   * @return 实际读到的字节数，到达末尾返回 -1
   * @throws IOException 当底层读取失败时抛出
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    checkAndUseNewPos();
    int delta = internalStream.read(b, off, len);
    pos += delta;
    readBytes.increment(delta);
    readOperations.increment();
    return delta;
  }

  /**
   * 在读取前协调待生效位置与当前流位置，必要时重开 range 流。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>无待生效位置（newPos &lt; 0）直接返回。
   *   <li>待生效位置等于当前流位置，清除标记并返回。
   *   <li>否则关闭旧流，以 newPos 为偏移重新打开 range 流，并清除标记。
   * </ol>
   *
   * @throws IOException 当关闭旧流失败时抛出
   */
  private void checkAndUseNewPos() throws IOException {
    if (newPos < 0) {
      return;
    }

    if (newPos == pos) {
      newPos = -1;
      return;
    }

    if (internalStream != null) {
      internalStream.close();
    }

    pos = newPos;
    internalStream = client.readObjectStream(uri.bucket(), uri.name(), Range.fromOffset(pos));
    newPos = -1;
  }

  /**
   * 关闭流，释放底层 InputStream。
   *
   * @throws IOException 当底层关闭失败时抛出
   */
  @Override
  public void close() throws IOException {
    if (internalStream != null) {
      internalStream.close();
    }
  }
}
