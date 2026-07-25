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
package org.apache.iceberg.data.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.message.MessageDecoder;
import org.apache.iceberg.avro.ProjectionDatumReader;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 原始消息解码器：使用固定的写入 schema 直接解码 Avro 二进制消息。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的单条消息解码实现。
 *
 * <p>职责：基于给定的读取 schema 与写入 schema，将输入流中的 Avro 二进制数据解码为数据对象。
 *
 * <p>设计意图：与 {@link IcebergDecoder} 不同，本类假设消息的写入 schema 已知且固定， 无需从消息头解析 schema 指纹。使用 {@link
 * ThreadLocal} 缓存 BinaryDecoder 以避免重复创建。 通过 {@link ProjectionDatumReader} 支持读写 schema 的投影与兼容读取。
 *
 * <p>上下游关系：被 {@link IcebergDecoder} 按写入 schema 维度创建并持有，由其根据消息头指纹 路由到对应的 RawDecoder 实例执行实际解码。
 *
 * @param <D> 解码结果的数据类型
 */
public class RawDecoder<D> extends MessageDecoder.BaseDecoder<D> {
  private static final ThreadLocal<BinaryDecoder> DECODER = new ThreadLocal<>();

  private final DatumReader<D> reader;

  /**
   * 构造原始消息解码器。
   *
   * <p>逻辑：以 readerFunction、readSchema 构造 {@link ProjectionDatumReader}， 并设置写入 schema 作为解码依据。
   *
   * @param readSchema 期望的读取 schema（投影 schema）
   * @param readerFunction 根据写入 schema 创建 DatumReader 的工厂函数
   * @param writeSchema 消息实际写入时使用的 schema
   */
  public RawDecoder(
      org.apache.iceberg.Schema readSchema,
      Function<Schema, DatumReader<?>> readerFunction,
      Schema writeSchema) {
    this.reader = new ProjectionDatumReader<>(readerFunction, readSchema, ImmutableMap.of(), null);
    this.reader.setSchema(writeSchema);
  }

  /**
   * 从输入流解码一条消息。
   *
   * <p>逻辑：从 ThreadLocal 获取或创建 BinaryDecoder 并绑定到输入流，调用 reader 读取记录； IO 异常包装为 {@link
   * UncheckedIOException}。
   *
   * @param stream 输入流
   * @param reuse 可复用的对象
   * @return 解码后的数据对象
   */
  @Override
  public D decode(InputStream stream, D reuse) {
    BinaryDecoder decoder = DecoderFactory.get().directBinaryDecoder(stream, DECODER.get());
    DECODER.set(decoder);
    try {
      return reader.read(reuse, decoder);
    } catch (IOException e) {
      throw new UncheckedIOException("Decoding datum failed", e);
    }
  }
}
