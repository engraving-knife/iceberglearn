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
package org.apache.iceberg.types;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 类型系统工具类：提供 schema 投影、字段 ID 管理、类型遍历与兼容性校验等核心能力。
 *
 * <p>所属模块：iceberg-api（被 core、各引擎集成模块广泛依赖，是类型系统最常用的工具入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>schema 投影/选择/排除：按字段 ID 集合裁剪 schema（project/select/selectNot）。
 *   <li>字段 ID 管理：分配新 ID（assignFreshIds）、按名称重分配 ID（reassignIds）、 刷新标识字段（refreshIdentifierFields）。
 *   <li>类型遍历：提供 {@link SchemaVisitor} 与 {@link CustomOrderSchemaVisitor} 两种访问者模式， 支持后序遍历与自定义顺序遍历。
 *   <li>索引构建：按名/按 ID/按父字段构建索引（indexByName/indexById/indexParents）。
 *   <li>兼容性校验：校验写入 schema 与表 schema 的兼容性（validateWriteSchema/validateSchema）。
 *   <li>类型提升：判断原始类型间是否允许提升（isPromotionAllowed）。
 *   <li>Decimal 精度与存储长度换算（decimalMaxPrecision/decimalRequiredBytes）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式把"类型结构遍历"与"对类型的处理"解耦，{@link SchemaVisitor} 为后序遍历， {@link CustomOrderSchemaVisitor} 通过
 *       {@link Supplier} 懒求值支持前序/自定义顺序遍历。
 *   <li>project 与 select 的区别：project 只保留显式列出的字段（struct 被选中时子字段不自动包含）， select
 *       则保留被选中字段及其所有祖先（子树完整保留）。
 *   <li>Decimal 的 MAX_PRECISION/REQUIRED_LENGTH 在类加载时预计算，避免运行时反复计算。
 * </ul>
 *
 * <p>上下游关系：被 {@link Schema}、core 的 schema 演进、各引擎的 schema 映射与下推使用； 依赖 {@link PruneColumns}、{@link
 * GetProjectedIds}、{@link AssignFreshIds}、{@link ReassignIds}、 {@link ReassignDoc}、{@link
 * IndexByName}、{@link IndexById}、{@link IndexParents}、 {@link FindTypeVisitor}、{@link
 * CheckCompatibility} 等访问者实现。
 */
public class TypeUtil {

  private TypeUtil() {}

  /**
   * 按 ID 投影 schema：只保留显式列出的字段。
   *
   * <p>逻辑：委托 {@link #project(Types.StructType, Set)} 处理 struct；若结果与原 schema 相同则 返回原
   * schema（避免不必要的对象创建）；保留别名信息。
   *
   * <p>设计要点：与 {@link #select(Schema, Set)} 不同，project 只保留显式列出的字段—— 若 struct 被列出但其子字段未列出，则 struct
   * 变为空；list/map 不能被显式选择。
   *
   * @param schema 待投影 schema
   * @param fieldIds 显式要保留的字段 ID 集合
   * @return 投影后的 schema
   */
  public static Schema project(Schema schema, Set<Integer> fieldIds) {
    Preconditions.checkNotNull(schema, "Schema cannot be null");

    Types.StructType result = project(schema.asStruct(), fieldIds);
    if (schema.asStruct().equals(result)) {
      return schema;
    } else if (result != null) {
      if (schema.getAliases() != null) {
        return new Schema(result.fields(), schema.getAliases());
      } else {
        return new Schema(result.fields());
      }
    }
    return new Schema(Collections.emptyList(), schema.getAliases());
  }

