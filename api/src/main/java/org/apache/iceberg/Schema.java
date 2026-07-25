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
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.BiMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableBiMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.relocated.com.google.common.primitives.Ints;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;

/**
 * 表数据 Schema：描述一张表的所有列、字段 ID、别名与标识字段（identifier fields）。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块，是表/元数据/扫描的基础数据结构）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有顶层 {@link StructType}，提供按列名/字段 ID 查找列、类型、访问器等能力。
 *   <li>维护字段 ID 与列名、别名之间的双向映射，支持大小写敏感/不敏感的查找。
 *   <li>定义标识字段集合（类似主键），用于 upsert 等场景。
 *   <li>提供按列名做投影（{@link #select(Collection)}）的能力。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用字段 ID 而非列名作为列的稳定标识，支持列重命名/重排等演进。
 *   <li>各类索引（idToField、nameToId 等）以 {@code transient} + 懒加载方式持有， 既避免序列化体积膨胀，又保证首次访问时才构建，节省冷启动开销。
 *   <li>schemaId 仅在读写表元数据时填充，否则默认为 0；标识字段不强制唯一性， 与关系数据库主键语义有别。
 * </ul>
 *
 * <p>上下游关系：被 {@link Table}、{@link PartitionSpec}、扫描与写入器等几乎所有模块依赖。
 */
public class Schema implements Serializable {
  private static final Joiner NEWLINE = Joiner.on('\n');
  private static final String ALL_COLUMNS = "*";
  private static final int DEFAULT_SCHEMA_ID = 0;

  private final StructType struct;
  private final int schemaId;
  private final int[] identifierFieldIds;
  private final int highestFieldId;

  private transient BiMap<String, Integer> aliasToId = null;
  private transient Map<Integer, NestedField> idToField = null;
  private transient Map<String, Integer> nameToId = null;
  private transient Map<String, Integer> lowerCaseNameToId = null;
  private transient Map<Integer, Accessor<StructLike>> idToAccessor = null;
  private transient Map<Integer, String> idToName = null;
  private transient Set<Integer> identifierFieldIdSet = null;

  /** 构造 Schema：指定列与别名，无标识字段，schemaId 默认为 0。 */
  public Schema(List<NestedField> columns, Map<String, Integer> aliases) {
    this(columns, aliases, ImmutableSet.of());
  }

  /** 构造 Schema：指定列、别名与标识字段，schemaId 默认为 0。 */
  public Schema(
      List<NestedField> columns, Map<String, Integer> aliases, Set<Integer> identifierFieldIds) {
    this(DEFAULT_SCHEMA_ID, columns, aliases, identifierFieldIds);
  }

  /** 构造 Schema：仅指定列，无别名、无标识字段，schemaId 默认为 0。 */
  public Schema(List<NestedField> columns) {
    this(columns, ImmutableSet.of());
  }

  /** 构造 Schema：指定列与标识字段，无别名，schemaId 默认为 0。 */
  public Schema(List<NestedField> columns, Set<Integer> identifierFieldIds) {
    this(DEFAULT_SCHEMA_ID, columns, identifierFieldIds);
  }

  /** 构造 Schema：指定 schemaId 与列，无别名、无标识字段。 */
  public Schema(int schemaId, List<NestedField> columns) {
    this(schemaId, columns, ImmutableSet.of());
  }

  /** 构造 Schema：指定 schemaId、列与标识字段，无别名。 */
  public Schema(int schemaId, List<NestedField> columns, Set<Integer> identifierFieldIds) {
    this(schemaId, columns, null, identifierFieldIds);
  }

  /**
   * 全参数构造 Schema。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>把列包装为 {@link StructType}；
   *   <li>把别名映射拷贝为不可变 {@link BiMap}；
   *   <li>若提供了标识字段 ID，则通过 {@link #validateIdentifierField} 逐一校验合法性， 并构建 idToParent 索引供校验使用；
   *   <li>把标识字段 ID 集合转为 int 数组以节省内存；
   *   <li>计算本 schema 中最高的字段 ID，用于后续分配新字段 ID。
   * </ul>
   *
   * @param schemaId schema ID（仅在读写表元数据时填充）
   * @param columns 顶层列
   * @param aliases 列别名到字段 ID 的映射，可为 null
   * @param identifierFieldIds 标识字段 ID 集合，可为 null
   */
  public Schema(
      int schemaId,
      List<NestedField> columns,
      Map<String, Integer> aliases,
      Set<Integer> identifierFieldIds) {
    this.schemaId = schemaId;
    this.struct = StructType.of(columns);
    this.aliasToId = aliases != null ? ImmutableBiMap.copyOf(aliases) : null;

    // validate IdentifierField
    if (identifierFieldIds != null) {
      Map<Integer, Integer> idToParent = TypeUtil.indexParents(struct);
      identifierFieldIds.forEach(id -> validateIdentifierField(id, lazyIdToField(), idToParent));
    }

    this.identifierFieldIds =
        identifierFieldIds != null ? Ints.toArray(identifierFieldIds) : new int[0];

    this.highestFieldId = lazyIdToName().keySet().stream().mapToInt(i -> i).max().orElse(0);
  }

