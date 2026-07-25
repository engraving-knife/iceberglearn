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
 * 作业组信息，用于在 Spark UI 上展示当前作业。
 *
 * <p>所属模块：iceberg-spark。封装 Spark 作业组的 groupId、描述与取消时是否中断等元信息， 供 Iceberg 动作在提交 Spark
 * 作业前设置作业组，便于追踪与区分。
 *
 * <p>设计意图：不可变值对象，简化 {@link org.apache.spark.api.java.JavaSparkContext#setJobGroup} 调用参数传递。
 */
public class JobGroupInfo {
  private final String groupId;
  private final String description;
  private final boolean interruptOnCancel;

  /** 以 groupId 与描述构造，默认不中断取消。 */
  public JobGroupInfo(String groupId, String desc) {
    this(groupId, desc, false);
  }

  /** 以 groupId、描述与取消中断标志构造。 */
  public JobGroupInfo(String groupId, String desc, boolean interruptOnCancel) {
    this.groupId = groupId;
    this.description = desc;
    this.interruptOnCancel = interruptOnCancel;
  }

  public String groupId() {
    return groupId;
  }

  /** 返回作业描述。 */
  public String description() {
    return description;
  }

  /** 返回取消作业时是否中断线程。 */
  public boolean interruptOnCancel() {
    return interruptOnCancel;
  }
}
