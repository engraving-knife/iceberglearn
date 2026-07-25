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
package org.apache.iceberg.util;

import java.io.Closeable;
import java.io.IOException;
import org.apache.iceberg.exceptions.RuntimeIOException;

/**
 * 异常处理工具类，封装关闭资源、抑制异常等通用异常处理逻辑。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>安全关闭 {@link Closeable}，可选抑制关闭时抛出的 IOException；
 *   <li>在已有异常的清理路径中执行额外操作，把新产生的异常作为 suppressed 挂到原异常上， 保证原始异常不丢失。
 * </ul>
 *
 * <p>设计意图：try-with-resources 在复杂清理场景下不够灵活，且 Iceberg 常需在 finally 中 关闭多个资源并保留首个异常。本类提供 addSuppressed
 * 语义的封装，遵循“抛出首个异常、 其余异常附加为 suppressed”的最佳实践。
 *
 * <p>上下游关系：被 core 各类用于资源关闭与清理；依赖 api 的 {@link RuntimeIOException}。
 */
public class Exceptions {
  private Exceptions() {}

  /**
   * 关闭 Closeable 资源，可选抑制关闭过程抛出的 IOException。
   *
   * @param closeable 待关闭资源，可为 null 时由调用方自行判空
   * @param suppressExceptions true 时忽略关闭异常；false 时把 IOException 包装为 {@link RuntimeIOException} 抛出
   */
  public static void close(Closeable closeable, boolean suppressExceptions) {
    try {
      closeable.close();
    } catch (IOException e) {
      if (!suppressExceptions) {
        throw new RuntimeIOException(e, "Failed calling close");
      }
      // otherwise, ignore the exception
    }
  }

  /**
   * 在已有异常的清理路径中执行 run，把 run 抛出的异常作为 suppressed 附加到 alreadyThrown。
   *
   * <p>设计要点：保证原始异常不被后续清理异常覆盖，符合 try-with-resources 的异常传播语义。
   *
   * @param <E> 异常类型
   * @param alreadyThrown 已捕获的主异常
   * @param run 清理动作，可能抛异常
   * @return 传入的 alreadyThrown（可能已附加 suppressed 异常）
   */
  public static <E extends Exception> E suppressExceptions(E alreadyThrown, Runnable run) {
    try {
      run.run();
    } catch (Exception e) {
      alreadyThrown.addSuppressed(e);
    }
    return alreadyThrown;
  }

  /**
   * 在已有异常的清理路径中执行 run，附加 suppressed 后直接抛出主异常。
   *
   * @param <E> 异常类型
   * @param alreadyThrown 已捕获的主异常
   * @param run 清理动作，可能抛异常
   * @throws E 始终抛出 alreadyThrown
   */
  public static <E extends Exception> void suppressAndThrow(E alreadyThrown, Runnable run)
      throws E {
    throw suppressExceptions(alreadyThrown, run);
  }
}
