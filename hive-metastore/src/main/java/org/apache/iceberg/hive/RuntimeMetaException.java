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
import org.apache.hadoop.hive.metastore.api.MetaException;

/**
 * Hive Metastore 元数据操作的运行时异常包装类。
 *
 * <p>所属模块：iceberg-hive-metastore（Hive Metastore 调用层）。
 *
 * <p>职责：将 Hive Thrift API 抛出的受检异常 {@link MetaException}（及其他 Throwable） 包装为非受检 {@link
 * RuntimeException}，并支持附加格式化上下文消息。
 *
 * <p>设计意图：Hive Metastore Thrift 接口的 {@code TException}/{@code MetaException} 是受检异常， 在 Iceberg
 * 内部许多不便声明 throws 的调用路径中需要包装为运行时异常传播。 提供多个构造器以适配"仅原因"、"原因+消息"、"任意 Throwable+消息"三种场景。
 *
 * <p>上下游关系：由本模块内 Hive Metastore 调用相关代码抛出，被上层 Catalog/Operations 逻辑捕获。
 */
public class RuntimeMetaException extends RuntimeException {
  /**
   * 仅以 {@link MetaException} 为原因构造运行时异常。
   *
   * @param cause 底层 Hive Metastore 异常
   */
  public RuntimeMetaException(MetaException cause) {
    super(cause);
  }

  /**
   * 以 {@link MetaException} 为原因并附加格式化消息构造运行时异常。
   *
   * @param cause 底层 Hive Metastore 异常
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  public RuntimeMetaException(MetaException cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }

  /**
   * 以任意 {@link Throwable} 为原因并附加格式化消息构造运行时异常。
   *
   * @param throwable 底层异常原因
   * @param message 格式化模板字符串
   * @param args 格式化参数
   */
  @FormatMethod
  public RuntimeMetaException(Throwable throwable, String message, Object... args) {
    super(String.format(message, args), throwable);
  }
}