  /**
   * 校验某个字段 ID 是否可以作为标识字段。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>字段必须存在且为基本类型、必填、非 float/double；
   *   <li>从根到该字段的整条父链必须都是 struct 类型且必填，禁止嵌套在 list/map 或可选字段中。
   * </ul>
   *
   * <p>设计意图：从根开始遍历父链以给出针对 list/map 的更友好的错误信息。
   *
   * @param fieldId 待校验字段 ID
   * @param idToField 字段 ID 到字段的映射
   * @param idToParent 字段 ID 到父字段 ID 的映射
   */
  static void validateIdentifierField(
      int fieldId, Map<Integer, Types.NestedField> idToField, Map<Integer, Integer> idToParent) {
    Types.NestedField field = idToField.get(fieldId);
    Preconditions.checkArgument(
        field != null,
        "Cannot add fieldId %s as an identifier field: field does not exist",
        fieldId);
    Preconditions.checkArgument(
        field.type().isPrimitiveType(),
        "Cannot add field %s as an identifier field: not a primitive type field",
        field.name());
    Preconditions.checkArgument(
        field.isRequired(),
        "Cannot add field %s as an identifier field: not a required field",
        field.name());
    Preconditions.checkArgument(
        !Types.DoubleType.get().equals(field.type()) && !Types.FloatType.get().equals(field.type()),
        "Cannot add field %s as an identifier field: must not be float or double field",
        field.name());

    // check whether the nested field is in a chain of required struct fields
    // exploring from root for better error message for list and map types
    Integer parentId = idToParent.get(field.fieldId());
    Deque<Integer> deque = Lists.newLinkedList();
    while (parentId != null) {
      deque.push(parentId);
      parentId = idToParent.get(parentId);
    }

    while (!deque.isEmpty()) {
      Types.NestedField parent = idToField.get(deque.pop());
      Preconditions.checkArgument(
          parent.type().isStructType(),
          "Cannot add field %s as an identifier field: must not be nested in %s",
          field.name(),
          parent);
      Preconditions.checkArgument(
          parent.isRequired(),
          "Cannot add field %s as an identifier field: must not be nested in an optional field %s",
          field.name(),
          parent);
    }
  }

  /** 变参便捷构造：仅指定列，schemaId 默认为 0。 */
  public Schema(NestedField... columns) {
    this(DEFAULT_SCHEMA_ID, Arrays.asList(columns));
  }

  /** 变参便捷构造：指定 schemaId 与列。 */
  public Schema(int schemaId, NestedField... columns) {
    this(schemaId, Arrays.asList(columns));
  }

  /** 懒构建字段 ID → 字段 的映射。 */
  private Map<Integer, NestedField> lazyIdToField() {
    if (idToField == null) {
      this.idToField = TypeUtil.indexById(struct);
    }
    return idToField;
  }

  /** 懒构建列名 → 字段 ID 的映射。 */
  private Map<String, Integer> lazyNameToId() {
    if (nameToId == null) {
      this.nameToId = ImmutableMap.copyOf(TypeUtil.indexByName(struct));
    }
    return nameToId;
  }

  /** 懒构建字段 ID → 列名 的映射。 */
  private Map<Integer, String> lazyIdToName() {
    if (idToName == null) {
      this.idToName = ImmutableMap.copyOf(TypeUtil.indexNameById(struct));
    }
    return idToName;
  }

  /** 懒构建小写列名 → 字段 ID 的映射，用于大小写不敏感查找。 */
  private Map<String, Integer> lazyLowerCaseNameToId() {
    if (lowerCaseNameToId == null) {
      this.lowerCaseNameToId = ImmutableMap.copyOf(TypeUtil.indexByLowerCaseName(struct));
    }
    return lowerCaseNameToId;
  }

  /** 懒构建字段 ID → {@link Accessor} 的映射，用于从 {@link StructLike} 行中取值。 */
  private Map<Integer, Accessor<StructLike>> lazyIdToAccessor() {
    if (idToAccessor == null) {
      idToAccessor = Accessors.forSchema(this);
    }
    return idToAccessor;
  }

