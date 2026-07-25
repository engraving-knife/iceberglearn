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
package org.apache.iceberg.hadoop;

import java.util.function.Function;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.util.SerializableSupplier;

/**
 * 文件级说明：扩展 Hadoop {@link Configurable} 接口，为可序列化的 Iceberg 对象提供更友好的 Hadoop 配置序列化支持。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 Hadoop {@link Configurable}，保留 {@code setConf/getConf} 的标准语义。
 *   <li>新增 {@link #serializeConfWith(Function)}，允许调用方提供一个把 {@link Configuration} 转换为 {@link
 *       SerializableSupplier} 的序列化函数。
 * </ul>
 *
 * <p>设计意图：Hadoop 的 {@link Configuration} 本身不可序列化，但像 {@link org.apache.iceberg.io.FileIO}
 * 这类对象往往需要跨进程/跨任务序列化 （如 Spark/Flink 任务分发）。实现本接口的对象可在序列化前用传入的函数把 {@link Configuration} 转为可序列化的
 * supplier，从而安全地随对象一起传输，避免直接 携带不可序列化的 {@link Configuration}。
 *
 * <p>上下游关系：被 {@link HadoopFileIO} 等需要持久化 Hadoop 配置的类实现；由 引擎集成层在序列化前调用 {@link
 * #serializeConfWith(Function)} 注入序列化策略。
 */
public interface HadoopConfigurable extends Configurable {

  /**
   * 注册一个用于序列化 Hadoop {@link Configuration} 的函数。
   *
   * <p>逻辑：实现方应将自身当前的 Hadoop {@link Configuration} 传入该函数， 并用返回的 {@link SerializableSupplier}
   * 替换内部持有的不可序列化配置引用， 从而保证后续序列化操作安全可靠。
   *
   * @param confSerializer 一个把 Hadoop {@link Configuration} 转换为可序列化 supplier 的函数
   */
  void serializeConfWith(
      Function<Configuration, SerializableSupplier<Configuration>> confSerializer);
}
