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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.primitives.Bytes;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Guava 类清单持有类：声明需要保留进重定位后 Guava jar 的全部 Guava 类。
 *
 * <p>所属模块：iceberg-bundled-guava。该模块是 Iceberg 最底层的基础库之一，负责将 Guava 重新打包并重定位到 {@code
 * org.apache.iceberg.relocated.com.google.common.*} 命名空间， 供 Iceberg 其他所有模块（api/core
 * 及各引擎集成模块）统一依赖，从而避免与用户 classpath 上可能存在的不同版本 Guava 产生依赖冲突。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中罗列 Iceberg 实际需要使用的 Guava 工具类，作为打包白名单。
 *   <li>在类加载阶段强制引用这些 Guava 类，确保它们不会被 shade/minimize 插件裁剪掉。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>声明即保留：Maven Shade 插件在 minimizeJar 时只保留被字节码实际引用的类。本类通过 静态初始化块中调用各 Guava 类的 {@code
 *       Class.getName()}，在不真正调用业务逻辑的 前提下让这些类进入依赖图，从而被打入最终 jar。
 *   <li>集中可维护：所有需要保留的 Guava 类集中于这一处清单，方便增删与审查，避免散落 到各业务类中难以追踪。新增使用某个 Guava 类时需同步增补此清单。
 *   <li>实现启发自 Apache Avro 项目的同名 GuavaClasses。
 * </ul>
 *
 * <p>上下游关系：本类不依赖 Iceberg 任何业务模块，仅依赖原始 Guava；其本身不被代码直接调用， 而是被 bundled-guava 模块的构建（Shade 插件）所消费，最终产物供
 * Iceberg 全部其他模块 以 relocated 包名（{@code org.apache.iceberg.relocated.com.google.common.*}）间接使用。
 */
// inspired in part by
// https://github.com/apache/avro/blob/release-1.8.2/lang/java/guava/src/main/java/org/apache/avro/GuavaClasses.java
@SuppressWarnings("ReturnValueIgnored")
public class GuavaClasses {

  /*
   * 在此引用各 Guava 类，使其被纳入 minimize + relocate 后的 Guava jar。
   *
   * <p>逻辑：通过 {@code X.class.getName()} 触发对应类的引用加载，但不调用其方法，
   * 既能让 Shade 插件在依赖图中识别到这些类从而保留，又避免引入不必要的运行期副作用。
   * 列表内容须与 Iceberg 各模块实际使用的 Guava 类保持一致，新增使用需同步增补。
   */
  static {
    VisibleForTesting.class.getName();
    Joiner.class.getName();
    MoreObjects.class.getName();
    Objects.class.getName();
    Preconditions.class.getName();
    Splitter.class.getName();
    Throwables.class.getName();
    BiMap.class.getName();
    FluentIterable.class.getName();
    ImmutableBiMap.class.getName();
    ImmutableList.class.getName();
    ImmutableMap.class.getName();
    ImmutableSet.class.getName();
    Iterables.class.getName();
    Iterators.class.getName();
    ListMultimap.class.getName();
    Lists.class.getName();
    MapMaker.class.getName();
    Maps.class.getName();
    Multimap.class.getName();
    Multimaps.class.getName();
    Ordering.class.getName();
    Sets.class.getName();
    Streams.class.getName();
    Hasher.class.getName();
    HashFunction.class.getName();
    Hashing.class.getName();
    Files.class.getName();
    Bytes.class.getName();
    Resources.class.getName();
    MoreExecutors.class.getName();
    ThreadFactoryBuilder.class.getName();
    Iterables.class.getName();
    CountingOutputStream.class.getName();
    Suppliers.class.getName();
    Stopwatch.class.getName();
  }
}
