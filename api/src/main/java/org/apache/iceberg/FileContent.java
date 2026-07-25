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
 * 文件级说明：文件存储的内容类型枚举。
 *
 * <p>所属模块：iceberg-api（核心接口层）。
 *
 * <p>职责：标识 Iceberg 表中文件的内容性质，取值为 {@link #DATA}（数据）、 {@link #POSITION_DELETES}（位置删除）或 {@link
 * #EQUALITY_DELETES}（等值删除）。
 *
 * <p>设计意图：以整型 ID 持久化到 manifest 中，便于紧凑存储与跨版本兼容。manifest 读取时 通过 ID 反查枚举，区分数据文件与不同类型的删除文件。
 *
 * <p>上下游关系：由 {@link ContentFile#content()} 返回；被扫描规划、manifest 读写等组件使用。
 */
public enum FileContent {
  /** 数据文件：存储表的实际数据行。 */
  DATA(0),
  /** 位置删除文件：存储按行位置标记的删除记录。 */
  POSITION_DELETES(1),
  /** 等值删除文件：存储按等值条件标记的删除记录。 */
  EQUALITY_DELETES(2);

  private final int id;

  FileContent(int id) {
    this.id = id;
  }

  /** 返回该内容类型在 manifest 中持久化使用的整型 ID。 */
  public int id() {
    return id;
  }
}
