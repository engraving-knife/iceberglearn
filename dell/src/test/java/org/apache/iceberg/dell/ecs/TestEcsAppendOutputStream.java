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
package org.apache.iceberg.dell.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import com.emc.object.Range;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.iceberg.dell.mock.ecs.EcsS3MockRule;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * 文件级说明：测试 TestEcsAppendOutputStream 的功能。
 *
 * <p>所属模块：iceberg-dell。职责：验证 TestEcsAppendOutputStream 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestEcsAppendOutputStream {

  @ClassRule public static EcsS3MockRule rule = EcsS3MockRule.create();

  /**
   * 测试场景：Base Object Write。
   *
   * <p>验证该方法在 Base Object Write 条件下的行为是否符合预期。
   */
  @Test
  public void testBaseObjectWrite() throws IOException {
    String objectName = rule.randomObjectName();
    try (EcsAppendOutputStream output =
        EcsAppendOutputStream.createWithBufferSize(
            rule.client(),
            new EcsURI(rule.bucket(), objectName),
            10,
            MetricsContext.nullMetrics())) {
      // write 1 byte
      output.write('1');
      // write 3 bytes
      output.write("123".getBytes());
      // write 7 bytes, totally 11 bytes > local buffer limit (10)
      output.write("1234567".getBytes());
      // write 11 bytes, flush remain 7 bytes and new 11 bytes
      output.write("12345678901".getBytes());
    }

    try (InputStream input =
        rule.client().readObjectStream(rule.bucket(), objectName, Range.fromOffset(0))) {
      assertThat(new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8))
          .as("Must write all the object content")
          .isEqualTo("1" + "123" + "1234567" + "12345678901");
    }
  }

  /**
   * 测试场景：Rewrite。
   *
   * <p>验证该方法在 Rewrite 条件下的行为是否符合预期。
   */
  @Test
  public void testRewrite() throws IOException {
    String objectName = rule.randomObjectName();
    try (EcsAppendOutputStream output =
        EcsAppendOutputStream.createWithBufferSize(
            rule.client(),
            new EcsURI(rule.bucket(), objectName),
            10,
            MetricsContext.nullMetrics())) {
      // write 7 bytes
      output.write("7654321".getBytes());
    }

    try (EcsAppendOutputStream output =
        EcsAppendOutputStream.createWithBufferSize(
            rule.client(),
            new EcsURI(rule.bucket(), objectName),
            10,
            MetricsContext.nullMetrics())) {
      // write 14 bytes
      output.write("1234567".getBytes());
      output.write("1234567".getBytes());
    }

    try (InputStream input =
        rule.client().readObjectStream(rule.bucket(), objectName, Range.fromOffset(0))) {
      assertThat(new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8))
          .as("Must replace the object content")
          .isEqualTo("1234567" + "1234567");
    }
  }
}
