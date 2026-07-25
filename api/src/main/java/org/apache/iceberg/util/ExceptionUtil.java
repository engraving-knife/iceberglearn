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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常处理工具类：提供带类型化受检异常声明的"try-catch-finally"包装执行能力， 并支持把任意 {@link Throwable} 按类型重新抛出。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #castAndThrow} 把 Throwable 按优先级（RuntimeException/Error/指定类型/兜底包装）
 *       重新抛出，便于在泛型回调中传播受检异常。
 *   <li>{@link #runSafely} 系列方法提供 1~3 个类型化受检异常的 try-catch-finally 模板， 支持传入 catch 与 finally 块，并妥善处理
 *       catch/finally 中再次抛出的异常（抑制并附加）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Java 泛型 Lambda 无法直接声明 throws，本类通过 {@link Block} 的多类型参数 （E1/E2/E3）在签名中显式传播受检异常，使调用方仍能用具体
 *       catch 处理。
 *   <li>catch/finally 块中抛出的二级异常会作为 suppressed 附加到主异常，避免丢失， 符合 try-with-resources 的异常抑制语义。
 *   <li>finally 中若主流程正常但 finally 抛异常，则按相同类型化策略抛出。
 * </ul>
 *
 * <p>上下游关系：被 core 模块及引擎集成代码用于在泛型回调中安全传播受检异常。
 */
public class ExceptionUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ExceptionUtil.class);

  private ExceptionUtil() {}

  /**
   * 将异常按优先级重新抛出：RuntimeException 优先，其次 Error，再次指定类型，最后包装为 RuntimeException 抛出。
   *
   * <p>逻辑：依次判断异常类型，命中即抛出对应类型；都不命中则包装为 RuntimeException。
   *
   * @param exception 待抛出的异常
   * @param exceptionClass 期望的受检异常类型
   * @param <E> 受检异常类型
   * @throws E 若异常属于指定类型
   * @throws RuntimeException 兜底抛出
   */
  @SuppressWarnings("unchecked")
  public static <E extends Exception> void castAndThrow(
      Throwable exception, Class<E> exceptionClass) throws E {
    if (exception instanceof RuntimeException) {
      throw (RuntimeException) exception;
    } else if (exception instanceof Error) {
      throw (Error) exception;
    } else if (exceptionClass.isInstance(exception)) {
      throw (E) exception;
    }
    throw new RuntimeException(exception);
  }

  /**
   * 可抛出至多 3 种受检异常的代码块接口。
   *
   * @param <R> 返回值类型
   * @param <E1> 第一种受检异常
   * @param <E2> 第二种受检异常
   * @param <E3> 第三种受检异常
   */
  public interface Block<R, E1 extends Exception, E2 extends Exception, E3 extends Exception> {
    R run() throws E1, E2, E3;
  }

  /** catch 块接口，接收主流程抛出的失败。 */
  public interface CatchBlock {
    void run(Throwable failure) throws Exception;
  }

  /** finally 块接口。 */
  public interface FinallyBlock {
    void run() throws Exception;
  }

  /**
   * 安全执行无受检异常的代码块（catch/finally 可选）。
   *
   * @param block 主代码块
   * @param catchBlock catch 块，可为 null
   * @param finallyBlock finally 块，可为 null
   * @param <R> 返回值类型
   * @return 主代码块的返回值
   */
  public static <R> R runSafely(
      Block<R, RuntimeException, RuntimeException, RuntimeException> block,
      CatchBlock catchBlock,
      FinallyBlock finallyBlock) {
    return runSafely(
        block,
        catchBlock,
        finallyBlock,
        RuntimeException.class,
        RuntimeException.class,
        RuntimeException.class);
  }

  /**
   * 安全执行声明 1 种受检异常的代码块。
   *
   * @param block 主代码块
   * @param catchBlock catch 块，可为 null
   * @param finallyBlock finally 块，可为 null
   * @param e1Class 第一种受检异常类型
   * @param <R> 返回值类型
   * @param <E1> 第一种受检异常
   * @return 主代码块的返回值
   * @throws E1 若主代码块抛出该类型异常
   */
  public static <R, E1 extends Exception> R runSafely(
      Block<R, E1, RuntimeException, RuntimeException> block,
      CatchBlock catchBlock,
      FinallyBlock finallyBlock,
      Class<? extends E1> e1Class)
      throws E1 {
    return runSafely(
        block, catchBlock, finallyBlock, e1Class, RuntimeException.class, RuntimeException.class);
  }

  /**
   * 安全执行声明 2 种受检异常的代码块。
   *
   * @param block 主代码块
   * @param catchBlock catch 块，可为 null
   * @param finallyBlock finally 块，可为 null
   * @param e1Class 第一种受检异常类型
   * @param e2Class 第二种受检异常类型
   * @param <R> 返回值类型
   * @param <E1> 第一种受检异常
   * @param <E2> 第二种受检异常
   * @return 主代码块的返回值
   * @throws E1 若主代码块抛出该类型异常
   * @throws E2 若主代码块抛出该类型异常
   */
  public static <R, E1 extends Exception, E2 extends Exception> R runSafely(
      Block<R, E1, E2, RuntimeException> block,
      CatchBlock catchBlock,
      FinallyBlock finallyBlock,
      Class<? extends E1> e1Class,
      Class<? extends E2> e2Class)
      throws E1, E2 {
    return runSafely(block, catchBlock, finallyBlock, e1Class, e2Class, RuntimeException.class);
  }

  /**
   * 安全执行声明至多 3 种受检异常的代码块（核心实现）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>执行 block，正常则记录返回值；捕获 Throwable 作为 failure。
   *   <li>若有 catchBlock 则执行它，若 catchBlock 自身抛异常则作为 suppressed 附加到 failure。
   *   <li>按 e1/e2/e3/RuntimeException 顺序尝试把 failure 作为对应类型抛出；都不匹配则包装为 RuntimeException 抛出。
   *   <li>finally 块无论是否异常都执行；若主流程正常而 finally 抛异常，则按相同策略抛出； 若主流程已异常而 finally 又抛异常，则附加为 suppressed。
   * </ul>
   *
   * @param block 主代码块
   * @param catchBlock catch 块，可为 null
   * @param finallyBlock finally 块，可为 null
   * @param e1Class 第一种受检异常类型
   * @param e2Class 第二种受检异常类型
   * @param e3Class 第三种受检异常类型
   * @param <R> 返回值类型
   * @param <E1> 第一种受检异常
   * @param <E2> 第二种受检异常
   * @param <E3> 第三种受检异常
   * @return 主代码块的返回值
   * @throws E1 主代码块可能抛出的第一种受检异常
   * @throws E2 主代码块可能抛出的第二种受检异常
   * @throws E3 主代码块可能抛出的第三种受检异常
   */
  @SuppressWarnings("Finally")
  public static <R, E1 extends Exception, E2 extends Exception, E3 extends Exception> R runSafely(
      Block<R, E1, E2, E3> block,
      CatchBlock catchBlock,
      FinallyBlock finallyBlock,
      Class<? extends E1> e1Class,
      Class<? extends E2> e2Class,
      Class<? extends E3> e3Class)
      throws E1, E2, E3 {

    Throwable failure = null;
    try {
      return block.run();

    } catch (Throwable t) {
      failure = t;

      if (catchBlock != null) {
        try {
          catchBlock.run(failure);
        } catch (Exception e) {
          LOG.warn("Suppressing failure in catch block", e);
          failure.addSuppressed(e);
        }
      }

      tryThrowAs(failure, e1Class);
      tryThrowAs(failure, e2Class);
      tryThrowAs(failure, e3Class);
      tryThrowAs(failure, RuntimeException.class);
      throw new RuntimeException("Unknown throwable", failure);

    } finally {
      if (finallyBlock != null) {
        try {
          finallyBlock.run();
        } catch (Exception e) {
          if (failure != null) {
            LOG.warn("Suppressing failure in finally block", e);
            failure.addSuppressed(e);
          } else {
            tryThrowAs(e, e1Class);
            tryThrowAs(e, e2Class);
            tryThrowAs(e, e3Class);
            tryThrowAs(e, RuntimeException.class);
            throw new RuntimeException("Unknown exception in finally block", e);
          }
        }
      }
    }
  }

  /**
   * 若 failure 属于指定类型则按该类型抛出。
   *
   * @param failure 待判断的异常
   * @param excClass 目标异常类型
   * @param <E> 异常类型
   * @throws E 若 failure 属于该类型
   */
  private static <E extends Exception> void tryThrowAs(Throwable failure, Class<E> excClass)
      throws E {
    if (excClass.isInstance(failure)) {
      throw excClass.cast(failure);
    }
  }
}
