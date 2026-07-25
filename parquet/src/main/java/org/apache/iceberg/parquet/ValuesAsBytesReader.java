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
package org.apache.iceberg.parquet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.parquet.bytes.ByteBufferInputStream;
import org.apache.parquet.column.values.ValuesReader;
import org.apache.parquet.io.ParquetDecodingException;

/**
 * 文件级说明：按字节流读取 Parquet 值的 {@link ValuesReader} 实现。
 *
 * <p>所属模块：iceberg-parquet（读取侧，直接从字节流切取定长字节作为值）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从底层 {@link ByteBufferInputStream} 按需切取定长字节，返回 int/long/float/double/boolean。
 *   <li>支持按位读取 boolean（一个字节打包 8 个 boolean）。
 *   <li>提供 readBooleanAsInt 返回 0/1 整数，便于向量化处理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>直接切字节：不做解码转换，直接 slice 字节流以小端序返回原始值，性能高。
 *   <li>跳过不支持：skip() 抛 UnsupportedOperationException，调用方需自行处理跳过。
 * </ul>
 *
 * <p>上下游关系：被 Parquet 页迭代器在读取非字典编码页时使用；依赖 parquet-mr {@link ByteBufferInputStream}。
 */
public class ValuesAsBytesReader extends ValuesReader {
  private ByteBufferInputStream valuesInputStream = null;
  // 仅用于 boolean 的位偏移
  private int bitOffset;
  private byte currentByte = 0;

  public ValuesAsBytesReader() {}

  /**
   * 从数据页初始化字节流。
   *
   * @param valueCount 值数量（未使用）
   * @param in 数据页字节流
   */
  @Override
  public void initFromPage(int valueCount, ByteBufferInputStream in) {
    this.valuesInputStream = in;
  }

  /** 不支持跳过操作。 */
  @Override
  public void skip() {
    throw new UnsupportedOperationException();
  }

  /**
   * 从字节流切取指定长度的 ByteBuffer（小端序）。
   *
   * @param length 字节数
   * @return 切取的小端序 ByteBuffer
   * @throws ParquetDecodingException 读取失败
   */
  public ByteBuffer getBuffer(int length) {
    try {
      return valuesInputStream.slice(length).order(ByteOrder.LITTLE_ENDIAN);
    } catch (IOException e) {
      throw new ParquetDecodingException("Failed to read " + length + " bytes", e);
    }
  }

  /** 读取 4 字节小端 int。 */
  @Override
  public final int readInteger() {
    return getBuffer(4).getInt();
  }

  /** 读取 8 字节小端 long。 */
  @Override
  public final long readLong() {
    return getBuffer(8).getLong();
  }

  /** 读取 4 字节小端 float。 */
  @Override
  public final float readFloat() {
    return getBuffer(4).getFloat();
  }

  /** 读取 8 字节小端 double。 */
  @Override
  public final double readDouble() {
    return getBuffer(8).getDouble();
  }

  /**
   * 按位读取 boolean。
   *
   * <p>逻辑：每 8 个 boolean 共用一个字节，bitOffset 记录当前位位置； bitOffset 归零时读取下一字节，取对应位返回。
   *
   * @return boolean 值
   */
  @Override
  public final boolean readBoolean() {
    if (bitOffset == 0) {
      currentByte = getByte();
    }

    boolean value = (currentByte & (1 << bitOffset)) != 0;
    bitOffset += 1;
    if (bitOffset == 8) {
      bitOffset = 0;
    }
    return value;
  }

  /**
   * 按位读取 boolean 并返回 0/1 整数。
   *
   * <p>逻辑：与 {@link #readBoolean} 相同的位操作，但返回 int 0 或 1，便于向量化批处理。
   *
   * @return 1 表示 true，0 表示 false
   */
  public final int readBooleanAsInt() {
    if (bitOffset == 0) {
      currentByte = getByte();
    }
    int value = (currentByte & (1 << bitOffset)) >> bitOffset;
    bitOffset += 1;
    if (bitOffset == 8) {
      bitOffset = 0;
    }
    return value;
  }

  /**
   * 从字节流读取单个字节。
   *
   * @return 字节值
   * @throws ParquetDecodingException 读取失败
   */
  private byte getByte() {
    try {
      return (byte) valuesInputStream.read();
    } catch (IOException e) {
      throw new ParquetDecodingException("Failed to read a byte", e);
    }
  }
}