  /**
   * 按 ID 投影 struct 类型。
   *
   * <p>逻辑：用 {@link PruneColumns}（select=false）遍历 struct，裁剪不在 fieldIds 中的字段。
   *
   * @param struct 待投影 struct
   * @param fieldIds 显式要保留的字段 ID 集合
   * @return 投影后的 struct；全部裁剪完返回空 struct
   */
  public static Types.StructType project(Types.StructType struct, Set<Integer> fieldIds) {
    Preconditions.checkNotNull(struct, "Struct cannot be null");
    Preconditions.checkNotNull(fieldIds, "Field ids cannot be null");

    Type result = visit(struct, new PruneColumns(fieldIds, false));
    if (struct.equals(result)) {
      return struct;
    } else if (result != null) {
      return result.asStructType();
    }

    return Types.StructType.of();
  }

  /**
   * 按 ID 选择 schema：保留选中字段及其所有祖先字段。
   *
   * <p>设计要点：与 {@link #project(Schema, Set)} 不同，select 会完整保留被选中字段的子树 （struct 被选中时其所有子字段自动包含），通过
   * {@link PruneColumns}（select=true）实现。
   *
   * @param schema 待选择 schema
   * @param fieldIds 要保留的字段 ID 集合
   * @return 选择后的 schema
   */
  public static Schema select(Schema schema, Set<Integer> fieldIds) {
    Preconditions.checkNotNull(schema, "Schema cannot be null");

    Types.StructType result = select(schema.asStruct(), fieldIds);
    if (Objects.equals(schema.asStruct(), result)) {
      return schema;
    } else if (result != null) {
      if (schema.getAliases() != null) {
        return new Schema(result.fields(), schema.getAliases());
      } else {
        return new Schema(result.fields());
      }
    }

    return new Schema(ImmutableList.of(), schema.getAliases());
  }

  /**
   * 按 ID 选择 struct 类型，保留选中字段及其子树。
   *
   * @param struct 待选择 struct
   * @param fieldIds 要保留的字段 ID 集合
   * @return 选择后的 struct
   */
  public static Types.StructType select(Types.StructType struct, Set<Integer> fieldIds) {
    Preconditions.checkNotNull(struct, "Struct cannot be null");
    Preconditions.checkNotNull(fieldIds, "Field ids cannot be null");

    Type result = visit(struct, new PruneColumns(fieldIds, true));
    if (struct.equals(result)) {
      return struct;
    } else if (result != null) {
      return result.asStructType();
    }

    return Types.StructType.of();
  }

  /** 返回 schema 中所有字段的 ID 集合（含 struct 字段 ID）。 */
  public static Set<Integer> getProjectedIds(Schema schema) {
    return ImmutableSet.copyOf(getIdsInternal(schema.asStruct(), true));
  }

  /** 返回类型中所有字段的 ID 集合；原始类型返回空集。 */
  public static Set<Integer> getProjectedIds(Type type) {
    if (type.isPrimitiveType()) {
      return ImmutableSet.of();
    }
    return ImmutableSet.copyOf(getIdsInternal(type, true));
  }

  private static Set<Integer> getIdsInternal(Type type, boolean includeStructIds) {
    return visit(type, new GetProjectedIds(includeStructIds));
  }

  /**
   * 排除指定 ID 的字段，返回裁剪后的 struct。
   *
   * <p>逻辑：先获取全部字段 ID，移除 fieldIds 后再 project。
   *
   * @param struct 待裁剪 struct
   * @param fieldIds 要排除的字段 ID 集合
   * @return 裁剪后的 struct
   */
  public static Types.StructType selectNot(Types.StructType struct, Set<Integer> fieldIds) {
    Set<Integer> projectedIds = getIdsInternal(struct, false);
    projectedIds.removeAll(fieldIds);
    return project(struct, projectedIds);
  }

  /**
   * 排除指定 ID 的字段，返回裁剪后的 schema。
   *
   * @param schema 待裁剪 schema
   * @param fieldIds 要排除的字段 ID 集合
   * @return 裁剪后的 schema
   */
  public static Schema selectNot(Schema schema, Set<Integer> fieldIds) {
    Set<Integer> projectedIds = getIdsInternal(schema.asStruct(), false);
    projectedIds.removeAll(fieldIds);
    return project(schema, projectedIds);
  }

