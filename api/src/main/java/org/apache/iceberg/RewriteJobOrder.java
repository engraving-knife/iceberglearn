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
 * 重写作业排序枚举：定义文件重写（compaction）任务组的执行顺序。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>可选值：
 *
 * <ul>
 *   <li>bytes-asc：先重写字节数最少的任务组。
 *   <li>bytes-desc：先重写字节数最多的任务组。
 *   <li>files-asc：先重写文件数最少的任务组。
 *   <li>files-desc：先重写文件数最多的任务组。
 *   <li>none：按规划顺序执行，不做额外排序。
 * </ul>
 *
 * <p>设计意图：把排序策略固化为枚举常量，配合表属性 {@code write.distribution-mode} 等使用，使重写作业能根据 SLA 选择"先小后大快速释放资源"
 * 或"先大后大尽快压缩热点"等策略。
 *
 * <p>上下游关系：被 core 模块的文件重写动作（如 {@code RewriteFilesAction}）读取并应用。
 */
public enum RewriteJobOrder {
  BYTES_ASC("bytes-asc"),
  BYTES_DESC("bytes-desc"),
  FILES_ASC("files-asc"),
  FILES_DESC("files-desc"),
  NONE("none");

  private final String orderName;

  RewriteJobOrder(String orderName) {
    this.orderName = orderName;
  }

  /** 返回该排序策略的字符串名称（如 "bytes-asc"）。 */
  public String orderName() {
    return orderName;
  }

  /**
   * 把字符串名称解析为 {@link RewriteJobOrder}。
   *
   * <p>逻辑：先把名称中的连字符替换为下划线（如 "bytes-asc" → "BYTES_ASC"），再大写后 调用 {@link Enum#valueOf} 解析。解析失败时抛出
   * {@link IllegalArgumentException}。
   *
   * @param orderName 排序策略名称
   * @return 对应的枚举值
   * @throws IllegalArgumentException 入参为 null 或无法识别
   */
  public static RewriteJobOrder fromName(String orderName) {
    Preconditions.checkArgument(orderName != null, "Invalid rewrite job order name: null");
    // Replace the hyphen in order name with underscore to map to the enum value. For example:
    // bytes-asc to BYTES_ASC
    try {
      return RewriteJobOrder.valueOf(orderName.replaceFirst("-", "_").toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Invalid rewrite job order name: %s", orderName), e);
    }
  }
}
