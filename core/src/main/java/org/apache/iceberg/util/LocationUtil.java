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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 路径工具类，处理表/数据文件路径的规范化。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：去除路径末尾的多余斜杠，避免路径拼接时出现双斜杠导致的不一致。
 *
 * <p>设计意图：Iceberg 表的 location 属性可能由用户或引擎以带尾斜杠的形式传入， 而文件路径拼接时多余的斜杠会影响相等性比较与存储层路径解析，因此统一在此剥离。
 *
 * <p>上下游关系：被表元数据加载、catalog 路径解析等逻辑调用；依赖 relocated guava 做参数校验。
 */
public class LocationUtil {
  private LocationUtil() {}

  /**
   * 去除路径末尾的所有连续斜杠。
   *
   * @param path 待处理的路径，不可为 null 或空
   * @return 去除尾部斜杠后的路径；若路径本身不含尾斜杠则原样返回
   */
  public static String stripTrailingSlash(String path) {
    Preconditions.checkArgument(
        path != null && path.length() > 0, "path must not be null or empty");

    String result = path;
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
