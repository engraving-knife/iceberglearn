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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestCiphers，用于验证 Ciphers 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Ciphers 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestCiphers {

  /**
   * 测试场景：basic encrypt。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBasicEncrypt() {
    testEncryptDecrypt(null, true, false, false);
  }

  /**
   * 测试场景：aad。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAAD() {
    byte[] aad = "abcd".getBytes(StandardCharsets.UTF_8);
    testEncryptDecrypt(aad, true, false, false);
  }

  /**
   * 测试场景：bad aad。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBadAAD() {
    byte[] aad = "abcd".getBytes(StandardCharsets.UTF_8);
    testEncryptDecrypt(aad, false, true, false);
  }

  /**
   * 测试场景：content corruption。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testContentCorruption() {
    byte[] aad = "abcd".getBytes(StandardCharsets.UTF_8);
    testEncryptDecrypt(aad, false, false, true);
  }

  /**
   * 测试场景：encrypt decrypt。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  private void testEncryptDecrypt(
      byte[] aad, boolean testDecrypt, boolean testBadAad, boolean testCorruption) {
    SecureRandom random = new SecureRandom();
    int[] aesKeyLengthArray = {16, 24, 32};

    for (int keyLength : aesKeyLengthArray) {
      byte[] key = new byte[keyLength];
      random.nextBytes(key);
      Ciphers.AesGcmEncryptor encryptor = new Ciphers.AesGcmEncryptor(key);
      byte[] plaintext = new byte[16]; // typically used to encrypt DEKs
      random.nextBytes(plaintext);
      byte[] ciphertext = encryptor.encrypt(plaintext, aad);

      Ciphers.AesGcmDecryptor decryptor = new Ciphers.AesGcmDecryptor(key);

      if (testDecrypt) {
        byte[] decryptedText = decryptor.decrypt(ciphertext, aad);
        assertThat(decryptedText).as("Key length " + keyLength).isEqualTo(plaintext);
      }

      if (testBadAad) {
        final byte[] badAad = (aad == null) ? new byte[1] : aad;
        badAad[0]++;

        Assertions.assertThatThrownBy(() -> decryptor.decrypt(ciphertext, badAad))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("GCM tag check failed");
      }

      if (testCorruption) {
        ciphertext[ciphertext.length / 2]++;

        Assertions.assertThatThrownBy(() -> decryptor.decrypt(ciphertext, aad))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("GCM tag check failed");
      }
    }
  }
}
