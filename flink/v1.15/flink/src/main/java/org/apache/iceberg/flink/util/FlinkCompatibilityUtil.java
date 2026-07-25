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
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.runtime.typeutils.InternalTypeInfo;
import org.apache.flink.table.types.logical.RowType;

/**
 * 兼容性工具类，封装对 Flink 内部或 PublicEvolve 接口的调用。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：屏蔽 Flink 内部 API 在小版本间的差异， 集中调用点便于适配不同 Flink 版本。
 *
 * <p>设计意图：工具类 + 静态方法；当 Flink 升级时仅需修改本类。
 */
public class FlinkCompatibilityUtil {

  private FlinkCompatibilityUtil() {}

  /** 把 RowType 转换为 Flink 的 TypeInformation（基于 InternalTypeInfo）。 */
  public static TypeInformation<RowData> toTypeInfo(RowType rowType) {
    return InternalTypeInfo.of(rowType);
  }

  /** 判断 TableColumn 是否为物理列。 */
  public static boolean isPhysicalColumn(TableColumn column) {
    return column.isPhysical();
  }
}
