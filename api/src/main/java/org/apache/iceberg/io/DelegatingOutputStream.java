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
package org.apache.iceberg.io;

import java.io.OutputStream;

/**
 * 文件级说明：委托输出流标记接口，用于暴露被包装的真实输出流。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：提供 {@link #getDelegate()} 方法返回底层真正承担写操作的 {@link OutputStream}。
 *
 * <p>设计意图：Iceberg 或引擎在 {@link PositionOutputStream} 之上常会叠加多层包装（加密、 缓冲、统计等），但在某些场景下需要拿到原始流以使用特定
 * API（如 Hadoop 的 FSDataOutputStream 的同步/位置能力）。本接口让包装类显式声明“我是委托类”，便于上层解包 获取底层流。
 *
 * <p>上下游关系：由包装输出流的实现额外实现；被需要访问底层流的上层逻辑（如某些引擎 集成）调用。
 */
public interface DelegatingOutputStream {
  /**
   * 返回被包装的真实 {@link OutputStream}。
   *
   * @return 底层输出流
   */
  OutputStream getDelegate();
}
