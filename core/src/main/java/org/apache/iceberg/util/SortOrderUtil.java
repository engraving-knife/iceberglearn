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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortField;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.transforms.SortOrderVisitor;

/**
 * 排序顺序相关静态工具方法集合。
 *
 * <p>所属模块：iceberg-core（util 子包）。职责：根据分区规格与用户排序顺序构建满足分区聚簇要求的 最终排序顺序，并提供排序顺序的投影/比较等辅助方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>满足分区聚簇：分区字段必须作为排序前缀，本工具把分区字段与用户排序字段合并为最终顺序。
 *   <li>去重与顺序保持：避免重复字段，并维持 partition-spec 的字段顺序优先。
 *   <li>无状态工具类：构造函数私有。
 * </ul>
 *
 * <p>上下游关系：被写入路径在决定数据文件内部排序时调用；依赖 {@link SortOrderVisitor}。
 */
public class SortOrderUtil {

  /** 私有构造：工具类禁止实例化。 */
  private SortOrderUtil() {}

  /**
   * 根据表的 schema、分区规格与当前排序顺序构建最终排序顺序。
   *
   * @param table Iceberg 表
   * @return 满足分区聚簇要求的最终排序顺序
   */
  public static SortOrder buildSortOrder(Table table) {
    return buildSortOrder(table.schema(), table.spec(), table.sortOrder());
  }

  // builds a sort order using both the table partition spec and the user supplied sort order

  /**
   * 根据表的 schema、分区规格与指定排序顺序构建最终排序顺序。
   *
   * @param table Iceberg 表
   * @param sortOrder 用户指定的排序顺序
   * @return 满足分区聚簇要求的最终排序顺序
   */
  public static SortOrder buildSortOrder(Table table, SortOrder sortOrder) {
    return buildSortOrder(table.schema(), table.spec(), sortOrder);
  }

  /**
   * 构建满足分区聚簇要求的最终排序顺序。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>若排序与分区均为空，直接返回 unsorted；
   *   <li>计算分区规格中需要聚簇的字段（排除 void 变换与被其他分区字段满足的字段）；
   *   <li>遍历排序顺序前缀，移除已被排序字段满足的分区聚簇字段；
   *   <li>把剩余分区聚簇字段作为排序前缀，再追加用户排序字段。
   * </ol>
   *
   * @param schema 表 schema
   * @param spec 分区规格
   * @param sortOrder 用户排序顺序
   * @return 包含分区聚簇前缀的最终排序顺序
   */
  public static SortOrder buildSortOrder(Schema schema, PartitionSpec spec, SortOrder sortOrder) {
    if (sortOrder.isUnsorted() && spec.isUnpartitioned()) {
      return SortOrder.unsorted();
    }

    // make a map of the partition fields that need to be included in the clustering produced by the
    // sort order
    Map<Pair<String, Integer>, PartitionField> requiredClusteringFields =
        requiredClusteringFields(spec);

    // remove any partition fields that are clustered by the sort order by iterating over a prefix
    // in the sort order.
    // this will stop when a non-partition field is found, or when the sort field only satisfies the
    // partition field.
    for (SortField sortField : sortOrder.fields()) {
      Pair<String, Integer> sourceAndTransform =
          Pair.of(sortField.transform().toString(), sortField.sourceId());
      if (requiredClusteringFields.containsKey(sourceAndTransform)) {
        requiredClusteringFields.remove(sourceAndTransform);
        continue; // keep processing the prefix
      }

      // if the field satisfies the order of any partition fields, also remove them before stopping
      // use a set to avoid concurrent modification
      for (PartitionField field : spec.fields()) {
        if (sortField.sourceId() == field.sourceId()
            && sortField.transform().satisfiesOrderOf(field.transform())) {
          requiredClusteringFields.remove(Pair.of(field.transform().toString(), field.sourceId()));
        }
      }

      break;
    }

    // build a sort prefix of partition fields that are not already in the sort order's prefix
    SortOrder.Builder builder = SortOrder.builderFor(schema);
    for (PartitionField field : requiredClusteringFields.values()) {
      String sourceName = schema.findColumnName(field.sourceId());
      builder.asc(Expressions.transform(sourceName, field.transform()));
    }

    // add the configured sort to the partition spec prefix sort
    SortOrderVisitor.visit(sortOrder, new CopySortOrderFields(builder));

    return builder.build();
  }

  private static Map<Pair<String, Integer>, PartitionField> requiredClusteringFields(
      PartitionSpec spec) {
    Map<Pair<String, Integer>, PartitionField> requiredClusteringFields = Maps.newLinkedHashMap();
    for (PartitionField partField : spec.fields()) {
      if (!partField.transform().toString().equals("void")) {
        requiredClusteringFields.put(
            Pair.of(partField.transform().toString(), partField.sourceId()), partField);
      }
    }

    // remove any partition fields that are satisfied by another partition field, like days(ts) and
    // hours(ts)
    for (PartitionField partField : spec.fields()) {
      for (PartitionField field : spec.fields()) {
        if (!partField.equals(field)
            && partField.sourceId() == field.sourceId()
            && partField.transform().satisfiesOrderOf(field.transform())) {
          requiredClusteringFields.remove(Pair.of(field.transform().toString(), field.sourceId()));
        }
      }
    }

    return requiredClusteringFields;
  }

  /**
   * 返回排序顺序中保持顺序的变换所对应的源列名集合。
   *
   * <p>过滤出 transform.preservesOrder() 为 true 的字段，映射为列名。
   *
   * @param sortOrder 排序顺序（可为 null）
   * @return 保持顺序的排序列名集合，null 返回空集
   */
  public static Set<String> orderPreservingSortedColumns(SortOrder sortOrder) {
    if (sortOrder == null) {
      return Collections.emptySet();
    } else {
      return sortOrder.fields().stream()
          .filter(f -> f.transform().preservesOrder())
          .map(SortField::sourceId)
          .map(sid -> sortOrder.schema().findColumnName(sid))
          .filter(Objects::nonNull)
          .collect(Collectors.toSet());
    }
  }
}
