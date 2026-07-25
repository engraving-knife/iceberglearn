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
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试类：TestKeyMetadataParser，用于验证 Key Metadata Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Key Metadata Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestKeyMetadataParser {

  /**
   * 测试场景：parser。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testParser() {
    ByteBuffer encryptionKey = ByteBuffer.wrap("0123456789012345".getBytes(StandardCharsets.UTF_8));
    ByteBuffer aadPrefix = ByteBuffer.wrap("1234567890123456".getBytes(StandardCharsets.UTF_8));
    KeyMetadata metadata = new KeyMetadata(encryptionKey, aadPrefix);
    ByteBuffer serialized = metadata.buffer();

    KeyMetadata parsedMetadata = KeyMetadata.parse(serialized);
    Assert.assertEquals(parsedMetadata.encryptionKey(), encryptionKey);
    Assert.assertEquals(parsedMetadata.aadPrefix(), aadPrefix);
  }

  /**
   * 测试场景：unsupported version。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testUnsupportedVersion() {
    ByteBuffer badBuffer = ByteBuffer.wrap(new byte[] {0x02});
    Assertions.assertThatThrownBy(() -> KeyMetadata.parse(badBuffer))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Cannot resolve schema for version: 2");
  }
}
