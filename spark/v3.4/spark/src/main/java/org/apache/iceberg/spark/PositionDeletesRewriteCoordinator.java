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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：位置删除文件重写协调器，聚合 Executor 写出的新位置删除文件并在 Driver 端提交。
 *
 * <p>设计意图：与 FileRewriteCoordinator 结构一致，但面向 position-delete 文件以支持删除文件压缩。
 *
 * <p>上下游关系：继承 BaseFileRewriteCoordinator；由 RewritePositionDeleteFilesSparkAction 调用。
 */
public class PositionDeletesRewriteCoordinator extends BaseFileRewriteCoordinator<DeleteFile> {

  private static final PositionDeletesRewriteCoordinator INSTANCE =
      new PositionDeletesRewriteCoordinator();

  private PositionDeletesRewriteCoordinator() {}
  /** 返回值。 */
  public static PositionDeletesRewriteCoordinator get() {
    return INSTANCE;
  }
}
