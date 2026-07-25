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
package org.apache.iceberg.spark.source;

import static org.apache.iceberg.TableProperties.CURRENT_SNAPSHOT_ID;
import static org.apache.iceberg.TableProperties.FORMAT_VERSION;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.BaseMetadataTable;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFiles;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Partitioning;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.expressions.Evaluator;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionUtil;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Projections;
import org.apache.iceberg.expressions.StrictMetricsEvaluator;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.CommitMetadata;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkFilters;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.SparkUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.catalog.MetadataColumn;
import org.apache.spark.sql.connector.catalog.SupportsDelete;
import org.apache.spark.sql.connector.catalog.SupportsMetadataColumns;
import org.apache.spark.sql.connector.catalog.SupportsRead;
import org.apache.spark.sql.connector.catalog.SupportsWrite;
import org.apache.spark.sql.connector.catalog.TableCapability;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.connector.iceberg.catalog.SupportsRowLevelOperations;
import org.apache.spark.sql.connector.iceberg.write.RowLevelOperationBuilder;
import org.apache.spark.sql.connector.iceberg.write.RowLevelOperationInfo;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.write.LogicalWriteInfo;
import org.apache.spark.sql.connector.write.WriteBuilder;
import org.apache.spark.sql.sources.Filter;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkTable。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class SparkTable
    implements org.apache.spark.sql.connector.catalog.Table,
        SupportsRead,
        SupportsWrite,
        SupportsDelete,
        SupportsRowLevelOperations,
        SupportsMetadataColumns {

  private static final Logger LOG = LoggerFactory.getLogger(SparkTable.class);

  private static final Set<String> RESERVED_PROPERTIES =
      ImmutableSet.of(
          "provider",
          "format",
          CURRENT_SNAPSHOT_ID,
          "location",
          FORMAT_VERSION,
          "sort-order",
          "identifier-fields");
  private static final Set<TableCapability> CAPABILITIES =
      ImmutableSet.of(
          TableCapability.BATCH_READ,
          TableCapability.BATCH_WRITE,
          TableCapability.MICRO_BATCH_READ,
          TableCapability.STREAMING_WRITE,
          TableCapability.OVERWRITE_BY_FILTER,
          TableCapability.OVERWRITE_DYNAMIC);
  private static final Set<TableCapability> CAPABILITIES_WITH_ACCEPT_ANY_SCHEMA =
      ImmutableSet.<TableCapability>builder()
          .addAll(CAPABILITIES)
          .add(TableCapability.ACCEPT_ANY_SCHEMA)
          .build();

  private final Table icebergTable;
  private final Long snapshotId;
  private final boolean refreshEagerly;
  private final Set<TableCapability> capabilities;
  private StructType lazyTableSchema = null;
  private SparkSession lazySpark = null;

  /** 构造 SparkTable 实例。 */
  public SparkTable(Table icebergTable, boolean refreshEagerly) {
    this(icebergTable, null, refreshEagerly);
  }

  /** 构造 SparkTable 实例。 */
  public SparkTable(Table icebergTable, Long snapshotId, boolean refreshEagerly) {
    this.icebergTable = icebergTable;
    this.snapshotId = snapshotId;
    this.refreshEagerly = refreshEagerly;

    boolean acceptAnySchema =
        PropertyUtil.propertyAsBoolean(
            icebergTable.properties(),
            TableProperties.SPARK_WRITE_ACCEPT_ANY_SCHEMA,
            TableProperties.SPARK_WRITE_ACCEPT_ANY_SCHEMA_DEFAULT);
    this.capabilities = acceptAnySchema ? CAPABILITIES_WITH_ACCEPT_ANY_SCHEMA : CAPABILITIES;
  }

  /** 执行该方法的具体逻辑。 */
  private SparkSession sparkSession() {
    if (lazySpark == null) {
      this.lazySpark = SparkSession.active();
    }

    return lazySpark;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public Table table() {
    return icebergTable;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String name() {
    return icebergTable.toString();
  }

  /** 执行该方法的具体逻辑。 */
  private Schema snapshotSchema() {
    if (icebergTable instanceof BaseMetadataTable) {
      return icebergTable.schema();
    } else {
      return SnapshotUtil.schemaFor(icebergTable, snapshotId, null);
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public StructType schema() {
    if (lazyTableSchema == null) {
      this.lazyTableSchema = SparkSchemaUtil.convert(snapshotSchema());
    }

    return lazyTableSchema;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Transform[] partitioning() {
    return Spark3Util.toTransforms(icebergTable.spec());
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Map<String, String> properties() {
    ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();

    String fileFormat =
        icebergTable
            .properties()
            .getOrDefault(
                TableProperties.DEFAULT_FILE_FORMAT, TableProperties.DEFAULT_FILE_FORMAT_DEFAULT);
    propsBuilder.put("format", "iceberg/" + fileFormat);
    propsBuilder.put("provider", "iceberg");
    String currentSnapshotId =
        icebergTable.currentSnapshot() != null
            ? String.valueOf(icebergTable.currentSnapshot().snapshotId())
            : "none";
    propsBuilder.put(CURRENT_SNAPSHOT_ID, currentSnapshotId);
    propsBuilder.put("location", icebergTable.location());

    if (icebergTable instanceof BaseTable) {
      TableOperations ops = ((BaseTable) icebergTable).operations();
      propsBuilder.put(FORMAT_VERSION, String.valueOf(ops.current().formatVersion()));
    }

    if (!icebergTable.sortOrder().isUnsorted()) {
      propsBuilder.put("sort-order", Spark3Util.describe(icebergTable.sortOrder()));
    }

    Set<String> identifierFields = icebergTable.schema().identifierFieldNames();
    if (!identifierFields.isEmpty()) {
      propsBuilder.put("identifier-fields", "[" + String.join(",", identifierFields) + "]");
    }

    icebergTable.properties().entrySet().stream()
        .filter(entry -> !RESERVED_PROPERTIES.contains(entry.getKey()))
        .forEach(propsBuilder::put);

    return propsBuilder.build();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Set<TableCapability> capabilities() {
    return capabilities;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public MetadataColumn[] metadataColumns() {
    DataType sparkPartitionType = SparkSchemaUtil.convert(Partitioning.partitionType(table()));
    return new MetadataColumn[] {
      new SparkMetadataColumn(MetadataColumns.SPEC_ID.name(), DataTypes.IntegerType, false),
      new SparkMetadataColumn(MetadataColumns.PARTITION_COLUMN_NAME, sparkPartitionType, true),
      new SparkMetadataColumn(MetadataColumns.FILE_PATH.name(), DataTypes.StringType, false),
      new SparkMetadataColumn(MetadataColumns.ROW_POSITION.name(), DataTypes.LongType, false),
      new SparkMetadataColumn(MetadataColumns.IS_DELETED.name(), DataTypes.BooleanType, false)
    };
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param options 参数
   * @return 结果对象
   */
  @Override
  public ScanBuilder newScanBuilder(CaseInsensitiveStringMap options) {
    if (options.containsKey(SparkReadOptions.FILE_SCAN_TASK_SET_ID)) {
      // skip planning the job and fetch already staged file scan tasks
      /** 执行该方法的具体逻辑。 */
      return new SparkFilesScanBuilder(sparkSession(), icebergTable, options);
    }

    if (refreshEagerly) {
      icebergTable.refresh();
    }

    CaseInsensitiveStringMap scanOptions = addSnapshotId(options, snapshotId);
    /** 执行该方法的具体逻辑。 */
    return new SparkScanBuilder(sparkSession(), icebergTable, snapshotSchema(), scanOptions);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param info 参数
   * @return 结果对象
   */
  @Override
  public WriteBuilder newWriteBuilder(LogicalWriteInfo info) {
    Preconditions.checkArgument(
        snapshotId == null, "Cannot write to table at a specific snapshot: %s", snapshotId);

    /** 执行该方法的具体逻辑。 */
    return new SparkWriteBuilder(sparkSession(), icebergTable, info);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param info 参数
   * @return 结果对象
   */
  @Override
  public RowLevelOperationBuilder newRowLevelOperationBuilder(RowLevelOperationInfo info) {
    /** 执行该方法的具体逻辑。 */
    return new SparkRowLevelOperationBuilder(sparkSession(), icebergTable, info);
  }

  /** 判断是否能够deletewhere。 */
  @Override
  public boolean canDeleteWhere(Filter[] filters) {
    Preconditions.checkArgument(
        snapshotId == null, "Cannot delete from table at a specific snapshot: %s", snapshotId);

    Expression deleteExpr = Expressions.alwaysTrue();

    for (Filter filter : filters) {
      Expression expr = SparkFilters.convert(filter);
      if (expr != null) {
        deleteExpr = Expressions.and(deleteExpr, expr);
      } else {
        return false;
      }
    }

    return canDeleteUsingMetadata(deleteExpr);
  }

  // a metadata delete is possible iff matching files can be deleted entirely
  /** 判断是否能够deleteusingmetadata。 */
  private boolean canDeleteUsingMetadata(Expression deleteExpr) {
    boolean caseSensitive = SparkUtil.caseSensitive(sparkSession());

    if (ExpressionUtil.selectsPartitions(deleteExpr, table(), caseSensitive)) {
      return true;
    }

    TableScan scan =
        table()
            .newScan()
            .filter(deleteExpr)
            .caseSensitive(caseSensitive)
            .includeColumnStats()
            .ignoreResiduals();

    try (CloseableIterable<FileScanTask> tasks = scan.planFiles()) {
      Map<Integer, Evaluator> evaluators = Maps.newHashMap();
      StrictMetricsEvaluator metricsEvaluator =
          new StrictMetricsEvaluator(table().schema(), deleteExpr);

      return Iterables.all(
          tasks,
          task -> {
            DataFile file = task.file();
            PartitionSpec spec = task.spec();
            Evaluator evaluator =
                evaluators.computeIfAbsent(
                    spec.specId(),
                    specId ->
                        new Evaluator(
                            spec.partitionType(), Projections.strict(spec).project(deleteExpr)));
            return evaluator.eval(file.partition()) || metricsEvaluator.eval(file);
          });

    } catch (IOException ioe) {
      LOG.warn("Failed to close task iterable", ioe);
      return false;
    }
  }

  /**
   * 删除数据或文件。
   *
   * @param filters 参数
   */
  @Override
  public void deleteWhere(Filter[] filters) {
    Expression deleteExpr = SparkFilters.convert(filters);

    if (deleteExpr == Expressions.alwaysFalse()) {
      LOG.info("Skipping the delete operation as the condition is always false");
      return;
    }

    DeleteFiles deleteFiles =
        icebergTable
            .newDelete()
            .set("spark.app.id", sparkSession().sparkContext().applicationId())
            .deleteFromRowFilter(deleteExpr);

    if (!CommitMetadata.commitProperties().isEmpty()) {
      CommitMetadata.commitProperties().forEach(deleteFiles::set);
    }

    deleteFiles.commit();
  }

  /** 返回该对象的字符串表示。 */
  @Override
  public String toString() {
    return icebergTable.toString();
  }

  /** 判断是否与给定对象相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null || getClass() != other.getClass()) {
      return false;
    }

    // use only name in order to correctly invalidate Spark cache
    SparkTable that = (SparkTable) other;
    return icebergTable.name().equals(that.icebergTable.name());
  }

  /** 返回该对象的哈希码。 */
  @Override
  public int hashCode() {
    // use only name in order to correctly invalidate Spark cache
    return icebergTable.name().hashCode();
  }

  /** 添加元素或项。 */
  private static CaseInsensitiveStringMap addSnapshotId(
      CaseInsensitiveStringMap options, Long snapshotId) {
    if (snapshotId != null) {
      String snapshotIdFromOptions = options.get(SparkReadOptions.SNAPSHOT_ID);
      String value = snapshotId.toString();
      Preconditions.checkArgument(
          snapshotIdFromOptions == null || snapshotIdFromOptions.equals(value),
          "Cannot override snapshot ID more than once: %s",
          snapshotIdFromOptions);

      Map<String, String> scanOptions = Maps.newHashMap();
      scanOptions.putAll(options.asCaseSensitiveMap());
      scanOptions.put(SparkReadOptions.SNAPSHOT_ID, value);
      scanOptions.remove(SparkReadOptions.AS_OF_TIMESTAMP);

      /** 执行该方法的具体逻辑。 */
      return new CaseInsensitiveStringMap(scanOptions);
    }

    return options;
  }
}
