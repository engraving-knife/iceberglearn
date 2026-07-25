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

/**
 * 客户端连接池接口：封装对底层外部客户端（如 Hive Metastore、Hadoop FileSystem 等）的复用与并发访问。
 *
 * <p>所属模块：iceberg-core（基础设施工具层，被表与目录实现使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>维护一组可复用的客户端实例，避免频繁创建/销毁的开销；
 *   <li>提供 {@code run} 方法把"借用客户端 → 执行 Action → 归还客户端"的流程封装为受控调用， 屏蔽池化细节。
 * </ul>
 *
 * <p>设计意图：把客户端池化抽象为接口，便于不同后端（Hive、REST 等）各自实现；通过 {@link Action} 函数式接口让调用方专注于业务逻辑；{@code retry}
 * 参数支持在客户端失效时 自动重试一次。
 *
 * <p>上下游关系：被 catalog/表操作类使用，典型实现如 {@code HiveClientPool}。
 *
 * @param <C> 客户端类型
 * @param <E> 可能抛出的受检异常类型
 */
public interface ClientPool<C, E extends Exception> {
  /**
   * 在借用的客户端上执行的操作。
   *
   * @param <R> 返回值类型
   * @param <C> 客户端类型
   * @param <E> 受检异常类型
   */
  interface Action<R, C, E extends Exception> {
    /**
     * 在给定客户端上执行操作并返回结果。
     *
     * @param client 借用的客户端
     * @return 执行结果
     * @throws E 业务异常
     */
    R run(C client) throws E;
  }

  /**
   * 借用一个客户端执行 Action，执行完毕后归还；发生瞬时错误时默认不重试。
   *
   * @param action 待执行的操作
   * @param <R> 返回值类型
   * @return 执行结果
   * @throws E 业务异常
   * @throws InterruptedException 若线程被中断
   */
  <R> R run(Action<R, C, E> action) throws E, InterruptedException;

  /**
   * 借用一个客户端执行 Action，执行完毕后归还。
   *
   * @param action 待执行的操作
   * @param retry 是否在客户端失效时重试一次（重新借用新客户端）
   * @param <R> 返回值类型
   * @return 执行结果
   * @throws E 业务异常
   * @throws InterruptedException 若线程被中断
   */
  <R> R run(Action<R, C, E> action, boolean retry) throws E, InterruptedException;
}
