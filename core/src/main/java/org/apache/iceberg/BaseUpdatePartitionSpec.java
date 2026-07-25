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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.BoundTerm;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Term;
import org.apache.iceberg.expressions.UnboundTerm;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.transforms.PartitionSpecVisitor;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.transforms.UnknownTransform;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.Pair;

/**
 * 分区规格（PartitionSpec）更新事务的实现：支持增删改分区字段并提交。
 *
 * <p>所属模块：iceberg-core（表元数据更新事务层，实现 api 中的 {@link UpdatePartitionSpec}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在现有 {@link PartitionSpec} 上累积"新增/删除/重命名"分区字段的变更，并提供 {@link #apply()} 预览结果与 {@link
 *       #commit()} 提交事务。
 *   <li>处理跨版本兼容：V2 表会从历史 specs 中复用同源同 transform 的字段 id；V1 表删除字段 时改写为 alwaysNull transform 以保持字段 id
 *       一致性。
 *   <li>校验变更合法性：禁止重复添加、禁止同时重命名与删除、禁止对同一源字段添加冗余时间分区等。
 * </ul>
 *
 * <p>设计意图：分区规格演进是高频但需谨慎的操作，本类把所有变更暂存为内部集合（adds/deletes/ renames），最终在 {@link #apply()} 时一次性合并到新
 * spec，保证变更可预览、可回滚（commit 前 不影响表元数据）。
 *
 * <p>上下游关系：由表 API（{@code table.updatePartitionSpec()}）构造；底层通过 {@link TableOperations#commit} 把新
 * spec 写入元数据。
 */
class BaseUpdatePartitionSpec implements UpdatePartitionSpec {
  private final TableOperations ops;
  private final TableMetadata base;
  private final int formatVersion;
  private final PartitionSpec spec;
  private final Schema schema;
  private final Map<String, PartitionField> nameToField;
  private final Map<Pair<Integer, String>, PartitionField> transformToField;

  private final List<PartitionField> adds = Lists.newArrayList();
  private final Map<Integer, PartitionField> addedTimeFields = Maps.newHashMap();
  private final Map<Pair<Integer, String>, PartitionField> transformToAddedField =
      Maps.newHashMap();
  private final Map<String, PartitionField> nameToAddedField = Maps.newHashMap();
  private final Set<Object> deletes = Sets.newHashSet();
  private final Map<String, String> renames = Maps.newHashMap();

  private boolean caseSensitive;
  private int lastAssignedPartitionId;

  /**
   * 构造一个分区规格更新事务，基于当前表元数据初始化索引。
   *
   * <p>逻辑：拷贝当前 spec、schema、formatVersion；按字段名与（源id+transform）建立索引便于 后续查重；记录已分配的最大分区字段 id。若当前 spec 含
   * {@link UnknownTransform} 则直接抛异常， 因为无法对未知 transform 进行演进。
   *
   * @param ops 表操作接口
   */
  BaseUpdatePartitionSpec(TableOperations ops) {
    this.ops = ops;
    this.caseSensitive = true;
    this.base = ops.current();
    this.formatVersion = base.formatVersion();
    this.spec = base.spec();
    this.schema = spec.schema();
    this.nameToField = indexSpecByName(spec);
    this.transformToField = indexSpecByTransform(spec);
    this.lastAssignedPartitionId = base.lastAssignedPartitionId();

    spec.fields().stream()
        .filter(field -> field.transform() instanceof UnknownTransform)
        .findAny()
        .ifPresent(
            field -> {
              throw new IllegalArgumentException(
                  "Cannot update partition spec with unknown transform: " + field);
            });
  }

  /** 仅供测试使用：基于 formatVersion 与 spec 构造，不绑定真实表。 */
  @VisibleForTesting
  BaseUpdatePartitionSpec(int formatVersion, PartitionSpec spec) {
    this(formatVersion, spec, spec.lastAssignedFieldId());
  }

  /** 仅供测试使用：基于 formatVersion、spec 与起始分区字段 id 构造。 */
  @VisibleForTesting
  BaseUpdatePartitionSpec(int formatVersion, PartitionSpec spec, int lastAssignedPartitionId) {
    this.ops = null;
    this.base = null;
    this.formatVersion = formatVersion;
    this.caseSensitive = true;
    this.spec = spec;
    this.schema = spec.schema();
    this.nameToField = indexSpecByName(spec);
    this.transformToField = indexSpecByTransform(spec);
    this.lastAssignedPartitionId = lastAssignedPartitionId;
  }

