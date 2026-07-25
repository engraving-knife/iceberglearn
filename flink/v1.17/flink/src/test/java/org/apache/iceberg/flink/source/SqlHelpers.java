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
package org.apache.iceberg.flink.source;

import java.util.List;
import java.util.Map;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：测试 SqlHelpers 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 SqlHelpers 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class SqlHelpers {
  /** 辅助方法：SqlHelpers，Sql Helpers。 */
  private SqlHelpers() {}

  /** 辅助方法：sql，sql。 */
  public static List<Row> sql(TableEnvironment tableEnv, String query, Object... args) {
    TableResult tableResult = tableEnv.executeSql(String.format(query, args));
    try (CloseableIterator<Row> iter = tableResult.collect()) {
      List<Row> results = Lists.newArrayList(iter);
      return results;
    } catch (Exception e) {
      throw new RuntimeException("Failed to collect table result", e);
    }
  }

  /** 辅助方法：sqlOptionsToString，sql Options To String。 */
  public static String sqlOptionsToString(Map<String, String> sqlOptions) {
    StringBuilder builder = new StringBuilder();
    sqlOptions.forEach((key, value) -> builder.append(optionToKv(key, value)).append(","));
    String optionStr = builder.toString();
    if (optionStr.endsWith(",")) {
      optionStr = optionStr.substring(0, optionStr.length() - 1);
    }

    if (!optionStr.isEmpty()) {
      optionStr = String.format("/*+ OPTIONS(%s)*/", optionStr);
    }

    return optionStr;
  }

  /** 辅助方法：optionToKv，option To Kv。 */
  private static String optionToKv(String key, Object value) {
    return "'" + key + "'='" + value + "'";
  }
}
