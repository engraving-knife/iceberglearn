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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * AES-GCM 加解密原语集合：提供分块流式加解密所依赖的底层 Cipher 封装、流格式常量与 AAD 构造工具。
 *
 * <p>所属模块：iceberg-core 的 encryption 包（文件级加密能力的底层实现层，介于 IO 层与 {@link
 * org.apache.iceberg.encryption.EncryptionManager} 之间）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 GCM 流的物理格式常量：明文块大小、Nonce 长度、GCM Tag 长度、文件头魔数 {@code AGS1} 等。
 *   <li>提供 {@link AesGcmEncryptor} / {@link AesGcmDecryptor} 两个对称原语，封装 JCE {@link Cipher} 并把异常转换为
 *       RuntimeException。
 *   <li>为每个流式块构造附加认证数据（AAD），保证块序号与文件级 AAD 前缀共同参与认证，防止块替换/重排攻击。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用“分块 GCM”而非整文件 GCM：单块 1MB 明文，避免大文件一次性载入内存，同时每块独立生成 随机 Nonce，配合文件级 AAD 前缀 + 块序号共同作为
 *       AAD，做到既能流式读写又能逐块校验完整性。
 *   <li>Nonce 随机生成：GCM 安全性强烈依赖 Nonce 不重复，使用 {@link SecureRandom} 生成 12 字节随机 Nonce，并随密文一起存储（密文前 12
 *       字节即 Nonce）。
 *   <li>密钥长度仅允许 16/24/32 字节（AES-128/192/256），在构造期校验，避免运行期失败。
 * </ul>
 *
 * <p>上下游关系：被 {@link AesGcmInputStream}/{@link AesGcmOutputStream} 直接调用完成流式加解密； 被 {@link
 * AesGcmInputFile}/{@link AesGcmOutputFile} 间接使用。属于 encryption 包的最底层。
 */
public class Ciphers {
  /** 单个明文块的字节大小：1MB。流式加密以该大小切块。 */
  public static final int PLAIN_BLOCK_SIZE = 1024 * 1024;
  /** GCM Nonce（初始化向量）长度：12 字节，GCM 规范推荐值。 */
  public static final int NONCE_LENGTH = 12;
  /** GCM 认证 Tag 长度：16 字节（128 位），提供完整性校验。 */
  public static final int GCM_TAG_LENGTH = 16;
  /** 单个密文块的字节大小：明文块 + Nonce + GCM Tag。 */
  public static final int CIPHER_BLOCK_SIZE = PLAIN_BLOCK_SIZE + NONCE_LENGTH + GCM_TAG_LENGTH;
  /** GCM 流文件头魔数字符串，用于识别 Iceberg GCM 流格式。 */
  public static final String GCM_STREAM_MAGIC_STRING = "AGS1";

  static final byte[] GCM_STREAM_MAGIC_ARRAY =
      GCM_STREAM_MAGIC_STRING.getBytes(StandardCharsets.UTF_8);
  static final ByteBuffer GCM_STREAM_MAGIC =
      ByteBuffer.wrap(GCM_STREAM_MAGIC_ARRAY).asReadOnlyBuffer();
  static final int GCM_STREAM_HEADER_LENGTH =
      GCM_STREAM_MAGIC_ARRAY.length + 4; // magic_len + block_size_len

  private static final int GCM_TAG_LENGTH_BITS = 8 * GCM_TAG_LENGTH;

  /** GCM 流的最小合法字节数：文件头 + 至少一个块（Nonce + Tag，明文可为 0 字节）。 */
  static final int MIN_STREAM_LENGTH = GCM_STREAM_HEADER_LENGTH + NONCE_LENGTH + GCM_TAG_LENGTH;

  private Ciphers() {}

