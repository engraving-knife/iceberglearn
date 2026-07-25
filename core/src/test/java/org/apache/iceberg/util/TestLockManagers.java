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
package org.apache.iceberg.util;

import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestLockManagers，用于验证 Lock Managers 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Lock Managers 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestLockManagers {

  /**
   * 测试场景：load default lock manager。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testLoadDefaultLockManager() {
    Assertions.assertThat(LockManagers.defaultLockManager())
        .isInstanceOf(LockManagers.InMemoryLockManager.class);
  }

  /**
   * 测试场景：load custom lock manager。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testLoadCustomLockManager() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(CatalogProperties.LOCK_IMPL, CustomLockManager.class.getName());
    Assertions.assertThat(LockManagers.from(properties)).isInstanceOf(CustomLockManager.class);
  }

  static class CustomLockManager implements LockManager {

    /** 辅助方法：acquire。 */
    @Override
    public boolean acquire(String entityId, String ownerId) {
      return false;
    }

    /** 辅助方法：release。 */
    @Override
    public boolean release(String entityId, String ownerId) {
      return false;
    }

    /** 辅助方法：close。 */
    @Override
    public void close() throws Exception {}

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(Map<String, String> properties) {}
  }
}
