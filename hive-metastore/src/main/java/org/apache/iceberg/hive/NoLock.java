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
package org.apache.iceberg.hive;

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 空操作锁实现（无锁方案）。
 *
 * <p>所属模块：iceberg-hive-metastore（表元数据提交的并发控制层）。
 *
 * <p>职责：实现 {@link HiveLock} 接口，但所有方法均为空操作，用于不需要显式加锁的场景。
 *
 * <p>设计意图：HIVE-26882 引入了基于 Hive Metastore 自身事务/原子更新的并发控制方案， 在该方案下 Iceberg 提交无需再额外通过 lock/heartbeat
 * 机制加锁。{@code NoLock} 作为 "空对象"实现，让上层 {@link HiveTableOperations} 的提交流程代码保持一致（始终持有 一个 HiveLock
 * 引用），无需为无锁场景单独分支处理。构造时校验 Hive 版本不低于 2， 因为 HIVE-26882 方案依赖 Hive 2+ 的 Metastore 能力。
 *
 * <p>上下游关系：被 {@link HiveTableOperations} 在配置为使用 HIVE-26882 无锁方案时持有； 依赖 {@link HiveVersion} 做版本校验。
 */
public class NoLock implements HiveLock {
  /**
   * 构造空操作锁，校验当前 Hive 版本不低于 2。
   *
   * @throws IllegalArgumentException 若 Hive 版本低于 2，无法使用 HIVE-26882 方案
   */
  public NoLock() {
    Preconditions.checkArgument(
        HiveVersion.min(HiveVersion.HIVE_2),
        "Minimally Hive 2 HMS client is needed to use HIVE-26882 based locking");
  }

  /** 空操作，无需加锁。 */
  @Override
  public void lock() throws LockException {
    // no-op
  }

  /** 空操作，无需续约保活。 */
  @Override
  public void ensureActive() throws LockException {
    // no-op
  }

  /** 空操作，无需释放。 */
  @Override
  public void unlock() {
    // no-op
  }
}
