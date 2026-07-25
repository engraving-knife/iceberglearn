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

import java.io.Serializable;

/**
 * 文件级说明：可继承的快照元信息接口，用于在 manifest 读取时补全文件元信息。
 *
 * <p>所属模块：iceberg-core。职责：定义 {@link #apply(ManifestEntry)} 方法，把继承的 分区规格、快照 ID 等上下文信息应用到 manifest
 * 条目上。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>manifest 文件本身不存储分区规格 ID、快照 ID 等上下文信息，这些信息由 InheritableMetadata 在读取时补全，减小 manifest 体积。
 *   <li>实现 Serializable 以支持缓存和远程传输。
 * </ul>
 *
 * <p>上下游关系：由 {@link ManifestEntry} 在读取时调用；由 TableMetadata 创建。
 */
interface InheritableMetadata extends Serializable {
  /**
   * 把继承的上下文（分区规格、快照 ID 等）应用到 manifest 条目上。
   *
   * <p>设计要点：manifest 文件本身不存储这些上下文信息，由本方法在读取时补全。
   *
   * @param manifestEntry 待补全的 manifest 条目
   * @return 补全后的 manifest 条目
   */
  <F extends ContentFile<F>> ManifestEntry<F> apply(ManifestEntry<F> manifestEntry);
}