  /**
   * 合并两个 schema：以 left 为基准，把 right 中不在 left 中的字段追加。
   *
   * <p>逻辑：遍历 right 的字段，若 ID 在 left 中已存在则校验完全相等，否则追加到结果列表。
   *
   * @param left 基准 schema
   * @param right 待合并 schema
   * @return 合并后的 schema
   * @throws IllegalArgumentException 同 ID 字段不完全相等时抛出
   */
  public static Schema join(Schema left, Schema right) {
    List<Types.NestedField> joinedColumns = Lists.newArrayList(left.columns());
    for (Types.NestedField rightColumn : right.columns()) {
      Types.NestedField leftColumn = left.findField(rightColumn.fieldId());

      if (leftColumn == null) {
        joinedColumns.add(rightColumn);
      } else {
        Preconditions.checkArgument(
            leftColumn.equals(rightColumn),
            "Schemas have different columns with same id: %s, %s",
            leftColumn,
            rightColumn);
      }
    }

    return new Schema(joinedColumns);
  }

  /** 构建字段名→字段 ID 的索引。 */
  public static Map<String, Integer> indexByName(Types.StructType struct) {
    IndexByName indexer = new IndexByName();
    visit(struct, indexer);
    return indexer.byName();
  }

  /** 构建字段 ID→字段名的索引。 */
  public static Map<Integer, String> indexNameById(Types.StructType struct) {
    IndexByName indexer = new IndexByName();
    visit(struct, indexer);
    return indexer.byId();
  }

  /** 构建字段 ID→引号包裹字段名的索引，quotingFunc 用于对字段名加引号。 */
  public static Map<Integer, String> indexQuotedNameById(
      Types.StructType struct, Function<String, String> quotingFunc) {
    IndexByName indexer = new IndexByName(quotingFunc);
    visit(struct, indexer);
    return indexer.byId();
  }

  /** 构建小写字段名→字段 ID 的索引，用于大小写不敏感查询。 */
  public static Map<String, Integer> indexByLowerCaseName(Types.StructType struct) {
    Map<String, Integer> indexByLowerCaseName = Maps.newHashMap();
    indexByName(struct)
        .forEach(
            (name, integer) -> indexByLowerCaseName.put(name.toLowerCase(Locale.ROOT), integer));
    return indexByLowerCaseName;
  }

  /** 构建字段 ID→NestedField 的索引。 */
  public static Map<Integer, Types.NestedField> indexById(Types.StructType struct) {
    return visit(struct, new IndexById());
  }

  /** 构建字段 ID→父字段 ID 的索引；顶层字段的父 ID 为 -1。 */
  public static Map<Integer, Integer> indexParents(Types.StructType struct) {
    return ImmutableMap.copyOf(visit(struct, new IndexParents()));
  }

  /**
   * 为类型中所有字段分配全新的 ID，结构保持不变。
   *
   * @param type 待分配 ID 的类型
   * @param nextId ID 分配函数
   * @return 结构相同但 ID 全新的类型
   */
  public static Type assignFreshIds(Type type, NextID nextId) {
    return TypeUtil.visit(type, new AssignFreshIds(nextId));
  }

  /**
   * 为 schema 中所有字段分配全新的 ID，结构保持不变，并刷新标识字段。
   *
   * @param schema 待分配 ID 的 schema
   * @param nextId ID 分配函数
   * @return 结构相同但 ID 全新的 schema
   */
  public static Schema assignFreshIds(Schema schema, NextID nextId) {
    Types.StructType struct =
        TypeUtil.visit(schema.asStruct(), new AssignFreshIds(nextId)).asStructType();
    return new Schema(struct.fields(), refreshIdentifierFields(struct, schema));
  }

  /**
   * 为 schema 分配全新 ID 并指定 schema ID。
   *
   * @param schemaId 分配给此 schema 的 ID
   * @param schema 待分配 ID 的 schema
   * @param nextId ID 分配函数
   * @return 结构相同但 ID 全新的 schema
   */
  public static Schema assignFreshIds(int schemaId, Schema schema, NextID nextId) {
    Types.StructType struct =
        TypeUtil.visit(schema.asStruct(), new AssignFreshIds(nextId)).asStructType();
    return new Schema(schemaId, struct.fields(), refreshIdentifierFields(struct, schema));
  }

