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

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.UnknownTransform;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.Pair;
import org.apache.spark.sql.RuntimeConfig;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.expressions.BoundReference;
import org.apache.spark.sql.catalyst.expressions.EqualTo;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Literal;
import org.apache.spark.sql.connector.expressions.NamedReference;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.joda.time.DateTime;

/**
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkUtil。
 */
public class SparkUtil {

  public static final String TIMESTAMP_WITHOUT_TIMEZONE_ERROR =
      String.format(
          "Cannot handle timestamp without"
              + " timezone fields in Spark. Spark does not natively support this type but if you would like to handle all"
              + " timestamps as timestamp with timezone set '%s' to true. This will not change the underlying values stored"
              + " but will change their displayed values in Spark. For more information please see"
              + " https://docs.databricks.com/spark/latest/dataframes-datasets/dates-timestamps.html#ansi-sql-and"
              + "-spark-sql-timestamps",
          SparkSQLProperties.HANDLE_TIMESTAMP_WITHOUT_TIMEZONE);

  private static final String SPARK_CATALOG_CONF_PREFIX = "spark.sql.catalog";
  // Format string used as the prefix for Spark configuration keys to override Hadoop configuration
  // values for Iceberg tables from a given catalog. These keys can be specified as
  // `spark.sql.catalog.$catalogName.hadoop.*`, similar to using `spark.hadoop.*` to override
  // Hadoop configurations globally for a given Spark session.
  private static final String SPARK_CATALOG_HADOOP_CONF_OVERRIDE_FMT_STR =
      SPARK_CATALOG_CONF_PREFIX + ".%s.hadoop.";

  private static final Joiner DOT = Joiner.on(".");

  /** 构造 SparkUtil 实例。 */
  private SparkUtil() {}

