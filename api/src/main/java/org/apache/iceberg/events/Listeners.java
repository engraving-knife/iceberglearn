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
package org.apache.iceberg.events;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 事件监听器静态注册中心：全局管理 {@link Listener} 的注册与事件分发。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与事件契约的最底层模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按事件类型（{@link Class}）注册 {@link Listener}。
 *   <li>当事件发生时，根据事件运行时类型查找并回调所有匹配的监听器。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用进程级静态 Map 作为注册表，让任意代码位置都能便捷地发布/订阅事件， 无需显式传递事件总线实例。
 *   <li>Map 与 Queue 均为并发容器（{@link java.util.concurrent.ConcurrentHashMap}、 {@link
 *       ConcurrentLinkedQueue}），支持多线程并发注册与通知。
 *   <li>按事件类型精确匹配（{@code event.getClass()}），避免类型擦除带来的误派发， 同时降低无谓遍历开销。
 * </ul>
 *
 * <p>上下游关系：上游被 core 模块的扫描/提交逻辑调用 {@link #notifyAll(Object)} 发布事件； 下游持有使用方注册的 {@link Listener}
 * 实现并回调。
 */
public class Listeners {
  private Listeners() {}

  private static final Map<Class<?>, Queue<Listener<?>>> listeners = Maps.newConcurrentMap();

  /**
   * 注册一个监听器，监听指定类型的事件。
   *
   * <p>逻辑：以 eventType 为 key 在并发 Map 中查找（或新建）一个并发队列，将 listener 加入队列。 使用 {@code computeIfAbsent}
   * 保证同类型首个注册时队列的原子创建。
   *
   * @param listener 待注册的监听器
   * @param eventType 监听器关注的事件类型
   * @param <E> 事件类型
   */
  public static <E> void register(Listener<E> listener, Class<E> eventType) {
    Queue<Listener<?>> list =
        listeners.computeIfAbsent(eventType, k -> new ConcurrentLinkedQueue<>());
    list.add(listener);
  }

  /**
   * 向所有注册了 event 运行时类型的监听器派发该事件。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>先校验 event 非空。
   *   <li>按 {@code event.getClass()} 精确查找监听器队列（不支持父类型匹配）。
   *   <li>遍历队列，将每个 Listener 强转为 {@code Listener<E>} 后回调其 {@code notify}。 此处
   *       {@code @SuppressWarnings("unchecked")} 是必要的，因为注册时类型已被擦除。
   * </ul>
   *
   * <p>注意：回调在调用线程内同步执行；单个监听器抛出异常会中断后续监听器的通知。
   *
   * @param event 待分发的事件，不能为 null
   * @param <E> 事件类型
   */
  @SuppressWarnings("unchecked")
  public static <E> void notifyAll(E event) {
    Preconditions.checkNotNull(event, "Cannot notify listeners for a null event.");

    Queue<Listener<?>> list = listeners.get(event.getClass());
    if (list != null) {
      for (Listener<?> value : list) {
        Listener<E> listener = (Listener<E>) value;
        listener.notify(event);
      }
    }
  }
}
