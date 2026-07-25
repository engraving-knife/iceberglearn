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

import org.apache.iceberg.expressions.Expressions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestManifestCleanup，用于验证 Manifest Cleanup 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Manifest Cleanup 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestManifestCleanup extends TableTestBase {
  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {1, 2};
  }

  /** 辅助方法：manifest cleanup。 */
  public TestManifestCleanup(int formatVersion) {
    super(formatVersion);
  }

  /**
   * 测试场景：delete。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDelete() {
    Assert.assertEquals("Table should start with no manifests", 0, listManifestFiles().size());

    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Assert.assertEquals(
        "Table should have one append manifest",
        1,
        table.currentSnapshot().allManifests(table.io()).size());

    table.newDelete().deleteFromRowFilter(Expressions.alwaysTrue()).commit();

    Assert.assertEquals(
        "Table should have one delete manifest",
        1,
        table.currentSnapshot().allManifests(table.io()).size());

    table.newAppend().commit();

    Assert.assertEquals(
        "Table should have no manifests",
        0,
        table.currentSnapshot().allManifests(table.io()).size());
  }

  /**
   * 测试场景：partial delete。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testPartialDelete() {
    Assert.assertEquals("Table should start with no manifests", 0, listManifestFiles().size());

    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Snapshot s1 = table.currentSnapshot();
    Assert.assertEquals(
        "Table should have one append manifest", 1, s1.allManifests(table.io()).size());

    table.newDelete().deleteFile(FILE_B).commit();

    Snapshot s2 = table.currentSnapshot();
    Assert.assertEquals(
        "Table should have one mixed manifest", 1, s2.allManifests(table.io()).size());

    table.newAppend().commit();

    Snapshot s3 = table.currentSnapshot();
    Assert.assertEquals(
        "Table should have the same manifests",
        s2.allManifests(table.io()),
        s3.allManifests(table.io()));
  }

  /**
   * 测试场景：overwrite。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testOverwrite() {
    Assert.assertEquals("Table should start with no manifests", 0, listManifestFiles().size());

    table.newAppend().appendFile(FILE_A).appendFile(FILE_B).commit();

    Assert.assertEquals(
        "Table should have one append manifest",
        1,
        table.currentSnapshot().allManifests(table.io()).size());

    table
        .newOverwrite()
        .overwriteByRowFilter(Expressions.alwaysTrue())
        .addFile(FILE_C)
        .addFile(FILE_D)
        .commit();

    Assert.assertEquals(
        "Table should have one delete manifest and one append manifest",
        2,
        table.currentSnapshot().allManifests(table.io()).size());

    table
        .newOverwrite()
        .overwriteByRowFilter(Expressions.alwaysTrue())
        .addFile(FILE_A)
        .addFile(FILE_B)
        .commit();

    Assert.assertEquals(
        "Table should have one delete manifest and one append manifest",
        2,
        table.currentSnapshot().allManifests(table.io()).size());
  }
}
