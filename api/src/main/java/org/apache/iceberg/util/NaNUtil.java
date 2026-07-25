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
package org.apache.iceberg.util;

/**
 * NaN 判定工具类：判断一个值是否为浮点 NaN（Not-a-Number）。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：对 Double/Float 类型的值判断是否为 NaN，其他类型（含 null）返回 false。
 *
 * <p>设计意图：NaN 在 Iceberg 比较语义中需要特殊处理（NaN 与任何值都不等，甚至与自身不等）， 表达式求值/统计收集时需要先识别 NaN
 * 再决定处理方式。本类提供统一的判定入口，避免散落各处 的 instanceof 判断。
 *
 * <p>上下游关系：被 core 模块的表达式求值与统计信息收集逻辑调用，用于 NaN 值识别。
 */
public class NaNUtil {

  private NaNUtil() {}

  /**
   * 判断给定值是否为 NaN。
   *
   * <p>逻辑：null 返回 false；Double 用 {@link Double#isNaN}；Float 用 {@link Float#isNaN}； 其他类型返回 false。
   *
   * @param value 待判断的值
   * @return 若为 Double/Float 且值为 NaN 则返回 true，否则 false
   */
  public static boolean isNaN(Object value) {
    if (value == null) {
      return false;
    }

    if (value instanceof Double) {
      return Double.isNaN((Double) value);
    } else if (value instanceof Float) {
      return Float.isNaN((Float) value);
    } else {
      return false;
    }
  }
}
