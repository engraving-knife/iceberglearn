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
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 JobGroupInfo。
 */
public class JobGroupInfo {
  private String groupId;
  private String description;
  private boolean interruptOnCancel;

  /** 构造 JobGroupInfo 实例。 */
  public JobGroupInfo(String groupId, String desc, boolean interruptOnCancel) {
    this.groupId = groupId;
    this.description = desc;
    this.interruptOnCancel = interruptOnCancel;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String groupId() {
    return groupId;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public String description() {
    return description;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public boolean interruptOnCancel() {
    return interruptOnCancel;
  }
}