  /**
   * 按 baseSchema 的字段名复制已有 ID，其余字段用 nextId 分配新 ID。
   *
   * <p>设计要点：用于 schema 演进——保留已有字段的 ID 稳定性，仅给新增字段分配新 ID。
   *
   * @param schema 待分配 ID 的 schema
   * @param baseSchema 提供已有 ID 的基准 schema
   * @param nextId 新字段的 ID 分配函数
   * @return 结构相同、ID 兼容基准 schema 的新 schema
   */
  public static Schema assignFreshIds(Schema schema, Schema baseSchema, NextID nextId) {
    Types.StructType struct =
        TypeUtil.visit(schema.asStruct(), new AssignFreshIds(schema, baseSchema, nextId))
            .asStructType();
    return new Schema(struct.fields(), refreshIdentifierFields(struct, schema));
  }

  /**
   * 根据基准 schema 的标识字段名，在新 schema 中找到对应的字段 ID。
   *
   * <p>逻辑：构建新 schema 的名→ID 索引，遍历基准 schema 的标识字段名，校验存在于新 schema 后映射为 ID。
   *
   * @param freshSchema 新 schema
   * @param baseSchema 基准 schema
   * @return 新 schema 中的标识字段 ID 集合
   */
  public static Set<Integer> refreshIdentifierFields(
      Types.StructType freshSchema, Schema baseSchema) {
    Map<String, Integer> nameToId = TypeUtil.indexByName(freshSchema);
    Set<String> identifierFieldNames = baseSchema.identifierFieldNames();
    identifierFieldNames.forEach(
        name ->
            Preconditions.checkArgument(
                nameToId.containsKey(name),
                "Cannot find ID for identifier field %s in schema %s",
                name,
                freshSchema));
    return identifierFieldNames.stream().map(nameToId::get).collect(Collectors.toSet());
  }

  /**
   * 为 schema 所有字段分配从 1 开始严格递增的新 ID。
   *
   * @param schema 待分配 ID 的 schema
   * @return 结构相同、ID 从 1 递增的 schema
   */
  public static Schema assignIncreasingFreshIds(Schema schema) {
    AtomicInteger lastColumnId = new AtomicInteger(0);
    return TypeUtil.assignFreshIds(schema, lastColumnId::incrementAndGet);
  }

  /**
   * 按 idSourceSchema 的字段名重分配 schema 的字段 ID（大小写敏感）。
   *
   * <p>逻辑：按字段名匹配，若 schema 中某字段在 idSourceSchema 中找不到则抛异常。不改变结构、可空性与类型。
   *
   * @param schema 待重分配 ID 的 schema
   * @param idSourceSchema 提供 ID 的源 schema
   * @return 结构相同、ID 匹配源 schema 的 schema
   * @throws IllegalArgumentException 字段名在源 schema 中找不到时抛出
   */
  public static Schema reassignIds(Schema schema, Schema idSourceSchema) {
    return reassignIds(schema, idSourceSchema, true);
  }

  /**
   * 按 docSourceSchema 的字段 ID 重分配 schema 的字段文档。
   *
   * <p>逻辑：按字段 ID 匹配，把 docSourceSchema 中的文档复制到 schema 对应字段。不改变结构、可空性与类型。
   *
   * @param schema 待重分配文档的 schema
   * @param docSourceSchema 提供文档的源 schema
   * @return 结构相同、文档匹配源 schema 的 schema
   * @throws IllegalArgumentException 字段 ID 在源 schema 中找不到时抛出
   */
  public static Schema reassignDoc(Schema schema, Schema docSourceSchema) {
    TypeUtil.CustomOrderSchemaVisitor<Type> visitor = new ReassignDoc(docSourceSchema);
    return new Schema(
        visitor
            .schema(schema, new VisitFuture<>(schema.asStruct(), visitor))
            .asStructType()
            .fields());
  }