  /** 懒构建标识字段 ID 集合的 {@link Set} 视图。 */
  private Set<Integer> lazyIdentifierFieldIdSet() {
    if (identifierFieldIdSet == null) {
      identifierFieldIdSet = ImmutableSet.copyOf(Ints.asList(identifierFieldIds));
    }
    return identifierFieldIdSet;
  }

  /**
   * 返回本 schema 的 ID。
   *
   * <p>仅在读写表元数据时填充，否则默认为 0。
   *
   * @return schema ID
   */
  public int schemaId() {
    return this.schemaId;
  }

  /** 返回本 schema 中最高的字段 ID（含嵌套字段）。 */
  public int highestFieldId() {
    return highestFieldId;
  }

  /**
   * 返回本 schema 的别名映射（若存在）。
   *
   * <p>别名映射通常在从 Avro/Parrot 等外部 schema 转换为本 Schema 时设置，用于保留原始列名。
   *
   * @return 列别名到字段 ID 的映射，未设置时返回 null
   */
  public Map<String, Integer> getAliases() {
    return aliasToId;
  }

  /**
   * 返回字段 ID 到全限定列名的映射。
   *
   * @return 字段 ID → 全限定列名
   */
  public Map<Integer, String> idToName() {
    return lazyIdToName();
  }

  /**
   * 返回本 schema 对应的底层 {@link StructType}。
   *
   * @return struct 类型视图
   */
  public StructType asStruct() {
    return struct;
  }

  /** 返回本 schema 的顶层列列表。 */
  public List<NestedField> columns() {
    return struct.fields();
  }

  /**
   * 返回标识字段 ID 集合。
   *
   * <p>标识字段类似关系数据库主键，由 schema 中一组唯一的基本类型字段构成。标识字段可位于根， 也可嵌套在 struct 链中（不允许在 list/map
   * 中）。可选字段、float、double 不能作为标识字段。
   *
   * <p>与主键的区别：Iceberg 不基于标识字段强制行级唯一性，它主要用于 upsert 等操作的默认键； 嵌套 struct 中的字段（如
   * "user.last_name"）也可作为标识字段的一部分。
   *
   * @return 标识字段 ID 集合
   */
  public Set<Integer> identifierFieldIds() {
    return lazyIdentifierFieldIdSet();
  }

  /** 返回标识字段的列名集合。 */
  public Set<String> identifierFieldNames() {
    return identifierFieldIds().stream()
        .map(id -> lazyIdToName().get(id))
        .collect(Collectors.toSet());
  }

  /**
   * 按列名查找子字段的 {@link Type}。
   *
   * @param name 字段名
   * @return 字段类型，未找到返回 null
   */
  public Type findType(String name) {
    Preconditions.checkArgument(!name.isEmpty(), "Invalid column name: (empty)");
    Integer id = lazyNameToId().get(name);
    if (id != null) { // name is found
      return findType(id);
    }

    // name could not be found
    return null;
  }

  /**
   * 按字段 ID 查找子字段的 {@link Type}。
   *
   * @param id 字段 ID
   * @return 字段类型，未找到返回 null
   */
  public Type findType(int id) {
    NestedField field = lazyIdToField().get(id);
    if (field != null) {
      return field.type();
    }
    return null;
  }

  /**
   * 按字段 ID 查找子字段（返回 {@link NestedField}）。
   *
   * @param id 字段 ID
   * @return 子字段，未找到返回 null
   */
  public NestedField findField(int id) {
    return lazyIdToField().get(id);
  }

  /**
   * 按列名查找子字段（返回 {@link NestedField}）。
   *
   * <p>结果可以是顶层或嵌套字段。
   *
   * @param name 字段名
   * @return 子字段，未找到返回 null
   */
  public NestedField findField(String name) {
    Preconditions.checkArgument(!name.isEmpty(), "Invalid column name: (empty)");
    Integer id = lazyNameToId().get(name);
    if (id != null) {
      return lazyIdToField().get(id);
    }
    return null;
  }

  /**
   * 按列名大小写不敏感地查找子字段（返回 {@link NestedField}）。
   *
   * <p>结果可以是顶层或嵌套字段。
   *
   * @param name 字段名
   * @return 子字段，未找到返回 null
   */
  public NestedField caseInsensitiveFindField(String name) {
    Preconditions.checkArgument(!name.isEmpty(), "Invalid column name: (empty)");
    Integer id = lazyLowerCaseNameToId().get(name.toLowerCase(Locale.ROOT));
    if (id != null) {
      return lazyIdToField().get(id);
    }
    return null;
  }

