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
package org.apache.iceberg.spark;

import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.iceberg.SnapshotSummary;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ExceptionUtil;

/**
 * 提交元数据工具类：基于 ThreadLocal 为 Iceberg 快照提交附加额外元数据。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块）。位于 spark 顶级包，提供线程局部的提交属性传递。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>用 ThreadLocal 保存当前线程的提交属性映射，供 Spark 写入器在 commit 时读取。
 *   <li>提供 {@link #withCommitProperties} 包裹 callable，使其中的快照提交自动附带这些属性。
 * </ul>
 *
 * <p>设计意图：Spark 作业在 executor 端多线程执行，但最终 commit 通常在 driver 单线程完成。 用 ThreadLocal
 * 避免参数层层透传，且保证线程隔离；finally 中重置为空 map 防止属性泄漏到后续提交。 属性名前缀 {@link
 * SnapshotSummary#EXTRA_METADATA_PREFIX} 会被剥离。
 *
 * <p>上下游关系：被 Spark 写入路径调用以注入自定义提交元数据；下游被 Iceberg core 的 快照提交逻辑读取（通过 {@link #commitProperties}）。
 */
public class CommitMetadata {

  private CommitMetadata() {}

  private static final ThreadLocal<Map<String, String>> COMMIT_PROPERTIES =
      ThreadLocal.withInitial(ImmutableMap::of);

  /**
   * 在带有指定提交属性的上下文中执行 callable。
   *
   * <p>逻辑：拷贝 properties 并去除 {@link SnapshotSummary#EXTRA_METADATA_PREFIX} 前缀， 设置到 ThreadLocal；执行
   * callable；无论成功失败都在 finally 中清空 ThreadLocal。 callable 抛出的异常会被转换为 exClass 类型抛出。
   *
   * @param properties 附加到快照的额外提交元数据
   * @param callable 待执行代码
   * @param exClass callable 预期抛出的异常类型
   * @param <R> 返回类型
   * @param <E> 异常类型
   * @return callable 执行结果
   * @throws E callable 抛出的异常
   */
  public static <R, E extends Exception> R withCommitProperties(
      Map<String, String> properties, Callable<R> callable, Class<E> exClass) throws E {
    Map<String, String> props = Maps.newHashMap();
    properties.forEach(
        (k, v) -> props.put(k.replace(SnapshotSummary.EXTRA_METADATA_PREFIX, ""), v));

    COMMIT_PROPERTIES.set(props);
    try {
      return callable.call();
    } catch (Throwable e) {
      ExceptionUtil.castAndThrow(e, exClass);
      return null;
    } finally {
      COMMIT_PROPERTIES.set(ImmutableMap.of());
    }
  }

  /** 返回当前线程设置的提交属性映射（仅供提交逻辑读取）。 */
  public static Map<String, String> commitProperties() {
    return COMMIT_PROPERTIES.get();
  }
}
