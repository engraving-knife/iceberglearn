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
package org.apache.iceberg.io;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Map;

/**
 * 文件级说明：可插拔的文件读写删除抽象接口，是 Iceberg 与底层存储（HDFS/S3/GCS/Azure 等） 交互的统一入口。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。表元数据文件与数据文件均通过本接口读写。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #newInputFile(String)} 创建读取文件的 {@link InputFile}。
 *   <li>通过 {@link #newOutputFile(String)} 创建写文件的 {@link OutputFile}。
 *   <li>通过 {@link #deleteFile(String)} 删除指定路径文件。
 *   <li>提供 {@link #initialize(Map)} 从 catalog 属性初始化、{@link #close()} 释放资源的能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>可插拔存储：将存储差异屏蔽在实现层，使 Iceberg 表操作逻辑与具体存储解耦，由 catalog 通过配置加载具体 FileIO 实现（如
 *       S3FileIO、HadoopFileIO 等）。
 *   <li>实现必须可序列化（{@link Serializable}）：Spark 等引擎会序列化 FileIO 实例并分发到 executor 执行，因此 FileIO 需要能跨 JVM
 *       传输，且其字段也应是可序列化的。
 *   <li>默认方法留空：{@link #initialize} / {@link #close} 提供默认空实现，老版本实现无须改动 即可向前兼容新接口契约。
 * </ul>
 *
 * <p>上下游关系：由 catalog 创建并交给表操作（提交、扫描）使用；实现侧通常依赖对应存储 SDK， 上游被 iceberg-core 的 {@code TableOperations}
 * / 扫描任务调用。
 */
public interface FileIO extends Serializable, Closeable {

  /**
   * 创建用于读取指定路径文件的 {@link InputFile} 实例。
   *
   * @param path 文件全限定路径
   * @return 用于读取的 {@link InputFile}
   */
  InputFile newInputFile(String path);

  /**
   * 创建用于读取指定路径文件的 {@link InputFile} 实例，并显式传入已知文件长度。
   *
   * <p>设计要点：部分对象存储获取文件长度代价较高，调用方若已知长度可避免实现再次发起 HEAD 请求；默认实现忽略长度，直接回退到 {@link
   * #newInputFile(String)}。
   *
   * @param path 文件全限定路径
   * @param length 已知文件长度（字节）
   * @return 用于读取的 {@link InputFile}
   */
  default InputFile newInputFile(String path, long length) {
    return newInputFile(path);
  }

  /**
   * 创建用于向指定路径写文件的 {@link OutputFile} 实例。
   *
   * @param path 文件全限定路径
   * @return 用于写入的 {@link OutputFile}
   */
  OutputFile newOutputFile(String path);

  /**
   * 删除指定路径的文件。
   *
   * @param path 要删除的文件全限定路径
   */
  void deleteFile(String path);

  /**
   * 便捷方法：根据 {@link InputFile} 的 location 删除对应文件。
   *
   * @param file 要删除的 {@link InputFile}
   */
  default void deleteFile(InputFile file) {
    deleteFile(file.location());
  }

  /**
   * 便捷方法：根据 {@link OutputFile} 的 location 删除对应文件。
   *
   * @param file 要删除的 {@link OutputFile}
   */
  default void deleteFile(OutputFile file) {
    deleteFile(file.location());
  }

  /**
   * 返回用于配置本 FileIO 的属性映射。
   *
   * @return 配置属性映射
   * @throws UnsupportedOperationException 若本 FileIO 实现不对外暴露其配置属性
   */
  default Map<String, String> properties() {
    throw new UnsupportedOperationException(
        String.format("%s does not expose configuration properties", this.getClass().toString()));
  }

  /**
   * 根据 catalog 属性初始化 FileIO。
   *
   * <p>设计要点：默认空实现以兼容旧版本 FileIO；新实现可重写以接收 catalog 传入的连接、 凭证、加密等配置。
   *
   * @param properties catalog 属性
   */
  default void initialize(Map<String, String> properties) {}

  /**
   * 关闭 FileIO 以释放底层资源。
   *
   * <p>设计要点：仅在本 FileIO 实例不再被使用、且其持有的资源需要显式释放以避免泄漏时 才需要调用；默认空实现兼容老版本。
   */
  @Override
  default void close() {}
}