  /** 分配并返回一个新的分区字段 id（自增）。 */
  private int assignFieldId() {
    this.lastAssignedPartitionId += 1;
    return lastAssignedPartitionId;
  }

  /**
   * V2 表中复用历史 spec 中的同源同 transform 字段，否则新建分区字段。
   *
   * <p>逻辑：在 V2 表中遍历所有历史 spec 的字段，若找到 sourceId 与 transform 都相同的字段 （可选地校验名称一致），则复用其 fieldId；找不到或在 V1
   * 表中则新建一个 PartitionField。
   *
   * <p>设计意图：V2 表的分区字段 id 全局唯一且应保持稳定，复用历史 id 可避免数据文件分区值 与字段 id 错配。
   *
   * @param sourceTransform 源字段 id 与 transform 的二元组
   * @param name 目标分区字段名（可为 null 表示不指定）
   * @return 复用或新建的分区字段
   */
  private PartitionField recycleOrCreatePartitionField(
      Pair<Integer, Transform<?, ?>> sourceTransform, String name) {
    if (formatVersion == 2 && base != null) {
      int sourceId = sourceTransform.first();
      Transform<?, ?> transform = sourceTransform.second();

      Set<PartitionField> allHistoricalFields = Sets.newHashSet();
      for (PartitionSpec partitionSpec : base.specs()) {
        allHistoricalFields.addAll(partitionSpec.fields());
      }

      for (PartitionField field : allHistoricalFields) {
        if (field.sourceId() == sourceId && field.transform().equals(transform)) {
          // if target name is specified then consider it too, otherwise not
          if (name == null || field.name().equals(name)) {
            return field;
          }
        }
      }
    }
    return new PartitionField(
        sourceTransform.first(), assignFieldId(), name, sourceTransform.second());
  }

  /**
   * 设置字段名解析是否大小写敏感。
   *
   * @param isCaseSensitive 是否大小写敏感
   * @return 当前事务
   */
  @Override
  public UpdatePartitionSpec caseSensitive(boolean isCaseSensitive) {
    this.caseSensitive = isCaseSensitive;
    return this;
  }

  /**
   * 按源列名添加一个 identity 分区字段。
   *
   * @param sourceName 源列名
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec addField(String sourceName) {
    return addField(Expressions.ref(sourceName));
  }

  /**
   * 按 Term 添加分区字段（不指定名称，由 transform 推导）。
   *
   * @param term 描述源列与 transform 的 Term
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec addField(Term term) {
    return addField(null, term);
  }

  /**
   * 把一个"已删除但本次又添加同源同 transform"的字段改写为恢复（撤销删除，必要时重命名）。
   *
   * <p>逻辑：从 deletes 中移除该字段；若指定了 name 且与现名不同则触发重命名，否则直接保留。
   *
   * @param existing 已存在（被标记删除）的字段
   * @param name 新名称
   * @param sourceTransform 源 id 与 transform 二元组
   * @return 当前事务
   */
  private BaseUpdatePartitionSpec rewriteDeleteAndAddField(
      PartitionField existing, String name, Pair<Integer, Transform<?, ?>> sourceTransform) {
    deletes.remove(existing.fieldId());
    if (name == null || existing.name().equals(name)) {
      return this;
    } else {
      return renameField(existing.name(), name);
    }
  }

