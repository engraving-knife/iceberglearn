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
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkSchemaUtil。
 */
public class SparkSchemaUtil {
  /** 构造 SparkSchemaUtil 实例。 */
  private SparkSchemaUtil() {}

  /** 执行该方法的具体逻辑。 */
  public static Schema schemaForTable(SparkSession spark, String name) {
    StructType sparkType = spark.table(name).schema();
    Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
    /** 执行该方法的具体逻辑。 */
    return new Schema(converted.asNestedType().asStructType().fields());
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 把输入转换为另一种表示。 */
  public static StructType convert(Schema schema) {
    return (StructType) TypeUtil.visit(schema, new TypeToSparkType());
  }

  /** 把输入转换为另一种表示。 */
  public static DataType convert(Type type) {
    return TypeUtil.visit(type, new TypeToSparkType());
  }

  /** 把输入转换为另一种表示。 */
  public static Schema convert(StructType sparkType) {
    return convert(sparkType, false);
  }

  /** 把输入转换为另一种表示。 */
  public static Schema convert(StructType sparkType, boolean useTimestampWithoutZone) {
    Type converted = SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType));
    Schema schema = new Schema(converted.asNestedType().asStructType().fields());
    if (useTimestampWithoutZone) {
      schema = SparkFixupTimestampType.fixup(schema);
    }
    return schema;
  }

  /** 把输入转换为另一种表示。 */
  public static Type convert(DataType sparkType) {
    return SparkTypeVisitor.visit(sparkType, new SparkTypeToType());
  }

  /** 把输入转换为另一种表示。 */
  public static Schema convert(Schema baseSchema, StructType sparkType) {
    // convert to a type with fresh ids
    Types.StructType struct =
        SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType)).asStructType();
    // reassign ids to match the base schema
    Schema schema = TypeUtil.reassignIds(new Schema(struct.fields()), baseSchema);
    // fix types that can't be represented in Spark (UUID and Fixed)
    return SparkFixupTypes.fixup(schema, baseSchema);
  }

  /** 把输入转换为另一种表示。 */
  public static Schema convertWithFreshIds(Schema baseSchema, StructType sparkType) {
    // convert to a type with fresh ids
    Types.StructType struct =
        SparkTypeVisitor.visit(sparkType, new SparkTypeToType(sparkType)).asStructType();
    // reassign ids to match the base schema
    Schema schema = TypeUtil.reassignOrRefreshIds(new Schema(struct.fields()), baseSchema);
    // fix types that can't be represented in Spark (UUID and Fixed)
    return SparkFixupTypes.fixup(schema, baseSchema);
  }

  /** 执行该方法的具体逻辑。 */
  public static Schema prune(Schema schema, StructType requestedType) {
    /** 执行该方法的具体逻辑。 */
    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, ImmutableSet.of()))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /** 执行该方法的具体逻辑。 */
  public static Schema prune(Schema schema, StructType requestedType, List<Expression> filters) {
    Set<Integer> filterRefs = Binder.boundReferences(schema.asStruct(), filters, true);
    /** 执行该方法的具体逻辑。 */
    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, filterRefs))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /** 执行该方法的具体逻辑。 */
  public static Schema prune(
      Schema schema, StructType requestedType, Expression filter, boolean caseSensitive) {
    Set<Integer> filterRefs =
        Binder.boundReferences(schema.asStruct(), Collections.singletonList(filter), caseSensitive);

    /** 执行该方法的具体逻辑。 */
    return new Schema(
        TypeUtil.visit(schema, new PruneColumnsWithoutReordering(requestedType, filterRefs))
            .asNestedType()
            .asStructType()
            .fields());
  }

  /** 执行该方法的具体逻辑。 */
  private static PartitionSpec identitySpec(Schema schema, Collection<Column> columns) {
    List<String> names = Lists.newArrayList();
    for (Column column : columns) {
      if (column.isPartition()) {
        names.add(column.name());
      }
    }

    return identitySpec(schema, names);
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
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

  /** 校验前置条件或参数。 */
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

  /** 执行该方法的具体逻辑。 */
  public static Map<Integer, String> indexQuotedNameById(Schema schema) {
    Function<String, String> quotingFunc = name -> String.format("`%s`", name.replace("`", "``"));
    return TypeUtil.indexQuotedNameById(schema.asStruct(), quotingFunc);
  }
}
