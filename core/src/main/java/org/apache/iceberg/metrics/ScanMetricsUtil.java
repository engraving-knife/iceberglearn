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
package org.apache.iceberg.metrics;

import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileContent;

/**
 * 扫描度量记录工具类，封装对 {@link ScanMetrics} 各计数器的便捷递增逻辑。
 *
 * <p>所属模块：iceberg-core，度量包内供扫描执行路径调用的无状态辅助类。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在索引删除文件时，根据删除文件类型（位置删除/等值删除）递增对应计数器。
 *   <li>在处理文件任务（数据文件 + 关联删除文件）时，统一更新结果文件数与文件大小等计数。
 * </ul>
 *
 * <p>设计意图：把"判断删除文件类型并分别计数""累计删除文件大小"等重复逻辑集中到此工具类， 避免扫描实现中散落的手动计数代码，降低出错概率。
 *
 * <p>上下游关系：依赖 {@link ScanMetrics}、{@link DataFile}、{@link DeleteFile}； 被扫描任务执行路径（如 {@code
 * ScanTask}、引擎集成层）调用以记录扫描指标。
 */
public class ScanMetricsUtil {

  private ScanMetricsUtil() {}

  /**
   * 记录一次删除文件索引操作，递增索引计数并按删除文件类型分类递增。
   *
   * <p>逻辑：先递增 {@link ScanMetrics#indexedDeleteFiles()} 总计数；再判断删除文件内容类型， 若为 {@link
   * FileContent#POSITION_DELETES} 则递增位置删除文件计数， 若为 {@link FileContent#EQUALITY_DELETES} 则递增等值删除文件计数。
   *
   * @param metrics 扫描度量
   * @param deleteFile 被索引的删除文件
   */
  public static void indexedDeleteFile(ScanMetrics metrics, DeleteFile deleteFile) {
    metrics.indexedDeleteFiles().increment();

    if (deleteFile.content() == FileContent.POSITION_DELETES) {
      metrics.positionalDeleteFiles().increment();
    } else if (deleteFile.content() == FileContent.EQUALITY_DELETES) {
      metrics.equalityDeleteFiles().increment();
    }
  }

  /**
   * 记录一个文件任务（一个数据文件及其关联的删除文件）的扫描结果指标。
   *
   * <p>逻辑：递增数据文件总字节数与结果数据文件数；按删除文件数组长度递增结果删除文件数； 再遍历删除文件累计其字节数并递增删除文件总字节数计数器。
   *
   * @param metrics 扫描度量
   * @param dataFile 该任务的数据文件
   * @param deleteFiles 该任务关联的删除文件数组
   */
  public static void fileTask(ScanMetrics metrics, DataFile dataFile, DeleteFile[] deleteFiles) {
    metrics.totalFileSizeInBytes().increment(dataFile.fileSizeInBytes());
    metrics.resultDataFiles().increment();
    metrics.resultDeleteFiles().increment(deleteFiles.length);

    long deletesSizeInBytes = 0L;
    for (DeleteFile deleteFile : deleteFiles) {
      deletesSizeInBytes += deleteFile.fileSizeInBytes();
    }

    metrics.totalDeleteFileSizeInBytes().increment(deletesSizeInBytes);
  }
}