  /**
   * 添加一个分区字段（可指定名称）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验同名字段尚未被本次添加过；
   *   <li>解析 Term 得到 (sourceId, transform)，构造校验 key；
   *   <li>若已存在同 key 字段且本次已删除，转走 {@link #rewriteDeleteAndAddField} 恢复；
   *   <li>否则校验不与现有/已添加字段冲突；
   *   <li>复用或新建 PartitionField，若未指定 name 则由 {@link PartitionNameGenerator} 生成；
   *   <li>校验冗余时间分区；更新各索引；处理与现有 void transform 字段的名称冲突。
   * </ol>
   *
   * @param name 分区字段名（可为 null）
   * @param term 描述源列与 transform 的 Term
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec addField(String name, Term term) {
    PartitionField alreadyAdded = nameToAddedField.get(name);
    Preconditions.checkArgument(
        alreadyAdded == null, "Cannot add duplicate partition field: %s", alreadyAdded);

    Pair<Integer, Transform<?, ?>> sourceTransform = resolve(term);
    Pair<Integer, String> validationKey =
        Pair.of(sourceTransform.first(), sourceTransform.second().toString());

    PartitionField existing = transformToField.get(validationKey);
    if (existing != null
        && deletes.contains(existing.fieldId())
        && existing.transform().equals(sourceTransform.second())) {
      return rewriteDeleteAndAddField(existing, name, sourceTransform);
    }

    Preconditions.checkArgument(
        existing == null
            || (deletes.contains(existing.fieldId())
                && !existing.transform().toString().equals(sourceTransform.second().toString())),
        "Cannot add duplicate partition field %s=%s, conflicts with %s",
        name,
        term,
        existing);

    PartitionField added = transformToAddedField.get(validationKey);
    Preconditions.checkArgument(
        added == null,
        "Cannot add duplicate partition field %s=%s, already added: %s",
        name,
        term,
        added);

    PartitionField newField = recycleOrCreatePartitionField(sourceTransform, name);
    if (newField.name() == null) {
      String partitionName =
          PartitionSpecVisitor.visit(schema, newField, PartitionNameGenerator.INSTANCE);
      newField =
          new PartitionField(
              newField.sourceId(), newField.fieldId(), partitionName, newField.transform());
    }

    checkForRedundantAddedPartitions(newField);
    transformToAddedField.put(validationKey, newField);

    PartitionField existingField = nameToField.get(newField.name());
    if (existingField != null && !deletes.contains(existingField.fieldId())) {
      if (isVoidTransform(existingField)) {
        // rename the old deleted field that is being replaced by the new field
        renameField(existingField.name(), existingField.name() + "_" + existingField.fieldId());
      } else {
        throw new IllegalArgumentException(
            String.format("Cannot add duplicate partition field name: %s", name));
      }
    } else if (existingField != null && deletes.contains(existingField.fieldId())) {
      renames.put(existingField.name(), existingField.name() + "_" + existingField.fieldId());
    }

    nameToAddedField.put(newField.name(), newField);

    adds.add(newField);

    return this;
  }

  /**
   * 按字段名删除分区字段。
   *
   * <p>逻辑：校验该字段不是本次新添加的、未被重命名；找到后将其 id 加入 deletes 集合。
   *
   * @param name 待删除字段名
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec removeField(String name) {
    PartitionField alreadyAdded = nameToAddedField.get(name);
    Preconditions.checkArgument(
        alreadyAdded == null, "Cannot delete newly added field: %s", alreadyAdded);

    Preconditions.checkArgument(
        renames.get(name) == null, "Cannot rename and delete partition field: %s", name);

    PartitionField field = nameToField.get(name);
    Preconditions.checkArgument(field != null, "Cannot find partition field to remove: %s", name);

    deletes.add(field.fieldId());

    return this;
  }

  /**
   * 按 Term 删除分区字段（按 sourceId+transform 定位）。
   *
   * @param term 描述源列与 transform 的 Term
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec removeField(Term term) {
    Pair<Integer, Transform<?, ?>> sourceTransform = resolve(term);
    Pair<Integer, String> key =
        Pair.of(sourceTransform.first(), sourceTransform.second().toString());

    PartitionField added = transformToAddedField.get(key);
    Preconditions.checkArgument(added == null, "Cannot delete newly added field: %s", added);

    PartitionField field = transformToField.get(key);
    Preconditions.checkArgument(field != null, "Cannot find partition field to remove: %s", term);
    Preconditions.checkArgument(
        renames.get(field.name()) == null,
        "Cannot rename and delete partition field: %s",
        field.name());

    deletes.add(field.fieldId());

    return this;
  }

  /**
   * 重命名一个分区字段。
   *
   * <p>逻辑：若新名与现有 void transform 字段冲突，先把旧字段改名让位；校验不能重命名本次新添加 的字段、不能重命名已删除字段；最后把 (oldName -> newName)
   * 记入 renames。
   *
   * @param name 旧字段名
   * @param newName 新字段名
   * @return 当前事务
   */
  @Override
  public BaseUpdatePartitionSpec renameField(String name, String newName) {
    PartitionField existingField = nameToField.get(newName);
    if (existingField != null && isVoidTransform(existingField)) {
      // rename the old deleted field that is being replaced by the new field
      renameField(existingField.name(), existingField.name() + "_" + existingField.fieldId());
    }

    PartitionField added = nameToAddedField.get(name);
    Preconditions.checkArgument(
        added == null, "Cannot rename newly added partition field: %s", name);

    PartitionField field = nameToField.get(name);
    Preconditions.checkArgument(field != null, "Cannot find partition field to rename: %s", name);
    Preconditions.checkArgument(
        !deletes.contains(field.fieldId()), "Cannot delete and rename partition field: %s", name);

    renames.put(name, newName);

    return this;
  }

