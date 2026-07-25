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
package org.apache.iceberg.aws.glue;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.glue.model.Column;
import software.amazon.awssdk.services.glue.model.DatabaseInput;
import software.amazon.awssdk.services.glue.model.StorageDescriptor;
import software.amazon.awssdk.services.glue.model.TableInput;

/**
 * Iceberg 元数据到 AWS Glue 格式的转换工具类。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 Iceberg 命名空间校验/转换为 Glue 数据库名，表名校验/转换为 Glue 表名。
 *   <li>将 Iceberg 命名空间属性转换为 Glue DatabaseInput。
 *   <li>将 Iceberg 表元数据（schema、location）设置到 Glue TableInput，供 Glue UI/CLI 展示。
 *   <li>将 Iceberg 类型映射为 Glue 可显示的类型字符串。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Glue 对数据库名/表名有严格命名规范（小写字母、数字、下划线，长度限制）， 本类通过正则校验确保 Iceberg 标识符符合 Glue 要求，可通过
 *       skipNameValidation 跳过。
 *   <li>setTableInputInformation 做的是"尽力转换"（best-effort），仅用于人类通过 Glue UI/CLI 查看表信息，不应被查询引擎用于推断
 *       schema 或分区。真正的 source of truth 在 Iceberg 元数据文件中（由 metadata_location 指定）。
 *   <li>列转换时对历史 schema 中的字段做去重（按字段名），优先保留 current schema 的字段， 并通过 iceberg.field.* 参数保留字段
 *       ID、optional、是否当前等元信息。
 *   <li>使用 DynMethods 反射调用 additionalLocations，兼容不同 AWS SDK 版本。
 * </ul>
 *
 * <p>上下游关系：被 {@link GlueTableOperations} 和 GlueCatalog 调用， 用于在提交表时构建 Glue TableInput 和
 * DatabaseInput。
 */
class IcebergToGlueConverter {

  private static final Logger LOG = LoggerFactory.getLogger(IcebergToGlueConverter.class);

  private IcebergToGlueConverter() {}

  private static final Pattern GLUE_DB_PATTERN = Pattern.compile("^[a-z0-9_]{1,252}$");
  private static final Pattern GLUE_TABLE_PATTERN = Pattern.compile("^[a-z0-9_]{1,255}$");
  public static final String GLUE_DB_LOCATION_KEY = "location";
  public static final String GLUE_DB_DESCRIPTION_KEY = "comment";
  public static final String ICEBERG_FIELD_ID = "iceberg.field.id";
  public static final String ICEBERG_FIELD_OPTIONAL = "iceberg.field.optional";
  public static final String ICEBERG_FIELD_CURRENT = "iceberg.field.current";
  private static final List<String> ADDITIONAL_LOCATION_PROPERTIES =
      ImmutableList.of(
          TableProperties.WRITE_DATA_LOCATION,
          TableProperties.WRITE_METADATA_LOCATION,
          TableProperties.OBJECT_STORE_PATH,
          TableProperties.WRITE_FOLDER_STORAGE_LOCATION);

  // Attempt to set additionalLocations if available on the given AWS SDK version
  private static final DynMethods.UnboundMethod SET_ADDITIONAL_LOCATIONS =
      DynMethods.builder("additionalLocations")
          .hiddenImpl(
              "software.amazon.awssdk.services.glue.model.StorageDescriptor$Builder",
              Collection.class)
          .orNoop()
          .build();

  /**
   * 判断命名空间是否可作为 Glue 数据库名。
   *
   * <p>Glue 数据库名不超过 252 字符，仅允许小写字母、数字和下划线， 且命名空间必须为单层（详见 Glue 最佳实践）。
   *
   * @param namespace 命名空间
   * @return true 表示可被 Glue 接受
   */
  static boolean isValidNamespace(Namespace namespace) {
    if (namespace.levels().length != 1) {
      return false;
    }
    String dbName = namespace.level(0);
    return dbName != null && GLUE_DB_PATTERN.matcher(dbName).find();
  }

  /**
   * 校验命名空间在 Glue 中是否合法，不合法则抛 ValidationException。
   *
   * @param namespace 命名空间
   * @throws org.apache.iceberg.exceptions.ValidationException 命名空间不符合 Glue 命名规范
   */
  static void validateNamespace(Namespace namespace) {
    ValidationException.check(
        isValidNamespace(namespace),
        "Cannot convert namespace %s to Glue database name, "
            + "because it must be 1-252 chars of lowercase letters, numbers, underscore",
        namespace);
  }

