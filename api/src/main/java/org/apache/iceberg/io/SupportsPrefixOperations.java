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
 * 文件级说明：支持按前缀操作的 FileIO 扩展接口。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link #listPrefix(String)} 列出某前缀下的所有文件。
 *   <li>通过 {@link #deletePrefix(String)} 删除某前缀下的所有文件。
 * </ul>
 *
 * <p>设计意图：在执行“过期快照”、“删除分区”等支撑性操作时，常需要按目录/前缀批量处理 文件。本接口把这种前缀语义抽象出来，由具体存储实现各自映射（HDFS 等层级文件系统通常
 * 要求前缀精确匹配目录，而 KV 对象存储则允许任意前缀）。
 *
 * <p>上下游关系：由具备前缀列举/删除能力的 FileIO 实现标记实现；被快照过期、删除表等 上层流程调用。
 */
public interface SupportsPrefixOperations extends FileIO {

  /**
   * 返回指定前缀下所有文件的可迭代结果。
   *
   * <p>层级文件系统（如 HDFS）可能附加限制，例如前缀必须完整匹配一个目录；而 KV 对象存储 通常允许任意前缀。
   *
   * @param prefix 要列举的前缀
   * @return 文件信息可迭代结果
   */
  Iterable<FileInfo> listPrefix(String prefix);

  /**
   * 删除指定前缀下的所有文件。
   *
   * <p>层级文件系统（如 HDFS）可能附加限制，例如前缀必须完整匹配一个目录；而 KV 对象存储 通常允许任意前缀。
   *
   * @param prefix 要删除的前缀
   */
  void deletePrefix(String prefix);
}
