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

import java.util.UUID;

/**
 * 快照 id 生成工具。
 *
 * <p>所属模块：iceberg-core。职责：基于 {@link UUID} 生成一个非负的 long 型快照 id。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>随机但非负：取 UUID 的高 64 位与低 64 位异或后按位与 {@link Long#MAX_VALUE}，保证为正数。
 *   <li>无状态：纯静态方法，可跨进程并行调用，冲突概率极低。
 * </ul>
 *
 * <p>上下游关系：被 core 内部提交路径在创建新快照时调用。
 */
public final class SnapshotIdGeneratorUtil {

  /** 私有构造：工具类禁止实例化。 */
  private SnapshotIdGeneratorUtil() {}

  /**
   * 生成一个新的快照 id。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>生成一个随机 {@link UUID}；
   *   <li>取其高 64 位与低 64 位异或；
   *   <li>与 {@link Long#MAX_VALUE} 按位与，保证结果为非负 long。
   * </ol>
   *
   * @return 非负的快照 id
   */
  public static long generateSnapshotID() {
    UUID uuid = UUID.randomUUID();
    long mostSignificantBits = uuid.getMostSignificantBits();
    long leastSignificantBits = uuid.getLeastSignificantBits();
    return (mostSignificantBits ^ leastSignificantBits) & Long.MAX_VALUE;
  }
}
