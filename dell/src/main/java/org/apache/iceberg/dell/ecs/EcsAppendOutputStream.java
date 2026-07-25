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

import com.emc.object.s3.S3Client;
import com.emc.object.s3.request.PutObjectRequest;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.Counter;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.metrics.MetricsContext.Unit;

/**
 * 基于 ECS append API 的 {@link PositionOutputStream} 实现，支持带缓冲的增量写入。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护一块本地字节缓冲，将多次小写入合并为少量 ECS 请求。
 *   <li>首段使用 {@code putObject} 创建对象，后续段使用 {@code appendObject} 追加， 借助 ECS 特有的 append 能力实现顺序写入。
 *   <li>维护写入位置 {@code pos} 并向指标上下文上报写入字节/操作计数。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>缓冲合并：S3 类协议单次请求开销高，逐字节写入会触发海量请求；用 {@link ByteBuffer} 缓冲攒批后批量上传，显著降低请求数与延迟。
 *   <li>首段特殊处理：appendObject 要求对象已存在，故首段必须用 putObject 创建对象， 通过 {@code firstPart} 标记区分两阶段。
 *   <li>大块直传优化：当单次写入超过缓冲容量时，跳过缓冲直接上传该段，避免无谓拷贝。
 * </ul>
 *
 * <p>上下游关系：由 {@link EcsOutputFile#createOrOverwrite()} 创建；底层依赖 {@link S3Client#putObject} 与 {@link
 * S3Client#appendObject}；被 Iceberg 写入侧使用。
 */
class EcsAppendOutputStream extends PositionOutputStream {

  private final S3Client client;

  private final EcsURI uri;

  /**
   * 本地字节缓冲，用于攒批以减少请求次数。
   *
   * <p>使用 {@link ByteBuffer} 维护写入偏移。
   */
  private final ByteBuffer localCache;

  /** 标记首段：首段用 putObject 创建对象，之后改用 appendObject 追加。 */
  private boolean firstPart = true;

  /** 当前写入位置（已写出字节数），供 {@link PositionOutputStream} 查询。 */
  private long pos;

  private final Counter writeBytes;
  private final Counter writeOperations;

  private EcsAppendOutputStream(
      S3Client client, EcsURI uri, byte[] localCache, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.localCache = ByteBuffer.wrap(localCache);
    this.writeBytes = metrics.counter(FileIOMetricsContext.WRITE_BYTES, Unit.BYTES);
    this.writeOperations = metrics.counter(FileIOMetricsContext.WRITE_OPERATIONS);
  }

  /**
   * 使用内置 1 KiB 缓冲创建输出流。
   *
   * @param client S3 客户端
   * @param uri 目标对象 location
   * @param metrics 指标上下文
   * @return 输出流实例
   */
  static EcsAppendOutputStream create(S3Client client, EcsURI uri, MetricsContext metrics) {
    return createWithBufferSize(client, uri, 1024, metrics);
  }

  /**
   * 使用指定缓冲大小创建输出流。
   *
   * @param client S3 客户端
   * @param uri 目标对象 location
   * @param size 缓冲字节数
   * @param metrics 指标上下文
   * @return 输出流实例
   */
  static EcsAppendOutputStream createWithBufferSize(
      S3Client client, EcsURI uri, int size, MetricsContext metrics) {
    return new EcsAppendOutputStream(client, uri, new byte[size], metrics);
  }

  /**
   * 写入单个字节。缓冲满时先上传缓冲。
   *
   * @param b 待写入字节
   */
  @Override
  public void write(int b) {
    if (!checkBuffer(1)) {
      flush();
    }

    localCache.put((byte) b);
    pos += 1;
    writeBytes.increment();
    writeOperations.increment();
  }

  /**
   * 写入一段字节。缓冲不足时先上传缓冲；若待写仍大于缓冲容量则直接上传该段。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若缓冲剩余空间不足容纳 len，先 {@link #flush()} 上传已有缓冲。
   *   <li>再次检查：若现在能容纳则拷入缓冲；否则内容大于缓冲，直接上传该段。
   *   <li>累加 pos 与指标计数。
   * </ol>
   *
   * @param b 字节数组
   * @param off 起始偏移
   * @param len 写入长度
   */
  @Override
  public void write(byte[] b, int off, int len) {
    if (!checkBuffer(len)) {
      flush();
    }

    if (checkBuffer(len)) {
      localCache.put(b, off, len);
    } else {
      // 内容大于缓冲容量，直接上传该段，避免无谓拷贝
      flushBuffer(b, off, len);
    }

    pos += len;
    writeBytes.increment(len);
    writeOperations.increment();
  }

  /** 判断缓冲剩余空间是否不小于下次写入字节数。 */
  private boolean checkBuffer(int nextWrite) {
    return localCache.remaining() >= nextWrite;
  }

  /**
   * 上传一段字节到 ECS。
   *
   * <p>逻辑：首段调用 {@code putObject} 创建对象并清除 {@code firstPart} 标记， 后续段调用 {@code appendObject} 追加。
   *
   * @param buffer 字节缓冲
   * @param offset 起始偏移
   * @param length 长度
   */
  private void flushBuffer(byte[] buffer, int offset, int length) {
    if (firstPart) {
      client.putObject(
          new PutObjectRequest(
              uri.bucket(), uri.name(), new ByteArrayInputStream(buffer, offset, length)));
      firstPart = false;
    } else {
      client.appendObject(
          uri.bucket(), uri.name(), new ByteArrayInputStream(buffer, offset, length));
    }
  }

  /** 返回当前写入位置。 */
  @Override
  public long getPos() {
    return pos;
  }

  /**
   * 将缓冲中已有字节上传到 ECS。
   *
   * <p>逻辑：若缓冲中有数据（remaining 小于 capacity），则 flip 后上传，并清空缓冲。
   */
  @Override
  public void flush() {
    if (localCache.remaining() < localCache.capacity()) {
      localCache.flip();
      flushBuffer(localCache.array(), localCache.arrayOffset(), localCache.remaining());
      localCache.clear();
    }
  }

  /** 关闭流时触发一次 flush 以确保缓冲数据落盘。 */
  @Override
  public void close() {
    flush();
  }
}
