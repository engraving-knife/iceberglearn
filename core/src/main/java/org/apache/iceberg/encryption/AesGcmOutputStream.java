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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：基于 AES-GCM 的分块加密输出流。
 *
 * <p>所属模块：iceberg-core（加密包），继承 {@link PositionOutputStream}，把写入的明文字节 按 1MB 分块用 GCM 加密后写入底层输出流。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>首次写入时输出流头（magic "AGS1" + 明文块大小）。
 *   <li>把明文累积到 {@link Ciphers#PLAIN_BLOCK_SIZE} 后整块加密写出；不足整块的尾部块在 {@link #close()} 时作为最后一块写出。
 *   <li>每块绑定文件 AAD 前缀与块序号，保证完整性与抗块重排。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>流头预构建：HEADER_BYTES 在类加载期一次性构造（小端序 magic + 块大小），避免每次写流重复构造。
 *   <li>最后一块标记：一旦写出不足整块的部分，置 {@code lastBlockWritten} 防止再写更多块， 因为解密侧依赖“最后一块可短”的几何约定。
 *   <li>顺序写入：不支持回写/定位写，加密流只能从头到尾顺序产生。
 * </ul>
 *
 * <p>上下游关系：由 {@link AesGcmOutputFile#create()} 创建；底层依赖一个接收密文字节的 {@link PositionOutputStream}，并使用
 * {@link Ciphers.AesGcmEncryptor} 执行加密。
 */
public class AesGcmOutputStream extends PositionOutputStream {

  private static final byte[] HEADER_BYTES =
      ByteBuffer.allocate(Ciphers.GCM_STREAM_HEADER_LENGTH)
          .order(ByteOrder.LITTLE_ENDIAN)
          .put(Ciphers.GCM_STREAM_MAGIC_ARRAY)
          .putInt(Ciphers.PLAIN_BLOCK_SIZE)
          .array();

  private final Ciphers.AesGcmEncryptor gcmEncryptor;
  private final PositionOutputStream targetStream;
  private final byte[] fileAadPrefix;
  private final byte[] singleByte;
  private final byte[] plainBlock;
  private final byte[] cipherBlock;

  private int positionInPlainBlock;
  private int currentBlockIndex;
  private boolean isHeaderWritten;
  private boolean lastBlockWritten;

  /**
   * 构造加密输出流。
   *
   * @param targetStream 接收密文字节的底层输出流
   * @param aesKey 数据密钥（DEK）
   * @param fileAadPrefix 文件级 AAD 前缀
   */
  AesGcmOutputStream(PositionOutputStream targetStream, byte[] aesKey, byte[] fileAadPrefix) {
    this.targetStream = targetStream;
    this.gcmEncryptor = new Ciphers.AesGcmEncryptor(aesKey);
    this.fileAadPrefix = fileAadPrefix;
    this.singleByte = new byte[1];
    this.plainBlock = new byte[Ciphers.PLAIN_BLOCK_SIZE];
    this.cipherBlock = new byte[Ciphers.CIPHER_BLOCK_SIZE];
    this.positionInPlainBlock = 0;
    this.currentBlockIndex = 0;
    this.isHeaderWritten = false;
    this.lastBlockWritten = false;
  }

  /** 写入单个字节（委托给 {@link #write(byte[], int, int)}）。 */
  @Override
  public void write(int b) throws IOException {
    singleByte[0] = (byte) (b & 0x000000FF);
    write(singleByte);
  }

  /**
   * 写入明文字节，按需分块加密后写出。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>尚未写过流头时先写头。
   *   <li>校验缓冲区可用字节足够。
   *   <li>循环把数据拷入当前明文块，块满（== {@link Ciphers#PLAIN_BLOCK_SIZE}）则调用 {@link #encryptAndWriteBlock()}
   *       加密写出。
   * </ol>
   *
   * @param b 明文数据
   * @param off 起始偏移
   * @param len 写入长度
   * @throws IOException 若缓冲区可用字节不足
   */
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (!isHeaderWritten) {
      writeHeader();
    }

    if (b.length - off < len) {
      throw new IOException(
          "Insufficient bytes in buffer: " + b.length + " - " + off + " < " + len);
    }

    int remaining = len;
    int offset = off;

    while (remaining > 0) {
      int freeBlockBytes = plainBlock.length - positionInPlainBlock;
      int toWrite = Math.min(freeBlockBytes, remaining);

      System.arraycopy(b, offset, plainBlock, positionInPlainBlock, toWrite);
      positionInPlainBlock += toWrite;
      offset += toWrite;
      remaining -= toWrite;

      if (positionInPlainBlock == plainBlock.length) {
        encryptAndWriteBlock();
      }
    }
  }

  /** 返回当前明文写入位置（块索引 × 明文块大小 + 块内偏移）。 */
  @Override
  public long getPos() throws IOException {
    return (long) currentBlockIndex * Ciphers.PLAIN_BLOCK_SIZE + positionInPlainBlock;
  }

  /** 刷新底层输出流。 */
  @Override
  public void flush() throws IOException {
    targetStream.flush();
  }

  /**
   * 关闭流，保证写出最后的尾部块。
   *
   * <p>逻辑：尚未写头时先写头（空流也产出合法流头 + 一个空最后块），再调用 {@link #encryptAndWriteBlock()}
   * 把当前累积的明文（可能不足整块）作为最后一块写出， 最后关闭底层流。
   */
  @Override
  public void close() throws IOException {
    if (!isHeaderWritten) {
      writeHeader();
    }

    encryptAndWriteBlock();

    targetStream.close();
  }

  /** 写出预构建的流头并标记已写头。 */
  private void writeHeader() throws IOException {
    targetStream.write(HEADER_BYTES);
    isHeaderWritten = true;
  }

  /**
   * 加密当前累积的明文块并写出。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验未已写最后块（已写最后块后不允许再写）。
   *   <li>块序号溢出 Integer.MAX_VALUE 抛异常。
   *   <li>非首块且无累积字节则直接返回（避免空块）。
   *   <li>累积字节不足整块时标记 {@code lastBlockWritten}，后续禁止再写。
   *   <li>构造块 AAD（文件前缀 + 块序号）加密后写出密文，重置块内游标并递增块序号。
   * </ol>
   *
   * @throws IllegalStateException 若已写过最后块
   * @throws IOException 若块序号超过 Integer.MAX_VALUE
   */
  private void encryptAndWriteBlock() throws IOException {
    Preconditions.checkState(
        !lastBlockWritten, "Cannot encrypt block: a partial block has already been written");

    if (currentBlockIndex == Integer.MAX_VALUE) {
      throw new IOException("Cannot write block: exceeded Integer.MAX_VALUE blocks");
    }

    if (positionInPlainBlock == 0 && currentBlockIndex != 0) {
      return;
    }

    if (positionInPlainBlock != plainBlock.length) {
      // signal that a partial block has been written and must be the last
      this.lastBlockWritten = true;
    }

    byte[] aad = Ciphers.streamBlockAAD(fileAadPrefix, currentBlockIndex);
    int ciphertextLength =
        gcmEncryptor.encrypt(plainBlock, 0, positionInPlainBlock, cipherBlock, 0, aad);
    targetStream.write(cipherBlock, 0, ciphertextLength);
    positionInPlainBlock = 0;
    currentBlockIndex++;
  }
}
