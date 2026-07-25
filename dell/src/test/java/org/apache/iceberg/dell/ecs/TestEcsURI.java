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

import org.apache.iceberg.exceptions.ValidationException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestEcsURI 的功能。
 *
 * <p>所属模块：iceberg-dell。职责：验证 TestEcsURI 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestEcsURI {

  /**
   * 测试场景：Constructor。
   *
   * <p>验证该方法在 Constructor 条件下的行为是否符合预期。
   */
  @Test
  public void testConstructor() {
    assertURI("bucket", "", new EcsURI("ecs://bucket"));
    assertURI("bucket", "", new EcsURI("ecs://bucket/"));
    assertURI("bucket", "", new EcsURI("ecs://bucket//"));
    assertURI("bucket", "a", new EcsURI("ecs://bucket//a"));
    assertURI("bucket", "a/b", new EcsURI("ecs://bucket/a/b"));
    assertURI("bucket", "a//b", new EcsURI("ecs://bucket/a//b"));
    assertURI("bucket", "a//b", new EcsURI("ecs://bucket//a//b"));
  }

  /**
   * 测试场景：Constructor With Bucket And Name。
   *
   * <p>验证该方法在 Constructor With Bucket And Name 条件下的行为是否符合预期。
   */
  @Test
  public void testConstructorWithBucketAndName() {
    assertURI("bucket", "", new EcsURI("bucket", ""));
    assertURI("bucket", "", new EcsURI("bucket", "/"));
    assertURI("bucket", "", new EcsURI("bucket", "//"));
    assertURI("bucket", "a", new EcsURI("bucket", "a"));
    assertURI("bucket", "a", new EcsURI("bucket", "/a"));
    assertURI("bucket", "a/b", new EcsURI("bucket", "a/b"));
    assertURI("bucket", "a//b", new EcsURI("bucket", "/a//b"));
  }

  /** 辅助方法：assertURI。 */
  private void assertURI(String bucket, String name, EcsURI ecsURI) {
    assertThat(ecsURI.bucket()).as("bucket").isEqualTo(bucket);
    assertThat(ecsURI.name()).as("name").isEqualTo(name);
  }

  /**
   * 测试场景：Invalid Location。
   *
   * <p>验证该方法在 Invalid Location 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidLocation() {
    Assertions.assertThatThrownBy(() -> new EcsURI("http://bucket/a"))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Invalid ecs location: http://bucket/a");
  }
}
