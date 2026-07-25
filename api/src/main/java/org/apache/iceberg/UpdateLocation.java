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

/**
 * 表基址更新 API：设置表的 base location。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：把表的存储根路径更新为指定值，并提交该元数据变更。
 *
 * <p>设计意图：作为 {@link PendingUpdate} 的子类型，遵循"配置后提交"的统一更新模式。
 *
 * <p>上下游关系：由 {@link Table#updateLocation()} 创建；下游实现位于 core 模块。
 */
public interface UpdateLocation extends PendingUpdate<String> {
  /**
   * 设置表的存储位置。
   *
   * @param location 表的新存储路径
   * @return this，便于链式调用
   */
  UpdateLocation setLocation(String location);
}
