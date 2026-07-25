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

import org.apache.iceberg.DataFile;

/**
 * 数据文件重写结果协调器：以单例方式收集 Spark 数据源写出后的数据文件产物。
 *
 * <p>所属模块：iceberg-spark（核心包，服务于数据文件重写动作）。
 *
 * <p>职责：作为 {@link BaseFileRewriteCoordinator} 的具体实现，按 {@link DataFile} 类型 暂存重写产物，供重写动作在提交前读取。
 *
 * <p>设计意图：采用饿汉式单例，保证整个 JVM 内只有一个协调器实例，使数据源写端与 重写动作端能通过同一实例互通结果。
 *
 * <p>上下游关系：继承 {@link BaseFileRewriteCoordinator}；由 Spark 数据源写端调用 {@code stageRewrite} 暂存，由
 * RewriteDataFiles 系列动作读取结果。
 */
public class FileRewriteCoordinator extends BaseFileRewriteCoordinator<DataFile> {

  private static final FileRewriteCoordinator INSTANCE = new FileRewriteCoordinator();

  private FileRewriteCoordinator() {}

  /** 返回全局唯一的 {@link FileRewriteCoordinator} 实例。 */
  public static FileRewriteCoordinator get() {
    return INSTANCE;
  }
}
