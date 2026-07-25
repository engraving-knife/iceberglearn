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
package org.apache.iceberg.flink.util;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.api.TableColumn;
import org.apache.flink.table.catalog.Column;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.logical.RowType;

/**
 * 文件级说明：Flink 内部 API 兼容性工具类。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 util 子包）。
 *
 * <p>职责：封装对 Flink Internal 或 PublicEvolve 接口的调用， 屏蔽 Flink 在小版本升级时可能变更的 API，集中管理兼容性问题。
 *
 * <p>设计意图：把对 Flink 不稳定 API 的依赖集中到一处， 便于升级 Flink 版本时仅修改本类。
 *
 * <p>上下游关系：上游为 Iceberg-Flink 模块的各处调用点， 下游为 Flink 的 {@link InternalTypeInfo}、{@link
 * TableColumn}、{@link Column} 等 API。
 */
public class FlinkCompatibilityUtil {

  private FlinkCompatibilityUtil() {}

  /** 由 RowType 构造 Flink TypeInformation。 */
  public static TypeInformation<RowData> toTypeInfo(RowType rowType) {
    return InternalTypeInfo.of(rowType);
  }

  /** 判断旧版 {@link TableColumn} 是否为物理列。 */
  public static boolean isPhysicalColumn(TableColumn column) {
    return column.isPhysical();
  }

  /** 判断新版 {@link Column} 是否为物理列。 */
  public static boolean isPhysicalColumn(Column column) {
    return column.isPhysical();
  }
}
