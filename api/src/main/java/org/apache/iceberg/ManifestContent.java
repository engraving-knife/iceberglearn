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
 * manifest 文件所存储的内容类型枚举：DATA 或 DELETES。
 *
 * <p>所属模块：iceberg-api（元数据抽象层）。
 *
 * <p>职责：标识一个 manifest 文件中包含的是数据文件还是删除文件，便于扫描规划时按需筛选。
 *
 * <p>设计意图：每个枚举值绑定一个稳定的整数 ID，用于在 manifest 文件元数据中持久化， 保证跨版本兼容；{@link #fromId(int)} 提供反向解析。
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.ManifestFile} 等元数据结构持有；扫描规划阶段 据此过滤 manifest。
 */
public enum ManifestContent {
  DATA(0),
  DELETES(1);

  private final int id;

  ManifestContent(int id) {
    this.id = id;
  }

  /** 返回该内容类型对应的持久化整数 ID。 */
  public int id() {
    return id;
  }

  /**
   * 根据整数 ID 反向解析为 {@link ManifestContent}。
   *
   * @param id 内容类型 ID
   * @return 对应的枚举值
   * @throws IllegalArgumentException 若 ID 未知
   */
  public static ManifestContent fromId(int id) {
    switch (id) {
      case 0:
        return DATA;
      case 1:
        return DELETES;
    }
    throw new IllegalArgumentException("Unknown manifest content: " + id);
  }
}
