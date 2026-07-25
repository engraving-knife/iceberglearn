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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.iceberg.IncrementalChangelogScan;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Snapshot;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.Binder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkFilters;
import org.apache.iceberg.spark.SparkReadConf;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.SnapshotUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.connector.read.Scan;
import org.apache.spark.sql.connector.read.ScanBuilder;
import org.apache.spark.sql.connector.read.Statistics;
import org.apache.spark.sql.connector.read.SupportsPushDownFilters;
import org.apache.spark.sql.connector.read.SupportsPushDownRequiredColumns;
import org.apache.spark.sql.connector.read.SupportsReportStatistics;
import org.apache.spark.sql.sources.Filter;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的构建器，负责分步骤构造目标对象。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkScanBuilder。
 *
 * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
public class SparkScanBuilder
    implements ScanBuilder,
        SupportsPushDownFilters,
        SupportsPushDownRequiredColumns,
        SupportsReportStatistics {

  private static final Logger LOG = LoggerFactory.getLogger(SparkScanBuilder.class);
  private static final Filter[] NO_FILTERS = new Filter[0];

  private final SparkSession spark;
  private final Table table;
  private final CaseInsensitiveStringMap options;
  private final SparkReadConf readConf;
  private final List<String> metaColumns = Lists.newArrayList();

  private Schema schema = null;
  private boolean caseSensitive;
  private List<Expression> filterExpressions = null;
  private Filter[] pushedFilters = NO_FILTERS;

  SparkScanBuilder(
      SparkSession spark, Table table, Schema schema, CaseInsensitiveStringMap options) {
    this.spark = spark;
    this.table = table;
    this.schema = schema;
    this.options = options;
    this.readConf = new SparkReadConf(spark, table, options);
    this.caseSensitive = readConf.caseSensitive();
  }

  SparkScanBuilder(SparkSession spark, Table table, CaseInsensitiveStringMap options) {
    this(spark, table, table.schema(), options);
  }

  /** 按条件过滤。 */
  private Expression filterExpression() {
    if (filterExpressions != null) {
      return filterExpressions.stream().reduce(Expressions.alwaysTrue(), Expressions::and);
    }
    return Expressions.alwaysTrue();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param isCaseSensitive 参数
   * @return 结果对象
   */
  public SparkScanBuilder caseSensitive(boolean isCaseSensitive) {
    this.caseSensitive = isCaseSensitive;
    return this;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param filters 参数
   * @return 结果对象
   */
  @Override
  public Filter[] pushFilters(Filter[] filters) {
    List<Expression> expressions = Lists.newArrayListWithExpectedSize(filters.length);
    List<Filter> pushed = Lists.newArrayListWithExpectedSize(filters.length);

    for (Filter filter : filters) {
      Expression expr = null;
      try {
        expr = SparkFilters.convert(filter);
      } catch (IllegalArgumentException e) {
        // converting to Iceberg Expression failed, so this expression cannot be pushed down
        LOG.info(
            "Failed to convert filter to Iceberg expression, skipping push down for this expression: {}. {}",
            filter,
            e.getMessage());
      }

      if (expr != null) {
        try {
          Binder.bind(schema.asStruct(), expr, caseSensitive);
          expressions.add(expr);
          pushed.add(filter);
        } catch (ValidationException e) {
          // binding to the table schema failed, so this expression cannot be pushed down
          LOG.info(
              "Failed to bind expression to table schema, skipping push down for this expression: {}. {}",
              filter,
              e.getMessage());
        }
      }
    }

    this.filterExpressions = expressions;
    this.pushedFilters = pushed.toArray(new Filter[0]);

    // Spark doesn't support residuals per task, so return all filters
    // to get Spark to handle record-level filtering
    return filters;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Filter[] pushedFilters() {
    return pushedFilters;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param requestedSchema 参数
   */
  @Override
  public void pruneColumns(StructType requestedSchema) {
    StructType requestedProjection =
        new StructType(
            Stream.of(requestedSchema.fields())
                .filter(field -> MetadataColumns.nonMetadataColumn(field.name()))
                .toArray(StructField[]::new));

    // the projection should include all columns that will be returned, including those only used in
    // filters
    this.schema =
        SparkSchemaUtil.prune(schema, requestedProjection, filterExpression(), caseSensitive);

    Stream.of(requestedSchema.fields())
        .map(StructField::name)
        .filter(MetadataColumns::isMetadataColumn)
        .distinct()
        .forEach(metaColumns::add);
  }

  /** 执行该方法的具体逻辑。 */
  private Schema schemaWithMetadataColumns() {
    // metadata columns
    List<Types.NestedField> fields =
        metaColumns.stream()
            .distinct()
            .map(name -> MetadataColumns.metadataColumn(table, name))
            .collect(Collectors.toList());
    Schema meta = new Schema(fields);

    // schema or rows returned by readers
    return TypeUtil.join(schema, meta);
  }

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  @Override
  public Scan build() {
    Long snapshotId = readConf.snapshotId();
    Long asOfTimestamp = readConf.asOfTimestamp();

    Preconditions.checkArgument(
        snapshotId == null || asOfTimestamp == null,
        "Cannot set both %s and %s to select which table snapshot to scan",
        SparkReadOptions.SNAPSHOT_ID,
        SparkReadOptions.AS_OF_TIMESTAMP);

    Long startSnapshotId = readConf.startSnapshotId();
    Long endSnapshotId = readConf.endSnapshotId();

    if (snapshotId != null || asOfTimestamp != null) {
      Preconditions.checkArgument(
          startSnapshotId == null && endSnapshotId == null,
          "Cannot set %s and %s for incremental scans when either %s or %s is set",
          SparkReadOptions.START_SNAPSHOT_ID,
          SparkReadOptions.END_SNAPSHOT_ID,
          SparkReadOptions.SNAPSHOT_ID,
          SparkReadOptions.AS_OF_TIMESTAMP);
    }

    Preconditions.checkArgument(
        startSnapshotId != null || endSnapshotId == null,
        "Cannot set only %s for incremental scans. Please, set %s too.",
        SparkReadOptions.END_SNAPSHOT_ID,
        SparkReadOptions.START_SNAPSHOT_ID);

    Long startTimestamp = readConf.startTimestamp();
    Long endTimestamp = readConf.endTimestamp();
    Preconditions.checkArgument(
        startTimestamp == null && endTimestamp == null,
        "Cannot set %s or %s for incremental scans and batch scan. They are only valid for "
            + "changelog scans.",
        SparkReadOptions.START_TIMESTAMP,
        SparkReadOptions.END_TIMESTAMP);

    Schema expectedSchema = schemaWithMetadataColumns();

    TableScan scan =
        table
            .newScan()
            .caseSensitive(caseSensitive)
            .filter(filterExpression())
            .project(expectedSchema);

    if (snapshotId != null) {
      scan = scan.useSnapshot(snapshotId);
    }

    if (asOfTimestamp != null) {
      scan = scan.asOfTime(asOfTimestamp);
    }

    if (startSnapshotId != null) {
      if (endSnapshotId != null) {
        scan = scan.appendsBetween(startSnapshotId, endSnapshotId);
      } else {
        scan = scan.appendsAfter(startSnapshotId);
      }
    }

    scan = configureSplitPlanning(scan);

    /** 执行该方法的具体逻辑。 */
    return new SparkBatchQueryScan(spark, table, scan, readConf, expectedSchema, filterExpressions);
  }

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  @SuppressWarnings("CyclomaticComplexity")
  public Scan buildChangelogScan() {
    Preconditions.checkArgument(
        readConf.snapshotId() == null && readConf.asOfTimestamp() == null,
        "Cannot set neither %s nor %s for changelogs",
        SparkReadOptions.SNAPSHOT_ID,
        SparkReadOptions.AS_OF_TIMESTAMP);

    Long startSnapshotId = readConf.startSnapshotId();
    Long endSnapshotId = readConf.endSnapshotId();
    Long startTimestamp = readConf.startTimestamp();
    Long endTimestamp = readConf.endTimestamp();

    Preconditions.checkArgument(
        !(startSnapshotId != null && startTimestamp != null),
        "Cannot set both %s and %s for changelogs",
        SparkReadOptions.START_SNAPSHOT_ID,
        SparkReadOptions.START_TIMESTAMP);

    Preconditions.checkArgument(
        !(endSnapshotId != null && endTimestamp != null),
        "Cannot set both %s and %s for changelogs",
        SparkReadOptions.END_SNAPSHOT_ID,
        SparkReadOptions.END_TIMESTAMP);

    if (startTimestamp != null && endTimestamp != null) {
      Preconditions.checkArgument(
          startTimestamp < endTimestamp,
          "Cannot set %s to be greater than %s for changelogs",
          SparkReadOptions.START_TIMESTAMP,
          SparkReadOptions.END_TIMESTAMP);
    }

    boolean emptyScan = false;
    if (startTimestamp != null) {
      startSnapshotId = getStartSnapshotId(startTimestamp);
      if (startSnapshotId == null && endTimestamp == null) {
        emptyScan = true;
      }
    }

    if (endTimestamp != null) {
      endSnapshotId = SnapshotUtil.nullableSnapshotIdAsOfTime(table, endTimestamp);
      if ((startSnapshotId == null && endSnapshotId == null)
          || (startSnapshotId != null && startSnapshotId.equals(endSnapshotId))) {
        emptyScan = true;
      }
    }

    Schema expectedSchema = schemaWithMetadataColumns();

    IncrementalChangelogScan scan =
        table
            .newIncrementalChangelogScan()
            .caseSensitive(caseSensitive)
            .filter(filterExpression())
            .project(expectedSchema);

    if (startSnapshotId != null) {
      scan = scan.fromSnapshotExclusive(startSnapshotId);
    }

    if (endSnapshotId != null) {
      scan = scan.toSnapshot(endSnapshotId);
    }

    scan = configureSplitPlanning(scan);

    /** 执行该方法的具体逻辑。 */
    return new SparkChangelogScan(
        spark, table, scan, readConf, expectedSchema, filterExpressions, emptyScan);
  }

  /** 返回startsnapshotid。 */
  private Long getStartSnapshotId(Long startTimestamp) {
    Snapshot oldestSnapshotAfter = SnapshotUtil.oldestAncestorAfter(table, startTimestamp);

    if (oldestSnapshotAfter == null) {
      return null;
    } else if (oldestSnapshotAfter.timestampMillis() == startTimestamp) {
      return oldestSnapshotAfter.snapshotId();
    } else {
      return oldestSnapshotAfter.parentId();
    }
  }

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  public Scan buildMergeOnReadScan() {
    Preconditions.checkArgument(
        readConf.snapshotId() == null && readConf.asOfTimestamp() == null,
        "Cannot set time travel options %s and %s for row-level command scans",
        SparkReadOptions.SNAPSHOT_ID,
        SparkReadOptions.AS_OF_TIMESTAMP);

    Preconditions.checkArgument(
        readConf.startSnapshotId() == null && readConf.endSnapshotId() == null,
        "Cannot set incremental scan options %s and %s for row-level command scans",
        SparkReadOptions.START_SNAPSHOT_ID,
        SparkReadOptions.END_SNAPSHOT_ID);

    Snapshot snapshot = table.currentSnapshot();

    if (snapshot == null) {
      /** 执行该方法的具体逻辑。 */
      return new SparkBatchQueryScan(
          spark, table, null, readConf, schemaWithMetadataColumns(), filterExpressions);
    }

    // remember the current snapshot ID for commit validation
    long snapshotId = snapshot.snapshotId();

    CaseInsensitiveStringMap adjustedOptions =
        Spark3Util.setOption(SparkReadOptions.SNAPSHOT_ID, Long.toString(snapshotId), options);
    SparkReadConf adjustedReadConf = new SparkReadConf(spark, table, adjustedOptions);

    Schema expectedSchema = schemaWithMetadataColumns();

    TableScan scan =
        table
            .newScan()
            .useSnapshot(snapshotId)
            .caseSensitive(caseSensitive)
            .filter(filterExpression())
            .project(expectedSchema);

    scan = configureSplitPlanning(scan);

    /** 执行该方法的具体逻辑。 */
    return new SparkBatchQueryScan(
        spark, table, scan, adjustedReadConf, expectedSchema, filterExpressions);
  }

  /**
   * 构造并返回目标对象。
   *
   * @return 结果对象
   */
  public Scan buildCopyOnWriteScan() {
    Snapshot snapshot = table.currentSnapshot();

    if (snapshot == null) {
      /** 执行该方法的具体逻辑。 */
      return new SparkCopyOnWriteScan(
          spark, table, readConf, schemaWithMetadataColumns(), filterExpressions);
    }

    Schema expectedSchema = schemaWithMetadataColumns();

    TableScan scan =
        table
            .newScan()
            .useSnapshot(snapshot.snapshotId())
            .ignoreResiduals()
            .caseSensitive(caseSensitive)
            .filter(filterExpression())
            .project(expectedSchema);

    scan = configureSplitPlanning(scan);

    /** 执行该方法的具体逻辑。 */
    return new SparkCopyOnWriteScan(
        spark, table, scan, snapshot, readConf, expectedSchema, filterExpressions);
  }

  /** 执行该方法的具体逻辑。 */
  private <T extends org.apache.iceberg.Scan<T, ?, ?>> T configureSplitPlanning(T scan) {
    T configuredScan = scan;

    Long splitSize = readConf.splitSizeOption();
    if (splitSize != null) {
      configuredScan = configuredScan.option(TableProperties.SPLIT_SIZE, String.valueOf(splitSize));
    }

    Integer splitLookback = readConf.splitLookbackOption();
    if (splitLookback != null) {
      configuredScan =
          configuredScan.option(TableProperties.SPLIT_LOOKBACK, String.valueOf(splitLookback));
    }

    Long splitOpenFileCost = readConf.splitOpenFileCostOption();
    if (splitOpenFileCost != null) {
      configuredScan =
          configuredScan.option(
              TableProperties.SPLIT_OPEN_FILE_COST, String.valueOf(splitOpenFileCost));
    }

    return configuredScan;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Statistics estimateStatistics() {
    return ((SupportsReportStatistics) build()).estimateStatistics();
  }

  /**
   * 读取数据。
   *
   * @return 结果对象
   */
  @Override
  public StructType readSchema() {
    return build().readSchema();
  }
}