  /**
   * AES-GCM 加密器：线程不安全，每个使用方应持有独立实例（流对象内部各自创建）。
   *
   * <p>每个加密块随机生成 Nonce，输出布局为 {@code [12 字节 Nonce | 密文 | 16 字节 GCM Tag]}。
   */
  public static class AesGcmEncryptor {
    private final SecretKeySpec aesKey;
    private final Cipher cipher;
    private final SecureRandom randomGenerator;
    private final byte[] nonce;

    /**
     * 构造加密器。
     *
     * @param keyBytes 原始密钥字节，长度必须为 16/24/32 之一
     */
    public AesGcmEncryptor(byte[] keyBytes) {
      this.aesKey = newKey(keyBytes);
      this.cipher = newCipher();

      this.randomGenerator = new SecureRandom();
      this.nonce = new byte[NONCE_LENGTH];
    }

    /**
     * 加密整段明文，返回包含 Nonce + 密文 + Tag 的新数组。
     *
     * @param plaintext 明文
     * @param aad 附加认证数据，可为 null
     * @return 密文字节数组
     */
    public byte[] encrypt(byte[] plaintext, byte[] aad) {
      return encrypt(plaintext, 0, plaintext.length, aad);
    }

    /**
     * 加密明文的一段区间，返回包含 Nonce + 密文 + Tag 的新数组。
     *
     * @param plaintext 明文所在数组
     * @param plaintextOffset 明文起始偏移
     * @param plaintextLength 明文长度
     * @param aad 附加认证数据，可为 null
     * @return 密文字节数组
     */
    public byte[] encrypt(byte[] plaintext, int plaintextOffset, int plaintextLength, byte[] aad) {
      int cipherTextLength = NONCE_LENGTH + plaintextLength + GCM_TAG_LENGTH;
      byte[] cipherText = new byte[cipherTextLength];
      encrypt(plaintext, plaintextOffset, plaintextLength, cipherText, 0, aad);
      return cipherText;
    }

    /**
     * 加密明文的一段区间，写入调用方提供的密文缓冲区。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验 plaintextLength 非负。
     *   <li>用 {@link SecureRandom} 填充本块 Nonce。
     *   <li>以 {@code AES/GCM/NoPadding} 初始化加密 Cipher，并设置 GCMParameterSpec（Tag 位数 + Nonce）。
     *   <li>若 aad 非空则调用 {@link Cipher#updateAAD(byte[])} 注入附加认证数据。
     *   <li>调用 {@code doFinal} 把密文 + Tag 写入缓冲区（跳过前 NONCE_LENGTH 字节留给 Nonce）。
     *   <li>校验输出字节数等于 plaintextLength + GCM_TAG_LENGTH，否则抛 RuntimeException。
     *   <li>把 Nonce 拷贝到密文缓冲区开头。
     * </ol>
     *
     * <p>输出密文布局：{@code [Nonce(12) | 密文 | Tag(16)]}， 总长度 = NONCE_LENGTH + plaintextLength +
     * GCM_TAG_LENGTH。
     *
     * @param plaintext 明文所在数组
     * @param plaintextOffset 明文起始偏移
     * @param plaintextLength 明文长度
     * @param ciphertextBuffer 调用方提供的密文缓冲区，容量至少为 NONCE_LENGTH + plaintextLength + GCM_TAG_LENGTH
     * @param ciphertextOffset 密文写入起始偏移
     * @param aad 附加认证数据，可为 null
     * @return 实际写入的字节数（含 Nonce + 密文 + Tag）
     */
    public int encrypt(
        byte[] plaintext,
        int plaintextOffset,
        int plaintextLength,
        byte[] ciphertextBuffer,
        int ciphertextOffset,
        byte[] aad) {
      Preconditions.checkArgument(
          plaintextLength >= 0, "Invalid plain text length: %s", plaintextLength);
      randomGenerator.nextBytes(nonce);
      int enciphered;

      try {
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        if (null != aad) {
          cipher.updateAAD(aad);
        }

        // doFinal encrypts and adds a GCM tag. The nonce is added later.
        enciphered =
            cipher.doFinal(
                plaintext,
                plaintextOffset,
                plaintextLength,
                ciphertextBuffer,
                ciphertextOffset + NONCE_LENGTH);

        if (enciphered != plaintextLength + GCM_TAG_LENGTH) {
          throw new RuntimeException(
              "Failed to encrypt block: expected "
                  + plaintextLength
                  + GCM_TAG_LENGTH
                  + " encrypted bytes but produced bytes "
                  + enciphered);
        }
      } catch (GeneralSecurityException e) {
        throw new RuntimeException("Failed to encrypt", e);
      }

      // Add the nonce
      System.arraycopy(nonce, 0, ciphertextBuffer, ciphertextOffset, NONCE_LENGTH);

      return enciphered + NONCE_LENGTH;
    }
  }

