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

import java.util.List;

/**
 * 删除文件：表示表删除清单（delete manifest）中列出的一个等值删除或位置删除文件。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：作为 {@link ContentFile} 的子类型，专门描述用于行级删除（position delete / equality delete）的文件，承载体现在 V2
 * 格式中的删除文件元信息。
 *
 * <p>设计意图：把数据文件与删除文件统一在 {@link ContentFile} 抽象下，通过泛型自引用 {@code ContentFile<DeleteFile>}
 * 让接口方法返回类型精确收敛到 {@link DeleteFile}， 便于在清单读取、扫描规划等处区分两类文件。
 *
 * <p>上下游关系：由删除清单读取器构造，被 {@link ContentScanTask}、扫描规划器等使用。
 */
public interface DeleteFile extends ContentFile<DeleteFile> {
  /**
   * 返回推荐的分片位置列表。
   *
   * <p>当可用时，扫描规划器会按这些偏移确定扫描任务的边界。返回列表必须按升序排序； 若不适用则返回 {@code null}（默认实现）。
   *
   * @return 推荐分片偏移列表，或 {@code null}
   */
  @Override
  default List<Long> splitOffsets() {
    return null;
  }
}
