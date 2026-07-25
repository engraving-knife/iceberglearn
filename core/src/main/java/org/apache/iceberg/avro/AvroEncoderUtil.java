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
package org.apache.iceberg.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Avro 单对象编码/解码工具：把单个 Avro 对象序列化为带 magic 头与 schema 的字节数组， 并可反向解码。
 *
 * <p>所属模块：iceberg-core（avro 包，提供 Iceberg 内部对小尺寸 Avro 数据的序列化能力， 例如 manifest 文件中的 manifest-list
 * 元数据、统计信息等）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #encode(Object, Schema)}：把对象按给定 Avro schema 编码为字节数组， 字节布局为 {@code [magic 2 字节][UTF-8
 *       schema 字符串][二进制 Avro 数据]}。
 *   <li>{@link #decode(byte[])}：从字节数组还原对象，先校验 magic 头，再解析内嵌 schema， 最后用 {@link GenericAvroReader}
 *       读取二进制数据。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>把 schema 内嵌到字节流中，使解码端无需预先知道 schema，便于跨进程/跨版本传递 小型结构化数据（与 Avro 文件容器的 “schema 写在文件头”
 *       思想一致，但用于单对象）。
 *   <li>使用固定 magic 字节 {@code 0xC2 0x01} 作为格式标识，便于快速识别本格式并做校验。
 *   <li>静态注册 {@link LogicalMap} 逻辑类型，确保 Iceberg 的 map 表示在编解码时一致。
 * </ul>
 *
 * <p>上下游关系：被 core 的 manifest 写入、统计信息序列化等场景调用；依赖 {@link GenericAvroWriter}、{@link GenericAvroReader}
 * 与 Avro 编解码器。
 */
public class AvroEncoderUtil {

  private AvroEncoderUtil() {}

  static {
    LogicalTypes.register(LogicalMap.NAME, schema -> LogicalMap.get());
  }

  /** 编码格式的 magic 字节，用于识别本工具产生的字节数组。 */
  private static final byte[] MAGIC_BYTES = new byte[] {(byte) 0xC2, (byte) 0x01};

  /**
   * 把单个对象按 Avro schema 编码为字节数组。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>写入 2 字节 magic 头。
   *   <li>以 UTF-8 写入 Avro schema 的字符串表示（含长度前缀，由 {@code writeUTF} 处理）。
   *   <li>用 {@link GenericAvroWriter} 把对象写入二进制编码器，flush 后返回全部字节。
   * </ol>
   *
   * @param datum 待编码对象
   * @param avroSchema 对象的 Avro schema
   * @param <T> 对象类型
   * @return 编码后的字节数组
   * @throws IOException 若编码失败
   */
  public static <T> byte[] encode(T datum, Schema avroSchema) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      DataOutputStream dataOut = new DataOutputStream(out);

      // Write the magic bytes
      dataOut.write(MAGIC_BYTES);

      // Write avro schema
      dataOut.writeUTF(avroSchema.toString());

      // Encode the datum with avro schema.
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
      DatumWriter<T> writer = new GenericAvroWriter<>(avroSchema);
      writer.write(datum, encoder);
      encoder.flush();

      return out.toByteArray();
    }
  }

  /**
   * 从字节数组解码出 Avro 对象。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取并校验前 2 字节 magic 头，不匹配则抛出 IllegalStateException。
   *   <li>读取内嵌的 Avro schema 字符串并解析为 {@link Schema}。
   *   <li>用 {@link GenericAvroReader} 创建 reader，设置 schema 后从二进制解码器读取对象。
   * </ol>
   *
   * @param data 编码后的字节数组
   * @param <T> 期望的对象类型
   * @return 解码得到的对象
   * @throws IOException 若解码失败
   * @throws IllegalStateException 若 magic 头不匹配
   */
  public static <T> T decode(byte[] data) throws IOException {
    try (ByteArrayInputStream in = new ByteArrayInputStream(data, 0, data.length)) {
      DataInputStream dataInput = new DataInputStream(in);

      // Read the magic bytes
      byte header0 = dataInput.readByte();
      byte header1 = dataInput.readByte();
      Preconditions.checkState(
          header0 == MAGIC_BYTES[0] && header1 == MAGIC_BYTES[1],
          "Unrecognized header bytes: 0x%02X 0x%02X",
          header0,
          header1);

      // Read avro schema
      Schema avroSchema = new Schema.Parser().parse(dataInput.readUTF());

      // Decode the datum with the parsed avro schema.
      BinaryDecoder binaryDecoder = DecoderFactory.get().binaryDecoder(in, null);
      DatumReader<T> reader = new GenericAvroReader<>(avroSchema);
      reader.setSchema(avroSchema);
      return reader.read(null, binaryDecoder);
    }
  }
}