  /**
   * 把所有待应用变更合并为新的 {@link PartitionSpec}（不提交）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>遍历原 spec 字段：未删除的保留（应用重命名）；已删除的在 V1 表中改写为 alwaysNull transform 以保持字段 id 一致；V2 表中直接丢弃；
   *   <li>追加本次新增字段。
   * </ul>
   *
   * @return 应用变更后的新分区规格
   */
  @Override
  public PartitionSpec apply() {
    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);

    for (PartitionField field : spec.fields()) {
      if (!deletes.contains(field.fieldId())) {
        String newName = renames.get(field.name());
        if (newName != null) {
          builder.add(field.sourceId(), field.fieldId(), newName, field.transform());
        } else {
          builder.add(field.sourceId(), field.fieldId(), field.name(), field.transform());
        }
      } else if (formatVersion < 2) {
        // field IDs were not required for v1 and were assigned sequentially in each partition spec
        // starting at 1,000.
        // to maintain consistent field ids across partition specs in v1 tables, any partition field
        // that is removed
        // must be replaced with a null transform. null values are always allowed in partition data.
        String newName = renames.get(field.name());
        if (newName != null) {
          builder.add(field.sourceId(), field.fieldId(), newName, Transforms.alwaysNull());
        } else {
          builder.add(field.sourceId(), field.fieldId(), field.name(), Transforms.alwaysNull());
        }
      }
    }

    for (PartitionField newField : adds) {
      builder.add(newField.sourceId(), newField.fieldId(), newField.name(), newField.transform());
    }

