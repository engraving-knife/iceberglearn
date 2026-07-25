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

import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.avro.generic.IndexedRecord;
import org.apache.iceberg.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Types;

/**
 * 信封加密的密钥元数据：携带单文件数据加密密钥（DEK）与可选的文件级 AAD 前缀，并以 Avro 单对象编码序列化。
 *
 * <p>所属模块：iceberg-core 的 encryption 包，实现 api 模块的 {@link EncryptionKeyMetadata} 接口， 是 envelope
 * 加密方案中“随文件持久化、用于恢复 DEK”的载体。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>保存 {@code encryptionKey}（DEK 字节）与 {@code aadPrefix}（文件级 AAD 前缀，可为 null）。
 *   <li>提供 Avro 序列化/反序列化能力（实现 {@link IndexedRecord}），支持按 schema 版本读写。
 *   <li>通过 {@link KeyMetadataEncoder}/{@link KeyMetadataDecoder} 完成单对象编码（版本字节 + Avro 二进制）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>版本化 schema：以单字节版本号前缀区分 schema 演进，{@link #supportedSchemaVersions} 维护版本→Schema 映射，
 *       解码端按写入版本选择对应 RawDecoder，保证前向/后向兼容。
 *   <li>同时实现 {@link IndexedRecord} 以便 Avro 反射式读写，{@link #put(int, Object)} 对未知字段序号静默忽略， 兼容未来新增字段。
 *   <li>构造器私有化 + 静态编解码器单例，避免每次序列化都重建 encoder/decoder。
 * </ul>
 *
 * <p>上下游关系：由 {@code EncryptionManager}（如 envelope 实现）在写文件时生成并随 manifest 持久化； 读文件时从 {@link
 * org.apache.iceberg.DataFile#keyMetadata()} 取出并经 {@link #parse(ByteBuffer)} 还原为对象， 再交给 {@link
 * Ciphers} 等加解密原语使用。包级可见，外部通过 {@link EncryptionKeyMetadata} 接口访问。
 */
class KeyMetadata implements EncryptionKeyMetadata, IndexedRecord {
  private static final byte V1 = 1;
  private static final Schema SCHEMA_V1 =
      new Schema(
          required(0, "encryption_key", Types.BinaryType.get()),
          optional(1, "aad_prefix", Types.BinaryType.get()));
  private static final org.apache.avro.Schema AVRO_SCHEMA_V1 =
      AvroSchemaUtil.convert(SCHEMA_V1, KeyMetadata.class.getCanonicalName());

  private static final Map<Byte, Schema> schemaVersions = ImmutableMap.of(V1, SCHEMA_V1);
  private static final Map<Byte, org.apache.avro.Schema> avroSchemaVersions =
      ImmutableMap.of(V1, AVRO_SCHEMA_V1);

  private static final KeyMetadataEncoder KEY_METADATA_ENCODER = new KeyMetadataEncoder(V1);
  private static final KeyMetadataDecoder KEY_METADATA_DECODER = new KeyMetadataDecoder(V1);

  private ByteBuffer encryptionKey;
  private ByteBuffer aadPrefix;
  private org.apache.avro.Schema avroSchema;

  /** Avro 反射实例化用的无参构造器。 */
  KeyMetadata() {}

  /**
   * 构造一份密钥元数据。
   *
   * @param encryptionKey 数据加密密钥（DEK）字节
   * @param aadPrefix 文件级 AAD 前缀，可为 null
   */
  KeyMetadata(ByteBuffer encryptionKey, ByteBuffer aadPrefix) {
    this.encryptionKey = encryptionKey;
    this.aadPrefix = aadPrefix;
    this.avroSchema = AVRO_SCHEMA_V1;
  }

  /** 返回所有支持的 schema 版本到 Iceberg {@link Schema} 的映射，供编解码器查阅。 */
  static Map<Byte, Schema> supportedSchemaVersions() {
    return schemaVersions;
  }

  /** 返回所有支持的 schema 版本到 Avro {@link org.apache.avro.Schema} 的映射，供编解码器查阅。 */
  static Map<Byte, org.apache.avro.Schema> supportedAvroSchemaVersions() {
    return avroSchemaVersions;
  }

  /** 返回 DEK 字节缓冲。 */
  ByteBuffer encryptionKey() {
    return encryptionKey;
  }

  /** 返回文件级 AAD 前缀，可能为 null。 */
  ByteBuffer aadPrefix() {
    return aadPrefix;
  }

  /**
   * 将序列化字节解析为 {@link KeyMetadata} 实例。
   *
   * <p>逻辑：委托静态 {@link KeyMetadataDecoder} 读取版本字节并按对应 schema 解码。 IO 异常包装为 {@link
   * UncheckedIOException} 抛出。
   *
   * @param buffer 序列化字节
   * @return 解析得到的密钥元数据
   */
  static KeyMetadata parse(ByteBuffer buffer) {
    try {
      return KEY_METADATA_DECODER.decode(buffer);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse envelope encryption metadata", e);
    }
  }

  /**
   * 将本对象序列化为字节缓冲。
   *
   * <p>逻辑：委托静态 {@link KeyMetadataEncoder} 写入版本字节 + Avro 二进制。 IO 异常包装为 {@link UncheckedIOException}
   * 抛出。
   *
   * @return 序列化字节
   */
  @Override
  public ByteBuffer buffer() {
    try {
      return KEY_METADATA_ENCODER.encode(this);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to serialize envelope key metadata", e);
    }
  }

  /**
   * 深拷贝本元数据（DEK 与 AAD 前缀均为同一引用，未做字节级拷贝）。
   *
   * @return 新的 {@link KeyMetadata} 实例
   */
  @Override
  public EncryptionKeyMetadata copy() {
    KeyMetadata metadata = new KeyMetadata(encryptionKey(), aadPrefix());
    return metadata;
  }

  /**
   * Avro {@link IndexedRecord} 字段写入：按字段序号设置 DEK 或 AAD 前缀。
   *
   * <p>对未知序号静默忽略，以兼容未来版本新增字段的写入场景（写入方版本较新时，旧字段仍可被读取端识别）。
   *
   * @param i 字段序号（0=encryption_key，1=aad_prefix）
   * @param v 字段值
   */
  @Override
  public void put(int i, Object v) {
    switch (i) {
      case 0:
        this.encryptionKey = (ByteBuffer) v;
        return;
      case 1:
        this.aadPrefix = (ByteBuffer) v;
        return;
      default:
        // ignore the object, it must be from a newer version of the format
    }
  }

  /**
   * Avro {@link IndexedRecord} 字段读取：按字段序号返回 DEK 或 AAD 前缀。
   *
   * @param i 字段序号（0=encryption_key，1=aad_prefix）
   * @return 字段值
   * @throws UnsupportedOperationException 若序号未知
   */
  @Override
  public Object get(int i) {
    switch (i) {
      case 0:
        return encryptionKey;
      case 1:
        return aadPrefix;
      default:
        throw new UnsupportedOperationException("Unknown field ordinal: " + i);
    }
  }

  /** 返回本实例对应的 Avro schema。 */
  @Override
  public org.apache.avro.Schema getSchema() {
    return avroSchema;
  }
}
