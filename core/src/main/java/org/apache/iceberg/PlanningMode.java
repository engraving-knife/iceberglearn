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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 扫描计划执行模式枚举：控制文件计划是在本地还是分布式执行。
 *
 * <p>所属模块：iceberg-core（扫描配置层）。
 *
 * <p>职责：定义三种计划模式——AUTO（自动选择）、LOCAL（本地执行）、DISTRIBUTED（分布式执行）， 并提供从字符串名解析为枚举的能力。
 *
 * <p>设计意图：允许调用方通过表属性或扫描配置指定计划模式，以适配不同部署场景（如大表分布式 计划、小表本地计划）。
 *
 * <p>上下游关系：被 {@link TableScanContext} 与扫描实现读取，决定是否使用 planExecutor。
 */
public enum PlanningMode {
  /** 自动模式：由实现根据 manifest 数量等因素决定本地或分布式。 */
  AUTO("auto"),
  /** 本地模式：在当前线程执行计划。 */
  LOCAL("local"),
  /** 分布式模式：使用线程池/执行器并行执行计划。 */
  DISTRIBUTED("distributed");

  private final String modeName;

  PlanningMode(String modeName) {
    this.modeName = modeName;
  }

  /**
   * 从字符串名解析为 {@link PlanningMode}。
   *
   * @param modeName 模式名（auto/local/distributed，大小写不敏感）
   * @return 对应的枚举值
   * @throws IllegalArgumentException 若 modeName 为 null 或未知
   */
  public static PlanningMode fromName(String modeName) {
    Preconditions.checkArgument(modeName != null, "Mode name is null");

    if (AUTO.modeName().equalsIgnoreCase(modeName)) {
      return AUTO;

    } else if (LOCAL.modeName().equalsIgnoreCase(modeName)) {
      return LOCAL;

    } else if (DISTRIBUTED.modeName().equalsIgnoreCase(modeName)) {
      return DISTRIBUTED;

    } else {
      throw new IllegalArgumentException("Unknown planning mode: " + modeName);
    }
  }

  /** 返回模式名（小写字符串）。 */
  public String modeName() {
    return modeName;
  }
}
