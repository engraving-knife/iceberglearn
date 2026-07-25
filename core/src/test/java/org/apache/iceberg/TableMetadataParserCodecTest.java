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
package org.apache.iceberg;

import org.apache.iceberg.TableMetadataParser.Codec;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试类：TableMetadataParserCodecTest，用于验证 Table Metadata Parser Codec 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Table Metadata Parser Codec
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TableMetadataParserCodecTest {

  /**
   * 测试场景：compression codec。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testCompressionCodec() {
    Assert.assertEquals(Codec.GZIP, Codec.fromName("gzip"));
    Assert.assertEquals(Codec.GZIP, Codec.fromName("gZiP"));
    Assert.assertEquals(Codec.GZIP, Codec.fromFileName("v3.gz.metadata.json"));
    Assert.assertEquals(Codec.GZIP, Codec.fromFileName("v3-f326-4b66-a541-7b1c.gz.metadata.json"));
    Assert.assertEquals(Codec.GZIP, Codec.fromFileName("v3-f326-4b66-a541-7b1c.metadata.json.gz"));
    Assert.assertEquals(Codec.NONE, Codec.fromName("none"));
    Assert.assertEquals(Codec.NONE, Codec.fromName("nOnE"));
    Assert.assertEquals(Codec.NONE, Codec.fromFileName("v3.metadata.json"));
    Assert.assertEquals(Codec.NONE, Codec.fromFileName("v3-f326-4b66-a541-7b1c.metadata.json"));
  }

  /**
   * 测试场景：invalid codec name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidCodecName() {
    Assertions.assertThatThrownBy(() -> Codec.fromName("invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid codec name: invalid");
  }

  /**
   * 测试场景：invalid file name。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidFileName() {
    Assertions.assertThatThrownBy(() -> Codec.fromFileName("path/to/file.parquet"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path/to/file.parquet is not a valid metadata file");
  }
}
