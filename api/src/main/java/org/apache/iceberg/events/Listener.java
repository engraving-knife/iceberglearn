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

/**
 * 事件监听器接口：接收指定类型事件的回调通知。
 *
 * <p>所属模块：iceberg-api（定义核心公共 API 与事件契约的最底层模块）。
 *
 * <p>职责：定义单一 {@code notify(E)} 方法，由 SDK 使用方实现后通过 {@link Listeners#register} 注册，在事件发生时由 {@link
 * Listeners#notifyAll(Object)} 统一回调。
 *
 * <p>设计意图：采用泛型参数 {@code <E>} 让一个监听器与具体事件类型绑定，编译期保证类型安全； 单方法接口契合观察者模式，便于以 Lambda 形式注册。
 *
 * <p>上下游关系：被 {@link Listeners} 持有并调用；典型事件实现有 {@link ScanEvent}、{@link IncrementalScanEvent} 等。
 *
 * @param <E> 监听器所关注的事件类型
 */
public interface Listener<E> {
  /**
   * 接收事件通知。
   *
   * <p>实现方应避免在此方法内执行长时间阻塞操作，以免影响其他监听器的回调。
   *
   * @param event 已发生的事件
   */
  void notify(E event);
}
