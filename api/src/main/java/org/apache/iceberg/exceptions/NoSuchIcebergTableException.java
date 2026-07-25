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
package org.apache.iceberg.exceptions;

import com.google.errorprone.annotations.FormatMethod;

/**
 * 非 Iceberg 表异常：找到的表对象不是 Iceberg 表时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：Catalog 在加载表时找到了同名表，但其元数据并非 Iceberg 格式 （例如 Hive Metastore 中注册的 非 Iceberg
 * 表）。这与"表完全不存在"（{@link NoSuchTableException}） 语义不同，故单独建模。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link NoSuchTableException}：使捕获父类型的代码可统一处理"找不到可用 Iceberg 表"。
 *   <li>实现 {@link CleanableFailure}：明确提交不会成功，相关临时状态可安全清理。
 *   <li>提供静态 {@link #check(boolean, String, Object...)} 工具方法，简化断言式校验。
 * </ul>
 *
 * <p>上下游关系：由各 Catalog 实现在表类型检查阶段抛出；调用方据以提示需迁移或选择正确表。
 */
public class NoSuchIcebergTableException extends NoSuchTableException implements CleanableFailure {
  /**
   * 构造一个非 Iceberg 表异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NoSuchIcebergTableException(String message, Object... args) {
    super(message, args);
  }

  /**
   * 断言式校验：当 test 为 false 时抛出 {@link NoSuchIcebergTableException}。
   *
   * <p>逻辑：若 test 为真则直接返回；否则用 message 与 args 构造异常抛出。常用于 Catalog 加载表后对其类型做断言的入口。
   *
   * @param test 校验条件
   * @param message 异常消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public static void check(boolean test, String message, Object... args) {
    if (!test) {
      throw new NoSuchIcebergTableException(message, args);
    }
  }
}