  /**
   * 将 Iceberg 命名空间转换为 Glue 数据库名（可选校验）。
   *
   * @param namespace Iceberg 命名空间
   * @param skipNameValidation 是否跳过名称校验
   * @return 数据库名
   */
  static String toDatabaseName(Namespace namespace, boolean skipNameValidation) {
    if (!skipNameValidation) {
      validateNamespace(namespace);
    }

    return namespace.level(0);
  }

  /**
   * 从 Iceberg 表标识符中提取 Glue 数据库名（可选校验）。
   *
   * @param tableIdentifier Iceberg 表标识符
   * @param skipNameValidation 是否跳过名称校验
   * @return 数据库名
   */
  static String getDatabaseName(TableIdentifier tableIdentifier, boolean skipNameValidation) {
    return toDatabaseName(tableIdentifier.namespace(), skipNameValidation);
  }

  /**
   * 将 Iceberg 命名空间及其属性转换为 Glue DatabaseInput。
   *
   * <p>逻辑：name 取自命名空间转换；属性中 "comment" 映射为 description， "location" 映射为 locationUri，其余作为 parameters
   * 保留。
   *
   * @param namespace Iceberg 命名空间
   * @param metadata 属性 Map
   * @param skipNameValidation 是否跳过名称校验
   * @return Glue DatabaseInput
   */
  static DatabaseInput toDatabaseInput(
      Namespace namespace, Map<String, String> metadata, boolean skipNameValidation) {
    DatabaseInput.Builder builder =
        DatabaseInput.builder().name(toDatabaseName(namespace, skipNameValidation));
    Map<String, String> parameters = Maps.newHashMap();
    metadata.forEach(
        (k, v) -> {
          if (GLUE_DB_DESCRIPTION_KEY.equals(k)) {
            builder.description(v);
          } else if (GLUE_DB_LOCATION_KEY.equals(k)) {
            builder.locationUri(v);
          } else {
            parameters.put(k, v);
          }
        });

    return builder.parameters(parameters).build();
  }

  /**
   * 判断表名是否可作为 Glue 表名。
   *
   * <p>Glue 表名不超过 255 字符，仅允许小写字母、数字和下划线。
   *
   * @param tableName 表名
   * @return true 表示可被 Glue 接受
   */
  static boolean isValidTableName(String tableName) {
    return tableName != null && GLUE_TABLE_PATTERN.matcher(tableName).find();
  }

  /**
   * 校验表名在 Glue 中是否合法，不合法则抛 ValidationException。
   *
   * @param tableName 表名
   * @throws org.apache.iceberg.exceptions.ValidationException 表名不符合 Glue 命名规范
   */
  static void validateTableName(String tableName) {
    ValidationException.check(
        isValidTableName(tableName),
        "Cannot use %s as Glue table name, "
            + "because it must be 1-255 chars of lowercase letters, numbers, underscore",
        tableName);
  }

  /**
   * 从 Iceberg 表标识符中提取 Glue 表名（可选校验）。
   *
   * @param tableIdentifier 表标识符
   * @param skipNameValidation 是否跳过名称校验
   * @return 表名
   */
  static String getTableName(TableIdentifier tableIdentifier, boolean skipNameValidation) {
    if (!skipNameValidation) {
      validateTableName(tableIdentifier.name());
    }

    return tableIdentifier.name();
  }

