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
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.UnknownTransform;
import org.apache.iceberg.util.Pair;
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
 * Spark 集成通用工具类。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>校验分区 transform 是否可用于写入（拒绝 UnknownTransform）。
 *   <li>从多段标识符解析 catalog 与 identifier（模仿 Spark LookupCatalog）。
 *   <li>从 SparkSession 提取按 catalog 覆盖的 Hadoop 配置（{@code spark.sql.catalog.$name.hadoop.*}）。
 *   <li>把分区 Map 过滤器转为 Spark {@link Expression} 列表；提供列名/大小写敏感工具方法。
 * </ul>
 *
 * <p>设计意图：把与 Spark 版本相关或易错的解析逻辑集中托管，便于跨版本维护； Hadoop 配置覆盖镜像了 Spark 全局 {@code spark.hadoop.*}
 * 机制，但限定到单个 catalog。
 *
 * <p>上下游关系：被 Spark catalog、扫描、写入等多处调用；依赖 Spark Catalyst 与 Hadoop Configuration。
 */
public class SparkUtil {
  private static final String SPARK_CATALOG_CONF_PREFIX = "spark.sql.catalog";
  // Format string used as the prefix for Spark configuration keys to override Hadoop configuration
  // values for Iceberg tables from a given catalog. These keys can be specified as
  // `spark.sql.catalog.$catalogName.hadoop.*`, similar to using `spark.hadoop.*` to override
  // Hadoop configurations globally for a given Spark session.
  private static final String SPARK_CATALOG_HADOOP_CONF_OVERRIDE_FMT_STR =
      SPARK_CATALOG_CONF_PREFIX + ".%s.hadoop.";

  private static final Joiner DOT = Joiner.on(".");

  private SparkUtil() {}

  /**
   * 校验分区规格中的 transform 是否都可用于写入。
   *
   * <p>逻辑：若存在 {@link UnknownTransform}，收集其描述并抛出 UnsupportedOperationException。
   *
   * @param spec 分区规格
   * @throws UnsupportedOperationException 含未知 transform 时抛出
   */
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
   * 从多段标识符解析 catalog 与 identifier（模仿 Spark LookupCatalog.CatalogAndIdentifier.unapply）。
   *
   * <p>逻辑：单段时用当前 catalog 与 namespace；多段时尝试把首段当作 catalog 名， 命中则用该 catalog + 后续段为 namespace，未命中则首段作为
   * namespace 一部分。
   *
   * @param nameParts 多段标识符
   * @param catalogProvider 按名称获取 catalog 的函数
   * @param identiferProvider 按 namespace+name 构造 identifier 的函数
   * @param currentCatalog 当前默认 catalog
   * @param currentNamespace 当前默认 namespace
   * @param <C> catalog 类型
   * @param <T> identifier 类型
   * @return catalog 与 identifier 的 Pair
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

  /**
   * 从 SparkSession 提取指定 catalog 的 Hadoop 配置覆盖。
   *
   * <p>逻辑：构造前缀 {@code spark.sql.catalog.$catalogName.hadoop.}，遍历 Spark SQLConf 设置， 把匹配前缀的键去掉前缀后设置到新
   * Hadoop Configuration。
   *
   * <p>镜像 Spark 全局 {@code spark.hadoop.*} 覆盖机制，但限定到单个 catalog。
   *
   * @param spark SparkSession
   * @param catalogName catalog 名
   * @return 应用了 catalog 覆盖的 Hadoop Configuration
   */
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
  /** 执行 hadoopConfPrefixForCatalog 相关操作。 */
  private static String hadoopConfPrefixForCatalog(String catalogName) {
    return String.format(SPARK_CATALOG_HADOOP_CONF_OVERRIDE_FMT_STR, catalogName);
  }

  /**
   * 把分区 Map 过滤器转为 Spark {@link Expression} 列表。
   *
   * <p>逻辑：对每个 entry，按列名查 schema 得到类型，构造 BoundReference 与对应类型 Literal， 组成 EqualTo
   * 表达式；按类型分支解析值；非表列的过滤忽略。
   *
   * @param schema 表 schema
   * @param filters 列名到值的映射
   * @return Spark Expression 列表
   */
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

  /** 把 Spark {@link NamedReference} 的字段名数组用点号拼接为列名字符串。 */
  public static String toColumnName(NamedReference ref) {
    return DOT.join(ref.fieldNames());
  }

  /** 读取 Spark 配置 spark.sql.caseSensitive，返回是否大小写敏感。 */
  public static boolean caseSensitive(SparkSession spark) {
    return Boolean.parseBoolean(spark.conf().get("spark.sql.caseSensitive"));
  }
}
