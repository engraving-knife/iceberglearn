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

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.spark.sql.connector.catalog.Identifier;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：基于路径的表标识符，当表通过文件路径而非 catalog 命名空间引用时使用。
 *
 * <p>设计意图：封装路径字符串以兼容 Spark TableIdentifier 接口，区分 catalog 表与路径表。
 *
 * <p>上下游关系：由 SparkCatalog / IcebergSource 在路径式访问时构造。
 */
public class PathIdentifier implements Identifier {
  private static final Splitter SPLIT = Splitter.on("/");
  private static final Joiner JOIN = Joiner.on("/");
  private final String[] namespace;
  private final String location;
  private final String name;

  public PathIdentifier(String location) {
    this.location = location;
    List<String> pathParts = SPLIT.splitToList(location);
    name = Iterables.getLast(pathParts);
    namespace =
        pathParts.size() > 1
            ? new String[] {JOIN.join(pathParts.subList(0, pathParts.size() - 1))}
            : new String[0];
  }
  /** 返回命名空间。 */
  @Override
  public String[] namespace() {
    return namespace;
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return name;
  }
  /** 返回路径。 */
  public String location() {
    return location;
  }
}
