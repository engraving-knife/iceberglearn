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

/**
 * 文件级说明：文件元信息载体，描述一个文件的路径、大小与创建时间。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：作为 {@link SupportsPrefixOperations#listPrefix(String)} 的返回元素，承载列举结果 中每个文件的关键元信息。
 *
 * <p>设计意图：以不可变值对象形式封装文件元信息，字段全部 final，保证线程安全与一致性； 仅暴露只读访问方法。
 *
 * <p>上下游关系：由实现 {@link SupportsPrefixOperations} 的 FileIO 在前缀列举时产生；被 上层批量处理流程消费。
 */
public class FileInfo {
  private final String location;
  private final long size;
  private final long createdAtMillis;

  /**
   * 构造文件信息。
   *
   * @param location 文件全限定路径
   * @param size 文件大小（字节）
   * @param createdAtMillis 文件创建时间（毫秒，自 epoch 起）
   */
  public FileInfo(String location, long size, long createdAtMillis) {
    this.location = location;
    this.size = size;
    this.createdAtMillis = createdAtMillis;
  }

  /**
   * 返回文件全限定路径。
   *
   * @return 文件路径
   */
  public String location() {
    return location;
  }

  /**
   * 返回文件大小（字节）。
   *
   * @return 文件大小
   */
  public long size() {
    return size;
  }

  /**
   * 返回文件创建时间（毫秒，自 epoch 起）。
   *
   * @return 创建时间毫秒值
   */
  public long createdAtMillis() {
    return createdAtMillis;
  }
}
