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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ListMultimap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.transforms.UnknownTransform;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.StructType;

/**
 * 表示表的分区数据如何生成。
 *
 * <p>所属模块：iceberg-api（分区抽象层）。
 *
 * <p>职责：定义一组 {@link PartitionField}（源字段 + Transform），描述如何由表中的列变换 得到分区值；提供分区类型、分区到路径、按源字段查询分区字段等能力。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>分区字段 ID 从 1000 起分配，全表所有 spec 共享同一 ID 空间。
 *   <li>fields 以数组形式持久化以保证 DataFile schema 顺序稳定；多个派生视图
 *       （fieldList、fieldsBySourceId、partitionType、javaClasses）均为 transient + 双检锁懒加载，避免序列化体积并支持并发安全。
 *   <li>通过 {@link #compatibleWith(PartitionSpec)} 在忽略字段 ID 的情况下判断两个 spec 是否结构等价，用于演化兼容判断。
 * </ul>
 *
 * <p>上下游关系：由表元数据持有；被扫描规划、文件写入、分区值计算等模块使用。
 */
public class PartitionSpec implements Serializable {
  // IDs for partition fields start at 1000
  private static final int PARTITION_DATA_ID_START = 1000;

  private final Schema schema;

  // this is ordered so that DataFile has a consistent schema
  private final int specId;
  private final PartitionField[] fields;
  private transient volatile ListMultimap<Integer, PartitionField> fieldsBySourceId = null;
  private transient volatile Class<?>[] lazyJavaClasses = null;
  private transient volatile StructType lazyPartitionType = null;
  private transient volatile List<PartitionField> fieldList = null;
  private final int lastAssignedFieldId;

  private PartitionSpec(
      Schema schema, int specId, List<PartitionField> fields, int lastAssignedFieldId) {
    this.schema = schema;
    this.specId = specId;
    this.fields = fields.toArray(new PartitionField[0]);
    this.lastAssignedFieldId = lastAssignedFieldId;
  }

  /** 返回本 spec 关联的表 {@link Schema}。 */
  public Schema schema() {
    return schema;
  }

  /** 返回本 spec 的 ID。 */
  public int specId() {
    return specId;
  }

  /** 返回本 spec 的分区字段列表（不可变视图）。 */
  public List<PartitionField> fields() {
    return lazyFieldList();
  }

  /** 是否存在有效分区字段（非 void transform）。 */
  public boolean isPartitioned() {
    return fields.length > 0 && fields().stream().anyMatch(f -> !f.transform().isVoid());
  }

  /** 是否未分区。 */
  public boolean isUnpartitioned() {
    return !isPartitioned();
  }

  /** 返回本 spec 已分配的最大分区字段 ID。 */
  int lastAssignedFieldId() {
    return lastAssignedFieldId;
  }

  /**
   * 将本 spec 转换为未绑定形式 {@link UnboundPartitionSpec}，便于序列化/跨 schema 传输。
   *
   * <p>逻辑：以 specId 构造 builder，逐个字段写入 transform 字符串、sourceId、fieldId、name。
   */
  public UnboundPartitionSpec toUnbound() {
    UnboundPartitionSpec.Builder builder = UnboundPartitionSpec.builder().withSpecId(specId);

    for (PartitionField field : fields) {
      builder.addField(
          field.transform().toString(), field.sourceId(), field.fieldId(), field.name());
    }

    return builder.build();
  }

  /**
   * 返回按指定源字段 ID 分区的所有 {@link PartitionField}。
   *
   * @param fieldId 源 schema 中的字段 ID
   * @return 该源字段对应的分区字段列表
   */
  public List<PartitionField> getFieldsBySourceId(int fieldId) {
    return lazyFieldsBySourceId().get(fieldId);
  }

  /**
   * 返回本 spec 定义的分区数据 {@link StructType}。
   *
   * <p>逻辑：双检锁懒构建。遍历每个分区字段，由其 transform 的 getResultType 计算结果类型， 组装为 optional NestedField 列表后构造
   * StructType。
   */
  public StructType partitionType() {
    if (lazyPartitionType == null) {
      synchronized (this) {
        if (lazyPartitionType == null) {
          List<Types.NestedField> structFields = Lists.newArrayListWithExpectedSize(fields.length);

          for (PartitionField field : fields) {
            Type sourceType = schema.findType(field.sourceId());
            Type resultType = field.transform().getResultType(sourceType);
            structFields.add(Types.NestedField.optional(field.fieldId(), field.name(), resultType));
          }

          this.lazyPartitionType = Types.StructType.of(structFields);
        }
      }
    }

    return lazyPartitionType;
  }

