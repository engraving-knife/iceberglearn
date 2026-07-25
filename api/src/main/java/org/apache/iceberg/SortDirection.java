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
 * 文件级说明：排序方向枚举。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：定义排序字段的方向——{@link #ASC}（升序）或 {@link #DESC}（降序）。
 *
 * <p>设计意图：排序序号（{@link SortOrder}）中每个排序字段都需指定方向。以枚举形式固化两种 选择，并提供 {@link #fromString(String)}
 * 从字符串解析（大小写不敏感），便于从配置或元数据 反序列化。
 *
 * <p>上下游关系：被 {@link SortOrder}、{@link SortOrderBuilder}、{@link UnboundSortOrder} 等 排序相关类使用。
 */
public enum SortDirection {
  /** 升序（从小到大）。 */
  ASC,
  /** 降序（从大到小）。 */
  DESC;

  /**
   * 根据字符串解析出对应的 {@link SortDirection}（大小写不敏感）。
   *
   * <p>逻辑：先校验入参非 null，再将其大写化后通过 {@link Enum#valueOf} 匹配枚举； 匹配失败时抛出 {@link
   * IllegalArgumentException}。
   *
   * @param directionAsString 方向字符串（如 "asc"、"DESC"）
   * @return 对应的排序方向枚举
   * @throws IllegalArgumentException 若入参为 null 或无法识别
   */
  public static SortDirection fromString(String directionAsString) {
    Preconditions.checkArgument(null != directionAsString, "Invalid sort direction: null");
    try {
      return SortDirection.valueOf(directionAsString.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Invalid sort direction: %s", directionAsString), e);
    }
  }
}
