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
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.RuntimeIOException;

/**
 * 文件级说明：输出文件抽象接口，用于通过 {@link PositionOutputStream} 写入文件字节内容。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。本接口设计参考 Parquet 的 OutputFile。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #create()} 创建新文件并返回输出流（文件已存在则报错）。
 *   <li>通过 {@link #createOrOverwrite()} 创建或覆盖文件并返回输出流。
 *   <li>暴露输出文件路径，并支持转换为对应 {@link InputFile} 以便读取回写内容。
 * </ul>
 *
 * <p>设计意图：抽象出与具体存储无关的“可写文件”视图，使上层写入逻辑（Parquet/ORC/Avro 写入器、数据文件提交等）只依赖此接口，由 {@link FileIO} 实现负责构造具体
 * OutputFile。 区分 {@code create} 与 {@code createOrOverwrite} 是为了在调用侧显式表达“是否允许覆盖” 的语义，避免误覆盖已有数据。
 *
 * <p>上下游关系：由 {@link FileIO#newOutputFile(String)} 创建；被 Iceberg 写路径及底层列式 写入器消费，用于写数据文件与元数据文件。
 */
public interface OutputFile {

  /**
   * 创建新文件并返回其 {@link PositionOutputStream}。
   *
   * <p>若文件已存在则抛出异常，不进行覆盖。
   *
   * @return 可报告写入位置的输出流
   * @throws AlreadyExistsException 若目标路径已存在
   * @throws RuntimeIOException 若底层实现抛出 {@link IOException}
   */
  PositionOutputStream create();

  /**
   * 创建或覆盖文件并返回其 {@link PositionOutputStream}。
   *
   * <p>若文件已存在则不抛异常，而是替换原文件。
   *
   * @return 可报告写入位置的输出流
   * @throws RuntimeIOException 若底层实现抛出 {@link IOException}
   * @throws SecurityException 若因缺少 JVM 权限导致暂存目录创建失败
   */
  PositionOutputStream createOrOverwrite();

  /**
   * 返回该输出文件的目标路径。
   *
   * @return 输出文件路径
   */
  String location();

  /**
   * 返回与本输出文件同位置的 {@link InputFile}，便于在写完后读取回放。
   *
   * @return 对应位置的 {@link InputFile}
   */
  InputFile toInputFile();
}
