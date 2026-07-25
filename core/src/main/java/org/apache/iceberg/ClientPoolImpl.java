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
package org.apache.iceberg;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端连接池抽象实现：管理可复用的外部客户端（如 HiveMetastore Client）。
 *
 * <p>所属模块：iceberg-core（核心实现层），为需要长期持有外部客户端的 Catalog 提供连接池能力。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护固定大小的客户端队列（{@link ArrayDeque}），按需借出与归还。
 *   <li>在客户端用尽时阻塞等待，并通过 signal 机制唤醒等待线程。
 *   <li>识别连接异常并在 retry 时自动重连。
 *   <li>关闭时逐个关闭所有客户端，等待在用客户端归还。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>泛型 C 为客户端类型，E 为可识别为“连接异常”的异常类型，命中时触发重连。
 *   <li>双监视器（this 与 signal）分离客户端队列操作与等待唤醒，降低锁竞争。
 *   <li>等待时每秒超时唤醒一次，避免错过 signal 的边界情况。
 * </ul>
 *
 * <p>上下游关系：被 {@code HiveClientPool} 等具体连接池继承；上层被 HiveCatalog 等调用。
 */
public abstract class ClientPoolImpl<C, E extends Exception>
    implements Closeable, ClientPool<C, E> {
  private static final Logger LOG = LoggerFactory.getLogger(ClientPoolImpl.class);

  private final int poolSize;
  private final Deque<C> clients;
  private final Class<? extends E> reconnectExc;
  private final Object signal = new Object();
  private final boolean retryByDefault;
  private volatile int currentSize;
  private boolean closed;

  /**
   * 构造方法。
   *
   * @param poolSize 池大小
   * @param reconnectExc 视为“连接异常”的异常类型，命中时触发重连
   * @param retryByDefault 默认是否在连接异常时重试
   */
  public ClientPoolImpl(int poolSize, Class<? extends E> reconnectExc, boolean retryByDefault) {
    this.poolSize = poolSize;
    this.reconnectExc = reconnectExc;
    this.clients = new ArrayDeque<>(poolSize);
    this.currentSize = 0;
    this.closed = false;
    this.retryByDefault = retryByDefault;
  }

  /** 使用默认 retry 策略执行 action。 */
  @Override
  public <R> R run(Action<R, C, E> action) throws E, InterruptedException {
    return run(action, retryByDefault);
  }

  /**
   * 执行 action：借出客户端、运行 action、归还客户端，必要时重连重试。
   *
   * <p>逻辑：从池中借出客户端并执行 action；若抛出异常且 retry 开启且为连接异常， 则尝试 reconnect 后重新执行一次 action；无论成功失败都在 finally
   * 中归还客户端。
   *
   * @param action 要执行的操作
   * @param retry 是否在连接异常时重试
   * @return action 的返回值
   * @throws E action 抛出的异常
   * @throws InterruptedException 等待客户端时被中断
   */
  @Override
  public <R> R run(Action<R, C, E> action, boolean retry) throws E, InterruptedException {
    C client = get();
    try {
      return action.run(client);

    } catch (Exception exc) {
      if (retry && isConnectionException(exc)) {
        try {
          client = reconnect(client);
        } catch (Exception ignored) {
          // if reconnection throws any exception, rethrow the original failure
          throw reconnectExc.cast(exc);
        }

        return action.run(client);
      }

      throw exc;

    } finally {
      release(client);
    }
  }

  /** 子类实现：创建新客户端实例。 */
  protected abstract C newClient();

  /** 子类实现：基于旧客户端重连，返回新客户端。 */
  protected abstract C reconnect(C client);

  /** 判断异常是否为连接异常（类型匹配 reconnectExc）。 */
  protected boolean isConnectionException(Exception exc) {
    return reconnectExc.isInstance(exc);
  }

  /** 子类实现：关闭单个客户端。 */
  protected abstract void close(C client);

  /**
   * 关闭连接池。
   *
   * <p>逻辑：标记关闭；循环取出空闲客户端并关闭；若空闲队列空但仍有在用客户端， 则等待 signal 唤醒（每秒超时一次以防遗漏）；被中断时仅记录警告。
   */
  @Override
  public void close() {
    this.closed = true;
    try {
      while (currentSize > 0) {
        if (!clients.isEmpty()) {
          synchronized (this) {
            if (!clients.isEmpty()) {
              C client = clients.removeFirst();
              close(client);
              currentSize -= 1;
            }
          }
        }
        if (clients.isEmpty() && currentSize > 0) {
          // wake every second in case this missed the signal
          synchronized (signal) {
            signal.wait(1000);
          }
        }
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while shutting down pool. Some clients may not be closed.", e);
    }
  }

  /**
   * 借出客户端：优先取空闲客户端，否则在未达上限时新建。
   *
   * <p>逻辑：双重检查——先非同步判断队列非空或未达上限，再进入同步块取/建客户端； 队列空且达上限时等待 signal（每秒超时一次）。
   *
   * @return 可用客户端
   * @throws InterruptedException 等待时被中断
   * @throws IllegalStateException 池已关闭
   */
  private C get() throws InterruptedException {
    Preconditions.checkState(!closed, "Cannot get a client from a closed pool");
    while (true) {
      if (!clients.isEmpty() || currentSize < poolSize) {
        synchronized (this) {
          if (!clients.isEmpty()) {
            return clients.removeFirst();
          } else if (currentSize < poolSize) {
            C client = newClient();
            currentSize += 1;
            return client;
          }
        }
      }
      synchronized (signal) {
        // wake every second in case this missed the signal
        signal.wait(1000);
      }
    }
  }

  /** 归还客户端：放入队列头部并唤醒一个等待线程。 */
  private void release(C client) {
    synchronized (this) {
      clients.addFirst(client);
    }
    synchronized (signal) {
      signal.notify();
    }
  }

  /** 返回池大小。 */
  public int poolSize() {
    return poolSize;
  }

  /** 返回池是否已关闭。 */
  public boolean isClosed() {
    return closed;
  }
}
