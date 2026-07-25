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
package org.apache.iceberg.encryption;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.message.MessageDecoder;
import org.apache.iceberg.avro.GenericAvroReader;
import org.apache.iceberg.data.avro.RawDecoder;
import org.apache.iceberg.relocated.com.google.common.collect.MapMaker;

/**
 * 文件级说明：{@link KeyMetadata} 的 Avro 单对象解码器。
 *
 * <p>所属模块：iceberg-core（加密包），继承 Avro 的 {@link MessageDecoder.BaseDecoder}，把 “版本字节 + Avro
 * 二进制”格式的字节流还原为 {@link KeyMetadata} 实例。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>读取首字节版本号，按版本选择对应的写入 schema 进行解析。
 *   <li>用 {@link RawDecoder} 做按版本的 Avro 解码，支持读取 schema 与写入 schema 版本不一致时的兼容解析。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>按版本缓存解码器：{@code decoders} 用并发 Map 缓存每个写入版本对应的 {@link RawDecoder}， 避免重复构造；{@link
 *       MapMaker#makeMap()} 产出并发安全的 Map。
 *   <li>读写 schema 分离：构造期传入“读 schema 版本”，解码时按“写 schema 版本”做解析， 实现 schema 演进下的前向/后向兼容。
 * </ul>
 *
 * <p>上下游关系：由 {@link KeyMetadata#parse(ByteBuffer)} 调用，从持久化的密钥元数据字节还原对象。
 */
class KeyMetadataDecoder extends MessageDecoder.BaseDecoder<KeyMetadata> {
  private final org.apache.iceberg.Schema readSchema;
  private final Map<Byte, RawDecoder<KeyMetadata>> decoders = new MapMaker().makeMap();

  /**
   * 构造解码器。
   *
   * <p>逻辑：按 {@code readSchemaVersion} 从 {@link KeyMetadata#supportedSchemaVersions()} 取得 期望的读
   * schema；解析出的数据对象将按该 schema 描述。
   *
   * @param readSchemaVersion 期望（读）schema 版本号
   */
  KeyMetadataDecoder(byte readSchemaVersion) {
    this.readSchema = KeyMetadata.supportedSchemaVersions().get(readSchemaVersion);
  }

  /**
   * 从输入流解码出 {@link KeyMetadata}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取首字节作为写入 schema 版本号；流结束则抛异常。
   *   <li>按版本号从 {@link KeyMetadata#supportedAvroSchemaVersions()} 取写入 schema，未知版本抛异常。
   *   <li>从 {@code decoders} 缓存取或新建对应版本的 {@link RawDecoder}（用读 schema + 写 schema 构造）。
   *   <li>委托 {@link RawDecoder#decode} 完成实际 Avro 解析。
   * </ol>
   *
   * @param stream 包含版本字节 + Avro 二进制的输入流
   * @param reuse 可复用对象（本实现忽略）
   * @return 解析出的 {@link KeyMetadata}
   * @throws RuntimeException 若流已到尾或版本号无对应 schema
   */
  @Override
  public KeyMetadata decode(InputStream stream, KeyMetadata reuse) {
    byte writeSchemaVersion;

    try {
      writeSchemaVersion = (byte) stream.read();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read the version byte", e);
    }

    if (writeSchemaVersion < 0) {
      throw new RuntimeException("Version byte - end of stream reached");
    }

    Schema writeSchema = KeyMetadata.supportedAvroSchemaVersions().get(writeSchemaVersion);

    if (writeSchema == null) {
      throw new UnsupportedOperationException(
          "Cannot resolve schema for version: " + writeSchemaVersion);
    }

    RawDecoder<KeyMetadata> decoder = decoders.get(writeSchemaVersion);

    if (decoder == null) {
      decoder = new RawDecoder<>(readSchema, GenericAvroReader::create, writeSchema);

      decoders.put(writeSchemaVersion, decoder);
    }

    return decoder.decode(stream, reuse);
  }
}
