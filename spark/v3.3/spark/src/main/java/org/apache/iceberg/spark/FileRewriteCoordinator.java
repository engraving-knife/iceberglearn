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
 * Iceberg Spark 集成相关组件的写入组件，负责数据写入与提交。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 FileRewriteCoordinator。
 *
 * <p>设计意图：Catalyst 规则，通过 transformation 介入计划处理。
 */
public class FileRewriteCoordinator extends BaseFileRewriteCoordinator<DataFile> {

  private static final FileRewriteCoordinator INSTANCE = new FileRewriteCoordinator();

  /** 构造 FileRewriteCoordinator 实例。 */
  private FileRewriteCoordinator() {}

  /** 执行该方法的具体逻辑。 */
  public static FileRewriteCoordinator get() {
    return INSTANCE;
  }
}