  /**
   * AES-GCM 解密器：线程不安全，每个使用方应持有独立实例。
   *
   * <p>输入密文布局须为 {@code [12 字节 Nonce | 密文 | 16 字节 GCM Tag]}，与 {@link AesGcmEncryptor} 输出对应。
   */
  public static class AesGcmDecryptor {
    private final SecretKeySpec aesKey;
    private final Cipher cipher;

    /**
     * 构造解密器。
     *
     * @param keyBytes 原始密钥字节，长度必须为 16/24/32 之一，且须与加密密钥一致
     */
    public AesGcmDecryptor(byte[] keyBytes) {
      this.aesKey = newKey(keyBytes);
      this.cipher = newCipher();
    }

    /**
     * 解密整段密文，返回明文数组。
     *
     * @param ciphertext 密文（含 Nonce 与 Tag）
     * @param aad 附加认证数据，须与加密时一致
     * @return 明文字节数组
     */
    public byte[] decrypt(byte[] ciphertext, byte[] aad) {
      return decrypt(ciphertext, 0, ciphertext.length, aad);
    }

    /**
     * 解密密文的一段区间，返回明文数组。
     *
     * @param ciphertext 密文所在数组
     * @param ciphertextOffset 密文起始偏移
     * @param ciphertextLength 密文长度（含 Nonce 与 Tag）
     * @param aad 附加认证数据，须与加密时一致
     * @return 明文字节数组
     */
    public byte[] decrypt(
        byte[] ciphertext, int ciphertextOffset, int ciphertextLength, byte[] aad) {
      int plaintextLength = ciphertextLength - GCM_TAG_LENGTH - NONCE_LENGTH;
      byte[] plaintext = new byte[plaintextLength];
      decrypt(ciphertext, ciphertextOffset, ciphertextLength, plaintext, 0, aad);
      return plaintext;
    }

