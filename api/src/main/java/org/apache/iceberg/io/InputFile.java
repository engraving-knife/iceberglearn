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

import java.io.IOException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.RuntimeIOException;

/**
 * 文件级说明：输入文件抽象接口，用于通过 {@link SeekableInputStream} 读取文件字节内容。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。本接口设计参考 Parquet 的 InputFile。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>暴露文件长度、位置、是否存在等元信息。
 *   <li>通过 {@link #newStream()} 打开可随机定位的读取流。
 * </ul>
 *
 * <p>设计意图：抽象出与具体存储无关的“可读文件”视图，使上层读取逻辑（Parquet/ORC/Avro 读取器、扫描任务等）只依赖此接口，而由 {@link FileIO} 实现负责构造具体
 * InputFile。
 *
 * <p>上下游关系：由 {@link FileIO#newInputFile(String)} 创建；被 Iceberg 读路径及底层列式 读取器消费，以读取数据文件与元数据文件。
 */
public interface InputFile {
  /**
   * 返回文件总长度（字节）。
   *
   * @return 文件总长度
   * @throws RuntimeIOException 若底层实现抛出 {@link IOException}
   */
  long getLength();

  /**
   * 打开一个新的 {@link SeekableInputStream} 用于读取该文件。
   *
   * @return 用于读取文件的可定位流
   * @throws NotFoundException 若文件不存在
   * @throws RuntimeIOException 若底层实现抛出 {@link IOException}
   */
  SeekableInputStream newStream();

  /**
   * 返回该输入文件的全限定路径字符串。
   *
   * @return 输入文件路径
   */
  String location();

  /**
   * 判断该文件是否存在。
   *
   * @return 存在返回 true，否则返回 false
   */
  boolean exists();
}
