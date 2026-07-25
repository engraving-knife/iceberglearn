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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 测试类：TestInMemoryLockManager，用于验证 In Memory Lock Manager 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 In Memory Lock Manager 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@Timeout(value = 5)
public class TestInMemoryLockManager {

  private LockManagers.InMemoryLockManager lockManager;
  private String lockEntityId;
  private String ownerId;

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    lockEntityId = UUID.randomUUID().toString();
    ownerId = UUID.randomUUID().toString();
    lockManager = new LockManagers.InMemoryLockManager(Maps.newHashMap());
  }

  /** 辅助方法：after。 */
  @AfterEach
  public void after() {
    lockManager.close();
  }

  /**
   * 测试场景：acquire once single process。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAcquireOnceSingleProcess() {
    lockManager.acquireOnce(lockEntityId, ownerId);
    Assertions.assertThatThrownBy(() -> lockManager.acquireOnce(lockEntityId, ownerId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Lock for")
        .hasMessageContaining("currently held by")
        .hasMessageContaining("expiration");
  }

  /**
   * 测试场景：acquire once multi processes。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAcquireOnceMultiProcesses() {
    List<Boolean> results =
        IntStream.range(0, 10)
            .parallel()
            .mapToObj(
                i -> {
                  try {
                    lockManager.acquireOnce(lockEntityId, ownerId);
                    return true;
                  } catch (IllegalStateException e) {
                    return false;
                  }
                })
            .collect(Collectors.toList());
    assertThat(results.stream().filter(s -> s).count())
        .as("only 1 thread should have acquired the lock")
        .isOne();
  }

  /**
   * 测试场景：release and acquire。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReleaseAndAcquire() {
    assertThat(lockManager.acquire(lockEntityId, ownerId)).isTrue();
    assertThat(lockManager.release(lockEntityId, ownerId)).isTrue();
    assertThat(lockManager.acquire(lockEntityId, ownerId))
        .as("acquire after release should succeed")
        .isTrue();
  }

  /**
   * 测试场景：release with wrong owner。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testReleaseWithWrongOwner() {
    assertThat(lockManager.acquire(lockEntityId, ownerId)).isTrue();
    assertThat(lockManager.release(lockEntityId, UUID.randomUUID().toString()))
        .as("should return false if ownerId is wrong")
        .isFalse();
  }

  /**
   * 测试场景：acquire single process。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAcquireSingleProcess() throws Exception {
    lockManager.initialize(
        ImmutableMap.of(
            CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS, "500",
            CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS, "2000"));
    assertThat(lockManager.acquire(lockEntityId, ownerId)).isTrue();
    String oldOwner = ownerId;

    CompletableFuture.supplyAsync(
        () -> {
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          assertThat(lockManager.release(lockEntityId, oldOwner)).isTrue();
          return null;
        });

    ownerId = UUID.randomUUID().toString();
    long start = System.currentTimeMillis();
    assertThat(lockManager.acquire(lockEntityId, ownerId)).isTrue();
    assertThat(System.currentTimeMillis() - start)
        .as("should succeed after 200ms")
        .isGreaterThanOrEqualTo(200);
  }

  /**
   * 测试场景：acquire multi process all succeed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAcquireMultiProcessAllSucceed() {
    lockManager.initialize(ImmutableMap.of(CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS, "500"));
    long start = System.currentTimeMillis();
    List<Boolean> results =
        IntStream.range(0, 3)
            .parallel()
            .mapToObj(
                i -> {
                  String owner = UUID.randomUUID().toString();
                  boolean succeeded = lockManager.acquire(lockEntityId, owner);
                  if (succeeded) {
                    try {
                      Thread.sleep(1000);
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                    assertThat(lockManager.release(lockEntityId, owner)).isTrue();
                  }
                  return succeeded;
                })
            .collect(Collectors.toList());
    assertThat(results.stream().filter(s -> s).count())
        .as("all lock acquire should succeed sequentially")
        .isEqualTo(3);
    assertThat(System.currentTimeMillis() - start)
        .as("must take more than 3 seconds")
        .isGreaterThanOrEqualTo(3000);
  }

  /**
   * 测试场景：acquire multi process only one succeed。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testAcquireMultiProcessOnlyOneSucceed() {
    lockManager.initialize(
        ImmutableMap.of(
            CatalogProperties.LOCK_HEARTBEAT_INTERVAL_MS, "100",
            CatalogProperties.LOCK_ACQUIRE_INTERVAL_MS, "500",
            CatalogProperties.LOCK_ACQUIRE_TIMEOUT_MS, "2000"));

    List<Boolean> results =
        IntStream.range(0, 3)
            .parallel()
            .mapToObj(i -> lockManager.acquire(lockEntityId, ownerId))
            .collect(Collectors.toList());
    assertThat(results.stream().filter(s -> s).count())
        .as("only 1 thread should have acquired the lock")
        .isOne();
  }
}
