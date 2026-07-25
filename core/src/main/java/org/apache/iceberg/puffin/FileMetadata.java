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
package org.apache.iceberg.puffin;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 文件级说明：单个 Puffin 文件的整体元数据（footer 解析产物）。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>聚合该 Puffin 文件中所有 {@link BlobMetadata}（数据块元信息列表）。
 *   <li>承载文件级属性（{@link StandardPuffinProperties}，如 created-by 等）。
 * </ul>
 *
 * <p>设计意图：作为 Puffin 文件 footer 在内存中的标准表示，与序列化逻辑 {@link FileMetadataParser} 解耦；不可变值对象，集合入参以
 * Immutable* 拷贝。
 *
 * <p>上下游关系：由 {@link FileMetadataParser#fromJson} 解析 footer JSON 得到， 由 {@link PuffinReader}
 * 暴露给调用方，按其中的 {@link BlobMetadata#offset()} 与 {@link BlobMetadata#length()} 精确读取 Blob 字节。
 */
public class FileMetadata {
  private final List<BlobMetadata> blobs;
  private final Map<String, String> properties;

  /**
   * 构造文件元数据。
   *
   * <p>逻辑：对必填字段做非空校验，并将集合以 Immutable* 拷贝存入字段，保证不可变性。
   *
   * @param blobs 该文件中所有 Blob 的元信息列表
   * @param properties 文件级属性键值对
   */
  public FileMetadata(List<BlobMetadata> blobs, Map<String, String> properties) {
    Preconditions.checkNotNull(blobs, "blobs is null");
    Preconditions.checkNotNull(properties, "properties is null");
    this.blobs = ImmutableList.copyOf(blobs);
    this.properties = ImmutableMap.copyOf(properties);
  }

  /** 返回该文件中所有 Blob 的元信息列表。 */
  public List<BlobMetadata> blobs() {
    return blobs;
  }

  /** 返回文件级属性键值对。 */
  public Map<String, String> properties() {
    return properties;
  }
}