    /**
     * 解密密文的一段区间，写入调用方提供的明文缓冲区。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验密文长度不小于 NONCE_LENGTH + GCM_TAG_LENGTH，否则视为非 GCM 加密数据并抛异常。
     *   <li>从密文头部读取 12 字节 Nonce，构造 GCMParameterSpec。
     *   <li>以 {@code AES/GCM/NoPadding} 初始化解密 Cipher，注入 aad（若非空）。
     *   <li>调用 {@code doFinal} 对密文（跳过 Nonce 部分）解密并校验 Tag，明文写入 plaintextBuffer。
     *   <li>{@link AEADBadTagException} 单独处理：通常意味着密钥错误或数据被篡改，GCM 无法区分二者。
     * </ol>
     *
     * @param ciphertext 密文所在数组
     * @param ciphertextOffset 密文起始偏移
     * @param ciphertextLength 密文长度（含 Nonce 与 Tag）
     * @param plaintextBuffer 调用方提供的明文缓冲区，容量至少为 ciphertextLength - NONCE_LENGTH - GCM_TAG_LENGTH
     * @param plaintextOffset 明文写入起始偏移
     * @param aad 附加认证数据，须与加密时一致
     * @return 实际解出的明文字节数
     */
    public int decrypt(
        byte[] ciphertext,
        int ciphertextOffset,
        int ciphertextLength,
        byte[] plaintextBuffer,
        int plaintextOffset,
        byte[] aad) {
      Preconditions.checkState(
          ciphertextLength - GCM_TAG_LENGTH - NONCE_LENGTH >= 0,
          "Cannot decrypt cipher text of length "
              + ciphertext.length
              + " because text must longer than GCM_TAG_LENGTH + NONCE_LENGTH bytes. Text may not be encrypted"
              + " with AES GCM cipher");
      int plaintextLength;

      try {
        GCMParameterSpec spec =
            new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ciphertext, ciphertextOffset, NONCE_LENGTH);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        if (null != aad) {
          cipher.updateAAD(aad);
        }
        // For java Cipher, the nonce is not part of ciphertext
        plaintextLength =
            cipher.doFinal(
                ciphertext,
                ciphertextOffset + NONCE_LENGTH,
                ciphertextLength - NONCE_LENGTH,
                plaintextBuffer,
                plaintextOffset);
      } catch (AEADBadTagException e) {
        throw new RuntimeException(
            "GCM tag check failed. Possible reasons: wrong decryption key; or corrupt/tampered"
                + " data. AES GCM doesn't differentiate between these two.",
            e);
      } catch (GeneralSecurityException e) {
        throw new RuntimeException("Failed to decrypt", e);
      }

      return plaintextLength;
    }
  }

  /**
   * 根据原始字节构造 AES 密钥，并校验长度合法性。
   *
   * @param keyBytes 原始密钥字节
   * @return {@link SecretKeySpec}，算法为 AES
   */
  private static SecretKeySpec newKey(byte[] keyBytes) {
    Preconditions.checkArgument(keyBytes != null, "Invalid key: null");
    int keyLength = keyBytes.length;
    Preconditions.checkArgument(
        (keyLength == 16 || keyLength == 24 || keyLength == 32),
        "Invalid key length: %s (must be 16, 24, or 32 bytes)",
        keyLength);
    return new SecretKeySpec(keyBytes, "AES");
  }

  /**
   * 创建 AES/GCM/NoPadding 模式的 Cipher 实例。
   *
   * @return 已配置算法的 {@link Cipher}，尚未初始化
   */
  private static Cipher newCipher() {
    try {
      return Cipher.getInstance("AES/GCM/NoPadding");
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Failed to create GCM cipher", e);
    }
  }

  /**
   * 为某个流式块构造附加认证数据（AAD）。
   *
   * <p>逻辑：把块序号按小端序编码为 4 字节；若 fileAadPrefix 非空，则在其后拼接这 4 字节， 形成最终 AAD = {@code [fileAadPrefix |
   * blockIndex(4, little-endian)]}；否则 AAD 仅为块序号。
   *
   * <p>设计意图：将文件级 AAD 前缀与块序号一同纳入认证，使得攻击者无法在文件内重排或跨文件替换块—— 即使两个块使用相同 Nonce 和密钥，块序号不同也会导致 GCM Tag
   * 校验失败。
   *
   * @param fileAadPrefix 文件级 AAD 前缀，可为 null
   * @param currentBlockIndex 当前块在流中的序号（从 0 起）
   * @return 本块使用的 AAD 字节数组
   */
  static byte[] streamBlockAAD(byte[] fileAadPrefix, int currentBlockIndex) {
    byte[] blockAAD =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(currentBlockIndex).array();

    if (null == fileAadPrefix) {
      return blockAAD;
    } else {
      byte[] aad = new byte[fileAadPrefix.length + 4];
      System.arraycopy(fileAadPrefix, 0, aad, 0, fileAadPrefix.length);
      System.arraycopy(blockAAD, 0, aad, fileAadPrefix.length, 4);
      return aad;
    }
  }
}
