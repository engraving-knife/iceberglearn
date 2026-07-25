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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.message.MessageEncoder;
import org.apache.iceberg.Schema;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.primitives.Bytes;

/**
 * Iceberg Avro 消息编码器：将数据编码为带 schema 指纹头的 Avro 单条消息。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的单条消息编码实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>使用 {@link DataWriter} 将数据对象编码为 Avro 二进制数据。
 *   <li>在消息前添加 Iceberg V1 头部（2 字节标识 + 8 字节 CRC-64-AVRO schema 指纹）。
 *   <li>支持返回拷贝或零拷贝的 ByteBuffer。
 * </ul>
 *
 * <p>设计意图：Avro 单条消息规范要求在数据前写入 schema 指纹，以便解码端按指纹找到写入 schema。 本类使用 ThreadLocal
 * 缓存输出流与编码器以避免重复创建。V1_HEADER (0xC3, 0x01) 是 Iceberg 自定义 的消息标识，区分于标准 Avro 单条消息编码。当 shouldCopy 为
 * false 时返回的 ByteBuffer 包装 ThreadLocal 缓冲区，调用方需在下次 encode 前完成拷贝。
 *
 * <p>上下游关系：使用 {@link DataWriter} 执行实际编码；被需要编码 Iceberg Avro 单条消息的场景调用。
 *
 * @param <D> 编码数据的类型
 */
public class IcebergEncoder<D> implements MessageEncoder<D> {

  static final byte[] V1_HEADER = new byte[] {(byte) 0xC3, (byte) 0x01};

  private static final ThreadLocal<BufferOutputStream> TEMP =
      ThreadLocal.withInitial(BufferOutputStream::new);

  private static final ThreadLocal<BinaryEncoder> ENCODER = new ThreadLocal<>();

  private final byte[] headerBytes;
  private final boolean copyOutputBytes;
  private final DatumWriter<D> writer;

  /**
   * 创建编码器（默认拷贝输出）。
   *
   * <p>encode 返回的 ByteBuffer 是拷贝副本，不受后续 encode 调用影响。
   *
   * @param schema 数据对象的 Iceberg schema
   */
  public IcebergEncoder(Schema schema) {
    this(schema, true);
  }

  /**
   * 创建编码器（指定是否拷贝输出）。
   *
   * <p>若 shouldCopy 为 true，encode 返回拷贝副本；若为 false，返回的 ByteBuffer 包装 ThreadLocal 缓冲区，可被后续 encode
   * 复用——调用方须在下次 encode 前完成拷贝。
   *
   * @param schema 数据对象的 Iceberg schema
   * @param shouldCopy 是否在返回前拷贝缓冲区
   */
  public IcebergEncoder(Schema schema, boolean shouldCopy) {
    this.copyOutputBytes = shouldCopy;
    org.apache.avro.Schema avroSchema = AvroSchemaUtil.convert(schema, "table");
    this.writer = DataWriter.create(avroSchema);
    this.headerBytes = getWriteHeader(avroSchema);
  }

  /**
   * 将数据编码为带头的 ByteBuffer。
   *
   * <p>逻辑：从 ThreadLocal 获取输出流并重置；写入头部字节后编码数据；根据 copyOutputBytes 决定返回拷贝或零拷贝的 ByteBuffer。
   *
   * @param datum 待编码的数据
   * @return 包含头部与编码数据的 ByteBuffer
   * @throws IOException 编码时发生 IO 异常
   */
  @Override
  public ByteBuffer encode(D datum) throws IOException {
    BufferOutputStream temp = TEMP.get();
    temp.reset();

    temp.write(headerBytes);
    encode(datum, temp);

    if (copyOutputBytes) {
      return temp.toBufferWithCopy();
    } else {
      return temp.toBufferWithoutCopy();
    }
  }

  /**
   * 将数据编码到输出流（不含头部）。
   *
   * <p>逻辑：从 ThreadLocal 获取或创建 BinaryEncoder 并绑定到输出流，写入数据后 flush。
   *
   * @param datum 待编码的数据
   * @param stream 输出流
   * @throws IOException 编码时发生 IO 异常
   */
  @Override
  public void encode(D datum, OutputStream stream) throws IOException {
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(stream, ENCODER.get());
    ENCODER.set(encoder);
    writer.write(datum, encoder);
    encoder.flush();
  }

  /** 缓冲区输出流：支持零拷贝与拷贝两种方式获取 ByteBuffer。 */
  private static class BufferOutputStream extends ByteArrayOutputStream {
    BufferOutputStream() {}

    /** 零拷贝方式：直接包装内部缓冲区，后续 reset 会影响返回的 ByteBuffer。 */
    ByteBuffer toBufferWithoutCopy() {
      return ByteBuffer.wrap(buf, 0, count);
    }

    /** 拷贝方式：复制内部数据后包装为 ByteBuffer，不受后续操作影响。 */
    ByteBuffer toBufferWithCopy() {
      return ByteBuffer.wrap(toByteArray());
    }
  }

  /**
   * 构建消息头部：V1 标识 + schema 的 CRC-64-AVRO 指纹。
   *
   * <p>逻辑：计算 schema 的 CRC-64-AVRO 指纹，与 V1_HEADER 拼接为头部字节数组； NoSuchAlgorithmException 包装为
   * AvroRuntimeException。
   *
   * @param schema Avro 写入 schema
   * @return 头部字节数组（2 字节标识 + 8 字节指纹）
   */
  private static byte[] getWriteHeader(org.apache.avro.Schema schema) {
    try {
      byte[] fp = SchemaNormalization.parsingFingerprint("CRC-64-AVRO", schema);
      return Bytes.concat(V1_HEADER, fp);
    } catch (NoSuchAlgorithmException e) {
      throw new AvroRuntimeException(e);
    }
  }
}
