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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestIcebergBuild 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestIcebergBuild 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestIcebergBuild {
  /**
   * 测试场景：Full Version。
   *
   * <p>验证该方法在 Full Version 条件下的行为是否符合预期。
   */
  @Test
  public void testFullVersion() {
    assertThat(IcebergBuild.fullVersion())
        .as("Should build full version from version and commit ID")
        .isEqualTo(
            "Apache Iceberg "
                + IcebergBuild.version()
                + " (commit "
                + IcebergBuild.gitCommitId()
                + ")");
  }

  /**
   * 测试场景：Version。
   *
   * <p>验证该方法在 Version 条件下的行为是否符合预期。
   */
  @Test
  public void testVersion() {
    assertThat(IcebergBuild.version()).as("Should not use unknown version").isNotEqualTo("unknown");
  }

  /**
   * 测试场景：Git Commit Id。
   *
   * <p>验证该方法在 Git Commit Id 条件下的行为是否符合预期。
   */
  @Test
  public void testGitCommitId() {
    assertThat(IcebergBuild.gitCommitId())
        .as("Should not use unknown commit ID")
        .isNotEqualTo("unknown");
    assertThat(
            Pattern.compile("[0-9a-f]{40}")
                .matcher(IcebergBuild.gitCommitId().toLowerCase(Locale.ROOT)))
        .as("Should be a hexadecimal string of 20 bytes")
        .matches();
  }
}