  /**
   * 按 idSourceSchema 的字段名重分配 schema 的字段 ID，可指定大小写敏感性。
   *
   * @param schema 待重分配 ID 的 schema
   * @param idSourceSchema 提供 ID 的源 schema
   * @param caseSensitive 是否大小写敏感
   * @return 结构相同、ID 匹配源 schema 的 schema
   * @throws IllegalArgumentException 字段名在源 schema 中找不到时抛出
   */
  public static Schema reassignIds(Schema schema, Schema idSourceSchema, boolean caseSensitive) {
    Types.StructType struct =
        visit(schema, new ReassignIds(idSourceSchema, null, caseSensitive)).asStructType();
    return new Schema(struct.fields(), refreshIdentifierFields(struct, schema));
  }

  /** 按 idSourceSchema 重分配 ID，源 schema 中不存在的字段分配新 ID（大小写敏感）。 */
  public static Schema reassignOrRefreshIds(Schema schema, Schema idSourceSchema) {
    return reassignOrRefreshIds(schema, idSourceSchema, true);
  }

  /**
   * 按 idSourceSchema 重分配 ID，源 schema 中不存在的字段分配新 ID。
   *
   * <p>逻辑：新 ID 从 idSourceSchema 的最高字段 ID + 1 开始递增。
   *
   * @param schema 待重分配 ID 的 schema
   * @param idSourceSchema 提供 ID 的源 schema
   * @param caseSensitive 是否大小写敏感
   * @return 结构相同、ID 兼容源 schema 的 schema
   */
  public static Schema reassignOrRefreshIds(
      Schema schema, Schema idSourceSchema, boolean caseSensitive) {
    AtomicInteger highest = new AtomicInteger(idSourceSchema.highestFieldId());
    Types.StructType struct =
        visit(schema, new ReassignIds(idSourceSchema, highest::incrementAndGet, caseSensitive))
            .asStructType();
    return new Schema(struct.fields(), refreshIdentifierFields(struct, schema));
  }

  /**
   * 在 schema 中查找第一个满足谓词的类型。
   *
   * @param schema 待查找 schema
   * @param predicate 类型谓词
   * @return 第一个匹配的类型；未找到返回 null
   */
  public static Type find(Schema schema, Predicate<Type> predicate) {
    return visit(schema, new FindTypeVisitor(predicate));
  }

  /**
   * 判断从 from 类型提升到 to 类型是否允许。
   *
   * <p>逻辑：相同类型允许；INTEGER→LONG 允许；FLOAT→DOUBLE 允许； DECIMAL→DECIMAL 要求 scale 相同且目标 precision >= 源
   * precision；其余不允许。
   *
   * <p>设计要点：类型提升规则影响分区兼容性，修改前需确保不引入分区兼容问题。
   *
   * @param from 源类型
   * @param to 目标类型
   * @return 允许提升返回 true
   */
  public static boolean isPromotionAllowed(Type from, Type.PrimitiveType to) {
    // Warning! Before changing this function, make sure that the type change doesn't introduce
    // compatibility problems in partitioning.
    if (from.equals(to)) {
      return true;
    }

    switch (from.typeId()) {
      case INTEGER:
        return to.typeId() == Type.TypeID.LONG;

      case FLOAT:
        return to.typeId() == Type.TypeID.DOUBLE;

      case DECIMAL:
        Types.DecimalType fromDecimal = (Types.DecimalType) from;
        if (to.typeId() != Type.TypeID.DECIMAL) {
          return false;
        }

        Types.DecimalType toDecimal = (Types.DecimalType) to;
        return fromDecimal.scale() == toDecimal.scale()
            && fromDecimal.precision() <= toDecimal.precision();
    }

    return false;
  }

