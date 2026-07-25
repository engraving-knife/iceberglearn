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
import java.io.InputStream;

/**
 * 文件级说明：可随机定位的输入流抽象类。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。本类设计参考 Parquet 的 SeekableInputStream。
 *
 * <p>职责：在标准 {@link InputStream} 基础上增加 {@link #getPos()} 与 {@link #seek(long)} 能力，使读取方可以在文件内随机跳转读取。
 *
 * <p>设计意图：列式文件格式（如 Parquet/ORC）需要按偏移读取特定 row group / footer， 必须支持随机定位。本类以抽象类形式提供，便于与 {@link
 * InputStream} 的既有读方法体系 自然融合，由各存储实现（HDFS、S3 等）提供具体子类。
 *
 * <p>上下游关系：由 {@link InputFile#newStream()} 创建；被底层列式读取器及 Iceberg 扫描 任务消费，用于读取数据文件与元数据文件。
 */
public abstract class SeekableInputStream extends InputStream {
  /**
   * 返回输入流当前读取位置（自流起始的字节偏移）。
   *
   * @return 当前读取位置（字节）
   * @throws IOException 若底层流抛出 IOException
   */
  public abstract long getPos() throws IOException;

  /**
   * 将流定位到指定新位置。
   *
   * @param newPos 要定位到的字节位置
   * @throws IOException 若底层流抛出 IOException
   */
  public abstract void seek(long newPos) throws IOException;
}
