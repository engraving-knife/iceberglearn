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

/**
 * 文件级说明：Iceberg 内部自有的、可配置对象接口。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包，承担基于 HDFS 的 catalog/表/文件 IO 实现。
 *
 * <p>职责：声明一个泛型化的 {@code setConf(C conf)} 方法，用于在运行期向实现对象注入配置。
 *
 * <p>设计意图：刻意避免在编译期直接依赖 Hadoop 的 {@code org.apache.hadoop.conf.Configurable}，从而降低 Iceberg 公共接口对
 * Hadoop 运行时的硬依赖。需要注入 Hadoop {@link org.apache.hadoop.conf.Configuration} 的类 可实现本接口，由调用方按需传入具体配置类型。
 *
 * <p>上下游关系：被 {@code HadoopCatalog}、{@code HadoopTables} 等使用 Hadoop {@link
 * org.apache.hadoop.conf.Configuration} 的类作为可配置入口。
 *
 * @param <C> 配置的具体类型，通常为 Hadoop {@link org.apache.hadoop.conf.Configuration}
 */
public interface Configurable<C> {
  /**
   * 向当前对象注入配置。
   *
   * @param conf 配置对象，由调用方提供
   */
  void setConf(C conf);
}
