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
package org.apache.iceberg.spark.source;

import static org.apache.iceberg.TableProperties.DELETE_ISOLATION_LEVEL;
import static org.apache.iceberg.TableProperties.DELETE_ISOLATION_LEVEL_DEFAULT;
import static org.apache.iceberg.TableProperties.DELETE_MODE;
import static org.apache.iceberg.TableProperties.DELETE_MODE_DEFAULT;
import static org.apache.iceberg.TableProperties.MERGE_ISOLATION_LEVEL;
import static org.apache.iceberg.TableProperties.MERGE_ISOLATION_LEVEL_DEFAULT;
import static org.apache.iceberg.TableProperties.MERGE_MODE;
import static org.apache.iceberg.TableProperties.MERGE_MODE_DEFAULT;
import static org.apache.iceberg.TableProperties.UPDATE_ISOLATION_LEVEL;
import static org.apache.iceberg.TableProperties.UPDATE_ISOLATION_LEVEL_DEFAULT;
import static org.apache.iceberg.TableProperties.UPDATE_MODE;
import static org.apache.iceberg.TableProperties.UPDATE_MODE_DEFAULT;

import java.util.Map;
import org.apache.iceberg.IsolationLevel;
import org.apache.iceberg.RowLevelOperationMode;
import org.apache.iceberg.Table;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.write.RowLevelOperation;
import org.apache.spark.sql.connector.write.RowLevelOperation.Command;
import org.apache.spark.sql.connector.write.RowLevelOperationBuilder;
import org.apache.spark.sql.connector.write.RowLevelOperationInfo;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的构建器，负责分步骤构造目标对象。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkRowLevelOperationBuilder。
 *
 * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
class SparkRowLevelOperationBuilder implements RowLevelOperationBuilder {

  private final SparkSession spark;
  private final Table table;
  private final String branch;
  private final RowLevelOperationInfo info;
  private final RowLevelOperationMode mode;
  private final IsolationLevel isolationLevel;

  SparkRowLevelOperationBuilder(
      SparkSession spark, Table table, String branch, RowLevelOperationInfo info) {
    this.spark = spark;
    this.table = table;
    this.branch = branch;
    this.info = info;
    this.mode = mode(table.properties(), info.command());
    this.isolationLevel = isolationLevel(table.properties(), info.command());
  }

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  @Override
  public RowLevelOperation build() {
    switch (mode) {
      case COPY_ON_WRITE:
        /** 执行该方法的具体逻辑。 */
        return new SparkCopyOnWriteOperation(spark, table, branch, info, isolationLevel);
      case MERGE_ON_READ:
        /** 执行该方法的具体逻辑。 */
        return new SparkPositionDeltaOperation(spark, table, branch, info, isolationLevel);
      default:
        throw new IllegalArgumentException("Unsupported operation mode: " + mode);
    }
  }

  /** 执行该方法的具体逻辑。 */
  private RowLevelOperationMode mode(Map<String, String> properties, Command command) {
    String modeName;

    switch (command) {
      case DELETE:
        modeName = properties.getOrDefault(DELETE_MODE, DELETE_MODE_DEFAULT);
        break;
      case UPDATE:
        modeName = properties.getOrDefault(UPDATE_MODE, UPDATE_MODE_DEFAULT);
        break;
      case MERGE:
        modeName = properties.getOrDefault(MERGE_MODE, MERGE_MODE_DEFAULT);
        break;
      default:
        throw new IllegalArgumentException("Unsupported command: " + command);
    }

    return RowLevelOperationMode.fromName(modeName);
  }

  /** 执行该方法的具体逻辑。 */
  private IsolationLevel isolationLevel(Map<String, String> properties, Command command) {
    String levelName;

    switch (command) {
      case DELETE:
        levelName = properties.getOrDefault(DELETE_ISOLATION_LEVEL, DELETE_ISOLATION_LEVEL_DEFAULT);
        break;
      case UPDATE:
        levelName = properties.getOrDefault(UPDATE_ISOLATION_LEVEL, UPDATE_ISOLATION_LEVEL_DEFAULT);
        break;
      case MERGE:
        levelName = properties.getOrDefault(MERGE_ISOLATION_LEVEL, MERGE_ISOLATION_LEVEL_DEFAULT);
        break;
      default:
        throw new IllegalArgumentException("Unsupported command: " + command);
    }

    return IsolationLevel.fromName(levelName);
  }
}
