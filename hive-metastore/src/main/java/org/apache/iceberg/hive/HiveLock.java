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

/**
 * Hive Metastore 表锁抽象接口。
 *
 * <p>所属模块：iceberg-hive-metastore（表元数据提交的并发控制层）。
 *
 * <p>职责：定义对 Hive Metastore 中表/分区加锁、保活、释放的统一契约，供 Iceberg 提交操作 在多并发写入场景下做互斥控制。
 *
 * <p>设计意图：不同 Hive 版本/部署形态提供的锁机制不同（如基于 Hive Metastore 的 lock/heartbeat， 或 HIVE-26882
 * 提供的无锁方案）。通过该接口抽象，{@link HiveTableOperations} 等上层无需关心 具体锁实现，由 {@link MetastoreLock} / {@link
 * NoLock} 等实现各自适配。
 *
 * <p>上下游关系：被 {@link HiveTableOperations} 在提交（commit）流程中持有并调用； 实现类包括 {@link MetastoreLock}、{@link
 * NoLock}。
 */
interface HiveLock {
  /**
   * 加锁。阻塞直至获取到锁或失败。
   *
   * @throws LockException 加锁失败（如超时、被拒绝）
   */
  void lock() throws LockException;

  /**
   * 确保持有的锁仍然有效（处于活跃状态）。
   *
   * <p>对于依赖心跳续约的锁实现，此方法用于在长事务中续期；若锁已失效则抛出异常。
   *
   * @throws LockException 锁已失效或续约失败
   */
  void ensureActive() throws LockException;

  /** 释放锁。实现应保证幂等，即多次调用与一次调用效果一致。 */
  void unlock();
}
