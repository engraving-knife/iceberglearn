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
 * 文件级说明：变更日志中可能的操作类型枚举。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：标识变更日志扫描任务所对应的行级变更语义，供下游读取器按操作类型生成对应的 CDC 记录。
 *
 * <p>设计意图：Iceberg 的增量变更日志以“行级事件”形式表达表变更。INSERT/DELETE 直接对应 新增与删除；UPDATE 拆分为
 * UPDATE_BEFORE（更新前的旧值，视为删除）与 UPDATE_AFTER（更新后的 新值，视为插入）两个事件，便于下游还原更新前后的数据。
 *
 * <p>上下游关系：由 {@link ChangelogScanTask#operation()} 返回；被各引擎 CDC 读取器消费以 区分事件类型。
 */
public enum ChangelogOperation {
  /** 插入操作：表示一条新增行。 */
  INSERT,
  /** 删除操作：表示一条被删除的行。 */
  DELETE,
  /** 更新前镜像：表示更新操作产生的旧值记录（语义上等同删除）。 */
  UPDATE_BEFORE,
  /** 更新后镜像：表示更新操作产生的新值记录（语义上等同插入）。 */
  UPDATE_AFTER
}
