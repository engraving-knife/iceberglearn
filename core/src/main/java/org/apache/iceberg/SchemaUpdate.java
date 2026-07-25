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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Multimap;
import org.apache.iceberg.relocated.com.google.common.collect.Multimaps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.schema.UnionByNameVisitor;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema 演化 API 的实现类（iceberg-core 表演化层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link UpdateSchema} 接口，提供新增、删除、重命名、移动、类型变更、 可空性变更、文档更新等模式演化能力；
 *   <li>累积所有变更，在 {@link #apply()} 时产出新 {@link Schema}，在 {@link #commit()} 时 通过 {@link
 *       TableOperations} 提交新 {@link TableMetadata}；
 *   <li>同步维护表属性中的列级配置（如列指标模式、Bloom Filter 配置）和默认 NameMapping。
 * </ul>
 *
 * <p>设计意图：
 *
 * <p>采用"累积变更 + 延迟应用"模式，所有变更先记录到 deletes/updates/adds/moves 集合， 统一在 {@link #apply()} 时由 {@link
 * ApplyChanges} 访问者遍历并产出新 Schema。 通过 {@link #allowIncompatibleChanges}
 * 开关控制不兼容变更（如新增必填列、optional→required）。
 *
 * <p>上下游关系：由 {@link Table#updateSchema()} 创建，依赖 {@link TableOperations} 与 {@link
 * TableMetadata}；变更应用依赖 {@link TypeUtil}、{@link UnionByNameVisitor}、 {@link MappingUtil} 等。被 {@link
 * Transaction} 通过 {@link SchemaUpdate} 复用。
 */
class SchemaUpdate implements UpdateSchema {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaUpdate.class);
  private static final int TABLE_ROOT_ID = -1;

  private final TableOperations ops;
  private final TableMetadata base;
  private final Schema schema;
  private final Map<Integer, Integer> idToParent;
  private final List<Integer> deletes = Lists.newArrayList();
  private final Map<Integer, Types.NestedField> updates = Maps.newHashMap();
  private final Multimap<Integer, Types.NestedField> adds =
      Multimaps.newListMultimap(Maps.newHashMap(), Lists::newArrayList);
  private final Map<String, Integer> addedNameToId = Maps.newHashMap();
  private final Multimap<Integer, Move> moves =
      Multimaps.newListMultimap(Maps.newHashMap(), Lists::newArrayList);
  private int lastColumnId;
  private boolean allowIncompatibleChanges = false;
  private Set<String> identifierFieldNames;
  private boolean caseSensitive = true;

  /**
   * 基于 {@link TableOperations} 构造 SchemaUpdate，使用当前表元数据与 schema 作为基准。
   *
   * @param ops 表操作句柄，用于最终提交
   */
  SchemaUpdate(TableOperations ops) {
    this(ops, ops.current());
  }

  /** 仅用于测试的构造方法。 */
  SchemaUpdate(Schema schema, int lastColumnId) {
    this(null, null, schema, lastColumnId);
  }

  /** 从表操作句柄与基准元数据构造，委托给全参数构造方法。 */
  private SchemaUpdate(TableOperations ops, TableMetadata base) {
    this(ops, base, base.schema(), base.lastColumnId());
  }

  /**
   * 全参数构造方法，初始化基准 schema、ID 父映射、标识字段集合等内部状态。
   *
   * @param ops 表操作句柄
   * @param base 基准表元数据
   * @param schema 基准 schema
   * @param lastColumnId 当前已分配的最大列 ID
   */
  private SchemaUpdate(TableOperations ops, TableMetadata base, Schema schema, int lastColumnId) {
    this.ops = ops;
    this.base = base;
    this.schema = schema;
    this.lastColumnId = lastColumnId;
    this.idToParent = Maps.newHashMap(TypeUtil.indexParents(schema.asStruct()));
    this.identifierFieldNames = schema.identifierFieldNames();
  }

  /**
   * 允许不兼容变更（如新增必填列、optional→required）。
   *
   * @return 当前 SchemaUpdate 实例，用于链式调用
   */
  @Override
  public SchemaUpdate allowIncompatibleChanges() {
    this.allowIncompatibleChanges = true;
    return this;
  }

  /**
   * 在表根级别新增可选列。
   *
   * @param name 列名（不能含 "."，否则需使用 {@link #addColumn(String, String, Type, String)}）
   * @param type 列类型
   * @param doc 列文档，可为 null
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema addColumn(String name, Type type, String doc) {
    Preconditions.checkArgument(
        !name.contains("."),
        "Cannot add column with ambiguous name: %s, use addColumn(parent, name, type)",
        name);
    return addColumn(null, name, type, doc);
  }

  /**
   * 在指定父列下新增可选列。
   *
   * @param parent 父列名，为 null 时新增到表根
   * @param name 新列名
   * @param type 列类型
   * @param doc 列文档，可为 null
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema addColumn(String parent, String name, Type type, String doc) {
    internalAddColumn(parent, name, true, type, doc);
    return this;
  }

  /**
   * 在表根级别新增必填列（需先调用 {@link #allowIncompatibleChanges}）。
   *
   * @param name 列名（不能含 "."）
   * @param type 列类型
   * @param doc 列文档，可为 null
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema addRequiredColumn(String name, Type type, String doc) {
    Preconditions.checkArgument(
        !name.contains("."),
        "Cannot add column with ambiguous name: %s, use addColumn(parent, name, type)",
        name);
    addRequiredColumn(null, name, type, doc);
    return this;
  }

  /**
   * 在指定父列下新增必填列（需先调用 {@link #allowIncompatibleChanges}）。
   *
   * @param parent 父列名，为 null 时新增到表根
   * @param name 新列名
   * @param type 列类型
   * @param doc 列文档，可为 null
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema addRequiredColumn(String parent, String name, Type type, String doc) {
    Preconditions.checkArgument(
        allowIncompatibleChanges, "Incompatible change: cannot add required column: %s", name);
    internalAddColumn(parent, name, false, type, doc);
    return this;
  }

  /**
   * 新增列的内部实现，处理父列解析、ID 分配、嵌套类型 ID 刷新等逻辑。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若指定 parent，定位父字段；若父为 map/list，则将新增落到 value/element 上；
   *   <li>校验父字段为 struct 类型，且父列未被删除、列名不冲突；
   *   <li>分配新列 ID，并通过 {@link TypeUtil#assignFreshIds} 为嵌套类型分配连续 ID；
   *   <li>登记父子关系与 addedNameToId 以支持后续 move 操作。
   * </ol>
   *
   * @param parent 父列名，为 null 时新增到表根
   * @param name 新列名
   * @param isOptional 是否为可选列
   * @param type 列类型
   * @param doc 列文档
   */
  private void internalAddColumn(
      String parent, String name, boolean isOptional, Type type, String doc) {
    int parentId = TABLE_ROOT_ID;
    String fullName;
    if (parent != null) {
      Types.NestedField parentField = findField(parent);
      Preconditions.checkArgument(parentField != null, "Cannot find parent struct: %s", parent);
      Type parentType = parentField.type();
      if (parentType.isNestedType()) {
        Type.NestedType nested = parentType.asNestedType();
        if (nested.isMapType()) {
          // fields are added to the map value type
          parentField = nested.asMapType().fields().get(1);
        } else if (nested.isListType()) {
          // fields are added to the element type
          parentField = nested.asListType().fields().get(0);
        }
      }
      Preconditions.checkArgument(
          parentField.type().isNestedType() && parentField.type().asNestedType().isStructType(),
          "Cannot add to non-struct column: %s: %s",
          parent,
          parentField.type());
      parentId = parentField.fieldId();
      Types.NestedField currentField = findField(parent + "." + name);
      Preconditions.checkArgument(
          !deletes.contains(parentId), "Cannot add to a column that will be deleted: %s", parent);
      Preconditions.checkArgument(
          currentField == null || deletes.contains(currentField.fieldId()),
          "Cannot add column, name already exists: %s.%s",
          parent,
          name);
      fullName = schema.findColumnName(parentId) + "." + name;
    } else {
      Types.NestedField currentField = findField(name);
      Preconditions.checkArgument(
          currentField == null || deletes.contains(currentField.fieldId()),
          "Cannot add column, name already exists: %s",
          name);
      fullName = name;
    }

    // assign new IDs in order
    int newId = assignNewColumnId();

    // update tracking for moves
    addedNameToId.put(fullName, newId);
    if (parentId != TABLE_ROOT_ID) {
      idToParent.put(newId, parentId);
    }

    adds.put(
        parentId,
        Types.NestedField.of(
            newId, isOptional, name, TypeUtil.assignFreshIds(type, this::assignNewColumnId), doc));
  }

  /**
   * 删除指定列。
   *
   * @param name 待删除列的全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema deleteColumn(String name) {
    Types.NestedField field = findField(name);
    Preconditions.checkArgument(field != null, "Cannot delete missing column: %s", name);
    Preconditions.checkArgument(
        !adds.containsKey(field.fieldId()), "Cannot delete a column that has additions: %s", name);
    Preconditions.checkArgument(
        !updates.containsKey(field.fieldId()), "Cannot delete a column that has updates: %s", name);
    deletes.add(field.fieldId());

    return this;
  }

  /**
   * 重命名指定列，并同步更新标识字段集合中的引用。
   *
   * <p>逻辑：若该列已有 update 记录则合并到 update；否则新建 update。 若该列是标识字段，则更新 identifierFieldNames 中的名称。
   *
   * @param name 待重命名列的全名
   * @param newName 新列名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema renameColumn(String name, String newName) {
    Types.NestedField field = findField(name);
    Preconditions.checkArgument(field != null, "Cannot rename missing column: %s", name);
    Preconditions.checkArgument(newName != null, "Cannot rename a column to null");
    Preconditions.checkArgument(
        !deletes.contains(field.fieldId()),
        "Cannot rename a column that will be deleted: %s",
        field.name());

    // merge with an update, if present
    int fieldId = field.fieldId();
    Types.NestedField update = updates.get(fieldId);
    if (update != null) {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, update.isOptional(), newName, update.type(), update.doc()));
    } else {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, field.isOptional(), newName, field.type(), field.doc()));
    }

    if (identifierFieldNames.contains(name)) {
      identifierFieldNames.remove(name);
      identifierFieldNames.add(newName);
    }

    return this;
  }

  /**
   * 将指定列设为必填。
   *
   * @param name 列全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema requireColumn(String name) {
    internalUpdateColumnRequirement(name, false);
    return this;
  }

  /**
   * 将指定列设为可选。
   *
   * @param name 列全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema makeColumnOptional(String name) {
    internalUpdateColumnRequirement(name, true);
    return this;
  }

  /**
   * 更新列可空性的内部实现。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若目标可空性与当前一致则视为 no-op 直接返回；
   *   <li>设为必填时要求 {@link #allowIncompatibleChanges} 已开启；
   *   <li>校验该列未被删除后，合并/新建 update 记录。
   * </ul>
   *
   * @param name 列全名
   * @param isOptional 目标可空性
   */
  private void internalUpdateColumnRequirement(String name, boolean isOptional) {
    Types.NestedField field = findField(name);
    Preconditions.checkArgument(field != null, "Cannot update missing column: %s", name);

    if ((!isOptional && field.isRequired()) || (isOptional && field.isOptional())) {
      // if the change is a noop, allow it even if allowIncompatibleChanges is false
      return;
    }

    Preconditions.checkArgument(
        isOptional || allowIncompatibleChanges,
        "Cannot change column nullability: %s: optional -> required",
        name);
    Preconditions.checkArgument(
        !deletes.contains(field.fieldId()),
        "Cannot update a column that will be deleted: %s",
        field.name());

    int fieldId = field.fieldId();
    Types.NestedField update = updates.get(fieldId);

    if (update != null) {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, isOptional, update.name(), update.type(), update.doc()));
    } else {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, isOptional, field.name(), field.type(), field.doc()));
    }
  }

  /**
   * 更新列的基本类型，需满足 {@link TypeUtil#isPromotionAllowed} 允许的类型提升规则。
   *
   * <p>逻辑：若新旧类型一致直接返回；校验类型提升允许后，合并/新建 update 记录。
   *
   * @param name 列全名
   * @param newType 新的基本类型
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema updateColumn(String name, Type.PrimitiveType newType) {
    Types.NestedField field = findField(name);
    Preconditions.checkArgument(field != null, "Cannot update missing column: %s", name);
    Preconditions.checkArgument(
        !deletes.contains(field.fieldId()),
        "Cannot update a column that will be deleted: %s",
        field.name());

    if (field.type().equals(newType)) {
      return this;
    }

    Preconditions.checkArgument(
        TypeUtil.isPromotionAllowed(field.type(), newType),
        "Cannot change column type: %s: %s -> %s",
        name,
        field.type(),
        newType);

    // merge with a rename, if present
    int fieldId = field.fieldId();
    Types.NestedField update = updates.get(fieldId);
    if (update != null) {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, update.isOptional(), update.name(), newType, update.doc()));
    } else {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, field.isOptional(), field.name(), newType, field.doc()));
    }

    return this;
  }

  /**
   * 更新列的文档说明，若文档未变则直接返回。
   *
   * @param name 列全名
   * @param doc 新文档，可为 null
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema updateColumnDoc(String name, String doc) {
    Types.NestedField field = findField(name);
    Preconditions.checkArgument(field != null, "Cannot update missing column: %s", name);
    Preconditions.checkArgument(
        !deletes.contains(field.fieldId()),
        "Cannot update a column that will be deleted: %s",
        field.name());

    if (Objects.equals(field.doc(), doc)) {
      return this;
    }

    // merge with a rename or update, if present
    int fieldId = field.fieldId();
    Types.NestedField update = updates.get(fieldId);
    if (update != null) {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, update.isOptional(), update.name(), update.type(), doc));
    } else {
      updates.put(
          fieldId,
          Types.NestedField.of(fieldId, field.isOptional(), field.name(), field.type(), doc));
    }

    return this;
  }

  /**
   * 将指定列移动到其所在 struct 的最前位置。
   *
   * @param name 列全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema moveFirst(String name) {
    Integer fieldId = findForMove(name);
    Preconditions.checkArgument(fieldId != null, "Cannot move missing column: %s", name);
    internalMove(name, Move.first(fieldId));
    return this;
  }

  /**
   * 将指定列移动到另一列之前。
   *
   * @param name 待移动列全名
   * @param beforeName 参考列全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema moveBefore(String name, String beforeName) {
    Integer fieldId = findForMove(name);
    Preconditions.checkArgument(fieldId != null, "Cannot move missing column: %s", name);
    Integer beforeId = findForMove(beforeName);
    Preconditions.checkArgument(
        beforeId != null, "Cannot move %s before missing column: %s", name, beforeName);
    Preconditions.checkArgument(!fieldId.equals(beforeId), "Cannot move %s before itself", name);
    internalMove(name, Move.before(fieldId, beforeId));
    return this;
  }

  /**
   * 将指定列移动到另一列之后。
   *
   * @param name 待移动列全名
   * @param afterName 参考列全名
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema moveAfter(String name, String afterName) {
    Integer fieldId = findForMove(name);
    Preconditions.checkArgument(fieldId != null, "Cannot move missing column: %s", name);
    Integer afterId = findForMove(afterName);
    Preconditions.checkArgument(
        afterId != null, "Cannot move %s after missing column: %s", name, afterName);
    Preconditions.checkArgument(!fieldId.equals(afterId), "Cannot move %s after itself", name);
    internalMove(name, Move.after(fieldId, afterId));
    return this;
  }

  /**
   * 按字段名联合当前 schema 与给定 schema，将差异变更累积到本操作。
   *
   * @param newSchema 用于联合的新 schema
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema unionByNameWith(Schema newSchema) {
    UnionByNameVisitor.visit(this, schema, newSchema, caseSensitive);
    return this;
  }

  /**
   * 设置表的标识字段集合。
   *
   * @param names 标识字段名集合
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema setIdentifierFields(Collection<String> names) {
    this.identifierFieldNames = Sets.newHashSet(names);
    return this;
  }

  /**
   * 设置字段名匹配的大小写敏感性。
   *
   * @param caseSensitivity true 大小写敏感，false 不敏感
   * @return 当前 SchemaUpdate 实例
   */
  @Override
  public UpdateSchema caseSensitive(boolean caseSensitivity) {
    this.caseSensitive = caseSensitivity;
    return this;
  }

  /**
   * 查找用于 move 操作的字段 ID，优先返回新增字段的 ID。
   *
   * @param name 字段全名
   * @return 字段 ID，未找到返回 null
   */
  private Integer findForMove(String name) {
    Integer addedId = addedNameToId.get(name);
    if (addedId != null) {
      return addedId;
    }

    Types.NestedField field = findField(name);
    if (field != null) {
      return field.fieldId();
    }

    return null;
  }

  /**
   * move 操作的内部实现，按字段所在 struct 分组登记 move 记录。
   *
   * <p>逻辑：根据 fieldId 查找父 ID；对 BEFORE/AFTER 类型校验参考字段与待移动字段同属一个 struct； 父 ID 为 null 时归属到表根。
   *
   * @param name 字段全名（用于错误信息）
   * @param move 描述移动操作的 {@link Move}
   */
  private void internalMove(String name, Move move) {
    Integer parentId = idToParent.get(move.fieldId());
    if (parentId != null) {
      Types.NestedField parent = schema.findField(parentId);
      Preconditions.checkArgument(
          parent.type().isStructType(), "Cannot move fields in non-struct type: %s", parent.type());

      if (move.type() == Move.MoveType.AFTER || move.type() == Move.MoveType.BEFORE) {
        Preconditions.checkArgument(
            parentId.equals(idToParent.get(move.referenceFieldId())),
            "Cannot move field %s to a different struct",
            name);
      }

      moves.put(parentId, move);
    } else {
      if (move.type() == Move.MoveType.AFTER || move.type() == Move.MoveType.BEFORE) {
        Preconditions.checkArgument(
            idToParent.get(move.referenceFieldId()) == null,
            "Cannot move field %s to a different struct",
            name);
      }

      moves.put(TABLE_ROOT_ID, move);
    }
  }

  /**
   * 将待处理变更应用到原始 schema 并返回结果，不产生持久化更新。
   *
   * @return 应用所有待处理更新后得到的新 {@link Schema}
   */
  @Override
  public Schema apply() {
    Schema newSchema =
        applyChanges(schema, deletes, updates, adds, moves, identifierFieldNames, caseSensitive);

    return newSchema;
  }

  /**
   * 提交 schema 变更到表元数据。
   *
   * <p>逻辑：调用 {@link #apply()} 产出新 schema，通过 {@link #applyChangesToMetadata} 同步更新 NameMapping
   * 与列级属性，最终通过 {@link TableOperations#commit} 持久化。
   */
  @Override
  public void commit() {
    TableMetadata update = applyChangesToMetadata(base.updateSchema(apply(), lastColumnId));
    ops.commit(base, update);
  }

  /**
   * 分配一个新的列 ID 并推进 lastColumnId。
   *
   * @return 新分配的列 ID
   */
  private int assignNewColumnId() {
    int next = lastColumnId + 1;
    this.lastColumnId = next;
    return next;
  }

  /**
   * 将 schema 变更同步应用到表元数据，包括 NameMapping 更新与列级属性维护。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若元数据含默认 NameMapping，解析并应用 adds/updates 后重新序列化；
   *   <li>对于删除/重命名的列，更新列指标模式与 Bloom Filter 列级配置属性；
   *   <li>NameMapping 更新失败时仅告警不中断提交。
   * </ol>
   *
   * @param metadata 待更新的元数据
   * @return 应用变更后的新 {@link TableMetadata}
   */
  private TableMetadata applyChangesToMetadata(TableMetadata metadata) {
    String mappingJson = metadata.property(TableProperties.DEFAULT_NAME_MAPPING, null);
    TableMetadata newMetadata = metadata;
    if (mappingJson != null) {
      try {
        // parse and update the mapping
        NameMapping mapping = NameMappingParser.fromJson(mappingJson);
        NameMapping updated = MappingUtil.update(mapping, updates, adds);

        // replace the table property
        Map<String, String> updatedProperties = Maps.newHashMap();
        updatedProperties.putAll(metadata.properties());
        updatedProperties.put(
            TableProperties.DEFAULT_NAME_MAPPING, NameMappingParser.toJson(updated));

        newMetadata = metadata.replaceProperties(updatedProperties);

      } catch (RuntimeException e) {
        // log the error, but do not fail the update
        LOG.warn("Failed to update external schema mapping: {}", mappingJson, e);
      }
    }

    // Transform the metrics if they exist
    if (base != null && base.properties() != null) {
      Schema newSchema = newMetadata.schema();
      List<String> deletedColumns =
          deletes.stream().map(schema::findColumnName).collect(Collectors.toList());
      Map<String, String> renamedColumns =
          updates.keySet().stream()
              .filter(id -> !schema.findColumnName(id).equals(newSchema.findColumnName(id)))
              .collect(Collectors.toMap(schema::findColumnName, newSchema::findColumnName));
      if (!deletedColumns.isEmpty() || !renamedColumns.isEmpty()) {
        Set<String> columnProperties =
            ImmutableSet.of(
                TableProperties.METRICS_MODE_COLUMN_CONF_PREFIX,
                TableProperties.PARQUET_BLOOM_FILTER_COLUMN_ENABLED_PREFIX);
        Map<String, String> updatedProperties =
            PropertyUtil.applySchemaChanges(
                newMetadata.properties(), deletedColumns, renamedColumns, columnProperties);
        newMetadata = newMetadata.replaceProperties(updatedProperties);
      }
    }

    return newMetadata;
  }

  /**
   * 静态应用方法：将 deletes/updates/adds/moves 应用到给定 schema 并校验标识字段约束。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验现有标识字段及其祖先未被删除；
   *   <li>通过 {@link ApplyChanges} 访问者遍历 schema 产出新 struct；
   *   <li>校验所有标识字段名存在于新 schema，并执行标识字段合法性校验。
   * </ol>
   *
   * @param schema 基准 schema
   * @param deletes 待删除字段 ID 列表
   * @param updates 字段 ID 到更新后字段的映射
   * @param adds 父 ID 到新增字段的多值映射
   * @param moves 父 ID 到移动操作的多值映射
   * @param identifierFieldNames 标识字段名集合
   * @param caseSensitive 字段名匹配是否大小写敏感
   * @return 应用变更后的新 {@link Schema}
   */
  private static Schema applyChanges(
      Schema schema,
      List<Integer> deletes,
      Map<Integer, Types.NestedField> updates,
      Multimap<Integer, Types.NestedField> adds,
      Multimap<Integer, Move> moves,
      Set<String> identifierFieldNames,
      boolean caseSensitive) {
    // validate existing identifier fields are not deleted
    Map<Integer, Integer> idToParent = TypeUtil.indexParents(schema.asStruct());

    for (String name : identifierFieldNames) {
      Types.NestedField field =
          caseSensitive ? schema.findField(name) : schema.caseInsensitiveFindField(name);
      if (field != null) {
        Preconditions.checkArgument(
            !deletes.contains(field.fieldId()),
            "Cannot delete identifier field %s. To force deletion, "
                + "also call setIdentifierFields to update identifier fields.",
            field);
        Integer parentId = idToParent.get(field.fieldId());
        while (parentId != null) {
          Preconditions.checkArgument(
              !deletes.contains(parentId),
              "Cannot delete field %s as it will delete nested identifier field %s",
              schema.findField(parentId),
              field);
          parentId = idToParent.get(parentId);
        }
      }
    }

    // apply schema changes
    Types.StructType struct =
        TypeUtil.visit(schema, new ApplyChanges(deletes, updates, adds, moves))
            .asNestedType()
            .asStructType();

    // validate identifier requirements based on the latest schema
    Map<String, Integer> nameToId = TypeUtil.indexByName(struct);
    Set<Integer> freshIdentifierFieldIds = Sets.newHashSet();
    for (String name : identifierFieldNames) {
      Preconditions.checkArgument(
          nameToId.containsKey(name),
          "Cannot add field %s as an identifier field: not found in current schema or added columns",
          name);
      freshIdentifierFieldIds.add(nameToId.get(name));
    }

    Map<Integer, Types.NestedField> idToField = TypeUtil.indexById(struct);
    freshIdentifierFieldIds.forEach(
        id -> Schema.validateIdentifierField(id, idToField, idToParent));

    return new Schema(struct.fields(), freshIdentifierFieldIds);
  }

  /**
   * 应用 schema 变更的访问者（iceberg-core 表演化层内部类）。
   *
   * <p>职责：继承 {@link TypeUtil.SchemaVisitor}，在遍历 schema 各节点时应用 deletes/updates/adds/moves，
   * 产出表示变更后类型树的 {@link Type} 节点；field 节点返回 null 表示该字段被删除。
   */
  private static class ApplyChanges extends TypeUtil.SchemaVisitor<Type> {
    private final List<Integer> deletes;
    private final Map<Integer, Types.NestedField> updates;
    private final Multimap<Integer, Types.NestedField> adds;
    private final Multimap<Integer, Move> moves;

    /**
     * 构造访问者，传入各类变更集合。
     *
     * @param deletes 待删除字段 ID 列表
     * @param updates 字段 ID 到更新后字段的映射
     * @param adds 父 ID 到新增字段的多值映射
     * @param moves 父 ID 到移动操作的多值映射
     */
    private ApplyChanges(
        List<Integer> deletes,
        Map<Integer, Types.NestedField> updates,
        Multimap<Integer, Types.NestedField> adds,
        Multimap<Integer, Move> moves) {
      this.deletes = deletes;
      this.updates = updates;
      this.adds = adds;
      this.moves = moves;
    }

    /**
     * 访问 schema 根节点，对表根级别应用新增与移动后产出 struct 类型。
     *
     * @param schema 原 schema
     * @param structResult 根 struct 遍历后的类型结果
     * @return 应用表根级别新增/移动后的 struct 类型
     */
    @Override
    public Type schema(Schema schema, Type structResult) {
      List<Types.NestedField> fields =
          addAndMoveFields(
              structResult.asStructType().fields(),
              adds.get(TABLE_ROOT_ID),
              moves.get(TABLE_ROOT_ID));

      if (fields != null) {
        return Types.StructType.of(fields);
      }

      return structResult;
    }

    /**
     * 访问 struct 节点，按字段 ID 合并 update（重命名/可空性/文档/类型）产出新 struct。
     *
     * <p>逻辑：遍历各字段结果，null 表示被删除；对存活字段按 update 记录覆盖名称、文档、可空性； 任一字段发生变更即构建新的 {@link
     * Types.StructType}，否则返回原 struct。
     *
     * @param struct 原 struct 类型
     * @param fieldResults 各字段遍历后的类型结果列表
     * @return 应用变更后的 struct 类型
     */
    @Override
    public Type struct(Types.StructType struct, List<Type> fieldResults) {
      boolean hasChange = false;
      List<Types.NestedField> newFields = Lists.newArrayListWithExpectedSize(fieldResults.size());
      for (int i = 0; i < fieldResults.size(); i += 1) {
        Type resultType = fieldResults.get(i);
        if (resultType == null) {
          hasChange = true;
          continue;
        }

        Types.NestedField field = struct.fields().get(i);
        String name = field.name();
        String doc = field.doc();
        boolean isOptional = field.isOptional();
        Types.NestedField update = updates.get(field.fieldId());
        if (update != null) {
          name = update.name();
          doc = update.doc();
          isOptional = update.isOptional();
        }

        if (name.equals(field.name())
            && isOptional == field.isOptional()
            && field.type() == resultType
            && Objects.equals(doc, field.doc())) {
          newFields.add(field);
        } else {
          hasChange = true;
          newFields.add(Types.NestedField.of(field.fieldId(), isOptional, name, resultType, doc));
        }
      }

      if (hasChange) {
        // TODO: What happens if there are no fields left?
        return Types.StructType.of(newFields);
      }

      return struct;
    }

    /**
     * 处理字段：处理删除、类型更新，以及在 struct 字段上应用新增与移动。
     *
     * @param field 原字段定义
     * @param fieldResult 字段遍历后的类型结果
     * @return 应用变更后的字段类型，null 表示该字段被删除
     */
    @Override
    public Type field(Types.NestedField field, Type fieldResult) {
      // the API validates deletes, updates, and additions don't conflict
      // handle deletes
      int fieldId = field.fieldId();
      if (deletes.contains(fieldId)) {
        return null;
      }

      // handle updates
      Types.NestedField update = updates.get(field.fieldId());
      if (update != null && update.type() != field.type()) {
        // rename is handled in struct, but struct needs the correct type from the field result
        return update.type();
      }

      // handle adds
      Collection<Types.NestedField> newFields = adds.get(fieldId);
      Collection<Move> columnsToMove = moves.get(fieldId);
      if (!newFields.isEmpty() || !columnsToMove.isEmpty()) {
        // if either collection is non-null, then this must be a struct type. try to apply the
        // changes
        List<Types.NestedField> fields =
            addAndMoveFields(fieldResult.asStructType().fields(), newFields, columnsToMove);
        if (fields != null) {
          return Types.StructType.of(fields);
        }
      }

      return fieldResult;
    }

    /**
     * 处理 list：复用 {@link #field} 应用元素更新，并按可空性重建 list 类型。
     *
     * @param list 原 list 类型
     * @param elementResult 元素遍历后的类型结果
     * @return 应用变更后的 list 类型
     */
    @Override
    public Type list(Types.ListType list, Type elementResult) {
      // use field to apply updates
      Types.NestedField elementField = list.fields().get(0);
      Type elementType = field(elementField, elementResult);
      if (elementType == null) {
        throw new IllegalArgumentException("Cannot delete element type from list: " + list);
      }

      Types.NestedField elementUpdate = updates.get(elementField.fieldId());
      boolean isElementOptional =
          elementUpdate != null ? elementUpdate.isOptional() : list.isElementOptional();

      if (isElementOptional == elementField.isOptional() && list.elementType() == elementType) {
        return list;
      }

      if (isElementOptional) {
        return Types.ListType.ofOptional(list.elementId(), elementType);
      } else {
        return Types.ListType.ofRequired(list.elementId(), elementType);
      }
    }

    /**
     * 处理 map：禁止对 key 做删除/更新/新增，复用 {@link #field} 应用 value 更新， 按可空性重建 map 类型。
     *
     * @param map 原 map 类型
     * @param kResult key 遍历后的类型结果
     * @param valueResult value 遍历后的类型结果
     * @return 应用变更后的 map 类型
     */
    @Override
    public Type map(Types.MapType map, Type kResult, Type valueResult) {
      // if any updates are intended for the key, throw an exception
      int keyId = map.fields().get(0).fieldId();
      if (deletes.contains(keyId)) {
        throw new IllegalArgumentException("Cannot delete map keys: " + map);
      } else if (updates.containsKey(keyId)) {
        throw new IllegalArgumentException("Cannot update map keys: " + map);
      } else if (adds.containsKey(keyId)) {
        throw new IllegalArgumentException("Cannot add fields to map keys: " + map);
      } else if (!map.keyType().equals(kResult)) {
        throw new IllegalArgumentException("Cannot alter map keys: " + map);
      }

      // use field to apply updates to the value
      Types.NestedField valueField = map.fields().get(1);
      Type valueType = field(valueField, valueResult);
      if (valueType == null) {
        throw new IllegalArgumentException("Cannot delete value type from map: " + map);
      }

      Types.NestedField valueUpdate = updates.get(valueField.fieldId());
      boolean isValueOptional =
          valueUpdate != null ? valueUpdate.isOptional() : map.isValueOptional();

      if (isValueOptional == map.isValueOptional() && map.valueType() == valueType) {
        return map;
      }

      if (isValueOptional) {
        return Types.MapType.ofOptional(map.keyId(), map.valueId(), map.keyType(), valueType);
      } else {
        return Types.MapType.ofRequired(map.keyId(), map.valueId(), map.keyType(), valueType);
      }
    }

    /** 基本类型节点直接返回原类型。 */
    @Override
    public Type primitive(Type.PrimitiveType primitive) {
      return primitive;
    }
  }

  /**
   * 对字段列表应用新增与移动：先新增后移动，确保新增字段也可被移动。
   *
   * @param fields 原字段列表
   * @param adds 新增字段集合
   * @param moves 移动操作集合
   * @return 应用后的字段列表，无变更时返回 null
   */
  private static List<Types.NestedField> addAndMoveFields(
      List<Types.NestedField> fields, Collection<Types.NestedField> adds, Collection<Move> moves) {
    if (adds != null && !adds.isEmpty()) {
      if (moves != null && !moves.isEmpty()) {
        // always apply adds first so that added fields can be moved
        return moveFields(addFields(fields, adds), moves);
      } else {
        return addFields(fields, adds);
      }
    } else if (moves != null && !moves.isEmpty()) {
      return moveFields(fields, moves);
    }
    return null;
  }

  /**
   * 将新增字段追加到原字段列表末尾。
   *
   * @param fields 原字段列表
   * @param adds 新增字段集合
   * @return 包含新增字段的新列表
   */
  private static List<Types.NestedField> addFields(
      List<Types.NestedField> fields, Collection<Types.NestedField> adds) {
    List<Types.NestedField> newFields = Lists.newArrayList(fields);
    newFields.addAll(adds);
    return newFields;
  }

  /**
   * 按移动操作列表对字段重新排序，依次应用 FIRST/BEFORE/AFTER 三类移动。
   *
   * @param fields 原字段列表
   * @param moves 移动操作集合
   * @return 重排后的字段列表
   */
  @SuppressWarnings({"checkstyle:IllegalType", "JdkObsolete"})
  private static List<Types.NestedField> moveFields(
      List<Types.NestedField> fields, Collection<Move> moves) {
    LinkedList<Types.NestedField> reordered = Lists.newLinkedList(fields);

    for (Move move : moves) {
      Types.NestedField toMove =
          Iterables.find(reordered, field -> field.fieldId() == move.fieldId());
      reordered.remove(toMove);

      switch (move.type()) {
        case FIRST:
          reordered.addFirst(toMove);
          break;

        case BEFORE:
          Types.NestedField before =
              Iterables.find(reordered, field -> field.fieldId() == move.referenceFieldId());
          int beforeIndex = reordered.indexOf(before);
          // insert the new node at the index of the existing node
          reordered.add(beforeIndex, toMove);
          break;

        case AFTER:
          Types.NestedField after =
              Iterables.find(reordered, field -> field.fieldId() == move.referenceFieldId());
          int afterIndex = reordered.indexOf(after);
          reordered.add(afterIndex + 1, toMove);
          break;

        default:
          throw new UnsupportedOperationException("Unknown move type: " + move.type());
      }
    }

    return reordered;
  }

  /** 表示 struct 中一次列移动请求，包含目标字段 ID、参考字段 ID 与移动类型。 */
  private static class Move {
    private enum MoveType {
      FIRST,
      BEFORE,
      AFTER
    }

    /** 返回该移动操作的描述字符串。 */
    @Override
    public String toString() {
      String suffix = "";
      if (type != MoveType.FIRST) {
        suffix = " field " + referenceFieldId;
      }
      return "Move column " + fieldId + " " + type.toString() + suffix;
    }

    /**
     * 构造移动到最前位置的 Move。
     *
     * @param fieldId 待移动字段 ID
     * @return FIRST 类型的 Move
     */
    static Move first(int fieldId) {
      return new Move(fieldId, -1, MoveType.FIRST);
    }

    /**
     * 构造移动到参考字段之前的 Move。
     *
     * @param fieldId 待移动字段 ID
     * @param referenceFieldId 参考字段 ID
     * @return BEFORE 类型的 Move
     */
    static Move before(int fieldId, int referenceFieldId) {
      return new Move(fieldId, referenceFieldId, MoveType.BEFORE);
    }

    /**
     * 构造移动到参考字段之后的 Move。
     *
     * @param fieldId 待移动字段 ID
     * @param referenceFieldId 参考字段 ID
     * @return AFTER 类型的 Move
     */
    static Move after(int fieldId, int referenceFieldId) {
      return new Move(fieldId, referenceFieldId, MoveType.AFTER);
    }

    private final int fieldId;
    private final int referenceFieldId;
    private final MoveType type;

    private Move(int fieldId, int referenceFieldId, MoveType type) {
      this.fieldId = fieldId;
      this.referenceFieldId = referenceFieldId;
      this.type = type;
    }

    /** 返回待移动字段 ID。 */
    public int fieldId() {
      return fieldId;
    }

    /** 返回参考字段 ID。 */
    public int referenceFieldId() {
      return referenceFieldId;
    }

    /** 返回移动类型。 */
    public MoveType type() {
      return type;
    }
  }

  /**
   * 按大小写敏感性配置查找字段。
   *
   * @param fieldName 字段全名
   * @return 匹配到的字段，未找到返回 null
   */
  private Types.NestedField findField(String fieldName) {
    return caseSensitive ? schema.findField(fieldName) : schema.caseInsensitiveFindField(fieldName);
  }
}
