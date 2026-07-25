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
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;

/**
 * 校验异常：当一组参数单独看合法、但相互之间或与现有状态不一致时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：与 {@link IllegalArgumentException}（参数值本身非法）相对，本异常用于 "组合校验失败"的场景。例如：尝试用与表 {@link Schema}
 * 不兼容的 {@link PartitionSpec} 创建表时抛出。抛出后会导致当前操作中止。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link CleanableFailure}：校验失败时提交尚未真正发生，相关临时状态可安全清理。
 *   <li>提供静态 {@link #check(boolean, String, Object...)} 工具方法，简化"断言式"校验写法。
 *   <li>构造器接受 {@code String.format} 风格模板，配合 {@link FormatMethod} 做编译期格式化校验。
 * </ul>
 *
 * <p>上下游关系：由 core 模块的表/分区/提交校验逻辑抛出；子类有 {@link CherrypickAncestorCommitException}、{@link
 * DuplicateWAPCommitException}。
 */
public class ValidationException extends RuntimeException implements CleanableFailure {
  /**
   * 构造一个校验异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public ValidationException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的校验异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public ValidationException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }

  /**
   * 断言式校验：当 test 为 false 时抛出 {@link ValidationException}。
   *
   * <p>逻辑：若 test 为真则直接返回；否则用 message 与 args 构造异常抛出。常用于链式校验 入口，避免重复的 if-throw 样板代码。
   *
   * @param test 校验条件
   * @param message 异常消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public static void check(boolean test, String message, Object... args) {
    if (!test) {
      throw new ValidationException(message, args);
    }
  }
}
