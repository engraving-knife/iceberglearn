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
import java.io.OutputStream;

/**
 * 文件级说明：可报告当前位置的输出流抽象类。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：在标准 {@link OutputStream} 基础上增加 {@link #getPos()} 能力，使写入方在写过程 中可以获知当前已写入的字节偏移量。
 *
 * <p>设计意图：列式文件格式（如 Parquet）在写入完成后需要记录各 row group / 列块的起始 偏移以便后续按范围读取，因此输出流必须能暴露当前写入位置。本类以抽象类而非接口形式
 * 提供，便于与 {@link OutputStream} 的既有写方法体系自然融合。
 *
 * <p>上下游关系：由 {@link OutputFile#create()} / {@link OutputFile#createOrOverwrite()} 创建；
 * 被底层列式写入器（Parquet/ORC 等）以及 Iceberg 数据文件 appenders 使用。
 */
public abstract class PositionOutputStream extends OutputStream {
  /**
   * 返回输出流当前写入位置（自流起始的字节偏移）。
   *
   * @return 当前写入位置（字节）
   * @throws IOException 若底层流抛出 IOException
   */
  public abstract long getPos() throws IOException;
}
