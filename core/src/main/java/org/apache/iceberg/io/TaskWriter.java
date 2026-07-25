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
import java.io.IOException;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：任务写入器接口。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：定义一个写入任务的生命周期——接收行记录（write）、正常完成并返回结果（complete）、 异常中止并清理已写文件（abort）。
 *
 * <p>设计意图：TaskWriter 是 Iceberg 写入流程的顶层抽象，屏蔽了分区、滚动、删除等细节。 complete() 返回包含数据和删除文件的 {@link
 * WriteResult}；dataFiles() 是便捷方法， 仅在确定只产生数据文件时使用，否则抛出异常以防止遗漏删除文件。
 *
 * <p>上下游关系：由引擎集成层（Spark/FFLink 等）实例化并调用；core 中的实现包括 {@link BaseTaskWriter}、{@link
 * UnpartitionedWriter}、{@link PartitionedWriter} 等。
 *
 * @param <T> 行记录的数据类型
 */
public interface TaskWriter<T> extends Closeable {

  /**
   * 将一行记录写入数据文件。
   *
   * @param row 待写入的行记录
   * @throws IOException 发生 IO 错误时
   */
  void write(T row) throws IOException;

  /**
   * 中止写入并删除已生成的文件，用于任务失败时的清理。
   *
   * <p>逻辑：先 close 写入器，再删除所有已完成的文件。
   *
   * @throws IOException 清理过程中发生 IO 错误
   */
  void abort() throws IOException;

  /**
   * 关闭写入器并返回已完成的数据文件。仅适用于只产生数据文件的写入器。
   *
   * <p>逻辑：调用 {@link #complete()} 获取结果，校验不存在删除文件后返回数据文件数组。
   *
   * @return 已完成的数据文件数组
   * @throws IOException 关闭写入器时发生 IO 错误
   * @throws IllegalArgumentException 若结果中包含删除文件
   */
  default DataFile[] dataFiles() throws IOException {
    WriteResult result = complete();
    Preconditions.checkArgument(
        result.deleteFiles() == null || result.deleteFiles().length == 0,
        "Should have no delete files in this write result.");

    return result.dataFiles();
  }

  /**
   * 关闭写入器并返回包含数据和删除文件的完整结果。
   *
   * @return 本次写入任务的完整结果
   * @throws IOException 关闭写入器时发生 IO 错误
   */
  WriteResult complete() throws IOException;
}
