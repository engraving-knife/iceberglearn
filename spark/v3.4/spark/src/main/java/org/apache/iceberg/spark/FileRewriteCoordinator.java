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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：数据文件重写协调器，管理 Executor 端通过 ScanTaskSetManager 写出的新数据文件，并在 Driver 端完成提交。
 *
 * <p>设计意图：使用静态 ConcurrentMap 注册表按表+扫描 ID 隔离不同重写任务的结果。
 *
 * <p>上下游关系：继承 BaseFileRewriteCoordinator；由 RewriteDataFilesSparkAction 与 SparkBatch 写入链路调用。
 */
public class FileRewriteCoordinator extends BaseFileRewriteCoordinator<DataFile> {

  private static final FileRewriteCoordinator INSTANCE = new FileRewriteCoordinator();

  private FileRewriteCoordinator() {}
  /** 返回值。 */
  public static FileRewriteCoordinator get() {
    return INSTANCE;
  }
}
