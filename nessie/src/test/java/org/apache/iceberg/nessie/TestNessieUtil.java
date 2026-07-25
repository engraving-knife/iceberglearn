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
package org.apache.iceberg.nessie;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectnessie.model.CommitMeta;

/**
 * 文件级说明：测试 TestNessieUtil 的功能。
 *
 * <p>所属模块：iceberg-nessie。职责：验证 TestNessieUtil 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestNessieUtil {

  /**
   * 测试场景：Building Commit Metadata With Null Catalog Options。
   *
   * <p>验证该方法在 Building Commit Metadata With Null Catalog Options 条件下的行为是否符合预期。
   */
  @Test
  public void testBuildingCommitMetadataWithNullCatalogOptions() {
    Assertions.assertThatThrownBy(() -> NessieUtil.buildCommitMetadata("msg", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("catalogOptions must not be null");
  }

  /**
   * 测试场景：Spark App Id And User Is Set On Commit Metadata。
   *
   * <p>验证该方法在 Spark App Id And User Is Set On Commit Metadata 条件下的行为是否符合预期。
   */
  @Test
  public void testSparkAppIdAndUserIsSetOnCommitMetadata() {
    String commitMsg = "commit msg";
    String appId = "SPARK_ID_123";
    String user = "sparkUser";
    CommitMeta commitMeta =
        NessieUtil.buildCommitMetadata(
            commitMsg,
            ImmutableMap.of(CatalogProperties.APP_ID, appId, CatalogProperties.USER, user));
    Assertions.assertThat(commitMeta.getMessage()).isEqualTo(commitMsg);
    Assertions.assertThat(commitMeta.getAuthor()).isEqualTo(user);
    Assertions.assertThat(commitMeta.getProperties()).hasSize(2);
    Assertions.assertThat(commitMeta.getProperties().get(NessieUtil.APPLICATION_TYPE))
        .isEqualTo("iceberg");
    Assertions.assertThat(commitMeta.getProperties().get(CatalogProperties.APP_ID))
        .isEqualTo(appId);
  }

  /**
   * 测试场景：Author Is Set On Commit Metadata。
   *
   * <p>验证该方法在 Author Is Set On Commit Metadata 条件下的行为是否符合预期。
   */
  @Test
  public void testAuthorIsSetOnCommitMetadata() {
    String commitMsg = "commit msg";
    CommitMeta commitMeta = NessieUtil.buildCommitMetadata(commitMsg, ImmutableMap.of());
    Assertions.assertThat(commitMeta.getMessage()).isEqualTo(commitMsg);
    Assertions.assertThat(commitMeta.getAuthor()).isEqualTo(System.getProperty("user.name"));
    Assertions.assertThat(commitMeta.getProperties()).hasSize(1);
    Assertions.assertThat(commitMeta.getProperties().get(NessieUtil.APPLICATION_TYPE))
        .isEqualTo("iceberg");
  }

  /**
   * 测试场景：Author Is Null Without Jvm User。
   *
   * <p>验证该方法在 Author Is Null Without Jvm User 条件下的行为是否符合预期。
   */
  @Test
  public void testAuthorIsNullWithoutJvmUser() {
    String jvmUserName = System.getProperty("user.name");
    try {
      System.clearProperty("user.name");
      CommitMeta commitMeta = NessieUtil.buildCommitMetadata("commit msg", ImmutableMap.of());
      Assertions.assertThat(commitMeta.getAuthor()).isNull();
    } finally {
      System.setProperty("user.name", jvmUserName);
    }
  }
}
