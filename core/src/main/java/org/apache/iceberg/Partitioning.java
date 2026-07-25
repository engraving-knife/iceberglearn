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
package org.apache.iceberg;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.collect.FluentIterable;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.transforms.PartitionSpecVisitor;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.transforms.UnknownTransform;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;

/**
 * 文件级说明：分区相关工具方法。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>判断分区规范是否包含 bucket 字段。
 *   <li>根据分区规范生成推荐的 sort order（聚簇数据）。
 *   <li>构建 grouping key 类型 / 统一 partition type，支持多 spec 表。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>通过 {@link PartitionSpecVisitor} 模式遍历分区字段，避免对每种 transform 重复 if/else。
 *   <li>grouping key 取所有 spec 的交集（且非 void transform），保证 Iceberg 能保证不同 key 值的记录互不相交；统一 partition
 *       type 取并集，用于元数据表统一展示。
 *   <li>对 v1 表中“已删除分区字段（被替换为 void transform）”做特殊处理，避免类型丢失。
 * </ul>
 *
 * <p>上下游关系：被扫描层、元数据表（如 partitions 表）、写入层调用；依赖 {@link PartitionSpecVisitor}、{@link SortOrder}、{@link
 * Transform} 等。
 */
public class Partitioning {

  /** 私有构造：工具类禁止实例化。 */
  private Partitioning() {}

