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
package org.apache.iceberg.spark;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Spark 作业组信息载体，描述一个 Iceberg 后台动作对应的作业组标识、描述与是否可中断等属性。
 *
 * <p>设计意图：将作业组元数据与执行逻辑解耦，便于统一管理与 UI 展示。
 *
 * <p>上下游关系：由 JobGroupUtils 使用，被各 SparkAction 调用以设置作业组上下文。
 */
public class JobGroupInfo {
  private final String groupId;
  private final String description;
  private final boolean interruptOnCancel;

  public JobGroupInfo(String groupId, String desc) {
    this(groupId, desc, false);
  }

  public JobGroupInfo(String groupId, String desc, boolean interruptOnCancel) {
    this.groupId = groupId;
    this.description = desc;
    this.interruptOnCancel = interruptOnCancel;
  }
  /** 返回作业组 ID。 */
  public String groupId() {
    return groupId;
  }
  /** 返回描述。 */
  public String description() {
    return description;
  }
  /** 返回是否可中断。 */
  public boolean interruptOnCancel() {
    return interruptOnCancel;
  }
}
