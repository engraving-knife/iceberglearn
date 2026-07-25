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

/**
 * 文件级说明：Puffin 文件中标准 Blob 类型字符串常量。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：集中定义 Puffin 规范认可的 Blob 类型标识，供写入侧（{@link Blob#type()}） 与读取侧（{@link
 * BlobMetadata#type()}）一致使用。
 *
 * <p>设计意图：以常量类形式管理类型字符串，避免散落在各处的硬编码字符串造成拼写错误； 私有构造禁止实例化。
 *
 * <p>上下游关系：被统计信息收集器、{@link PuffinWriter}、{@link PuffinReader} 在判断 Blob 类型时引用。
 */
public final class StandardBlobTypes {
  private StandardBlobTypes() {}

  /**
   * 由 <a href="https://datasketches.apache.org/">Apache DataSketches</a> 库生成的 "compact" Theta
   * 草图的序列化形式（用于 NDV 等基数统计）。
   */
  public static final String APACHE_DATASKETCHES_THETA_V1 = "apache-datasketches-theta-v1";
}