  /**
   * 判断分区规范中是否包含 bucket 变换的字段。
   *
   * @param spec 分区规范
   * @return true 表示存在 bucket 字段
   */
  public static boolean hasBucketField(PartitionSpec spec) {
    List<Boolean> bucketList =
        PartitionSpecVisitor.visit(
            spec,
            new PartitionSpecVisitor<Boolean>() {
              @Override
              public Boolean identity(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean bucket(int fieldId, String sourceName, int sourceId, int width) {
                return true;
              }

              @Override
              public Boolean truncate(int fieldId, String sourceName, int sourceId, int width) {
                return false;
              }

              @Override
              public Boolean year(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean month(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean day(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean hour(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean alwaysNull(int fieldId, String sourceName, int sourceId) {
                return false;
              }

              @Override
              public Boolean unknown(
                  int fieldId, String sourceName, int sourceId, String transform) {
                return false;
              }
            });

    return bucketList.stream().anyMatch(Boolean::booleanValue);
  }

  /**
   * 根据分区规范生成推荐的排序顺序，使数据按分区聚簇。
   *
   * <p>若分区规范包含 bucket 字段，会在排序末尾追加桶数最多的列以提升聚簇效果。
   *
   * @param spec 分区规范
   * @return 聚簇数据的排序顺序
   */
  public static SortOrder sortOrderFor(PartitionSpec spec) {
    if (spec.isUnpartitioned()) {
      return SortOrder.unsorted();
    }

    SortOrder.Builder builder = SortOrder.builderFor(spec.schema());
    SpecToOrderVisitor converter = new SpecToOrderVisitor(builder);
    PartitionSpecVisitor.visit(spec, converter);

    // columns used for bucketing are high cardinality; add one to the sort at the end
    String bucketColumn = converter.bucketColumn();
    if (bucketColumn != null) {
      builder.asc(bucketColumn);
    }

    return builder.build();
  }

  /**
   * 把分区字段转换为 sort order 字段的访问器：每个分区字段对应一条 sort 字段。
   *
   * <p>设计要点：
   *
   * <ul>
   *   <li>identity / bucket / truncate / 时间变换均按相同 transform 加入 sort；
   *   <li>{@code alwaysNull} 不加入 sort；
   *   <li>bucket 字段额外记录“桶数最大”的列名，调用方会在末尾追加该列以提升聚簇效果。
   * </ul>
   */
  private static class SpecToOrderVisitor implements PartitionSpecVisitor<Void> {
    private final SortOrder.Builder builder;
    private String bucketColumn = null;
    private int highestNumBuckets = 0;

    private SpecToOrderVisitor(SortOrder.Builder builder) {
      this.builder = builder;
    }

    /** @return 当前记录的桶数最多的列名（用于在 sort order 末尾追加该列） */
    String bucketColumn() {
      return bucketColumn;
    }

    @Override
    public Void identity(int fieldId, String sourceName, int sourceId) {
      builder.asc(sourceName);
      return null;
    }

    @Override
    public Void bucket(int fieldId, String sourceName, int sourceId, int numBuckets) {
      // the column with highest cardinality is usually the one with the highest number of buckets
      if (numBuckets > highestNumBuckets) {
        this.highestNumBuckets = numBuckets;
        this.bucketColumn = sourceName;
      }
      builder.asc(Expressions.bucket(sourceName, numBuckets));
      return null;
    }

    @Override
    public Void truncate(int fieldId, String sourceName, int sourceId, int width) {
      builder.asc(Expressions.truncate(sourceName, width));
      return null;
    }

    @Override
    public Void year(int fieldId, String sourceName, int sourceId) {
      builder.asc(Expressions.year(sourceName));
      return null;
    }

    @Override
    public Void month(int fieldId, String sourceName, int sourceId) {
      builder.asc(Expressions.month(sourceName));
      return null;
    }

    @Override
    public Void day(int fieldId, String sourceName, int sourceId) {
      builder.asc(Expressions.day(sourceName));
      return null;
    }

    @Override
    public Void hour(int fieldId, String sourceName, int sourceId) {
      builder.asc(Expressions.hour(sourceName));
      return null;
    }

    @Override
    public Void alwaysNull(int fieldId, String sourceName, int sourceId) {
      // do nothing for alwaysNull, it doesn't need to be added to the sort
      return null;
    }
  }

  /**
   * 构建 grouping key 类型：取所有 spec 中非 void transform 分区字段的交集。
   *
   * <p>grouping key 定义了数据如何在文件间分割。Iceberg 保证不同 grouping key 值的记录互不相交。 多 spec 表取交集，单 spec 取全部活跃字段。v1
   * 表中被删除的分区字段（void transform）不参与。
   *
   * @param schema 可选 schema，指定只考虑某些 source 列（null 表示考虑全部）
   * @param specs 一个或多个分区规范
   * @return 构建的 grouping key 类型
   */
  public static StructType groupingKeyType(Schema schema, Collection<PartitionSpec> specs) {
    return buildPartitionProjectionType("grouping key", specs, commonActiveFieldIds(schema, specs));
  }

  /**
   * 构建表的统一分区类型：取所有 spec 中分区字段的并集。
   *
   * <p>单 spec 表直接返回该 spec 的分区类型；多 spec 表返回所有曾出现过的分区字段的并集 struct。
   *
   * @param table 包含一个或多个 spec 的表
   * @return 统一分区类型
   */
  public static StructType partitionType(Table table) {
    Collection<PartitionSpec> specs = table.specs().values();
    return buildPartitionProjectionType("table partition", specs, allFieldIds(specs));
  }

  /**
   * 构建分区投影类型（grouping key 或统一 partition type 的核心实现）。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>收集所有 spec 中的 unknown transform，存在则抛 {@link ValidationException}；
   *   <li>按 spec id 降序遍历，使最新 spec 的字段名优先被记录；
   *   <li>对每个目标 fieldId，校验跨 spec 是否兼容（忽略名称差异）； 若旧记录是 void transform 而新不是，则用新字段替换，恢复正确类型；
   *   <li>按 fieldId 升序组装为 {@link StructType}。
   * </ol>
   *
   * @param typeName 类型名称（用于错误信息）
   * @param specs 参与的分区规范集合
   * @param projectedFieldIds 需要包含的字段 id 集合
   * @return 构造出的分区投影类型
   * @throws ValidationException 当存在 unknown transform 或字段冲突时
   */
  private static StructType buildPartitionProjectionType(
      String typeName, Collection<PartitionSpec> specs, Set<Integer> projectedFieldIds) {

    // we currently don't know the output type of unknown transforms
    List<Transform<?, ?>> unknownTransforms = collectUnknownTransforms(specs);
    ValidationException.check(
        unknownTransforms.isEmpty(),
        "Cannot build %s type, unknown transforms: %s",
        typeName,
        unknownTransforms);

    Map<Integer, PartitionField> fieldMap = Maps.newHashMap();
    Map<Integer, Type> typeMap = Maps.newHashMap();
    Map<Integer, String> nameMap = Maps.newHashMap();

    // sort specs by ID in descending order to pick up the most recent field names
    List<PartitionSpec> sortedSpecs =
        specs.stream()
            .sorted(Comparator.comparingLong(PartitionSpec::specId).reversed())
            .collect(Collectors.toList());

    for (PartitionSpec spec : sortedSpecs) {
      for (PartitionField field : spec.fields()) {
        int fieldId = field.fieldId();

        if (!projectedFieldIds.contains(fieldId)) {
          continue;
        }

        NestedField structField = spec.partitionType().field(fieldId);
        PartitionField existingField = fieldMap.get(fieldId);

        if (existingField == null) {
          fieldMap.put(fieldId, field);
          typeMap.put(fieldId, structField.type());
          nameMap.put(fieldId, structField.name());

        } else {
          // verify the fields are compatible as they may conflict in v1 tables
          ValidationException.check(
              equivalentIgnoringNames(field, existingField),
              "Conflicting partition fields: ['%s', '%s']",
              field,
              existingField);

          // use the correct type for dropped partitions in v1 tables
          if (isVoidTransform(existingField) && !isVoidTransform(field)) {
            fieldMap.put(fieldId, field);
            typeMap.put(fieldId, structField.type());
          }
        }
      }
    }

    List<NestedField> sortedStructFields =
        fieldMap.keySet().stream()
            .sorted(Comparator.naturalOrder())
            .map(
                fieldId ->
                    NestedField.optional(fieldId, nameMap.get(fieldId), typeMap.get(fieldId)))
            .collect(Collectors.toList());
    return StructType.of(sortedStructFields);
  }

  /**
   * 判断分区字段是否使用 void transform（即 v1 表中已删除的分区字段被替换为 alwaysNull）。
   *
   * @param field 分区字段
   * @return true 表示该字段的 transform 是 alwaysNull
   */
  private static boolean isVoidTransform(PartitionField field) {
    return field.transform().equals(Transforms.alwaysNull());
  }

  /**
   * 收集所有 spec 中的 unknown transform（自定义但引擎不识别的 transform）。
   *
   * @param specs 分区规范集合
   * @return unknown transform 列表
   */
  private static List<Transform<?, ?>> collectUnknownTransforms(Collection<PartitionSpec> specs) {
    List<Transform<?, ?>> unknownTransforms = Lists.newArrayList();

    for (PartitionSpec spec : specs) {
      spec.fields().stream()
          .map(PartitionField::transform)
          .filter(transform -> transform instanceof UnknownTransform)
          .forEach(unknownTransforms::add);
    }

    return unknownTransforms;
  }

  /**
   * 判断两个分区字段是否等价（忽略字段名差异）。
   *
   * <p>等价条件：fieldId 相同 + sourceId 相同 + transform 兼容。
   *
   * @param field 字段 A
   * @param anotherField 字段 B
   * @return true 表示两字段等价（忽略名称）
   */
  private static boolean equivalentIgnoringNames(
      PartitionField field, PartitionField anotherField) {
    return field.fieldId() == anotherField.fieldId()
        && field.sourceId() == anotherField.sourceId()
        && compatibleTransforms(field.transform(), anotherField.transform());
  }

  /**
   * 判断两个 transform 是否兼容（用于跨 spec 字段比对）。
   *
   * <p>兼容条件：完全相等，或其中之一是 alwaysNull（被删除字段）。
   *
   * @param t1 transform A
   * @param t2 transform B
   * @return true 表示兼容
   */
  private static boolean compatibleTransforms(Transform<?, ?> t1, Transform<?, ?> t2) {
    return t1.equals(t2)
        || t1.equals(Transforms.alwaysNull())
        || t2.equals(Transforms.alwaysNull());
  }

  /**
   * 收集所有 spec 中出现过的分区字段 id 的并集（用于统一 partition type）。
   *
   * @param specs 分区规范集合
   * @return 字段 id 并集
   */
  private static Set<Integer> allFieldIds(Collection<PartitionSpec> specs) {
    return FluentIterable.from(specs)
        .transformAndConcat(PartitionSpec::fields)
        .transform(PartitionField::fieldId)
        .toSet();
  }

  /**
   * 收集所有 spec 中“非 void transform”的分区字段 id 的交集（用于 grouping key）。
   *
   * <p>步骤：以第一个 spec 的活跃字段 id 为初始集合，与后续每个 spec 取交集。
   *
   * @param schema 可选 schema（用于只考虑某些 source 列），可为 null
   * @param specs 分区规范集合
   * @return 字段 id 交集
   */
  private static Set<Integer> commonActiveFieldIds(Schema schema, Collection<PartitionSpec> specs) {
    Set<Integer> commonActiveFieldIds = Sets.newHashSet();

    int specIndex = 0;
    for (PartitionSpec spec : specs) {
      if (specIndex == 0) {
        commonActiveFieldIds.addAll(activeFieldIds(schema, spec));
      } else {
        commonActiveFieldIds.retainAll(activeFieldIds(schema, spec));
      }

      specIndex++;
    }

    return commonActiveFieldIds;
  }

  /**
   * 收集单个 spec 中的活跃分区字段 id（非 void transform，且 source 列在 schema 中存在）。
   *
   * @schema 为 null 时不过滤 source 列
   * @param schema 可选 schema，可为 null
   * @param spec 分区规范
   * @return 活跃字段 id 列表
   */
  private static List<Integer> activeFieldIds(Schema schema, PartitionSpec spec) {
    return spec.fields().stream()
        .filter(field -> schema == null || schema.findField(field.sourceId()) != null)
        .filter(field -> !isVoidTransform(field))
        .map(PartitionField::fieldId)
        .collect(Collectors.toList());
  }
}
