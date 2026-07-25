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
package org.apache.iceberg.hive;

import com.google.errorprone.annotations.FormatMethod;

/**
 * Hive Metastore 锁操作异常。
 *
 * <p>所属模块：iceberg-hive-metastore（表元数据提交的并发控制层）。
 *
 * <p>职责：在加锁、续约、释放等 {@link HiveLock} 操作失败时抛出，作为非受检异常向上传播。
 *
 * <p>设计意图：继承 {@link RuntimeException}，避免提交流程中繁琐的 try-catch；构造器使用 {@link FormatMethod} 注解，支持 {@code
 * String.format} 风格的格式化消息，便于在抛出点 直接拼接上下文信息。包级可见（无 public 修饰），限定仅本模块内锁实现使用。
 *
 * <p>上下游关系：由 {@link MetastoreLock}、{@link NoLock} 等锁实现抛出，被 {@link HiveTableOperations} 的提交流程捕获处理。
 */
class LockException extends RuntimeException {
  /**
   * 构造一个带格式化消息的锁异常。
   *
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  LockException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因和格式化消息的锁异常。
   *
   * @param cause 底层异常原因
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  LockException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
