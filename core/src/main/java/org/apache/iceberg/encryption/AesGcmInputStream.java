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

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.iceberg.io.IOUtil;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：基于 AES-GCM 的分块解密输入流。
 *
 * <p>所属模块：iceberg-core（加密包），继承 {@link SeekableInputStream}，把底层密文字节流
 * 透明地解密为明文字节流，支持随机定位（seek）与跳过（skip）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按需解密：仅当读取到某块时才解密该块，未读块不解密，支持随机访问。
 *   <li>流头校验：读取并校验 GCM 流的 magic 与明文块大小，防止读取非本格式数据。
 *   <li>明文长度推算：由密文总长减去头部、各块 nonce 与 GCM 标签开销，算出明文总长。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>分块独立加密：明文按 {@link Ciphers#PLAIN_BLOCK_SIZE}（1MB）切块，每块独立用 GCM 加密， 输出块布局为 [nonce(12B) | 密文 |
 *       GCM 标签(16B)]。这样可对任意块随机定位解密， 避免整文件一次性解密的开销。
 *   <li>每块 AAD 绑定块序号：见 {@link Ciphers#streamBlockAAD(byte[], int)}，把文件 AAD 前缀 与块序号拼成
 *       AAD，防止块重排/替换攻击。
 *   <li>当前块缓存：只缓存“当前明文块”，顺序读时复用，跨块读时按需 seek 并重解密下一块。
 * </ul>
 *
 * <p>上下游关系：由 {@link AesGcmInputFile#newStream()} 创建；底层依赖一个返回密文字节的 {@link SeekableInputStream}，并使用
 * {@link Ciphers.AesGcmDecryptor} 执行解密。
 */
public class AesGcmInputStream extends SeekableInputStream {
  private final SeekableInputStream sourceStream;
  private final byte[] fileAADPrefix;
  private final Ciphers.AesGcmDecryptor decryptor;
  private final byte[] cipherBlockBuffer;
  private final byte[] currentPlainBlock;
  private final long numBlocks;
  private final int lastCipherBlockSize;
  private final long plainStreamSize;
  private final byte[] singleByte;

  private long plainStreamPosition;
  private long currentPlainBlockIndex;
  private int currentPlainBlockSize;

  /**
   * 构造解密输入流并完成块的几何计算。
   *
   * <p>逻辑：扣除流头后，按 {@link Ciphers#CIPHER_BLOCK_SIZE} 计算完整块数量与最后一块的
   * 密文长度（最后一块可为不足整块的尾部块），再据此推算明文总长。此时不读取任何数据， 真正解密延迟到首次 {@link #read(byte[], int, int)} 时进行。
   *
   * @param sourceStream 返回密文字节的可定位输入流
   * @param sourceLength 密文总长度（含流头）
   * @param aesKey 数据密钥（DEK）
   * @param fileAADPrefix 文件级 AAD 前缀
   */
  AesGcmInputStream(
      SeekableInputStream sourceStream, long sourceLength, byte[] aesKey, byte[] fileAADPrefix) {
    this.sourceStream = sourceStream;
    this.fileAADPrefix = fileAADPrefix;
    this.decryptor = new Ciphers.AesGcmDecryptor(aesKey);
    this.cipherBlockBuffer = new byte[Ciphers.CIPHER_BLOCK_SIZE];
    this.currentPlainBlock = new byte[Ciphers.PLAIN_BLOCK_SIZE];
    this.plainStreamPosition = 0;
    this.currentPlainBlockIndex = -1;
    this.currentPlainBlockSize = 0;

    long streamLength = sourceLength - Ciphers.GCM_STREAM_HEADER_LENGTH;
    long numFullBlocks = Math.toIntExact(streamLength / Ciphers.CIPHER_BLOCK_SIZE);
    long cipherFullBlockLength = numFullBlocks * Ciphers.CIPHER_BLOCK_SIZE;
    int cipherBytesInLastBlock = Math.toIntExact(streamLength - cipherFullBlockLength);
    boolean fullBlocksOnly = (0 == cipherBytesInLastBlock);
    this.numBlocks = fullBlocksOnly ? numFullBlocks : numFullBlocks + 1;
    this.lastCipherBlockSize =
        fullBlocksOnly ? Ciphers.CIPHER_BLOCK_SIZE : cipherBytesInLastBlock; // never 0

    long lastPlainBlockSize =
        (long) lastCipherBlockSize - Ciphers.NONCE_LENGTH - Ciphers.GCM_TAG_LENGTH;
    this.plainStreamSize =
        numFullBlocks * Ciphers.PLAIN_BLOCK_SIZE + (fullBlocksOnly ? 0 : lastPlainBlockSize);
    this.singleByte = new byte[1];
  }

  /**
   * 读取并校验 GCM 流头部（magic + 明文块大小）。
   *
   * <p>逻辑：从源流读取 {@link Ciphers#GCM_STREAM_HEADER_LENGTH} 字节，校验前 4 字节为 "AGS1" magic、后 4 字节（小端序）等于
   * {@link Ciphers#PLAIN_BLOCK_SIZE}。
   *
   * @throws IllegalStateException 若 magic 或块大小不匹配
   */
  private void validateHeader() throws IOException {
    byte[] headerBytes = new byte[Ciphers.GCM_STREAM_HEADER_LENGTH];
    IOUtil.readFully(sourceStream, headerBytes, 0, headerBytes.length);

    Preconditions.checkState(
        Ciphers.GCM_STREAM_MAGIC.equals(ByteBuffer.wrap(headerBytes, 0, 4)),
        "Invalid GCM stream: magic does not match AGS1");

    int plainBlockSize = ByteBuffer.wrap(headerBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    Preconditions.checkState(
        plainBlockSize == Ciphers.PLAIN_BLOCK_SIZE,
        "Invalid GCM stream: block size %d != %d",
        plainBlockSize,
        Ciphers.PLAIN_BLOCK_SIZE);
  }

  /** 返回当前明文流中尚未读取的字节数（受 int 上限约束）。 */
  @Override
  public int available() {
    long maxAvailable = plainStreamSize - plainStreamPosition;
    // See InputStream.available contract
    if (maxAvailable >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return (int) maxAvailable;
    }
  }

  /**
   * 返回当前缓存块中尚未被读取的字节数。
   *
   * <p>若当前明文位置不在缓存块内（块索引不匹配），返回 0，调用方据此触发下一块解密。
   */
  private int availableInCurrentBlock() {
    if (blockIndex(plainStreamPosition) != currentPlainBlockIndex) {
      return 0;
    }

    return currentPlainBlockSize - offsetInBlock(plainStreamPosition);
  }

  /**
   * 读取明文字节到缓冲区。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若尚未解密任何块，先解密第 0 块。
   *   <li>若无可读字节且请求长度大于 0，抛 {@link EOFException}；长度为 0 直接返回 0。
   *   <li>循环填充调用方缓冲区：当前块有可用字节则拷贝并推进明文位置；当前块耗尽但流未到尾 则解密下一块；流到尾则跳出。
   *   <li>读取到字节返回读取数，否则返回 -1 表示 EOF。
   * </ol>
   *
   * @param b 目标缓冲区
   * @param off 缓冲区起始偏移
   * @param len 期望读取的字节数（必须 &gt;= 0）
   * @return 实际读取字节数，到 EOF 返回 -1
   * @throws EOFException 当无可用字节且 len &gt; 0
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Preconditions.checkArgument(len >= 0, "Invalid read length: " + len);

    if (currentPlainBlockIndex < 0) {
      decryptBlock(0);
    }

    if (available() <= 0 && len > 0) {
      throw new EOFException();
    }

    if (len == 0) {
      return 0;
    }

    int totalBytesRead = 0;
    int resultBufferOffset = off;
    int remainingBytesToRead = len;

    while (remainingBytesToRead > 0) {
      int availableInBlock = availableInCurrentBlock();
      if (availableInBlock > 0) {
        int bytesToCopy = Math.min(availableInBlock, remainingBytesToRead);
        int offsetInBlock = offsetInBlock(plainStreamPosition);
        System.arraycopy(currentPlainBlock, offsetInBlock, b, resultBufferOffset, bytesToCopy);
        totalBytesRead += bytesToCopy;
        remainingBytesToRead -= bytesToCopy;
        resultBufferOffset += bytesToCopy;
        this.plainStreamPosition += bytesToCopy;
      } else if (available() > 0) {
        decryptBlock(blockIndex(plainStreamPosition));

      } else {
        break;
      }
    }

    // return -1 for EOF
    return totalBytesRead > 0 ? totalBytesRead : -1;
  }

  /**
   * 将明文读取位置定位到 newPos。
   *
   * <p>逻辑：仅校验并更新 {@code plainStreamPosition}，不立即解密；真正解密发生在后续读取时。
   *
   * @param newPos 新的明文位置
   * @throws IOException 若 newPos 为负
   * @throws EOFException 若 newPos 超过明文流总长
   */
  @Override
  public void seek(long newPos) throws IOException {
    if (newPos < 0) {
      throw new IOException("Invalid position: " + newPos);
    } else if (newPos > plainStreamSize) {
      throw new EOFException(
          "Invalid position: " + newPos + " > stream length, " + plainStreamSize);
    }

    this.plainStreamPosition = newPos;
  }

  /**
   * 跳过 n 个明文字节。
   *
   * <p>逻辑：仅推进明文位置指针，不解密被跳过的块，从而高效跳过。跳过字节不得超过流尾。
   *
   * @return 实际跳过的字节数
   */
  @Override
  public long skip(long n) {
    if (n <= 0) {
      return 0;
    }

    long bytesLeftInStream = plainStreamSize - plainStreamPosition;
    if (n > bytesLeftInStream) {
      // skip the rest of the stream
      this.plainStreamPosition = plainStreamSize;
      return bytesLeftInStream;
    }

    this.plainStreamPosition += n;

    return n;
  }

  /** 返回当前明文读取位置。 */
  @Override
  public long getPos() throws IOException {
    return plainStreamPosition;
  }

  /** 读取单个明文字节，到 EOF 返回 -1。 */
  @Override
  public int read() throws IOException {
    int read = read(singleByte);
    if (read == -1) {
      return -1;
    }

    return singleByte[0] >= 0 ? singleByte[0] : 256 + singleByte[0];
  }

  /** 关闭底层源流。 */
  @Override
  public void close() throws IOException {
    sourceStream.close();
  }

  /**
   * 解密指定块索引对应的密文块到 {@code currentPlainBlock} 并更新缓存状态。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若已是当前块则直接返回（去重）。
   *   <li>计算该块在密文流中的字节偏移；若源流尚未定位到该处，则按需 seek；若源流仍在位置 0 （尚未读过头），先调用 {@link #validateHeader()} 校验流头。
   *   <li>从源流读取整块或最后一块的密文，构造块 AAD（文件前缀 + 块序号）后解密。
   *   <li>更新当前块索引与当前块明文长度。
   * </ol>
   *
   * @param blockIndex 要解密的块序号
   */
  private void decryptBlock(long blockIndex) throws IOException {
    if (blockIndex == currentPlainBlockIndex) {
      return;
    }

    long blockPositionInStream = blockOffset(blockIndex);
    if (sourceStream.getPos() != blockPositionInStream) {
      if (sourceStream.getPos() == 0) {
        validateHeader();
      }

      sourceStream.seek(blockPositionInStream);
    }

    boolean isLastBlock = blockIndex == numBlocks - 1;
    int cipherBlockSize = isLastBlock ? lastCipherBlockSize : Ciphers.CIPHER_BLOCK_SIZE;
    IOUtil.readFully(sourceStream, cipherBlockBuffer, 0, cipherBlockSize);

    byte[] blockAAD = Ciphers.streamBlockAAD(fileAADPrefix, Math.toIntExact(blockIndex));
    decryptor.decrypt(cipherBlockBuffer, 0, cipherBlockSize, currentPlainBlock, 0, blockAAD);
    this.currentPlainBlockSize = cipherBlockSize - Ciphers.NONCE_LENGTH - Ciphers.GCM_TAG_LENGTH;
    this.currentPlainBlockIndex = blockIndex;
  }

  /** 由明文位置计算所属块索引。 */
  private static long blockIndex(long plainPosition) {
    return plainPosition / Ciphers.PLAIN_BLOCK_SIZE;
  }

  /** 由明文位置计算在块内的偏移。 */
  private static int offsetInBlock(long plainPosition) {
    return Math.toIntExact(plainPosition % Ciphers.PLAIN_BLOCK_SIZE);
  }

  /** 由块索引计算该块在密文流中的字节偏移（含流头长度）。 */
  private static long blockOffset(long blockIndex) {
    return blockIndex * Ciphers.CIPHER_BLOCK_SIZE + Ciphers.GCM_STREAM_HEADER_LENGTH;
  }

  /**
   * 由密文总长反推明文长度。
   *
   * <p>逻辑：扣除流头后按块几何计算——完整块数 × 明文块大小，加上最后一块的明文长度 （最后一块密文减去 nonce 与 GCM 标签开销）；纯完整块时尾部明文为 0。
   *
   * @param sourceLength 密文总长度（含流头）
   * @return 明文总长度
   */
  static long calculatePlaintextLength(long sourceLength) {
    long streamLength = sourceLength - Ciphers.GCM_STREAM_HEADER_LENGTH;

    if (streamLength == 0) {
      return 0;
    }

    long numberOfFullBlocks = streamLength / Ciphers.CIPHER_BLOCK_SIZE;
    long fullBlockSize = numberOfFullBlocks * Ciphers.CIPHER_BLOCK_SIZE;
    long cipherBytesInLastBlock = streamLength - fullBlockSize;
    boolean fullBlocksOnly = (0 == cipherBytesInLastBlock);
    long plainBytesInLastBlock =
        fullBlocksOnly
            ? 0
            : (cipherBytesInLastBlock - Ciphers.NONCE_LENGTH - Ciphers.GCM_TAG_LENGTH);

    return (numberOfFullBlocks * Ciphers.PLAIN_BLOCK_SIZE) + plainBytesInLastBlock;
  }
}