  /**
   * 校验用户提供的写入 schema 是否与表 schema 兼容。
   *
   * @param tableSchema 表 schema（元数据中的）
   * @param writeSchema 用户提供的写入 schema
   * @param checkNullability 为 true 时不允许向必填字段写可选值
   * @param checkOrdering 为 true 时不允许写入 schema 的字段顺序与表 schema 不同
   * @throws IllegalArgumentException 不兼容时抛出
   */
  public static void validateWriteSchema(
      Schema tableSchema, Schema writeSchema, Boolean checkNullability, Boolean checkOrdering) {
    String errMsg = "Cannot write incompatible dataset to table with schema:";
    checkSchemaCompatibility(errMsg, tableSchema, writeSchema, checkNullability, checkOrdering);
  }

  /**
   * 校验提供的 schema 是否与期望 schema 兼容。
   *
   * @param context schema 上下文描述（如 row ID）
   * @param expectedSchema 期望 schema
   * @param providedSchema 提供的 schema
   * @param checkNullability 是否校验字段可空性
   * @param checkOrdering 是否校验字段顺序
   * @throws IllegalArgumentException 不兼容时抛出
   */
  public static void validateSchema(
      String context,
      Schema expectedSchema,
      Schema providedSchema,
      boolean checkNullability,
      boolean checkOrdering) {
    String errMsg =
        String.format("Provided %s schema is incompatible with expected schema:", context);
    checkSchemaCompatibility(
        errMsg, expectedSchema, providedSchema, checkNullability, checkOrdering);
  }

  /**
   * 校验两个 schema 的兼容性，收集错误信息并抛异常。
   *
   * <p>逻辑：根据 checkNullability 选择写入兼容性检查或类型兼容性检查；收集所有错误后 拼接成详细错误信息抛出 IllegalArgumentException。
   *
   * @param errMsg 错误消息前缀
   * @param schema 表 schema
   * @param providedSchema 提供的 schema
   * @param checkNullability 是否校验可空性
   * @param checkOrdering 是否校验字段顺序
   * @throws IllegalArgumentException 存在不兼容时抛出
   */
  private static void checkSchemaCompatibility(
      String errMsg,
      Schema schema,
      Schema providedSchema,
      boolean checkNullability,
      boolean checkOrdering) {
    List<String> errors;
    if (checkNullability) {
      errors = CheckCompatibility.writeCompatibilityErrors(schema, providedSchema, checkOrdering);
    } else {
      errors = CheckCompatibility.typeCompatibilityErrors(schema, providedSchema, checkOrdering);
    }

    if (!errors.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append(errMsg)
          .append("\n")
          .append(schema)
          .append("\n")
          .append("Provided schema:")
          .append("\n")
          .append(providedSchema)
          .append("\n")
          .append("Problems:");
      for (String error : errors) {
        sb.append("\n* ").append(error);
      }
      throw new IllegalArgumentException(sb.toString());
    }
  }

  /** 字段 ID 分配函数接口。 */
  public interface NextID {
    int get();
  }

  /**
   * 后序遍历 schema 的访问者基类。
   *
   * <p>设计要点：beforeField/afterField 在进入/退出字段时调用，可用于追踪字段路径； struct/list/map 的回调在子节点遍历完成后调用（后序）；默认方法返回
   * null，子类按需覆盖。
   *
   * @param <T> 访问者返回类型
   */
  public static class SchemaVisitor<T> {
    public void beforeField(Types.NestedField field) {}

    public void afterField(Types.NestedField field) {}

    public void beforeListElement(Types.NestedField elementField) {
      beforeField(elementField);
    }

    public void afterListElement(Types.NestedField elementField) {
      afterField(elementField);
    }

    public void beforeMapKey(Types.NestedField keyField) {
      beforeField(keyField);
    }

    public void afterMapKey(Types.NestedField keyField) {
      afterField(keyField);
    }

    public void beforeMapValue(Types.NestedField valueField) {
      beforeField(valueField);
    }

    public void afterMapValue(Types.NestedField valueField) {
      afterField(valueField);
    }

    public T schema(Schema schema, T structResult) {
      return null;
    }