    return builder.build();
  }

  /**
   * 把变更应用到表元数据并提交。
   *
   * <p>逻辑：基于当前元数据调用 {@link TableMetadata#updatePartitionSpec} 生成新元数据， 再通过 {@link
   * TableOperations#commit} 原子提交。
   */
  @Override
  public void commit() {
    TableMetadata update = base.updatePartitionSpec(apply());
    ops.commit(base, update);
  }

  /**
   * 把 Term 解析为 (sourceId, transform) 二元组。
   *
   * <p>逻辑：要求 Term 是 UnboundTerm；按大小写敏感性绑定到 schema；从 BoundTerm 取出 sourceId 与
   * transform；若源字段类型已知，用类型化的 {@link Transforms#fromString} 重建 transform。
   *
   * @param term 待解析的 Term
   * @return (sourceId, transform) 二元组
   */
  private Pair<Integer, Transform<?, ?>> resolve(Term term) {
    Preconditions.checkArgument(term instanceof UnboundTerm, "Term must be unbound");

    BoundTerm<?> boundTerm = ((UnboundTerm<?>) term).bind(schema.asStruct(), caseSensitive);
    int sourceId = boundTerm.ref().fieldId();
    Transform<?, ?> transform = toTransform(boundTerm);

    Type fieldType = schema.findType(sourceId);
    if (fieldType != null) {
      transform = Transforms.fromString(fieldType, transform.toString());
    } else {
      transform = Transforms.fromString(transform.toString());
    }
    return Pair.of(sourceId, transform);
  }

  /**
   * 把 BoundTerm 转换为 Transform：BoundReference 转 identity，BoundTransform 取其 transform。
   *
   * @param term 已绑定的 Term
   * @return 对应的 Transform
   * @throws ValidationException 若 Term 既非 BoundReference 也非 BoundTransform
   */
  private Transform<?, ?> toTransform(BoundTerm<?> term) {
    if (term instanceof BoundReference) {
      return Transforms.identity();
    } else if (term instanceof BoundTransform) {
      return ((BoundTransform<?, ?>) term).transform();
    } else {
      throw new ValidationException(
          "Invalid term: %s, expected either a bound reference or transform", term);
    }
  }

  /**
   * 校验新增字段不与已添加的时间分区字段冗余。
   *
   * <p>逻辑：若新字段是时间类 transform（year/month/day/hour），则同一 sourceId 下不允许已有 另一个时间分区字段，避免冗余分区。
   *
   * @param field 待校验的新字段
   */
  private void checkForRedundantAddedPartitions(PartitionField field) {
    if (isTimeTransform(field)) {
      PartitionField timeField = addedTimeFields.get(field.sourceId());
      Preconditions.checkArgument(
          timeField == null,
          "Cannot add redundant partition field: %s conflicts with %s",
          timeField,
          field);
      addedTimeFields.put(field.sourceId(), field);
    }
  }

  /** 按字段名建立索引。 */
  private static Map<String, PartitionField> indexSpecByName(PartitionSpec spec) {
    ImmutableMap.Builder<String, PartitionField> builder = ImmutableMap.builder();
    List<PartitionField> fields = spec.fields();
    for (PartitionField field : fields) {
      builder.put(field.name(), field);
    }

    return builder.build();
  }

  /** 按 (sourceId, transform 字符串) 建立索引。 */
  private static Map<Pair<Integer, String>, PartitionField> indexSpecByTransform(
      PartitionSpec spec) {
    Map<Pair<Integer, String>, PartitionField> indexSpecs = Maps.newHashMap();
    List<PartitionField> fields = spec.fields();
    for (PartitionField field : fields) {
      indexSpecs.put(Pair.of(field.sourceId(), field.transform().toString()), field);
    }

    return indexSpecs;
  }

  /** 判断字段是否为时间类 transform（year/month/day/hour）。 */
  private boolean isTimeTransform(PartitionField field) {
    return PartitionSpecVisitor.visit(schema, field, IsTimeTransform.INSTANCE);
  }

  /** 访问器：判断分区字段是否为时间类 transform。 */
  private static class IsTimeTransform implements PartitionSpecVisitor<Boolean> {
    private static final IsTimeTransform INSTANCE = new IsTimeTransform();

    private IsTimeTransform() {}

    @Override
    public Boolean identity(int fieldId, String sourceName, int sourceId) {
      return false;
    }

    @Override
    public Boolean bucket(int fieldId, String sourceName, int sourceId, int numBuckets) {
      return false;
    }

    @Override
    public Boolean truncate(int fieldId, String sourceName, int sourceId, int width) {
      return false;
    }

    @Override
    public Boolean year(int fieldId, String sourceName, int sourceId) {
      return true;
    }

    @Override
    public Boolean month(int fieldId, String sourceName, int sourceId) {
      return true;
    }

    @Override
    public Boolean day(int fieldId, String sourceName, int sourceId) {
      return true;
    }

    @Override
    public Boolean hour(int fieldId, String sourceName, int sourceId) {
      return true;
    }

    @Override
    public Boolean alwaysNull(int fieldId, String sourceName, int sourceId) {
      return false;
    }

    @Override
    public Boolean unknown(int fieldId, String sourceName, int sourceId, String transform) {
      return false;
    }
  }

  /** 判断字段是否为 alwaysNull（void）transform。 */
  private boolean isVoidTransform(PartitionField field) {
    return PartitionSpecVisitor.visit(schema, field, IsVoidTransform.INSTANCE);
  }

  /** 访问器：判断分区字段是否为 alwaysNull（void）transform。 */
  private static class IsVoidTransform implements PartitionSpecVisitor<Boolean> {
    private static final IsVoidTransform INSTANCE = new IsVoidTransform();

    private IsVoidTransform() {}

    @Override
    public Boolean identity(int fieldId, String sourceName, int sourceId) {
      return false;
    }

    @Override
    public Boolean bucket(int fieldId, String sourceName, int sourceId, int numBuckets) {
      return false;
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
      return true;
    }

    @Override
    public Boolean unknown(int fieldId, String sourceName, int sourceId, String transform) {
      return false;
    }
  }

  private static class PartitionNameGenerator implements PartitionSpecVisitor<String> {
    private static final PartitionNameGenerator INSTANCE = new PartitionNameGenerator();

    private PartitionNameGenerator() {}

    @Override
    public String identity(int fieldId, String sourceName, int sourceId) {
      return sourceName;
    }

    @Override
    public String bucket(int fieldId, String sourceName, int sourceId, int numBuckets) {
      return sourceName + "_bucket_" + numBuckets;
    }

    @Override
    public String truncate(int fieldId, String sourceName, int sourceId, int width) {
      return sourceName + "_trunc_" + width;
    }

    @Override
    public String year(int fieldId, String sourceName, int sourceId) {
      return sourceName + "_year";
    }

    @Override
    public String month(int fieldId, String sourceName, int sourceId) {
      return sourceName + "_month";
    }

    @Override
    public String day(int fieldId, String sourceName, int sourceId) {
      return sourceName + "_day";
    }

    @Override
    public String hour(int fieldId, String sourceName, int sourceId) {
      return sourceName + "_hour";
    }

    @Override
    public String alwaysNull(int fieldId, String sourceName, int sourceId) {
      return sourceName + "_null";
    }
  }
}
