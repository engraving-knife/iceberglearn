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

import java.util.List;
import org.apache.hive.common.util.HiveVersionInfo;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;

/**
 * Hive 运行时版本枚举，用于按 Hive 版本做能力开关。
 *
 * <p>所属模块：iceberg-hive-metastore（Hive Metastore 集成的版本探测层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 classpath 上的 Hive 客户端 jar 探测当前 Hive 主版本。
 *   <li>提供 {@link #min(HiveVersion)} 便捷判断"当前 Hive 版本不低于指定版本"，供各功能 按版本能力开关启用/禁用特定逻辑。
 * </ul>
 *
 * <p>设计意图：Hive 不同版本（1.2 / 2.x / 3.x / 4.x）的 Metastore Thrift API 与锁机制存在差异， Iceberg
 * 集成代码需要根据运行时版本选择不同实现路径。用枚举集中表达版本，避免散落的魔数比较。 使用 order 整数表示版本序号，便于单调比较。
 *
 * <p>上下游关系：被 {@link NoLock}、{@link MetastoreUtil} 等用于按版本决定是否启用 HIVE-26882 等特性；依赖 {@link
 * HiveVersionInfo}（Hive 自带）读取版本字符串。
 */
public enum HiveVersion {
  HIVE_4(4),
  HIVE_3(3),
  HIVE_2(2),
  HIVE_1_2(1),
  NOT_SUPPORTED(0);

  private final int order;
  private static final HiveVersion current = calculate();

  HiveVersion(int order) {
    this.order = order;
  }

  /**
   * 返回运行时探测到的当前 Hive 版本。
   *
   * <p>该值在类加载时通过 {@link #calculate()} 静态计算并缓存。
   *
   * @return 当前 Hive 版本枚举值；无法识别时返回 {@link #NOT_SUPPORTED}
   */
  public static HiveVersion current() {
    return current;
  }

  /**
   * 判断当前 Hive 版本是否不低于指定版本。
   *
   * @param other 需要满足的最低版本
   * @return 当前版本序号大于等于 other 序号时返回 true
   */
  public static boolean min(HiveVersion other) {
    return current.order >= other.order;
  }

  /**
   * 从 classpath 探测 Hive 版本并映射为枚举。
   *
   * <p>逻辑：通过 {@link HiveVersionInfo#getShortVersion()} 获取版本号字符串，按 '.' 拆分； 主版本号 4/3/2 分别映射到
   * HIVE_4/HIVE_3/HIVE_2；主版本号 1 时仅 1.2 受支持（HIVE_1_2）， 其余 1.x 返回 NOT_SUPPORTED；其他情况返回 NOT_SUPPORTED。
   *
   * @return 探测到的 Hive 版本枚举
   */
  private static HiveVersion calculate() {
    String version = HiveVersionInfo.getShortVersion();
    List<String> versions = Splitter.on('.').splitToList(version);
    switch (versions.get(0)) {
      case "4":
        return HIVE_4;
      case "3":
        return HIVE_3;
      case "2":
        return HIVE_2;
      case "1":
        if (versions.get(1).equals("2")) {
          return HIVE_1_2;
        } else {
          return NOT_SUPPORTED;
        }
      default:
        return NOT_SUPPORTED;
    }
  }
}
