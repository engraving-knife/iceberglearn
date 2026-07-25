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

import org.apache.iceberg.DeleteFile;

/**
 * 位置删除文件重写协调器（单例）。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：作为 {@link BaseFileRewriteCoordinator} 的具体实现，协调 Spark 端 position delete
 * 文件的重写任务，跟踪每个表对应的已重写文件集合。
 *
 * <p>设计意图：单例模式保证整个 driver 内对同一表的协调状态唯一，避免并发重写冲突。
 *
 * <p>上下游关系：被 {@code RewritePositionDeleteFilesProcedure} / Spark action 调用； 泛型参数为 {@link
 * DeleteFile}。
 */
public class PositionDeletesRewriteCoordinator extends BaseFileRewriteCoordinator<DeleteFile> {

  private static final PositionDeletesRewriteCoordinator INSTANCE =
      new PositionDeletesRewriteCoordinator();

  private PositionDeletesRewriteCoordinator() {}

  /** 返回单例实例。 */
  public static PositionDeletesRewriteCoordinator get() {
    return INSTANCE;
  }
}