  /** 校验前置条件或参数。 */
  public static void validatePartitionTransforms(PartitionSpec spec) {
    if (spec.fields().stream().anyMatch(field -> field.transform() instanceof UnknownTransform)) {
      String unsupported =
          spec.fields().stream()
              .map(PartitionField::transform)
              .filter(transform -> transform instanceof UnknownTransform)
              .map(Transform::toString)
              .collect(Collectors.joining(", "));

      throw new UnsupportedOperationException(
          String.format("Cannot write using unsupported transforms: %s", unsupported));
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param nameParts 参数
   * @param catalogProvider 参数
   * @param identiferProvider 参数
   * @param currentCatalog 参数
   * @param currentNamespace 参数
   * @return 结果对象
   */
  public static <C, T> Pair<C, T> catalogAndIdentifier(
      List<String> nameParts,
      Function<String, C> catalogProvider,
      BiFunction<String[], String, T> identiferProvider,
      C currentCatalog,
      String[] currentNamespace) {
    Preconditions.checkArgument(
        !nameParts.isEmpty(), "Cannot determine catalog and identifier from empty name");

    int lastElementIndex = nameParts.size() - 1;
    String name = nameParts.get(lastElementIndex);

    if (nameParts.size() == 1) {
      // Only a single element, use current catalog and namespace
      return Pair.of(currentCatalog, identiferProvider.apply(currentNamespace, name));
    } else {
      C catalog = catalogProvider.apply(nameParts.get(0));
      if (catalog == null) {
        // The first element was not a valid catalog, treat it like part of the namespace
        String[] namespace = nameParts.subList(0, lastElementIndex).toArray(new String[0]);
        return Pair.of(currentCatalog, identiferProvider.apply(namespace, name));
      } else {
        // Assume the first element is a valid catalog
        String[] namespace = nameParts.subList(1, lastElementIndex).toArray(new String[0]);
        return Pair.of(catalog, identiferProvider.apply(namespace, name));
      }
    }
  }

  /** 判断是否包含timestampwithoutzone。 */
  public static boolean hasTimestampWithoutZone(Schema schema) {
    return TypeUtil.find(schema, t -> Types.TimestampType.withoutZone().equals(t)) != null;
  }

  /** 执行该方法的具体逻辑。 */
  public static boolean useTimestampWithoutZoneInNewTables(RuntimeConfig sessionConf) {
    String sessionConfValue =
        sessionConf.get(SparkSQLProperties.USE_TIMESTAMP_WITHOUT_TIME_ZONE_IN_NEW_TABLES, null);
    if (sessionConfValue != null) {
      return Boolean.parseBoolean(sessionConfValue);
    }
    return SparkSQLProperties.USE_TIMESTAMP_WITHOUT_TIME_ZONE_IN_NEW_TABLES_DEFAULT;
  }

  /** 执行该方法的具体逻辑。 */
  public static Configuration hadoopConfCatalogOverrides(SparkSession spark, String catalogName) {
    // Find keys for the catalog intended to be hadoop configurations
    final String hadoopConfCatalogPrefix = hadoopConfPrefixForCatalog(catalogName);
    final Configuration conf = spark.sessionState().newHadoopConf();
    spark
        .sqlContext()
        .conf()
        .settings()
        .forEach(
            (k, v) -> {
              // these checks are copied from `spark.sessionState().newHadoopConfWithOptions()`
              // to avoid converting back and forth between Scala / Java map types
              if (v != null && k != null && k.startsWith(hadoopConfCatalogPrefix)) {
                conf.set(k.substring(hadoopConfCatalogPrefix.length()), v);
              }
            });
    return conf;
  }

  /** 执行该方法的具体逻辑。 */
  private static String hadoopConfPrefixForCatalog(String catalogName) {
    return String.format(SPARK_CATALOG_HADOOP_CONF_OVERRIDE_FMT_STR, catalogName);
  }

  /** 执行该方法的具体逻辑。 */
  public static List<Expression> partitionMapToExpression(
      StructType schema, Map<String, String> filters) {
    List<Expression> filterExpressions = Lists.newArrayList();
    for (Map.Entry<String, String> entry : filters.entrySet()) {
      try {
        int index = schema.fieldIndex(entry.getKey());
        DataType dataType = schema.fields()[index].dataType();
        BoundReference ref = new BoundReference(index, dataType, true);
        switch (dataType.typeName()) {
          case "integer":
            filterExpressions.add(
                new EqualTo(
                    ref,
                    Literal.create(Integer.parseInt(entry.getValue()), DataTypes.IntegerType)));
            break;
          case "string":
            filterExpressions.add(
                new EqualTo(ref, Literal.create(entry.getValue(), DataTypes.StringType)));
            break;
          case "short":
            filterExpressions.add(
                new EqualTo(
                    ref, Literal.create(Short.parseShort(entry.getValue()), DataTypes.ShortType)));
            break;
          case "long":
            filterExpressions.add(
                new EqualTo(
                    ref, Literal.create(Long.parseLong(entry.getValue()), DataTypes.LongType)));
            break;
          case "float":
            filterExpressions.add(
                new EqualTo(
                    ref, Literal.create(Float.parseFloat(entry.getValue()), DataTypes.FloatType)));
            break;
          case "double":
            filterExpressions.add(
                new EqualTo(
                    ref,
                    Literal.create(Double.parseDouble(entry.getValue()), DataTypes.DoubleType)));
            break;
          case "date":
            filterExpressions.add(
                new EqualTo(
                    ref,
                    Literal.create(
                        new Date(DateTime.parse(entry.getValue()).getMillis()),
                        DataTypes.DateType)));
            break;
          case "timestamp":
            filterExpressions.add(
                new EqualTo(
                    ref,
                    Literal.create(
                        new Timestamp(DateTime.parse(entry.getValue()).getMillis()),
                        DataTypes.TimestampType)));
            break;
          default:
            throw new IllegalStateException(
                "Unexpected data type in partition filters: " + dataType);
        }
      } catch (IllegalArgumentException e) {
        // ignore if filter is not on table columns
      }
    }

    return filterExpressions;
  }

  /** 转换为columnname。 */
  public static String toColumnName(NamedReference ref) {
    return DOT.join(ref.fieldNames());
  }

  /** 执行该方法的具体逻辑。 */
  public static boolean caseSensitive(SparkSession spark) {
    return Boolean.parseBoolean(spark.conf().get("spark.sql.caseSensitive"));
  }
}
