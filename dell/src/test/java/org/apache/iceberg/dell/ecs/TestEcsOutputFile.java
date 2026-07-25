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
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * 文件级说明：测试 TestEcsOutputFile 的功能。
 *
 * <p>所属模块：iceberg-dell。职责：验证 TestEcsOutputFile 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestEcsOutputFile {

  @ClassRule public static EcsS3MockRule rule = EcsS3MockRule.create();

  /**
   * 测试场景：File Write。
   *
   * <p>验证该方法在 File Write 条件下的行为是否符合预期。
   */
  @Test
  public void testFileWrite() throws IOException {
    String objectName = rule.randomObjectName();
    EcsOutputFile outputFile =
        EcsOutputFile.fromLocation(new EcsURI(rule.bucket(), objectName).toString(), rule.client());

    // File write
    try (PositionOutputStream output = outputFile.create()) {
      output.write("1234567890".getBytes());
    }

    try (InputStream input =
        rule.client().readObjectStream(rule.bucket(), objectName, Range.fromOffset(0))) {
      assertThat(new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8))
          .as("File content is expected")
          .isEqualTo("1234567890");
    }
  }

  /**
   * 测试场景：File Overwrite。
   *
   * <p>验证该方法在 File Overwrite 条件下的行为是否符合预期。
   */
  @Test
  public void testFileOverwrite() throws IOException {
    String objectName = rule.randomObjectName();
    EcsOutputFile outputFile =
        EcsOutputFile.fromLocation(new EcsURI(rule.bucket(), objectName).toString(), rule.client());

    try (PositionOutputStream output = outputFile.create()) {
      output.write("1234567890".getBytes());
    }

    try (PositionOutputStream output = outputFile.createOrOverwrite()) {
      output.write("abcdefghij".getBytes());
    }

    try (InputStream input =
        rule.client().readObjectStream(rule.bucket(), objectName, Range.fromOffset(0))) {
      assertThat(new String(ByteStreams.toByteArray(input), StandardCharsets.UTF_8))
          .as("File content should be overwritten")
          .isEqualTo("abcdefghij");
    }
  }

  /**
   * 测试场景：File Already Exists。
   *
   * <p>验证该方法在 File Already Exists 条件下的行为是否符合预期。
   */
  @Test
  public void testFileAlreadyExists() throws IOException {
    String objectName = rule.randomObjectName();
    EcsOutputFile outputFile =
        EcsOutputFile.fromLocation(new EcsURI(rule.bucket(), objectName).toString(), rule.client());

    try (PositionOutputStream output = outputFile.create()) {
      output.write("1234567890".getBytes());
    }

    Assertions.assertThatThrownBy(outputFile::create)
        .isInstanceOf(AlreadyExistsException.class)
        .hasMessage("ECS object already exists: " + outputFile.location());
  }
}
