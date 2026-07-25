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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.message.MessageEncoder;
import org.apache.iceberg.avro.GenericAvroWriter;

/**
 * 文件级说明：{@link KeyMetadata} 的 Avro 单对象编码器。
 *
 * <p>所属模块：iceberg-core（加密包），实现 Avro 的 {@link MessageEncoder}，把 {@link KeyMetadata} 序列化为“版本字节 + Avro
 * 二进制”格式以便随文件持久化。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按指定 schema 版本把 {@link KeyMetadata} 编码为 {@link ByteBuffer} 或直接写入输出流。
 *   <li>输出格式：首字节为 schema 版本号，后续为 Avro 二进制编码内容。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>线程局部缓冲：{@link BufferOutputStream} 与 {@link BinaryEncoder} 用 {@link ThreadLocal}
 *       缓存复用，避免每次编码重复分配；{@code shouldCopy} 控制返回的 buffer 是拷贝还是复用线程局部缓冲。
 *   <li>版本前缀：写入版本字节以支持 {@link KeyMetadataDecoder} 按版本选择解码 schema，实现演进兼容。
 * </ul>
 *
 * <p>上下游关系：由 {@link KeyMetadata#buffer()} 调用完成密钥元数据序列化；编码结果随数据文件元数据持久化。
 */
class KeyMetadataEncoder implements MessageEncoder<KeyMetadata> {
  private static final ThreadLocal<BufferOutputStream> TEMP =
      ThreadLocal.withInitial(BufferOutputStream::new);
  private static final ThreadLocal<BinaryEncoder> ENCODER = new ThreadLocal<>();

  private final byte schemaVersion;
  private final boolean copyOutputBytes;
  private final DatumWriter<KeyMetadata> writer;

  /**
   * 构造编码器（默认拷贝输出字节）。
   *
   * <p>逻辑：委托 {@link #KeyMetadataEncoder(byte, boolean)}，{@code shouldCopy=true}， 即 {@link
   * #encode(KeyMetadata)} 返回的 buffer 为独立拷贝，不受后续编码影响。
   *
   * @param schemaVersion 要写入的 schema 版本号
   */
  KeyMetadataEncoder(byte schemaVersion) {
    this(schemaVersion, true);
  }

  /**
   * 构造编码器，可控制返回 buffer 是否拷贝。
   *
   * <p>逻辑：按版本号从 {@link KeyMetadata#supportedAvroSchemaVersions()} 取得写入 schema， 若版本不存在则抛 {@link
   * UnsupportedOperationException}；再用 {@link GenericAvroWriter#create} 构造 DatumWriter。
   *
   * <p>设计意图：{@code shouldCopy=false} 时返回的 buffer 包裹线程局部缓冲，性能更高但调用方必须在 同线程下次调用 {@code encode}
   * 前自行拷贝，否则会被覆盖。
   *
   * @param schemaVersion 要写入的 schema 版本号
   * @param shouldCopy 是否对返回 buffer 做独立拷贝
   * @throws UnsupportedOperationException 若版本号无对应 schema
   */
  KeyMetadataEncoder(byte schemaVersion, boolean shouldCopy) {
    Schema writeSchema = KeyMetadata.supportedAvroSchemaVersions().get(schemaVersion);

    if (writeSchema == null) {
      throw new UnsupportedOperationException(
          "Cannot resolve schema for version: " + schemaVersion);
    }

    this.writer = GenericAvroWriter.create(writeSchema);
    this.schemaVersion = schemaVersion;
    this.copyOutputBytes = shouldCopy;
  }

  /**
   * 把 {@link KeyMetadata} 编码为 {@link ByteBuffer}。
   *
   * <p>逻辑：重置线程局部 {@link BufferOutputStream}，先写入版本字节，再写入 Avro 二进制内容； 最后按 {@code copyOutputBytes}
   * 决定返回拷贝或复用底层缓冲的视图。
   *
   * @param datum 要编码的密钥元数据
   * @return 包含版本字节 + Avro 二进制的缓冲
   */
  @Override
  public ByteBuffer encode(KeyMetadata datum) throws IOException {
    BufferOutputStream temp = TEMP.get();
    temp.reset();
    temp.write(schemaVersion);
    encode(datum, temp);

    if (copyOutputBytes) {
      return temp.toBufferWithCopy();
    } else {
      return temp.toBufferWithoutCopy();
    }
  }

  /**
   * 把 {@link KeyMetadata} 直接编码到输出流（不写版本字节，由调用方自行处理版本）。
   *
   * <p>逻辑：复用线程局部 {@link BinaryEncoder}，用 {@link DatumWriter#write} 写出 Avro 二进制并 flush。
   *
   * @param datum 要编码的密钥元数据
   * @param stream 目标输出流
   */
  @Override
  public void encode(KeyMetadata datum, OutputStream stream) throws IOException {
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(stream, ENCODER.get());
    ENCODER.set(encoder);
    writer.write(datum, encoder);
    encoder.flush();
  }

  /** 可复用的字节数组输出流，提供拷贝/不拷贝两种 {@link ByteBuffer} 视图。 */
  private static class BufferOutputStream extends ByteArrayOutputStream {
    BufferOutputStream() {}

    /** 直接包裹底层字节数组返回（不拷贝），调用方须在缓冲被复用前完成使用。 */
    ByteBuffer toBufferWithoutCopy() {
      return ByteBuffer.wrap(buf, 0, count);
    }

    /** 拷贝底层字节后返回独立 {@link ByteBuffer}。 */
    ByteBuffer toBufferWithCopy() {
      return ByteBuffer.wrap(toByteArray());
    }
  }
}
