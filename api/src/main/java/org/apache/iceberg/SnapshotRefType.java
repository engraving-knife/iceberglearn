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
 * 快照引用类型：区分分支（BRANCH）与标签（TAG）。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：作为表元数据中 refs 的类型标识，标识一个引用是可移动的分支还是不可变的标签。
 *
 * <p>设计意图：分支可随提交前进、可设置保留策略；标签一旦指向某快照即视为不可变锚点。 用枚举而非字符串常量避免拼写错误。
 *
 * <p>上下游关系：被 {@code SnapshotRef}、{@link ManageSnapshots} 等使用。
 */
enum SnapshotRefType {
  BRANCH,
  TAG;

  /**
   * 把字符串解析为 {@link SnapshotRefType}（大小写不敏感）。
   *
   * <p>逻辑：转大写后调用 {@link Enum#valueOf}；失败时抛出 {@link IllegalArgumentException} 并附原始输入。
   *
   * @param snapshotRefType 引用类型字符串
   * @return 对应的枚举值
   * @throws IllegalArgumentException 入参为 null 或无法识别
   */
  public static SnapshotRefType fromString(String snapshotRefType) {
    Preconditions.checkArgument(null != snapshotRefType, "Invalid snapshot ref type: null");
    try {
      return SnapshotRefType.valueOf(snapshotRefType.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Invalid snapshot ref type: %s", snapshotRefType), e);
    }
  }
}