  /**
   * 按字段 ID 查找其全限定列名。
   *
   * @param id 字段 ID
   * @return 全限定列名
   */
  public String findColumnName(int id) {
    return lazyIdToName().get(id);
  }

  /**
   * 按列别名查找字段 ID。
   *
   * <p>别名通常由 Parquet/Avro 转换为本 Schema 时设置。
   *
   * @param alias 未转换数据 schema 中的全限定列名
   * @return 本 schema 中的字段 ID，未找到返回 null
   */
  public Integer aliasToId(String alias) {
    if (aliasToId != null) {
      return aliasToId.get(alias);
    }
    return null;
  }

  /**
   * 按字段 ID 反查列别名。
   *
   * @param fieldId 本 schema 中的字段 ID
   * @return 未转换数据 schema 中的全限定列名，未找到返回 null
   */
  public String idToAlias(Integer fieldId) {
    if (aliasToId != null) {
      return aliasToId.inverse().get(fieldId);
    }
    return null;
  }

  /**
   * 返回从 {@link StructLike} 行中读取指定字段值的 {@link Accessor}。
   *
   * <p>Accessor 不会读取 list/map 内部的数据。
   *
   * @param id 字段 ID
   * @return 用于从行中取值的访问器
   */
  public Accessor<StructLike> accessorForField(int id) {
    return lazyIdToAccessor().get(id);
  }

  /**
   * 按列名做投影，返回新的投影 schema（变参便捷重载）。
   *
   * <p>嵌套字段名会选择其所属顶层列的全部或部分。
   *
   * @param names 选中的列名
   * @return 投影 schema
   */
  public Schema select(String... names) {
    return select(Arrays.asList(names));
  }

  /**
   * 按列名做投影，返回新的投影 schema。
   *
   * <p>嵌套字段名会选择其所属顶层列的全部或部分。
   *
   * @param names 选中的列名集合
   * @return 投影 schema
   */
  public Schema select(Collection<String> names) {
    return internalSelect(names, true);
  }

  /**
   * 按列名大小写不敏感地做投影，返回新的投影 schema（变参便捷重载）。
   *
   * @param names 选中的列名
   * @return 投影 schema
   */
  public Schema caseInsensitiveSelect(String... names) {
    return caseInsensitiveSelect(Arrays.asList(names));
  }

  /**
   * 按列名大小写不敏感地做投影，返回新的投影 schema。
   *
   * @param names 选中的列名集合
   * @return 投影 schema
   */
  public Schema caseInsensitiveSelect(Collection<String> names) {
    return internalSelect(names, false);
  }

  /**
   * 判断本 schema 与另一个 schema 在忽略 schema ID 的前提下是否等价。
   *
   * @param anotherSchema 另一个 schema
   * @return 结构与标识字段一致返回 true
   */
  public boolean sameSchema(Schema anotherSchema) {
    return asStruct().equals(anotherSchema.asStruct())
        && identifierFieldIds().equals(anotherSchema.identifierFieldIds());
  }

  /**
   * 内部投影实现：按列名（可指定大小写敏感性）选出字段 ID 集合，再委托 {@link TypeUtil#select} 构建投影 schema。
   *
   * <p>逻辑：若名称集合中包含 "*" 则直接返回本 schema；否则按大小写敏感性把列名映射为 字段 ID，收集到集合后调用 {@link TypeUtil#select} 生成投影。
   *
   * @param names 列名集合
   * @param caseSensitive 是否大小写敏感
   * @return 投影 schema
   */
  private Schema internalSelect(Collection<String> names, boolean caseSensitive) {
    if (names.contains(ALL_COLUMNS)) {
      return this;
    }

    Set<Integer> selected = Sets.newHashSet();
    for (String name : names) {
      Integer id;
      if (caseSensitive) {
        id = lazyNameToId().get(name);
      } else {
        id = lazyLowerCaseNameToId().get(name.toLowerCase(Locale.ROOT));
      }

      if (id != null) {
        selected.add(id);
      }
    }

    return TypeUtil.select(this, selected);
  }

  /** 把字段格式化为字符串，标识字段后追加 "(id)" 标记。 */
  private String identifierFieldToString(Types.NestedField field) {
    return "  " + field + (identifierFieldIds().contains(field.fieldId()) ? " (id)" : "");
  }

  /** 返回 schema 的可读字符串表示，标识字段以 "(id)" 标注。 */
  @Override
  public String toString() {
    return String.format(
        "table {\n%s\n}",
        NEWLINE.join(
            struct.fields().stream()
                .map(this::identifierFieldToString)
                .collect(Collectors.toList())));
  }
}
