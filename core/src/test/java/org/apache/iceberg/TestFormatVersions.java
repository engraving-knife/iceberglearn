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

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 测试类：TestFormatVersions，用于验证 Format Versions 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Format Versions 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestFormatVersions extends TableTestBase {
  /** 辅助方法：format versions。 */
  public TestFormatVersions() {
    super(1);
  }

  /**
   * 测试场景：default format version。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDefaultFormatVersion() {
    Assert.assertEquals("Should default to v1", 1, table.ops().current().formatVersion());
  }

  /**
   * 测试场景：format version upgrade。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFormatVersionUpgrade() {
    TableOperations ops = table.ops();
    TableMetadata base = ops.current();
    ops.commit(base, base.upgradeToFormatVersion(2));

    Assert.assertEquals("Should report v2", 2, ops.current().formatVersion());
  }

  /**
   * 测试场景：format version downgrade。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFormatVersionDowngrade() {
    TableOperations ops = table.ops();
    TableMetadata base = ops.current();
    ops.commit(base, base.upgradeToFormatVersion(2));

    Assert.assertEquals("Should report v2", 2, ops.current().formatVersion());

    Assertions.assertThatThrownBy(() -> ops.current().upgradeToFormatVersion(1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot downgrade v2 table to v1");

    Assert.assertEquals("Should report v2", 2, ops.current().formatVersion());
  }

  /**
   * 测试场景：format version upgrade not supported。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFormatVersionUpgradeNotSupported() {
    TableOperations ops = table.ops();
    TableMetadata base = ops.current();

    Assertions.assertThatThrownBy(
            () ->
                ops.commit(
                    base,
                    base.upgradeToFormatVersion(TableMetadata.SUPPORTED_TABLE_FORMAT_VERSION + 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot upgrade table to unsupported format version: v3 (supported: v2)");

    Assert.assertEquals("Should report v1", 1, ops.current().formatVersion());
  }
}