    public T struct(Types.StructType struct, List<T> fieldResults) {
      return null;
    }

    public T field(Types.NestedField field, T fieldResult) {
      return null;
    }

    public T list(Types.ListType list, T elementResult) {
      return null;
    }

    public T map(Types.MapType map, T keyResult, T valueResult) {
      return null;
    }

    public T primitive(Type.PrimitiveType primitive) {
      return null;
    }
  }

  /** 遍历 schema 并用访问者处理后序结果。 */
  public static <T> T visit(Schema schema, SchemaVisitor<T> visitor) {
    return visitor.schema(schema, visit(schema.asStruct(), visitor));
  }

  /**
   * 后序遍历类型树。
   *
   * <p>逻辑：按 typeId 分派——STRUCT 逐字段递归遍历后调用 struct 回调；LIST 递归元素后调用 list 回调； MAP 递归 key/value 后调用 map
   * 回调；原始类型直接调用 primitive 回调。
   *
   * @param type 待遍历类型
   * @param visitor 访问者
   * @param <T> 返回类型
   * @return 遍历结果
   */
  public static <T> T visit(Type type, SchemaVisitor<T> visitor) {
    switch (type.typeId()) {
      case STRUCT:
        Types.StructType struct = type.asNestedType().asStructType();
        List<T> results = Lists.newArrayListWithExpectedSize(struct.fields().size());
        for (Types.NestedField field : struct.fields()) {
          visitor.beforeField(field);
          T result;
          try {
            result = visit(field.type(), visitor);
          } finally {
            visitor.afterField(field);
          }
          results.add(visitor.field(field, result));
        }
        return visitor.struct(struct, results);

      case LIST:
        Types.ListType list = type.asNestedType().asListType();
        T elementResult;

        Types.NestedField elementField = list.field(list.elementId());
        visitor.beforeListElement(elementField);
        try {
          elementResult = visit(list.elementType(), visitor);
        } finally {
          visitor.afterListElement(elementField);
        }

        return visitor.list(list, elementResult);

      case MAP:
        Types.MapType map = type.asNestedType().asMapType();
        T keyResult;
        T valueResult;

        Types.NestedField keyField = map.field(map.keyId());
        visitor.beforeMapKey(keyField);
        try {
          keyResult = visit(map.keyType(), visitor);
        } finally {
          visitor.afterMapKey(keyField);
        }

        Types.NestedField valueField = map.field(map.valueId());
        visitor.beforeMapValue(valueField);
        try {
          valueResult = visit(map.valueType(), visitor);
        } finally {
          visitor.afterMapValue(valueField);
        }

        return visitor.map(map, keyResult, valueResult);

      default:
        return visitor.primitive(type.asPrimitiveType());
    }
  }

  /**
   * 自定义顺序遍历 schema 的访问者基类。
   *
   * <p>设计要点：通过 {@link Supplier} 懒求值传递子节点结果，使调用方可控制遍历顺序 （前序/中序/自定义），适用于需要前序遍历的场景（如 ID 分配）。
   *
   * @param <T> 访问者返回类型
   */
  public static class CustomOrderSchemaVisitor<T> {
    public T schema(Schema schema, Supplier<T> structResult) {
      return null;
    }

    public T struct(Types.StructType struct, Iterable<T> fieldResults) {
      return null;
    }

    public T field(Types.NestedField field, Supplier<T> fieldResult) {
      return null;
    }

    public T list(Types.ListType list, Supplier<T> elementResult) {
      return null;
    }

    public T map(Types.MapType map, Supplier<T> keyResult, Supplier<T> valueResult) {
      return null;
    }

    public T primitive(Type.PrimitiveType primitive) {
      return null;
    }
  }

  private static class VisitFuture<T> implements Supplier<T> {
    private final Type type;
    private final CustomOrderSchemaVisitor<T> visitor;

    private VisitFuture(Type type, CustomOrderSchemaVisitor<T> visitor) {
      this.type = type;
      this.visitor = visitor;
    }

