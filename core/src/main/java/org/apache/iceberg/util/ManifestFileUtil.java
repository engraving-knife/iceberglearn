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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.types.Conversions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * Manifest 文件分区统计信息工具类，利用 manifest 中每个分区字段的下界/上界/含 null/含 NaN 统计信息，快速判断某分区值是否可能被该 manifest
 * 包含，从而在扫描计划阶段裁剪无关 manifest。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link ManifestFile.PartitionFieldSummary} 解析为类型安全的 {@link FieldSummary}，封装 下界、上界、含
 *       null、含 NaN 四类信息。
 *   <li>提供 {@link #canContainAny} 系列方法，对一组候选分区值批量判断 manifest 是否可能包含其中任一。
 * </ul>
 *
 * <p>设计意图：利用 manifest 级别的分区统计（而非文件级别）做粗粒度裁剪，减少需要打开的 manifest 数量，提升扫描计划性能。判断逻辑用"值落在 [lowerBound,
 * upperBound] 区间内"作为必要条件， 配合 containsNull/containsNaN 处理特殊值，可能出现假阳性但不会漏判。
 *
 * <p>上下游关系：被扫描计划流程（如 {@link org.apache.iceberg.ManifestGroup}）调用以裁剪 manifest； 依赖 api 模块的 {@link
 * ManifestFile}、{@link PartitionSpec} 与 types 模块的类型转换工具。
 */
public class ManifestFileUtil {
  private ManifestFileUtil() {}

  /** 单个分区字段的统计摘要：持有该字段的比较器、Java 类型、下界、上界及 null/NaN 标记。 */
  private static class FieldSummary<T> {
    private final Comparator<T> comparator;
    private final Class<T> javaClass;
    private final T lowerBound;
    private final T upperBound;
    private final boolean containsNull;
    private final boolean containsNaN;

    /**
     * 从原始分区字段摘要构造类型安全的 FieldSummary。
     *
     * @param primitive 分区字段的原始类型
     * @param summary manifest 中存储的原始摘要
     */
    @SuppressWarnings("unchecked")
    FieldSummary(Type.PrimitiveType primitive, ManifestFile.PartitionFieldSummary summary) {
      this.comparator = Comparators.forType(primitive);
      this.javaClass = (Class<T>) primitive.typeId().javaClass();
      this.lowerBound = Conversions.fromByteBuffer(primitive, summary.lowerBound());
      this.upperBound = Conversions.fromByteBuffer(primitive, summary.upperBound());
      this.containsNull = summary.containsNull();
      this.containsNaN = summary.containsNaN() == null ? true : summary.containsNaN();
    }

    /**
     * 判断该字段统计区间是否可能包含给定值。
     *
     * <p>逻辑：null 值查 containsNull；NaN 值查 containsNaN；非空非 NaN 值则检查类型匹配且 落在 [lowerBound, upperBound]
     * 区间内。lowerBound 为 null 表示无非空值，非空值必不匹配。
     *
     * @param value 待检查的值
     * @return 可能包含返回 true
     */
    boolean canContain(Object value) {
      if (value == null) {
        return containsNull;
      }

      if (NaNUtil.isNaN(value)) {
        return containsNaN;
      }

      // if lower bound is null, then there are no non-null values
      if (lowerBound == null) {
        // the value is non-null, so it cannot match
        return false;
      }

      if (!javaClass.isInstance(value)) {
        return false;
      }

      T typedValue = javaClass.cast(value);

      if (comparator.compare(typedValue, lowerBound) < 0) {
        return false;
      }

      if (comparator.compare(typedValue, upperBound) > 0) {
        return false;
      }

      return true;
    }
  }

  /**
   * 判断一组字段摘要是否可能共同包含给定的分区结构。
   *
   * <p>逻辑：逐字段检查，任一字段不包含则整体返回 false（短路）。
   *
   * @param summaries 各字段的摘要列表
   * @param struct 分区值结构
   * @return 所有字段都可能包含该值返回 true
   */
  private static boolean canContain(List<FieldSummary<?>> summaries, StructLike struct) {
    if (struct.size() != summaries.size()) {
      return false;
    }

    // if any value is not contained, the struct is not contained and this can return early
    for (int pos = 0; pos < summaries.size(); pos += 1) {
      Object value = struct.get(pos, Object.class);
      if (!summaries.get(pos).canContain(value)) {
        return false;
      }
    }

    return true;
  }

  /**
   * 判断 manifest 是否可能包含给定分区集合中的任意一个（分区 spec 与 manifest 相同）。
   *
   * @param manifest 待检查的 manifest
   * @param partitions 候选分区值集合
   * @param specLookup 按 spec ID 查找 PartitionSpec 的函数
   * @return manifest 可能包含至少一个分区返回 true
   */
  public static boolean canContainAny(
      ManifestFile manifest,
      Iterable<StructLike> partitions,
      Function<Integer, PartitionSpec> specLookup) {
    if (manifest.partitions() == null) {
      return true;
    }

    List<FieldSummary<?>> summaries = summaries(manifest, specLookup);

    for (StructLike partition : partitions) {
      if (canContain(summaries, partition)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 判断 manifest 是否可能包含给定分区集合中的任意一个（分区携带 spec ID，需匹配 manifest 的 spec ID）。
   *
   * @param manifest 待检查的 manifest
   * @param partitions 候选分区集合，每个元素为 (specId, partitionValue)
   * @param specsById 按 spec ID 索引的 PartitionSpec 映射
   * @return manifest 可能包含至少一个分区返回 true
   */
  public static boolean canContainAny(
      ManifestFile manifest,
      Iterable<Pair<Integer, StructLike>> partitions,
      Map<Integer, PartitionSpec> specsById) {
    if (manifest.partitions() == null) {
      return true;
    }

    List<FieldSummary<?>> summaries = summaries(manifest, specsById::get);

    for (Pair<Integer, StructLike> partition : partitions) {
      if (partition.first() == manifest.partitionSpecId()
          && canContain(summaries, partition.second())) {
        return true;
      }
    }

    return false;
  }

  /**
   * 解析 manifest 的分区统计信息为 FieldSummary 列表。
   *
   * <p>逻辑：按 manifest 的 spec ID 查找 PartitionSpec，获取分区结构类型；逐字段把原始 PartitionFieldSummary 包装为类型安全的
   * FieldSummary。
   *
   * @param manifest manifest 文件
   * @param specLookup spec 查找函数
   * @return 各分区字段的摘要列表
   */
  private static List<FieldSummary<?>> summaries(
      ManifestFile manifest, Function<Integer, PartitionSpec> specLookup) {
    Types.StructType partitionType = specLookup.apply(manifest.partitionSpecId()).partitionType();
    List<ManifestFile.PartitionFieldSummary> fieldSummaries = manifest.partitions();
    List<Types.NestedField> fields = partitionType.fields();

    List<FieldSummary<?>> summaries = Lists.newArrayListWithExpectedSize(fieldSummaries.size());
    for (int pos = 0; pos < fieldSummaries.size(); pos += 1) {
      Type.PrimitiveType primitive = fields.get(pos).type().asPrimitiveType();
      summaries.add(new FieldSummary<>(primitive, fieldSummaries.get(pos)));
    }

    return summaries;
  }
}
