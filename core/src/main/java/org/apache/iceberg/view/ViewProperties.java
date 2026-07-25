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
package org.apache.iceberg.view;

/**
 * 文件级说明：视图可配置属性键常量。
 *
 * <p>所属模块：iceberg-core（视图元数据实现模块）。
 *
 * <p>职责：集中定义视图级属性键及其默认值，供 CREATE/REPLACE 视图或 updateProperties API 使用。
 *
 * <p>设计意图：以常量类形式管理属性键字符串，避免拼写错误；私有构造禁止实例化。
 *
 * <p>上下游关系：被 {@link ViewMetadata.Builder} 在构建时读取（如版本历史大小）， 被 {@link ViewMetadataParser} 序列化/反序列化
 * properties 字段时引用。
 */
public class ViewProperties {
  /** 视图保留的版本历史条目数的属性键。 */
  public static final String VERSION_HISTORY_SIZE = "version.history.num-entries";
  /** 版本历史条目数的默认值。 */
  public static final int VERSION_HISTORY_SIZE_DEFAULT = 10;

  private ViewProperties() {}
}
