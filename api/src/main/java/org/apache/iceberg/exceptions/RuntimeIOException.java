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
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * 已弃用：请直接使用 {@link java.io.UncheckedIOException}。
 *
 * <p>原职责：将 {@link IOException} 包装为 {@link RuntimeException} 并补充上下文信息， 让调用方免于处理受检 IO 异常。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>设计意图：JDK 已提供等价的 {@link UncheckedIOException}，故本类被标记为 {@link Deprecated}，新代码不应再使用，保留仅为向后兼容。
 *
 * <p>上下游关系：历史代码中可能仍抛出本类型；调用方应优先以 {@link UncheckedIOException} 处理。
 *
 * @deprecated 请直接使用 {@link java.io.UncheckedIOException} 替代本类。
 */
@Deprecated
public class RuntimeIOException extends UncheckedIOException {

  /**
   * 构造一个包装给定 {@link IOException} 的 RuntimeIOException。
   *
   * @param cause 被包装的 IOException
   */
  public RuntimeIOException(IOException cause) {
    super(cause);
  }

  /**
   * 构造一个带自定义消息和原因的 RuntimeIOException，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 被包装的 IOException
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public RuntimeIOException(IOException cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }

  /**
   * 构造一个仅带消息的 RuntimeIOException。
   *
   * <p>注意：内部会用消息构造一个 {@link IOException} 作为 cause。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public RuntimeIOException(String message, Object... args) {
    super(new IOException(String.format(message, args)));
  }
}
