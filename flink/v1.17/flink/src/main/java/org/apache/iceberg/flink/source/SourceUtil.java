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

import java.util.function.Supplier;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.table.api.config.ExecutionConfigOptions;
import org.apache.iceberg.Table;
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.apache.iceberg.hadoop.Util;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 文件级说明：Flink Source 工具类，提供本地性检测和并行度推断等静态方法。
 *
 * <p>所属模块：iceberg-flink（source 子包），被 source/enumerator 使用。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>isLocalityEnabled：检测是否启用本地性感知（检查文件系统是否支持块位置信息）。
 *   <li>inferParallelism：根据 split 数量推断 source 并行度。
 * </ul>
 *
 * <p>设计意图：封装 source 配置相关的通用逻辑，支持从 Flink 配置和表属性推断最优并行度。
 *
 * <p>上下游关系：被 {@link FlinkSplitPlanner} 和 source enumerator 调用。
 */
class SourceUtil {
  private SourceUtil() {}

  /**
   * 检测是否启用 split 本地性感知。
   *
   * <p>逻辑：优先使用显式配置的 exposeLocality，其次使用 Flink 配置项， 最后检查文件系统是否支持块位置信息。
   */
  static boolean isLocalityEnabled(
      Table table, ReadableConfig readableConfig, Boolean exposeLocality) {
    Boolean localityEnabled =
        exposeLocality != null
            ? exposeLocality
            : readableConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_EXPOSE_SPLIT_LOCALITY_INFO);

    if (localityEnabled != null && !localityEnabled) {
      return false;
    }

    return Util.mayHaveBlockLocations(table.io(), table.location());
  }

  /**
   * 推断 source 并行度。
   *
   * <p>逻辑：若启用并行度推断，则从 split 数量推断（受最大推断并行度限制）； 否则使用 Flink 默认并行度。split 数量计算可能较重，故采用懒求值。
   *
   * @param readableConfig Flink 配置
   * @param limitCount 限制输出数量
   * @param splitCountProvider split 数量提供者（懒求值）
   * @return 推断的并行度
   */
  static int inferParallelism(
      ReadableConfig readableConfig, long limitCount, Supplier<Integer> splitCountProvider) {
    int parallelism =
        readableConfig.get(ExecutionConfigOptions.TABLE_EXEC_RESOURCE_DEFAULT_PARALLELISM);
    if (readableConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM)) {
      int maxInferParallelism =
          readableConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM_MAX);
      Preconditions.checkState(
          maxInferParallelism >= 1,
          FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM_MAX.key()
              + " cannot be less than 1");
      parallelism = Math.min(splitCountProvider.get(), maxInferParallelism);
    }

    if (limitCount > 0) {
      int limit = limitCount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limitCount;
      parallelism = Math.min(parallelism, limit);
    }

    // parallelism must be positive.
    parallelism = Math.max(1, parallelism);
    return parallelism;
  }
}
