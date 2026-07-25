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
 * Iceberg Spark 集成相关组件，封装提交或表元数据。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 CommitMetadata。
 */
public class CommitMetadata {

  /** 构造 CommitMetadata 实例。 */
  private CommitMetadata() {}

  private static final ThreadLocal<Map<String, String>> COMMIT_PROPERTIES =
      ThreadLocal.withInitial(ImmutableMap::of);

  /**
   * 返回带新设置的副本。
   *
   * @param properties 参数
   * @param callable 参数
   * @param exClass 参数
   * @return 结果对象
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

  /** 提交事务或写入结果。 */
  public static Map<String, String> commitProperties() {
    return COMMIT_PROPERTIES.get();
  }
}
