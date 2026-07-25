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
package org.apache.iceberg.util;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 分区工具类，从扫描任务的文件分区数据中提取常量列映射，供引擎在扫描时直接投影分区值。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：把 {@link ContentScanTask} 中文件携带的分区数据转换为字段 ID 到常量值的映射， 包括 _file、_spec_id、_partition 元数据列及
 * identity 分区字段对应的源列常量。
 *
 * <p>设计意图：分区值在文件元数据中已知，无需读取数据文件即可获得。通过 constantsMap 把这些值
 * 以"常量列"形式注入扫描结果，让引擎跳过对分区列的物理读取。convertConstant 回调允许引擎做类型转换。
 *
 * <p>上下游关系：被各引擎集成层在扫描初始化时调用；依赖 api 模块的 {@link ContentScanTask}、 {@link PartitionSpec} 与 {@link
 * MetadataColumns}。
 */
public class PartitionUtil {
  private PartitionUtil() {}

  /**
   * 返回扫描任务的分区常量映射（不做类型转换）。
   *
   * @param task 内容扫描任务
   * @return 字段 ID 到常量值的映射
   */
  public static Map<Integer, ?> constantsMap(ContentScanTask<?> task) {
    return constantsMap(task, null, (type, constant) -> constant);
  }

  /**
   * 返回扫描任务的分区常量映射，使用指定的转换函数对常量值做类型转换。
   *
   * @param task 内容扫描任务
   * @param convertConstant 类型转换函数 (类型, 原始值) -> 转换后值
   * @return 字段 ID 到转换后常量值的映射
   */
  public static Map<Integer, ?> constantsMap(
      ContentScanTask<?> task, BiFunction<Type, Object, Object> convertConstant) {
    return constantsMap(task, null, convertConstant);
  }

  /**
   * 返回扫描任务的分区常量映射（完整版本），支持指定分区类型并使用转换函数。
   *
   * <p>逻辑：从任务获取 spec 与分区数据；填充 _file 路径、_spec_id；若指定 partitionType 则填充 _partition 列（通过 {@link
   * #coercePartition} 适配类型）；最后遍历 identity 分区字段，把分区值 映射到源字段 ID。
   *
   * @param task 内容扫描任务
   * @param partitionType 分区结构类型，为 null 时不填充 _partition 列
   * @param convertConstant 类型转换函数
   * @return 字段 ID 到常量值的映射
   */
  public static Map<Integer, ?> constantsMap(
      ContentScanTask<?> task,
      Types.StructType partitionType,
      BiFunction<Type, Object, Object> convertConstant) {
    PartitionSpec spec = task.spec();
    StructLike partitionData = task.file().partition();

    // use java.util.HashMap because partition data may contain null values
    Map<Integer, Object> idToConstant = Maps.newHashMap();

    // add _file
    idToConstant.put(
        MetadataColumns.FILE_PATH.fieldId(),
        convertConstant.apply(Types.StringType.get(), task.file().path()));

    // add _spec_id
    idToConstant.put(
        MetadataColumns.SPEC_ID.fieldId(),
        convertConstant.apply(Types.IntegerType.get(), task.file().specId()));

    // add _partition
    if (partitionType != null) {
      if (partitionType.fields().size() > 0) {
        StructLike coercedPartition = coercePartition(partitionType, spec, partitionData);
        idToConstant.put(
            MetadataColumns.PARTITION_COLUMN_ID,
            convertConstant.apply(partitionType, coercedPartition));
      } else {
        // use null as some query engines may not be able to handle empty structs
        idToConstant.put(MetadataColumns.PARTITION_COLUMN_ID, null);
      }
    }

    List<Types.NestedField> partitionFields = spec.partitionType().fields();
    List<PartitionField> fields = spec.fields();
    for (int pos = 0; pos < fields.size(); pos += 1) {
      PartitionField field = fields.get(pos);
      if (field.transform().isIdentity()) {
        Object converted =
            convertConstant.apply(
                partitionFields.get(pos).type(), partitionData.get(pos, Object.class));
        idToConstant.put(field.sourceId(), converted);
      }
    }

    return idToConstant;
  }

  /**
   * 把分区数据适配为指定的表分区类型，允许目标类型缺失部分字段（用 null 填充）。
   *
   * @param partitionType 目标分区结构类型
   * @param spec 分区规范
   * @param partition 原始分区数据
   * @return 类型适配后的分区数据（{@link StructProjection} 包装）
   */
  // 把分区数据适配为表分区类型
  public static StructLike coercePartition(
      Types.StructType partitionType, PartitionSpec spec, StructLike partition) {
    StructProjection projection =
        StructProjection.createAllowMissing(spec.partitionType(), partitionType);
    projection.wrap(partition);
    return projection;
  }
}