  /**
   * 返回各分区字段对应的 Java 类型数组。
   *
   * <p>逻辑：双检锁懒构建。对每个字段，若 transform 是 {@link UnknownTransform} 则用 Object， 否则由 transform 的
   * getResultType 推导 javaClass。
   */
  public Class<?>[] javaClasses() {
    if (lazyJavaClasses == null) {
      synchronized (this) {
        if (lazyJavaClasses == null) {
          Class<?>[] classes = new Class<?>[fields.length];
          for (int i = 0; i < fields.length; i += 1) {
            PartitionField field = fields[i];
            if (field.transform() instanceof UnknownTransform) {
              classes[i] = Object.class;
            } else {
              Type sourceType = schema.findType(field.sourceId());
              Type result = field.transform().getResultType(sourceType);
              classes[i] = result.typeId().javaClass();
            }
          }

          this.lazyJavaClasses = classes;
        }
      }
    }

    return lazyJavaClasses;
  }

  /** 按位置与 Java 类型从 {@link StructLike} 中取分区值。 */
  @SuppressWarnings("unchecked")
  private <T> T get(StructLike data, int pos, Class<?> javaClass) {
    return data.get(pos, (Class<T>) javaClass);
  }

  /** 对分区值字符串做 URL 编码，避免出现路径非法字符。 */
  private String escape(String string) {
    try {
      return URLEncoder.encode(string, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将分区数据元组转换为分区路径字符串（如 {@code fieldA=valA/fieldB=valB}）。
   *
   * <p>逻辑：遍历各分区字段，用其 transform 把分区值转为人类可读字符串，再 URL 编码， 以 {@code name=value} 形式拼接，多字段间用 "/" 分隔。
   *
   * @param data 分区数据元组
   * @return 分区路径字符串
   */
  public String partitionToPath(StructLike data) {
    StringBuilder sb = new StringBuilder();
    Class<?>[] javaClasses = javaClasses();
    List<Types.NestedField> outputFields = partitionType().fields();
    for (int i = 0; i < javaClasses.length; i += 1) {
      PartitionField field = fields[i];
      Type type = outputFields.get(i).type();
      String valueString = field.transform().toHumanString(type, get(data, i, javaClasses[i]));

      if (i > 0) {
        sb.append("/");
      }
      sb.append(field.name()).append("=").append(escape(valueString));
    }
    return sb.toString();
  }

  /**
   * 判断本 spec 与另一个 spec 是否结构兼容（忽略分区字段 ID）。
   *
   * <p>逻辑：若 equals 直接返回 true；否则要求字段数相同，且对应字段的 sourceId、 transform 字符串、name 全部相等。
   *
   * @param other 另一个 PartitionSpec
   * @return 若字段数、顺序、名称、源列、transform 均相同则返回 true
   */
  public boolean compatibleWith(PartitionSpec other) {
    if (equals(other)) {
      return true;
    }

    if (fields.length != other.fields.length) {
      return false;
    }

    for (int i = 0; i < fields.length; i += 1) {
      PartitionField thisField = fields[i];
      PartitionField thatField = other.fields[i];
      if (thisField.sourceId() != thatField.sourceId()
          || !thisField.transform().toString().equals(thatField.transform().toString())
          || !thisField.name().equals(thatField.name())) {
        return false;
      }
    }

    return true;
  }

  /** 相等性：specId 相同且 fields 数组内容相同。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (!(other instanceof PartitionSpec)) {
      return false;
    }

    PartitionSpec that = (PartitionSpec) other;
    if (this.specId != that.specId) {
      return false;
    }
    return Arrays.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return 31 * Integer.hashCode(specId) + Arrays.hashCode(fields);
  }

  /** 双检锁懒构建字段列表的不可变视图。 */
  private List<PartitionField> lazyFieldList() {
    if (fieldList == null) {
      synchronized (this) {
        if (fieldList == null) {
          this.fieldList = ImmutableList.copyOf(fields);
        }
      }
    }
    return fieldList;
  }

  /** 双检锁懒构建 sourceId -> 分区字段列表 的 Multimap。 */
  private ListMultimap<Integer, PartitionField> lazyFieldsBySourceId() {
    if (fieldsBySourceId == null) {
      synchronized (this) {
        if (fieldsBySourceId == null) {
          ListMultimap<Integer, PartitionField> multiMap =
              Multimaps.newListMultimap(
                  Maps.newHashMap(), () -> Lists.newArrayListWithCapacity(fields.length));
          for (PartitionField field : fields) {
            multiMap.put(field.sourceId(), field);
          }
          this.fieldsBySourceId = multiMap;
        }
      }
    }

    return fieldsBySourceId;
  }

  /**
   * 返回所有 identity transform 分区字段对应的源字段 ID 集合。
   *
   * @return identity 分区字段的源 ID 集合
   */
  public Set<Integer> identitySourceIds() {
    Set<Integer> sourceIds = Sets.newHashSet();
    for (PartitionField field : fields()) {
      if ("identity".equals(field.transform().toString())) {
        sourceIds.add(field.sourceId());
      }
    }

    return sourceIds;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (PartitionField field : fields) {
      sb.append("\n");
      sb.append("  ").append(field);
    }
    if (fields.length > 0) {
      sb.append("\n");
    }
    sb.append("]");
    return sb.toString();
  }

  private static final PartitionSpec UNPARTITIONED_SPEC =
      new PartitionSpec(new Schema(), 0, ImmutableList.of(), unpartitionedLastAssignedId());

  /**
   * 返回未分区表的 spec 单例。
   *
   * @return 不含任何分区字段的 spec
   */
  public static PartitionSpec unpartitioned() {
    return UNPARTITIONED_SPEC;
  }

  private static int unpartitionedLastAssignedId() {
    return PARTITION_DATA_ID_START - 1;
  }

  /**
   * 为给定 {@link Schema} 创建分区 spec 构建器。
   *
   * @param schema 表 schema
   * @return 分区 spec 构建器
   */
  public static Builder builderFor(Schema schema) {
    return new Builder(schema);
  }

  /**
   * 用于构造合法 {@link PartitionSpec} 的构建器。
   *
   * <p>设计意图：提供 identity/year/month/day/hour/bucket/truncate/alwaysNull 等常见 transform
   * 的便捷方法，统一处理分区名冲突校验、字段 ID 自增分配、冗余分区检测。通过 {@link #builderFor(Schema)} 创建实例。
   */
  public static class Builder {
    private final Schema schema;
    private final List<PartitionField> fields = Lists.newArrayList();
    private final Set<String> partitionNames = Sets.newHashSet();
    private Map<Map.Entry<Integer, String>, PartitionField> dedupFields = Maps.newHashMap();
    private int specId = 0;
    private final AtomicInteger lastAssignedFieldId =
        new AtomicInteger(unpartitionedLastAssignedId());
    // check if there are conflicts between partition and schema field name
    private boolean checkConflicts = true;

    private Builder(Schema schema) {
      this.schema = schema;
    }

    /** 自增分配下一个分区字段 ID。 */
    private int nextFieldId() {
      return lastAssignedFieldId.incrementAndGet();
    }

    private void checkAndAddPartitionName(String name) {
      checkAndAddPartitionName(name, null);
    }

    /** 控制是否校验分区名与 schema 字段名冲突。 */
    Builder checkConflicts(boolean check) {
      checkConflicts = check;
      return this;
    }

    /**
     * 校验并登记分区名。
     *
     * <p>逻辑：
     *
     * <ul>
     *   <li>identity 场景（sourceColumnId 非 null）允许分区名与 schema 字段同名，但要求 源字段一致；
     *   <li>其他 transform 不允许分区名与 schema 字段同名；
     *   <li>禁止空/null 名与重复名。
     * </ul>
     *
     * @param name 分区名
     * @param sourceColumnId 源字段 ID，identity 时传入；其他 transform 传 null
     */
    private void checkAndAddPartitionName(String name, Integer sourceColumnId) {
      Types.NestedField schemaField = schema.findField(name);
      if (checkConflicts) {
        if (sourceColumnId != null) {
          // for identity transform case we allow conflicts between partition and schema field name
          // as
          //   long as they are sourced from the same schema field
          Preconditions.checkArgument(
              schemaField == null || schemaField.fieldId() == sourceColumnId,
              "Cannot create identity partition sourced from different field in schema: %s",
              name);
        } else {
          // for all other transforms we don't allow conflicts between partition name and schema
          // field name
          Preconditions.checkArgument(
              schemaField == null,
              "Cannot create partition from name that exists in schema: %s",
              name);
        }
      }
      Preconditions.checkArgument(
          name != null && !name.isEmpty(), "Cannot use empty or null partition name: %s", name);
      Preconditions.checkArgument(
          !partitionNames.contains(name), "Cannot use partition name more than once: %s", name);
      partitionNames.add(name);
    }

    /**
     * 检测冗余分区：同一 sourceId + transform dedupName 只能出现一次。
     *
     * @param field 待检测的分区字段
     */
    private void checkForRedundantPartitions(PartitionField field) {
      Map.Entry<Integer, String> dedupKey =
          new AbstractMap.SimpleEntry<>(field.sourceId(), field.transform().dedupName());
      PartitionField partitionField = dedupFields.get(dedupKey);
      Preconditions.checkArgument(
          partitionField == null,
          "Cannot add redundant partition: %s conflicts with %s",
          partitionField,
          field);
      dedupFields.put(dedupKey, field);
    }

    /** 设置 spec ID。 */
    public Builder withSpecId(int newSpecId) {
      this.specId = newSpecId;
      return this;
    }

    /** 按名称查找源列，找不到则抛异常。 */
    private Types.NestedField findSourceColumn(String sourceName) {
      Types.NestedField sourceColumn = schema.findField(sourceName);
      Preconditions.checkArgument(
          sourceColumn != null, "Cannot find source column: %s", sourceName);
      return sourceColumn;
    }

    /** 添加一个 identity 分区字段（指定源名与目标名）。 */
    Builder identity(String sourceName, String targetName) {
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      checkAndAddPartitionName(targetName, sourceColumn.fieldId());
      PartitionField field =
          new PartitionField(
              sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.identity());
      checkForRedundantPartitions(field);
      fields.add(field);
      return this;
    }

    /** 添加一个 identity 分区字段，目标名与源名相同。 */
    public Builder identity(String sourceName) {
      return identity(sourceName, sourceName);
    }

    /** 添加一个 year 分区字段（指定源名与目标名）。 */
    public Builder year(String sourceName, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      PartitionField field =
          new PartitionField(sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.year());
      checkForRedundantPartitions(field);
      fields.add(field);
      return this;
    }

    /** 添加一个 year 分区字段，目标名为 源名_year。 */
    public Builder year(String sourceName) {
      return year(sourceName, sourceName + "_year");
    }

    /** 添加一个 month 分区字段（指定源名与目标名）。 */
    public Builder month(String sourceName, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      PartitionField field =
          new PartitionField(sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.month());
      checkForRedundantPartitions(field);
      fields.add(field);
      return this;
    }

    /** 添加一个 month 分区字段，目标名为 源名_month。 */
    public Builder month(String sourceName) {
      return month(sourceName, sourceName + "_month");
    }

    /** 添加一个 day 分区字段（指定源名与目标名）。 */
    public Builder day(String sourceName, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      PartitionField field =
          new PartitionField(sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.day());
      checkForRedundantPartitions(field);
      fields.add(field);
      return this;
    }

    /** 添加一个 day 分区字段，目标名为 源名_day。 */
    public Builder day(String sourceName) {
      return day(sourceName, sourceName + "_day");
    }

    /** 添加一个 hour 分区字段（指定源名与目标名）。 */
    public Builder hour(String sourceName, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      PartitionField field =
          new PartitionField(sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.hour());
      checkForRedundantPartitions(field);
      fields.add(field);
      return this;
    }

    /** 添加一个 hour 分区字段，目标名为 源名_hour。 */
    public Builder hour(String sourceName) {
      return hour(sourceName, sourceName + "_hour");
    }

    /** 添加一个 bucket 分区字段（指定源名、桶数、目标名）。 */
    public Builder bucket(String sourceName, int numBuckets, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      fields.add(
          new PartitionField(
              sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.bucket(numBuckets)));
      return this;
    }

    /** 添加一个 bucket 分区字段，目标名为 源名_bucket。 */
    public Builder bucket(String sourceName, int numBuckets) {
      return bucket(sourceName, numBuckets, sourceName + "_bucket");
    }

    /** 添加一个 truncate 分区字段（指定源名、截断宽度、目标名）。 */
    public Builder truncate(String sourceName, int width, String targetName) {
      checkAndAddPartitionName(targetName);
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      fields.add(
          new PartitionField(
              sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.truncate(width)));
      return this;
    }

    /** 添加一个 truncate 分区字段，目标名为 源名_trunc。 */
    public Builder truncate(String sourceName, int width) {
      return truncate(sourceName, width, sourceName + "_trunc");
    }

    /** 添加一个 alwaysNull 分区字段（用于占位/演化场景，指定源名与目标名）。 */
    public Builder alwaysNull(String sourceName, String targetName) {
      Types.NestedField sourceColumn = findSourceColumn(sourceName);
      checkAndAddPartitionName(
          targetName, sourceColumn.fieldId()); // can duplicate a source column name
      fields.add(
          new PartitionField(
              sourceColumn.fieldId(), nextFieldId(), targetName, Transforms.alwaysNull()));
      return this;
    }

    /** 添加一个 alwaysNull 分区字段，目标名为 源名_null。 */
    public Builder alwaysNull(String sourceName) {
      return alwaysNull(sourceName, sourceName + "_null");
    }

    // add a partition field with an auto-increment partition field id starting from
    // PARTITION_DATA_ID_START
    Builder add(int sourceId, String name, Transform<?, ?> transform) {
      return add(sourceId, nextFieldId(), name, transform);
    }

    /**
     * 添加一个分区字段，显式指定字段 ID。
     *
     * <p>逻辑：校验分区名后构造 PartitionField 加入列表，并用 Math.max 更新 lastAssignedFieldId 以保证后续分配不冲突。
     */
    Builder add(int sourceId, int fieldId, String name, Transform<?, ?> transform) {
      checkAndAddPartitionName(name, sourceId);
      fields.add(new PartitionField(sourceId, fieldId, name, transform));
      lastAssignedFieldId.getAndAccumulate(fieldId, Math::max);
      return this;
    }

    /**
     * 构建并校验分区 spec。
     *
     * <p>逻辑：先调用 {@link #buildUnchecked()} 构造，再调用 {@link #checkCompatibility} 校验 各字段 transform
     * 与源类型兼容。
     */
    public PartitionSpec build() {
      PartitionSpec spec = buildUnchecked();
      checkCompatibility(spec, schema);
      return spec;
    }

    /** 不做兼容性校验直接构造 spec，供内部使用。 */
    PartitionSpec buildUnchecked() {
      return new PartitionSpec(schema, specId, fields, lastAssignedFieldId.get());
    }
  }

  /**
   * 校验 spec 中每个分区字段的 transform 与源类型兼容。
   *
   * <p>逻辑：对每个字段，若 transform 是 alwaysNull（void）则跳过；否则校验源类型存在、 是基本类型、且 transform 可作用于该类型。
   *
   * @param spec 待校验的 spec
   * @param schema 表 schema
   */
  static void checkCompatibility(PartitionSpec spec, Schema schema) {
    for (PartitionField field : spec.fields) {
      Type sourceType = schema.findType(field.sourceId());
      Transform<?, ?> transform = field.transform();
      // In the case of a Version 1 partition-spec field gets deleted,
      // it is replaced with a void transform, see:
      // https://iceberg.apache.org/spec/#partition-transforms
      // We don't care about the source type since a VoidTransform is always compatible and skip the
      // checks
      if (!transform.equals(Transforms.alwaysNull())) {
        ValidationException.check(
            sourceType != null, "Cannot find source column for partition field: %s", field);
        ValidationException.check(
            sourceType.isPrimitiveType(),
            "Cannot partition by non-primitive source field: %s",
            sourceType);
        ValidationException.check(
            transform.canTransform(sourceType),
            "Invalid source type %s for transform: %s",
            sourceType,
            transform);
      }
    }
  }

  /**
   * 判断 spec 的分区字段 ID 是否从 {@code PARTITION_DATA_ID_START} 起连续递增。
   *
   * @param spec 待检查的 spec
   * @return 字段 ID 连续递增则返回 true
   */
  static boolean hasSequentialIds(PartitionSpec spec) {
    for (int i = 0; i < spec.fields.length; i += 1) {
      if (spec.fields[i].fieldId() != PARTITION_DATA_ID_START + i) {
        return false;
      }
    }
    return true;
  }
}
