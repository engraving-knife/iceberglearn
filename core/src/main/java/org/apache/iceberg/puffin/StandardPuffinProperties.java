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
 * 文件级说明：Puffin 文件级标准属性键常量。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：集中定义 Puffin 规范认可的文件级属性键，供 {@link Puffin.WriteBuilder#set} 写入、{@link
 * FileMetadata#properties()} 读取时一致使用。
 *
 * <p>设计意图：以常量类形式管理属性键字符串，避免拼写错误；私有构造禁止实例化。
 *
 * <p>上下游关系：被 {@link Puffin.WriteBuilder#createdBy(String)}、{@link FileMetadataParser} 在序列化/反序列化
 * footer properties 时引用。
 */
public final class StandardPuffinProperties {
  private StandardPuffinProperties() {}

  /** 写入文件的应用的人类可读标识（含版本），如 "Trino version 381"。 */
  public static final String CREATED_BY_PROPERTY = "created-by";
}
