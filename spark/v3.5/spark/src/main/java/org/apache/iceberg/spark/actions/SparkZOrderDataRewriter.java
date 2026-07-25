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
package org.apache.iceberg.spark.actions;

import static org.apache.spark.sql.functions.array;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.NullOrder;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortDirection;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.SparkUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.ZOrderByteUtils;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Spark 的 Z-Order 数据重写器，通过 Z-Order 排序优化数据文件的聚簇布局。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层）。
 *
 * <p>职责：对表数据按多个列的 Z-Order 值排序后重写，使多维度范围查询能更高效地 跳过不相关的数据文件，减少扫描数据量。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link SparkShufflingDataRewriter} 复用 Spark shuffle 排序重写框架。
 *   <li>将多列值通过 {@link SparkZOrderUDF} 编码为单个二进制 Z 值（字节交错）， 以该值排序即可实现多维数据的空间聚簇。
 *   <li>支持配置 max-output-size（Z 值输出字节数）和 var-length-contribution （变长类型参与 Z-Order 的字节数），在精度与排序开销间权衡。
 *   <li>自动排除分区列（分区内值恒定，参与 Z-Order 无意义）。
 * </ul>
 *
 * <p>上下游关系：被 RewriteDataFilesSparkAction 在 sort-order 为 zorder 时调用； 依赖 Spark 的 shuffle 排序能力与
 * SparkZOrderUDF。
 */
class SparkZOrderDataRewriter extends SparkShufflingDataRewriter {

  private static final Logger LOG = LoggerFactory.getLogger(SparkZOrderDataRewriter.class);

  private static final String Z_COLUMN = "ICEZVALUE";
  private static final Schema Z_SCHEMA =
      new Schema(Types.NestedField.required(0, Z_COLUMN, Types.BinaryType.get()));
  private static final SortOrder Z_SORT_ORDER =
      SortOrder.builderFor(Z_SCHEMA)
          .sortBy(Z_COLUMN, SortDirection.ASC, NullOrder.NULLS_LAST)
          .build();

  /** 控制 Z-Order 算法中交错的字节数。默认值为 {@link #MAX_OUTPUT_SIZE_DEFAULT}， 即所有字节都参与交错。值越小，Z 值越短、排序越快但精度越低。 */
  public static final String MAX_OUTPUT_SIZE = "max-output-size";

  public static final int MAX_OUTPUT_SIZE_DEFAULT = Integer.MAX_VALUE;

  /**
   * 控制变长类型（String、Binary）列参与 Z-Order 时考虑的字节数。
   *
   * <p>默认使用与定长类型相同的字节数 {@link ZOrderByteUtils#PRIMITIVE_BUFFER_SIZE}。
   */
  public static final String VAR_LENGTH_CONTRIBUTION = "var-length-contribution";

  public static final int VAR_LENGTH_CONTRIBUTION_DEFAULT = ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE;

  private final List<String> zOrderColNames;
  private int maxOutputSize;
  private int varLengthContribution;

  SparkZOrderDataRewriter(SparkSession spark, Table table, List<String> zOrderColNames) {
    super(spark, table);
    this.zOrderColNames = validZOrderColNames(spark, table, zOrderColNames);
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return "Z-ORDER";
  }
  /** 执行 validOptions 相关操作。 */
  @Override
  public Set<String> validOptions() {
    return ImmutableSet.<String>builder()
        .addAll(super.validOptions())
        .add(MAX_OUTPUT_SIZE)
        .add(VAR_LENGTH_CONTRIBUTION)
        .build();
  }
  /** 初始化。 */
  @Override
  public void init(Map<String, String> options) {
    super.init(options);
    this.maxOutputSize = maxOutputSize(options);
    this.varLengthContribution = varLengthContribution(options);
  }
  /** 执行 sortOrder 相关操作。 */
  @Override
  protected SortOrder sortOrder() {
    return Z_SORT_ORDER;
  }

