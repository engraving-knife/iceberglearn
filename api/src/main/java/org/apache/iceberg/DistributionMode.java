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

import java.util.Locale;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：写入分布模式枚举，定义批式或流式作业的写入数据分布行为。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：定义三种写入分布模式，指导引擎在写入前如何对数据进行 shuffle：
 *
 * <ul>
 *   <li>{@link #NONE}：不 shuffle。适合数据集中在少数分区的场景；否则各 task 会随机写不同分区， 产生过多小文件。
 *   <li>{@link #HASH}：按分区键哈希分布。适合数据在各分区均匀分布的场景。
 *   <li>{@link #RANGE}：按分区键（或表有 {@link SortOrder} 时按排序键）范围分布。适合数据在各 分区分布倾斜的场景。
 * </ul>
 *
 * <p>设计意图：通过表属性 {@code write.distribution-mode} 配置，让 Iceberg 在不同数据分布特征下
 * 平衡写入并行度与文件数量。以枚举形式定义并附带字符串名，便于序列化与跨引擎统一语义。
 *
 * <p>上下游关系：由 core 模块在写入规划时读取；被 Spark/Flink 等引擎集成模块用于决定 shuffle 策略。
 */
public enum DistributionMode {
  NONE("none"),
  HASH("hash"),
  RANGE("range");

  private final String modeName;

  DistributionMode(String modeName) {
    this.modeName = modeName;
  }

  /** 返回该分布模式的字符串名称（如 "none"、"hash"、"range"）。 */
  public String modeName() {
    return modeName;
  }

  /**
   * 根据字符串名称解析出对应的 {@link DistributionMode}。
   *
   * <p>逻辑：先校验入参非 null，再将其大写化后通过 {@link Enum#valueOf} 匹配枚举； 匹配失败时抛出 {@link
   * IllegalArgumentException}。
   *
   * @param modeName 模式名称字符串（大小写不敏感）
   * @return 对应的分布模式枚举
   * @throws IllegalArgumentException 若入参为 null 或无法识别
   */
  public static DistributionMode fromName(String modeName) {
    Preconditions.checkArgument(null != modeName, "Invalid distribution mode: null");
    try {
      return DistributionMode.valueOf(modeName.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format("Invalid distribution mode: %s", modeName));
    }
  }
}