  /**
   * 基于 Iceberg 表元数据设置 Glue TableInput 的 StorageDescriptor（location + columns）。
   *
   * <p>这是"尽力转换"（best-effort），仅用于通过 Glue UI/CLI 展示 Iceberg 表信息， 不应被查询引擎用于推断 schema、分区等信息。真正的 source
   * of truth 存储在 Iceberg 元数据文件中（由 metadata_location 表属性指定）。
   *
   * <p>逻辑：构建 StorageDescriptor，设置 location 和 columns，若 SDK 版本支持则设置
   * additionalLocations（写入数据/元数据的额外路径）。转换异常时仅记录警告不中断。
   *
   * @param tableInputBuilder Glue TableInput 构建器
   * @param metadata Iceberg 表元数据
   */
  static void setTableInputInformation(
      TableInput.Builder tableInputBuilder, TableMetadata metadata) {
    try {
      StorageDescriptor.Builder storageDescriptor = StorageDescriptor.builder();
      if (!SET_ADDITIONAL_LOCATIONS.isNoop()) {
        SET_ADDITIONAL_LOCATIONS.invoke(
            storageDescriptor,
            ADDITIONAL_LOCATION_PROPERTIES.stream()
                .map(metadata.properties()::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
      }

      tableInputBuilder.storageDescriptor(
          storageDescriptor.location(metadata.location()).columns(toColumns(metadata)).build());
    } catch (RuntimeException e) {
      LOG.warn(
          "Encountered unexpected exception while converting Iceberg metadata to Glue table information",
          e);
    }
  }

  /**
   * 将 Iceberg 类型映射为 Glue 可显示的类型字符串（仅用于展示，不可用于实际数据处理）。
   *
   * <p>逻辑：按 typeId 逐类型映射，如 INTEGER→int、LONG→bigint、DECIMAL→decimal(p,s)、
   * STRUCT→struct&lt;...&gt;、LIST→array&lt;...&gt;、MAP→map&lt;...,...&gt; 等， 复合类型递归调用。
   *
   * @param type Iceberg 类型
   * @return Glue 类型字符串
   */
  private static String toTypeString(Type type) {
    switch (type.typeId()) {
      case BOOLEAN:
        return "boolean";
      case INTEGER:
        return "int";
      case LONG:
        return "bigint";
      case FLOAT:
        return "float";
      case DOUBLE:
        return "double";
      case DATE:
        return "date";
      case TIME:
      case STRING:
      case UUID:
        return "string";
      case TIMESTAMP:
        return "timestamp";
      case FIXED:
      case BINARY:
        return "binary";
      case DECIMAL:
        final Types.DecimalType decimalType = (Types.DecimalType) type;
        return String.format("decimal(%s,%s)", decimalType.precision(), decimalType.scale());
      case STRUCT:
        final Types.StructType structType = type.asStructType();
        final String nameToType =
            structType.fields().stream()
                .map(f -> String.format("%s:%s", f.name(), toTypeString(f.type())))
                .collect(Collectors.joining(","));
        return String.format("struct<%s>", nameToType);
      case LIST:
        final Types.ListType listType = type.asListType();
        return String.format("array<%s>", toTypeString(listType.elementType()));
      case MAP:
        final Types.MapType mapType = type.asMapType();
        return String.format(
            "map<%s,%s>", toTypeString(mapType.keyType()), toTypeString(mapType.valueType()));
      default:
        return type.typeId().name().toLowerCase(Locale.ENGLISH);
    }
  }

  /**
   * 将 Iceberg 表元数据的 schema 转换为 Glue Column 列表（按字段名去重）。
   *
   * <p>逻辑：先添加 current schema 的字段（标记为 current=true）， 再遍历历史 schema 中的非当前字段（current=false），按字段名去重。
   *
   * @param metadata Iceberg 表元数据
   * @return Glue Column 列表
   */
  private static List<Column> toColumns(TableMetadata metadata) {
    List<Column> columns = Lists.newArrayList();
    Set<String> addedNames = Sets.newHashSet();

    for (NestedField field : metadata.schema().columns()) {
      addColumnWithDedupe(columns, addedNames, field, true /* is current */);
    }

    for (Schema schema : metadata.schemas()) {
      if (schema.schemaId() != metadata.currentSchemaId()) {
        for (NestedField field : schema.columns()) {
          addColumnWithDedupe(columns, addedNames, field, false /* is not current */);
        }
      }
    }

    return columns;
  }

  /**
   * 向列列表添加一个字段（若字段名未出现过），并附带 Iceberg 字段元信息参数。
   *
   * @param columns 列列表
   * @param dedupe 已添加字段名集合
   * @param field Iceberg 字段
   * @param isCurrent 是否属于当前 schema
   */
  private static void addColumnWithDedupe(
      List<Column> columns, Set<String> dedupe, NestedField field, boolean isCurrent) {
    if (!dedupe.contains(field.name())) {
      columns.add(
          Column.builder()
              .name(field.name())
              .type(toTypeString(field.type()))
              .comment(field.doc())
              .parameters(
                  ImmutableMap.of(
                      ICEBERG_FIELD_ID, Integer.toString(field.fieldId()),
                      ICEBERG_FIELD_OPTIONAL, Boolean.toString(field.isOptional()),
                      ICEBERG_FIELD_CURRENT, Boolean.toString(isCurrent)))
              .build());
      dedupe.add(field.name());
    }
  }
}