  /**
   * 在数据集中添加 Z 值列，应用排序函数后移除该列。
   *
   * <p>逻辑：通过 withColumn 添加 ICEZVALUE 列（由 zValue 计算得出）， 应用父类的排序函数对该列排序，最后 drop 掉 Z 值列使输出 schema
   * 不含临时列。
   *
   * @param df 输入数据集
   * @param sortFunc 排序函数
   * @return 排序后的数据集（不含 Z 值列）
   */
  @Override
  protected Dataset<Row> sortedDF(Dataset<Row> df, Function<Dataset<Row>, Dataset<Row>> sortFunc) {
    Dataset<Row> zValueDF = df.withColumn(Z_COLUMN, zValue(df));
    Dataset<Row> sortedDF = sortFunc.apply(zValueDF);
    return sortedDF.drop(Z_COLUMN);
  }

  /**
   * 计算 Z-Order 值列表达式：对每个 Z-Order 列做字典序编码后交错字节。
   *
   * <p>逻辑：创建 SparkZOrderUDF，对每列调用 sortedLexicographically 转为定长字节表示， 然后通过 interleaveBytes
   * 将各列字节交错拼接为最终的二进制 Z 值。
   *
   * @param df 输入数据集
   * @return Z 值列表达式
   */
  private Column zValue(Dataset<Row> df) {
    SparkZOrderUDF zOrderUDF =
        new SparkZOrderUDF(zOrderColNames.size(), varLengthContribution, maxOutputSize);

    Column[] zOrderCols =
        zOrderColNames.stream()
            .map(df.schema()::apply)
            .map(col -> zOrderUDF.sortedLexicographically(df.col(col.name()), col.dataType()))
            .toArray(Column[]::new);

    return zOrderUDF.interleaveBytes(array(zOrderCols));
  }
  /** 执行 varLengthContribution 相关操作。 */
  private int varLengthContribution(Map<String, String> options) {
    int value =
        PropertyUtil.propertyAsInt(
            options, VAR_LENGTH_CONTRIBUTION, VAR_LENGTH_CONTRIBUTION_DEFAULT);
    Preconditions.checkArgument(
        value > 0,
        "Cannot use less than 1 byte for variable length types with ZOrder, '%s' was set to %s",
        VAR_LENGTH_CONTRIBUTION,
        value);
    return value;
  }
  /** 执行 maxOutputSize 相关操作。 */
  private int maxOutputSize(Map<String, String> options) {
    int value = PropertyUtil.propertyAsInt(options, MAX_OUTPUT_SIZE, MAX_OUTPUT_SIZE_DEFAULT);
    Preconditions.checkArgument(
        value > 0,
        "Cannot have the interleaved ZOrder value use less than 1 byte, '%s' was set to %s",
        MAX_OUTPUT_SIZE,
        value);
    return value;
  }

  /**
   * 校验并过滤出有效的 Z-Order 列名。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验输入列名列表非空。
   *   <li>根据 Spark 的大小写敏感设置在表 schema 中查找每列，不存在则报错。
   *   <li>排除分区列（恒定值参与 Z-Order 无意义，仅记录警告）。
   *   <li>校验过滤后至少剩一列可用。
   * </ol>
   *
   * @param spark SparkSession 实例
   * @param table 目标 Iceberg 表
   * @param inputZOrderColNames 用户指定的 Z-Order 列名
   * @return 过滤后的有效列名列表
   */
  private List<String> validZOrderColNames(
      SparkSession spark, Table table, List<String> inputZOrderColNames) {

    Preconditions.checkArgument(
        inputZOrderColNames != null && !inputZOrderColNames.isEmpty(),
        "Cannot ZOrder when no columns are specified");

    Schema schema = table.schema();
    Set<Integer> identityPartitionFieldIds = table.spec().identitySourceIds();
    boolean caseSensitive = SparkUtil.caseSensitive(spark);

    List<String> validZOrderColNames = Lists.newArrayList();

    for (String colName : inputZOrderColNames) {
      Types.NestedField field =
          caseSensitive ? schema.findField(colName) : schema.caseInsensitiveFindField(colName);
      Preconditions.checkArgument(
          field != null,
          "Cannot find column '%s' in table schema (case sensitive = %s): %s",
          colName,
          caseSensitive,
          schema.asStruct());

      if (identityPartitionFieldIds.contains(field.fieldId())) {
        LOG.warn("Ignoring '{}' as such values are constant within a partition", colName);
      } else {
        validZOrderColNames.add(colName);
      }
    }

    Preconditions.checkArgument(
        validZOrderColNames.size() > 0,
        "Cannot ZOrder, all columns provided were identity partition columns and cannot be used");

    return validZOrderColNames;
  }
}
