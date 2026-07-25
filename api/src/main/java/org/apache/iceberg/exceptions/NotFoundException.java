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
 * 文件不存在异常：尝试读取一个不存在的文件时抛出。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与异常契约的最底层模块）。
 *
 * <p>触发场景：在读取数据文件/manifest 文件等底层对象时，对象存储报告该文件不存在 （可能因并发删除、孤儿清理或元数据不一致）。
 *
 * <p>设计意图：以 {@link RuntimeException} 为基类，构造器接受 {@code String.format} 风格模板， 配合 {@link FormatMethod}
 * 做编译期格式化校验。未实现 {@link CleanableFailure}， 因为文件层缺失通常不在提交清理路径上。
 *
 * <p>上下游关系：由 core 模块的文件读取逻辑抛出；调用方据以跳过或重建缺失文件。
 */
public class NotFoundException extends RuntimeException {
  /**
   * 构造一个文件不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NotFoundException(String message, Object... args) {
    super(String.format(message, args));
  }

  /**
   * 构造一个带原因的文件不存在异常，消息按 {@link String#format(String, Object...)} 格式化。
   *
   * @param cause 原始异常
   * @param message 消息模板
   * @param args 模板参数
   */
  @FormatMethod
  public NotFoundException(Throwable cause, String message, Object... args) {
    super(String.format(message, args), cause);
  }
}
