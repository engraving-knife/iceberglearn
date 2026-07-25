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
package org.apache.iceberg.jdbc;

import com.google.errorprone.annotations.FormatMethod;

/**
 * 文件级说明：把 InterruptedException 包装为 RuntimeException 的不受检异常。
 *
 * <p>所属模块：iceberg-core（jdbc 子包）。职责：在 JDBC 操作被中断时， 把受检的 InterruptedException 包装为 RuntimeException
 * 抛出，简化调用方异常处理。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>避免在每一层调用都强制 try-catch InterruptedException。
 *   <li>保留原始异常作为 cause，便于排查。
 * </ul>
 *
 * <p>上下游关系：由 {@link JdbcClientPool} 在连接操作被中断时抛出。
 */
public class UncheckedInterruptedException extends RuntimeException {

  /**
   * 构造带格式化消息的异常。
   *
   * <p>设计要点：使用 @FormatMethod 注解支持 printf 风格的格式化消息。
   *
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  public UncheckedInterruptedException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造带原因和格式化消息的异常。
   *
   * @param cause 原始中断异常
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  public UncheckedInterruptedException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
