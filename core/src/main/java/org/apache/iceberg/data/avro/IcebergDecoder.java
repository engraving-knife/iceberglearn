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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.message.BadHeaderException;
import org.apache.avro.message.MessageDecoder;
import org.apache.avro.message.MissingSchemaException;
import org.apache.avro.message.SchemaStore;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.collect.MapMaker;

/**
 * Iceberg Avro 消息解码器：根据消息头中的 schema 指纹路由到对应写入 schema 的解码器。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的单条消息解码实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从消息头读取 Iceberg V1 头部标识与 schema 指纹（CRC-64-AVRO）。
 *   <li>按指纹从已注册的 schema 集合或外部 {@link SchemaStore} 查找写入 schema， 路由到对应的 {@link RawDecoder} 执行实际解码。
 *   <li>支持通过 {@link #addSchema} 预注册多个写入 schema。
 * </ul>
 *
 * <p>设计意图：Avro 单条消息编码会在头部写入 schema 指纹，解码时需根据指纹找到对应写入 schema。 本类维护指纹到 RawDecoder 的映射，并使用 ThreadLocal
 * 缓存头部读取缓冲区以避免重复分配。 当本地未命中指纹时可回退到外部 SchemaStore 查找。
 *
 * <p>上下游关系：使用 {@link RawDecoder} 执行实际解码；被需要解码 Iceberg Avro 单条消息的场景调用。
 *
 * @param <D> 解码结果的数据类型
 */
public class IcebergDecoder<D> extends MessageDecoder.BaseDecoder<D> {
  private static final ThreadLocal<byte[]> HEADER_BUFFER =
      ThreadLocal.withInitial(() -> new byte[10]);

  private static final ThreadLocal<ByteBuffer> FP_BUFFER =
      ThreadLocal.withInitial(
          () -> {
            byte[] header = HEADER_BUFFER.get();
            return ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
          });

  private final org.apache.iceberg.Schema readSchema;
  private final SchemaStore resolver;
  private final Map<Long, RawDecoder<D>> decoders = new MapMaker().makeMap();

  /**
   * 创建解码器（无外部 SchemaStore）。
   *
   * <p>使用 readSchema 作为期望 schema，解码消息时按消息头指纹路由。除 readSchema 外， 可通过 {@link #addSchema} 预注册更多写入
   * schema。
   *
   * @param readSchema 期望的读取 schema
   */
  public IcebergDecoder(org.apache.iceberg.Schema readSchema) {
    this(readSchema, null);
  }

  /**
   * 创建解码器（带外部 SchemaStore）。
   *
   * <p>除预注册的 schema 外，当消息头指纹在本地未命中时，可从外部 {@link SchemaStore} 查找写入 schema。 来自 store 的 Avro schema 须与
   * Iceberg 兼容（含 id 属性且仅使用 Iceberg 类型）。
   *
   * @param readSchema 期望的读取 schema
   * @param resolver 用于按指纹查找 schema 的 SchemaStore
   */
  public IcebergDecoder(org.apache.iceberg.Schema readSchema, SchemaStore resolver) {
    this.readSchema = readSchema;
    this.resolver = resolver;
    addSchema(this.readSchema);
  }

  /**
   * 注册一个 Iceberg schema 作为可解码的写入 schema。
   *
   * <p>逻辑：将 Iceberg schema 转为 Avro schema 后委托 {@link #addSchema(Schema)} 私有方法。
   *
   * @param writeSchema 注册的写入 schema
   */
  public void addSchema(org.apache.iceberg.Schema writeSchema) {
    addSchema(AvroSchemaUtil.convert(writeSchema, "table"));
  }

  /**
   * 注册一个 Avro 写入 schema 并创建对应的 RawDecoder。
   *
   * <p>逻辑：计算 schema 的 CRC-64-AVRO 指纹，以 readSchema 和写入 schema 创建 RawDecoder， 存入指纹到解码器的映射。
   *
   * @param writeSchema Avro 写入 schema
   */
  private void addSchema(Schema writeSchema) {
    long fp = SchemaNormalization.parsingFingerprint64(writeSchema);
    RawDecoder decoder =
        new RawDecoder<>(
            readSchema, avroSchema -> DataReader.create(readSchema, avroSchema), writeSchema);
    decoders.put(fp, decoder);
  }

  /**
   * 按指纹获取解码器。
   *
   * <p>逻辑：先从本地映射查找；若未命中且配置了外部 resolver，则从 SchemaStore 按指纹查找写入 schema， 注册后返回；若仍未找到则抛出
   * MissingSchemaException。
   *
   * @param fp schema 指纹
   * @return 对应的 RawDecoder
   * @throws MissingSchemaException 若无法解析指纹对应的 schema
   */
  private RawDecoder<D> getDecoder(long fp) {
    RawDecoder<D> decoder = decoders.get(fp);
    if (decoder != null) {
      return decoder;
    }

    if (resolver != null) {
      Schema writeSchema = resolver.findByFingerprint(fp);
      if (writeSchema != null) {
        addSchema(writeSchema);
        return decoders.get(fp);
      }
    }

    throw new MissingSchemaException("Cannot resolve schema for fingerprint: " + fp);
  }

  /**
   * 从输入流解码一条消息。
   *
   * <p>逻辑：读取 10 字节头部（2 字节 V1 标识 + 8 字节指纹）；校验 V1 头部标识； 从头部提取指纹并路由到对应 RawDecoder
   * 执行解码；UncheckedIOException 包装为 AvroRuntimeException。
   *
   * @param stream 输入流
   * @param reuse 可复用的对象
   * @return 解码后的数据对象
   * @throws IOException 读取头部或解码时发生 IO 异常
   * @throws BadHeaderException 头部标识不匹配或字节不足
   */
  @Override
  public D decode(InputStream stream, D reuse) throws IOException {
    byte[] header = HEADER_BUFFER.get();
    try {
      if (!readFully(stream, header)) {
        throw new BadHeaderException("Not enough header bytes");
      }
    } catch (IOException e) {
      throw new IOException("Failed to read header and fingerprint bytes", e);
    }

    if (IcebergEncoder.V1_HEADER[0] != header[0] || IcebergEncoder.V1_HEADER[1] != header[1]) {
      throw new BadHeaderException(
          String.format("Unrecognized header bytes: 0x%02X 0x%02X", header[0], header[1]));
    }

    RawDecoder<D> decoder = getDecoder(FP_BUFFER.get().getLong(2));

    try {
      return decoder.decode(stream, reuse);
    } catch (UncheckedIOException e) {
      throw new AvroRuntimeException(e);
    }
  }

  /**
   * 从流中完整读取指定长度的缓冲区，必要时多次调用 read。
   *
   * <p>逻辑：循环调用 stream.read 直到填满缓冲区或流结束；返回是否读满。
   *
   * @param stream 输入流
   * @param bytes 目标缓冲区
   * @return 若缓冲区填满返回 true，流提前结束返回 false
   * @throws IOException 读取时发生 IO 异常
   */
  @SuppressWarnings("checkstyle:InnerAssignment")
  private boolean readFully(InputStream stream, byte[] bytes) throws IOException {
    int pos = 0;
    int bytesRead;
    while ((bytes.length - pos) > 0
        && (bytesRead = stream.read(bytes, pos, bytes.length - pos)) > 0) {
      pos += bytesRead;
    }
    return pos == bytes.length;
  }
}
