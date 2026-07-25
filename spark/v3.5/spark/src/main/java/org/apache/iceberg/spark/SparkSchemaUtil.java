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
package org.apache.iceberg.spark;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.math.LongMath;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.spark.sql.AnalysisException;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalog.Column;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructType;

/**
 * Spark 与 Iceberg 之间的 Schema/类型转换与元数据工具类。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>Spark StructType/DataType 与 Iceberg Schema/Type 互转（含字段 ID 重新分配与歧义类型修正）。
 *   <li>基于 Spark 表元数据构造 Iceberg Schema 与 PartitionSpec（identity 分区）。
 *   <li>按 Spark 投影与过滤器裁剪 Iceberg Schema 列。
 *   <li>估算表大小、校验元数据列名冲突、生成带反引号的字段名索引。
 * </ul>
 *
 * <p>设计意图：集中所有 Spark-Iceberg schema 转换逻辑，避免散落；通过 {@link SparkTypeVisitor} / {@link TypeToSparkType}
 * 访问者模式做类型映射；歧义类型（UUID/Fixed）用 {@link SparkFixupTypes} 借参考 schema 修正；字段 ID 通过 {@link
 * TypeUtil#reassignIds} 对齐已有 schema。
 *
 * <p>上下游关系：被 Spark 读写路径、catalog、procedures 等广泛调用；依赖 iceberg-core 的 TypeUtil 与 Spark SQL types。
 */
public class SparkSchemaUtil {
  private SparkSchemaUtil() {}

  /**
   * 为指定 Spark 表构造带新鲜字段 ID 的 Iceberg {@link Schema}。
   *
   * <p>逻辑：通过 Spark 查表得到 StructType，用 {@link SparkTypeVisitor} + {@link SparkTypeToType} 转换为 Iceberg
   * Schema（包含 Spark/Hive 分区列）。
   *
   * @param spark SparkSession
   * @param name 表名（可选含库名）
   * @return 表对应的 Iceberg Schema
   */
  public static Schema schemaForTable(SparkSession spark, String name) {
    StructType sparkType = spark.table(name).schema();
    Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
    return new Schema(converted.asNestedType().asStructType().fields());
  }

  /**
   * 为指定 Spark 表构造 Iceberg {@link PartitionSpec}。
   *
   * <p>逻辑：解析表名得到 db/table；用 {@link #schemaForTable} 得到 schema； 调 {@link #identitySpec} 对每个分区列创建
   * identity 分区；无分区列返回 unpartitioned。
   *
   * @param spark SparkSession
   * @param name 表名（可选含库名）
   * @return 表对应的 PartitionSpec
   * @throws AnalysisException Spark catalog 抛出时透传
   */
  public static PartitionSpec specForTable(SparkSession spark, String name)
      throws AnalysisException {
    List<String> parts = Lists.newArrayList(Splitter.on('.').limit(2).split(name));
    String db = parts.size() == 1 ? "default" : parts.get(0);
    String table = parts.get(parts.size() == 1 ? 0 : 1);

    PartitionSpec spec =
        identitySpec(
            schemaForTable(spark, name), spark.catalog().listColumns(db, table).collectAsList());
    return spec == null ? PartitionSpec.unpartitioned() : spec;
  }

  /** 把 Iceberg {@link Schema} 转为 Spark {@link StructType}。 */
  public static StructType convert(Schema schema) {
    return (StructType) TypeUtil.visit(schema, new TypeToSparkType());
  }

  /** 把 Iceberg {@link Type} 转为 Spark {@link DataType}。 */
  public static DataType convert(Type type) {
    return TypeUtil.visit(type, new TypeToSparkType());
  }

  /**
   * 把 Spark {@link StructType} 转为带新鲜字段 ID 的 Iceberg {@link Schema}。
   *
   * <p>歧义类型（如 UUID/Fixed）会转为默认类型；如需按参考 schema 还原，使用 {@link #convert(Schema, StructType)}。
   *
   * @param sparkType Spark StructType
   * @return 等价的 Iceberg Schema
   */
  public static Schema convert(StructType sparkType) {
    Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
    return new Schema(converted.asNestedType().asStructType().fields());
  }

