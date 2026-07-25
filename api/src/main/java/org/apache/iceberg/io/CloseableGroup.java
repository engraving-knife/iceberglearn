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

import java.io.Closeable;
import java.io.IOException;
import java.util.Deque;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.ExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：多资源关闭辅助类，用于集中管理与统一关闭一组 {@link Closeable} / {@link AutoCloseable} 资源。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #addCloseable(Closeable)} / {@link #addCloseable(AutoCloseable)} 注册待关闭 的资源。
 *   <li>通过 {@link #close()} 一次性关闭全部已注册资源。
 *   <li>支持通过 {@link #setSuppressCloseFailure(boolean)} 决定是否抑制关闭过程中的异常。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>既可被继承使用，也可作为组合成员使用，提供灵活的资源管理方式。
 *   <li>把 {@link Closeable} 当作 {@link AutoCloseable} 统一处理，并保证关闭幂等性：即使 并发调用 close，每个资源也只会被关闭一次。
 *   <li>关闭过程中抛出的非 IO 受检异常会被包装为 RuntimeException；可选抑制关闭失败， 以保证即使某个资源关闭失败，其余资源仍能被尝试关闭。
 * </ul>
 *
 * <p>上下游关系：被需要管理多个子资源生命周期的类（如 {@link CloseableIterable} 的
 * ConcatCloseableIterable、各写入器、扫描任务等）作为基类或成员使用。
 */
public class CloseableGroup implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(CloseableGroup.class);

  private final Deque<AutoCloseable> closeables = Lists.newLinkedList();
  private boolean suppressCloseFailure = false;

  /**
   * 注册一个待本类管理的 {@link Closeable} 资源。
   *
   * @param closeable 待关闭资源
   */
  public void addCloseable(Closeable closeable) {
    closeables.add(closeable);
  }

  /**
   * 注册一个待本类管理的 {@link AutoCloseable} 资源，将按 closeable 方式处理。
   *
   * @param autoCloseable 待关闭资源
   */
  public void addCloseable(AutoCloseable autoCloseable) {
    closeables.add(autoCloseable);
  }

  /**
   * 设置是否抑制本类所跟踪资源在关闭时抛出的异常。
   *
   * <p>设计要点：抑制异常有助于保证所有资源的 close 方法都被调用，避免因单个资源关闭 失败而提前中断导致其余资源泄漏。
   *
   * @param shouldSuppress true 表示希望抑制关闭失败
   */
  public void setSuppressCloseFailure(boolean shouldSuppress) {
    this.suppressCloseFailure = shouldSuppress;
  }

  /**
   * 关闭全部已注册的资源，每个资源的 close 只会被调用一次。
   *
   * <p>逻辑：从双端队列中依次取出资源并关闭；若关闭过程中抛出异常，则根据 {@link #setSuppressCloseFailure(boolean)}
   * 的设置决定是仅记录日志还是向上抛出。 AutoCloseable 抛出的受检异常会被包装为 RuntimeException 或 IOException。
   *
   * @throws IOException 关闭过程中抛出的 IO 异常（未抑制时）
   */
  @Override
  public void close() throws IOException {
    while (!closeables.isEmpty()) {
      AutoCloseable toClose = closeables.pollFirst();
      if (toClose != null) {
        try {
          toClose.close();
        } catch (Exception e) {
          if (suppressCloseFailure) {
            LOG.error("Exception suppressed when attempting to close resources", e);
          } else {
            ExceptionUtil.castAndThrow(e, IOException.class);
          }
        }
      }
    }
  }
}