    @Override
    public T get() {
      return visit(type, visitor);
    }
  }

  private static class VisitFieldFuture<T> implements Supplier<T> {
    private final Types.NestedField field;
    private final CustomOrderSchemaVisitor<T> visitor;

    private VisitFieldFuture(Types.NestedField field, CustomOrderSchemaVisitor<T> visitor) {
      this.field = field;
      this.visitor = visitor;
    }

    @Override
    public T get() {
      return visitor.field(field, new VisitFuture<>(field.type(), visitor));
    }
  }

  /** 用自定义顺序访问者遍历 schema。 */
  public static <T> T visit(Schema schema, CustomOrderSchemaVisitor<T> visitor) {
    return visitor.schema(schema, new VisitFuture<>(schema.asStruct(), visitor));
  }

  /**
   * 用自定义顺序访问者遍历类型树（非后序）。
   *
   * <p>逻辑：按 typeId 分派——STRUCT 把每个字段包装为 VisitFieldFuture（懒求值），通过 Iterables.transform
   * 在迭代时才访问子字段；LIST/MAP 把子类型包装为 VisitFuture。
   *
   * <p>设计要点：Supplier 懒求值使调用方可在访问子节点前执行自定义逻辑（如前序 ID 分配）。
   *
   * @param type 待遍历类型
   * @param visitor 自定义顺序访问者
   * @param <T> 返回类型
   * @return 遍历结果
   */
  public static <T> T visit(Type type, CustomOrderSchemaVisitor<T> visitor) {
    switch (type.typeId()) {
      case STRUCT:
        Types.StructType struct = type.asNestedType().asStructType();
        List<VisitFieldFuture<T>> results =
            Lists.newArrayListWithExpectedSize(struct.fields().size());
        for (Types.NestedField field : struct.fields()) {
          results.add(new VisitFieldFuture<>(field, visitor));
        }

        return visitor.struct(struct, Iterables.transform(results, VisitFieldFuture::get));

      case LIST:
        Types.ListType list = type.asNestedType().asListType();
        return visitor.list(list, new VisitFuture<>(list.elementType(), visitor));

      case MAP:
        Types.MapType map = type.asNestedType().asMapType();
        return visitor.map(
            map,
            new VisitFuture<>(map.keyType(), visitor),
            new VisitFuture<>(map.valueType(), visitor));

      default:
        return visitor.primitive(type.asPrimitiveType());
    }
  }

  /** 返回指定字节数能表示的 Decimal 最大精度。 */
  static int decimalMaxPrecision(int numBytes) {
    Preconditions.checkArgument(
        numBytes >= 0 && numBytes < 24, "Unsupported decimal length: %s", numBytes);
    return MAX_PRECISION[numBytes];
  }

  /** 返回指定精度所需的 Decimal 存储字节数。 */
  public static int decimalRequiredBytes(int precision) {
    Preconditions.checkArgument(
        precision >= 0 && precision < 40, "Unsupported decimal precision: %s", precision);
    return REQUIRED_LENGTH[precision];
  }

  private static final int[] MAX_PRECISION = new int[24];
  private static final int[] REQUIRED_LENGTH = new int[40];

  static {
    // for each length, calculate the max precision
    for (int len = 0; len < MAX_PRECISION.length; len += 1) {
      MAX_PRECISION[len] = (int) Math.floor(Math.log10(Math.pow(2, 8 * len - 1) - 1));
    }

    // for each precision, find the first length that can hold it
    for (int precision = 0; precision < REQUIRED_LENGTH.length; precision += 1) {
      REQUIRED_LENGTH[precision] = -1;
      for (int len = 0; len < MAX_PRECISION.length; len += 1) {
        // find the first length that can hold the precision
        if (precision <= MAX_PRECISION[len]) {
          REQUIRED_LENGTH[precision] = len;
          break;
        }
      }
      if (REQUIRED_LENGTH[precision] < 0) {
        throw new IllegalStateException(
            "Could not find required length for precision " + precision);
      }
    }
  }
}
