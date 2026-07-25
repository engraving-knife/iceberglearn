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
 * 表事务隔离级别枚举。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：定义 Iceberg 表操作支持的事务隔离级别。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>{@link #SERIALIZABLE}：最强隔离级别。若并发事务提交了可能匹配 UPDATE/DELETE/MERGE 条件的新文件，则当前操作必须失败。
 *   <li>{@link #SNAPSHOT}：快照隔离。读一致视图，但允许在并发写入新文件时仍可提交， 适合高并发写入场景。
 * </ul>
 *
 * 两者都提供读一致性视图，读者只能看到已提交数据。
 *
 * <p>上下游关系：被 {@link OverwriteFiles}、{@link RowDelta} 等操作的冲突校验逻辑使用。
 */
public enum IsolationLevel {
  SERIALIZABLE,
  SNAPSHOT;

  /**
   * 按名称解析隔离级别（大小写不敏感）。
   *
   * <p>逻辑：将名称转大写后用 {@link Enum#valueOf} 匹配，失败则抛 IllegalArgumentException。
   *
   * @param levelName 隔离级别名称
   * @return 对应的 {@link IsolationLevel}
   * @throws IllegalArgumentException 若名称为 null 或无效
   */
  public static IsolationLevel fromName(String levelName) {
    Preconditions.checkArgument(levelName != null, "Invalid isolation level: null");
    try {
      return IsolationLevel.valueOf(levelName.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Invalid isolation level: %s", levelName), e);
    }
  }
}