  /**
   * 把 Spark {@link DataType} 转为带新鲜字段 ID 的 Iceberg {@link Type}。
   *
   * @param sparkType Spark DataType
   * @return 等价的 Iceberg Type
   */
  public static Type convert(DataType sparkType) {
    return SparkTypeVisitor.visit(sparkType, new SparkTypeToType());
  }

  /**
   * 基于 baseSchema 把 Spark StructType 转为 Iceberg Schema（大小写敏感）。
   *
   * <p>逻辑：先转为带新鲜 ID 的 type，再用 {@link TypeUtil#reassignIds} 按 baseSchema 重分配 ID， 最后用 {@link
   * SparkFixupTypes#fixup} 修正歧义类型。字段顺序/可空性以 sparkType 为准。
   *
   * @param baseSchema 参考 schema（提供字段 ID）
   * @param sparkType Spark StructType
   * @return 等价的 Iceberg Schema
   */
  public static Schema convert(Schema baseSchema, StructType sparkType) {
    return convert(baseSchema, sparkType, true);
  }

  /**
   * 基于 baseSchema 把 Spark StructType 转为 Iceberg Schema，可配置大小写敏感。
   *
   * <p>逻辑：转 type -> reassignIds（按 caseSensitive）-> SparkFixupTypes.fixup 修正歧义类型。
   *
   * @param baseSchema 参考 schema
   * @param sparkType Spark StructType
   * @param caseSensitive false 时忽略字段名大小写
   * @return 等价的 Iceberg Schema
   */
  public static Schema convert(Schema baseSchema, StructType sparkType, boolean caseSensitive) {
    // convert to a type with fresh ids
    Types.StructType struct =
        SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType)).asStructType();
    // reassign ids to match the base schema
    Schema schema = TypeUtil.reassignIds(new Schema(struct.fields()), baseSchema, caseSensitive);
    // fix types that can't be represented in Spark (UUID and Fixed)
    return SparkFixupTypes.fixup(schema, baseSchema);
  }

  /**
   * 基于 baseSchema 转换，对 baseSchema 中不存在的字段分配新 ID（大小写敏感）。
   *
   * @param baseSchema 参考 schema
   * @param sparkType Spark StructType
   * @return 等价的 Iceberg Schema
   */
  public static Schema convertWithFreshIds(Schema baseSchema, StructType sparkType) {
    return convertWithFreshIds(baseSchema, sparkType, true);
  }

  /**
   * 基于 baseSchema 转换，对不存在字段分配新 ID，可配置大小写敏感。
   *
   * <p>逻辑：转 type -> {@link TypeUtil#reassignOrRefreshIds} -> SparkFixupTypes.fixup。
   *
   * @param baseSchema 参考 schema
   * @param sparkType Spark StructType
   * @param caseSensitive false 时忽略字段名大小写
   * @return 等价的 Iceberg Schema
   */
  public static Schema convertWithFreshIds(
      Schema baseSchema, StructType sparkType, boolean caseSensitive) {
    // convert to a type with fresh ids
    Types.StructType struct =
        SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType)).asStructType();
    // reassign ids to match the base schema
    Schema schema =
        TypeUtil.reassignOrRefreshIds(new Schema(struct.fields()), baseSchema, caseSensitive);
    // fix types that can't be represented in Spark (UUID and Fixed)
    return SparkFixupTypes.fixup(schema, baseSchema);
  }

  /**
   * 按 Spark 投影裁剪 Schema 列（不重排）。
   *
   * <p>逻辑：用 {@link PruneColumnsWithoutReordering} 访问 schema，只保留 requestedType 投影的列。
   *
   * @param schema 原 schema
   * @param requestedType Spark 投影类型
   * @return 裁剪后的 schema
   */
  public static Schema prune(Schema schema, StructType requestedType) {
    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, ImmutableSet.of()))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /**
   * 按 Spark 投影裁剪 Schema 列，并保证过滤器引用的列也被投影。
   *
   * <p>逻辑：用 {@link Binder#boundReferences} 收集 filters 引用的字段 ID，并入裁剪访问者。
   *
   * @param schema 原 schema
   * @param requestedType Spark 投影类型
   * @param filters 过滤器列表
   * @return 裁剪后的 schema
   */
  public static Schema prune(Schema schema, StructType requestedType, List<Expression> filters) {
    Set<Integer> filterRefs = Binder.boundReferences(schema.asStruct(), filters, true);
    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, filterRefs))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /**
   * 按 Spark 投影裁剪 Schema 列，单个过滤器，可配置大小写敏感。
   *
   * @param schema 原 schema
   * @param requestedType Spark 投影类型
   * @param filter 单个过滤器
   * @param caseSensitive false 时忽略字段名大小写
   * @return 裁剪后的 schema
   */
  public static Schema prune(
      Schema schema, StructType requestedType, Expression filter, boolean caseSensitive) {
    Set<Integer> filterRefs =
        Binder.boundReferences(schema.asStruct(), Collections.singletonList(filter), caseSensitive);

    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, filterRefs))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /** 从 Spark Column 集合中筛出分区列，构造 identity 分区规格。 */
  private static PartitionSpec identitySpec(Schema schema, Collection<Column> columns) {
    List<String> names = Lists.newArrayList();
    for (Column column : columns) {
      if (column.isPartition()) {
        names.add(column.name());
      }
    }

    return identitySpec(schema, names);
  }

  /** 按分区列名列表构造 identity 分区规格，无分区列返回 null。 */
  private static PartitionSpec identitySpec(Schema schema, List<String> partitionNames) {
    if (partitionNames == null || partitionNames.isEmpty()) {
      return null;
    }

    PartitionSpec.Builder builder = PartitionSpec.builderFor(schema);
    for (String partitionName : partitionNames) {
      builder.identity(partitionName);
    }

    return builder.build();
  }

  /**
   * 根据 Spark schema 默认大小与总记录数估算表大小。
   *
   * <p>逻辑：tableSchema.defaultSize() * totalRecords，溢出返回 Long.MAX_VALUE。
   *
   * @param tableSchema Spark schema
   * @param totalRecords 总记录数
   * @return 估算大小
   */
  public static long estimateSize(StructType tableSchema, long totalRecords) {
    if (totalRecords == Long.MAX_VALUE) {
      return totalRecords;
    }

    long result;
    try {
      result = LongMath.checkedMultiply(tableSchema.defaultSize(), totalRecords);
    } catch (ArithmeticException e) {
      result = Long.MAX_VALUE;
    }
    return result;
  }

  /**
   * 校验 readSchema 中没有与 Iceberg 元数据列名冲突的表列。
   *
   * <p>逻辑：找出 readSchema 中既是元数据列名又在 tableSchema 中存在的列名， 若有则抛出 {@link ValidationException} 提示用 ALTER
   * TABLE 重命名。
   *
   * @param tableSchema 表 schema
   * @param readSchema 读取 schema
   */
  public static void validateMetadataColumnReferences(Schema tableSchema, Schema readSchema) {
    List<String> conflictingColumnNames =
        readSchema.columns().stream()
            .map(Types.NestedField::name)
            .filter(
                name ->
                    MetadataColumns.isMetadataColumn(name) && tableSchema.findField(name) != null)
            .collect(Collectors.toList());

    ValidationException.check(
        conflictingColumnNames.isEmpty(),
        "Table column names conflict with names reserved for Iceberg metadata columns: %s.\n"
            + "Please, use ALTER TABLE statements to rename the conflicting table columns.",
        conflictingColumnNames);
  }

  /**
   * 生成字段 ID -> 反引号引用名的映射。
   *
   * <p>逻辑：用反引号包裹字段名并转义内部反引号（` -> ``），通过 {@link TypeUtil#indexQuotedNameById} 构造。
   *
   * @param schema 表 schema
   * @return 字段 ID 到引用名的映射
   */
  public static Map<Integer, String> indexQuotedNameById(Schema schema) {
    Function<String, String> quotingFunc = name -> String.format("`%s`", name.replace("`", "``"));
    return TypeUtil.indexQuotedNameById(schema.asStruct(), quotingFunc);
  }
}
