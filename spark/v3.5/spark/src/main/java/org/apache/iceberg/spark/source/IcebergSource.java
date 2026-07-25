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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.spark.PathIdentifier;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.SparkCachedTableCatalog;
import org.apache.iceberg.spark.SparkCatalog;
import org.apache.iceberg.spark.SparkReadOptions;
import org.apache.iceberg.spark.SparkSessionCatalog;
import org.apache.iceberg.spark.SparkTableCache;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.analysis.NoSuchTableException;
import org.apache.spark.sql.connector.catalog.CatalogManager;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.SupportsCatalogOptions;
import org.apache.spark.sql.connector.catalog.Table;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.expressions.Transform;
import org.apache.spark.sql.sources.DataSourceRegister;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：Iceberg 的 Spark DataSource V2 入口，提供按名/路径加载表的能力。
 *
 * <p>设计意图：实现 TableProvider，根据标识符创建 SparkTable 或元数据表。
 *
 * <p>上下游关系：由 Spark 在 CREATE TABLE USING iceberg 时加载；产出 SparkTable。
 */
public class IcebergSource implements DataSourceRegister, SupportsCatalogOptions {
  private static final String DEFAULT_CATALOG_NAME = "default_iceberg";
  private static final String DEFAULT_CACHE_CATALOG_NAME = "default_cache_iceberg";
  private static final String DEFAULT_CATALOG = "spark.sql.catalog." + DEFAULT_CATALOG_NAME;
  private static final String DEFAULT_CACHE_CATALOG =
      "spark.sql.catalog." + DEFAULT_CACHE_CATALOG_NAME;
  private static final String AT_TIMESTAMP = "at_timestamp_";
  private static final String SNAPSHOT_ID = "snapshot_id_";
  private static final String BRANCH_PREFIX = "branch_";
  private static final String TAG_PREFIX = "tag_";
  private static final String[] EMPTY_NAMESPACE = new String[0];

  private static final SparkTableCache TABLE_CACHE = SparkTableCache.get();
  /** 执行 shortName 相关操作。 */
  @Override
  public String shortName() {
    return "iceberg";
  }
  /** 执行 inferSchema 相关操作。 */
  @Override
  public StructType inferSchema(CaseInsensitiveStringMap options) {
    return null;
  }
  /** 执行 inferPartitioning 相关操作。 */
  @Override
  public Transform[] inferPartitioning(CaseInsensitiveStringMap options) {
    return getTable(null, null, options).partitioning();
  }
  /** 执行 supportsExternalMetadata 相关操作。 */
  @Override
  public boolean supportsExternalMetadata() {
    return true;
  }
  /** 返回 Table 属性。 */
  @Override
  public Table getTable(StructType schema, Transform[] partitioning, Map<String, String> options) {
    Spark3Util.CatalogAndIdentifier catalogIdentifier =
        catalogAndIdentifier(new CaseInsensitiveStringMap(options));
    CatalogPlugin catalog = catalogIdentifier.catalog();
    Identifier ident = catalogIdentifier.identifier();

    try {
      if (catalog instanceof TableCatalog) {
        return ((TableCatalog) catalog).loadTable(ident);
      }
    } catch (NoSuchTableException e) {
      // throwing an iceberg NoSuchTableException because the Spark one is typed and cant be thrown
      // from this interface
      throw new org.apache.iceberg.exceptions.NoSuchTableException(
          e, "Cannot find table for %s.", ident);
    }

    // throwing an iceberg NoSuchTableException because the Spark one is typed and cant be thrown
    // from this interface
    throw new org.apache.iceberg.exceptions.NoSuchTableException(
        "Cannot find table for %s.", ident);
  }
  /** 执行 catalogAndIdentifier 相关操作。 */
  private Spark3Util.CatalogAndIdentifier catalogAndIdentifier(CaseInsensitiveStringMap options) {
    Preconditions.checkArgument(
        options.containsKey(SparkReadOptions.PATH), "Cannot open table: path is not set");
    SparkSession spark = SparkSession.active();
    setupDefaultSparkCatalogs(spark);
    String path = options.get(SparkReadOptions.PATH);

    Long snapshotId = propertyAsLong(options, SparkReadOptions.SNAPSHOT_ID);
    Long asOfTimestamp = propertyAsLong(options, SparkReadOptions.AS_OF_TIMESTAMP);
    String branch = options.get(SparkReadOptions.BRANCH);
    String tag = options.get(SparkReadOptions.TAG);
    Preconditions.checkArgument(
        Stream.of(snapshotId, asOfTimestamp, branch, tag).filter(Objects::nonNull).count() <= 1,
        "Can specify only one of snapshot-id (%s), as-of-timestamp (%s), branch (%s), tag (%s)",
        snapshotId,
        asOfTimestamp,
        branch,
        tag);

    String selector = null;

    if (snapshotId != null) {
      selector = SNAPSHOT_ID + snapshotId;
    }

    if (asOfTimestamp != null) {
      selector = AT_TIMESTAMP + asOfTimestamp;
    }

    if (branch != null) {
      selector = BRANCH_PREFIX + branch;
    }

    if (tag != null) {
      selector = TAG_PREFIX + tag;
    }

    CatalogManager catalogManager = spark.sessionState().catalogManager();

    if (TABLE_CACHE.contains(path)) {
      return new Spark3Util.CatalogAndIdentifier(
          catalogManager.catalog(DEFAULT_CACHE_CATALOG_NAME),
          Identifier.of(EMPTY_NAMESPACE, pathWithSelector(path, selector)));
    } else if (path.contains("/")) {
      // contains a path. Return iceberg default catalog and a PathIdentifier
      return new Spark3Util.CatalogAndIdentifier(
          catalogManager.catalog(DEFAULT_CATALOG_NAME),
          new PathIdentifier(pathWithSelector(path, selector)));
    }

    final Spark3Util.CatalogAndIdentifier catalogAndIdentifier =
        Spark3Util.catalogAndIdentifier("path or identifier", spark, path);

    Identifier ident = identifierWithSelector(catalogAndIdentifier.identifier(), selector);
    if (catalogAndIdentifier.catalog().name().equals("spark_catalog")
        && !(catalogAndIdentifier.catalog() instanceof SparkSessionCatalog)) {
      // catalog is a session catalog but does not support Iceberg. Use Iceberg instead.
      return new Spark3Util.CatalogAndIdentifier(
          catalogManager.catalog(DEFAULT_CATALOG_NAME), ident);
    } else {
      return new Spark3Util.CatalogAndIdentifier(catalogAndIdentifier.catalog(), ident);
    }
  }
  /** 执行 pathWithSelector 相关操作。 */
  private String pathWithSelector(String path, String selector) {
    return (selector == null) ? path : path + "#" + selector;
  }
  /** 执行 identifierWithSelector 相关操作。 */
  private Identifier identifierWithSelector(Identifier ident, String selector) {
    if (selector == null) {
      return ident;
    } else {
      String[] namespace = ident.namespace();
      String[] ns = Arrays.copyOf(namespace, namespace.length + 1);
      ns[namespace.length] = ident.name();
      return Identifier.of(ns, selector);
    }
  }
  /** 执行 extractIdentifier 相关操作。 */
  @Override
  public Identifier extractIdentifier(CaseInsensitiveStringMap options) {
    return catalogAndIdentifier(options).identifier();
  }
  /** 执行 extractCatalog 相关操作。 */
  @Override
  public String extractCatalog(CaseInsensitiveStringMap options) {
    return catalogAndIdentifier(options).catalog().name();
  }
  /** 执行 extractTimeTravelVersion 相关操作。 */
  @Override
  public Optional<String> extractTimeTravelVersion(CaseInsensitiveStringMap options) {
    return Optional.ofNullable(
        PropertyUtil.propertyAsString(options, SparkReadOptions.VERSION_AS_OF, null));
  }
  /** 执行 extractTimeTravelTimestamp 相关操作。 */
  @Override
  public Optional<String> extractTimeTravelTimestamp(CaseInsensitiveStringMap options) {
    return Optional.ofNullable(
        PropertyUtil.propertyAsString(options, SparkReadOptions.TIMESTAMP_AS_OF, null));
  }
  /** 执行 propertyAsLong 相关操作。 */
  private static Long propertyAsLong(CaseInsensitiveStringMap options, String property) {
    String value = options.get(property);
    if (value != null) {
      return Long.parseLong(value);
    }

    return null;
  }
  /** 执行 setupDefaultSparkCatalogs 相关操作。 */
  private static void setupDefaultSparkCatalogs(SparkSession spark) {
    if (!spark.conf().contains(DEFAULT_CATALOG)) {
      ImmutableMap<String, String> config =
          ImmutableMap.of(
              "type", "hive",
              "default-namespace", "default",
              "cache-enabled", "false" // the source should not use a cache
              );
      spark.conf().set(DEFAULT_CATALOG, SparkCatalog.class.getName());
      config.forEach((key, value) -> spark.conf().set(DEFAULT_CATALOG + "." + key, value));
    }

    if (!spark.conf().contains(DEFAULT_CACHE_CATALOG)) {
      spark.conf().set(DEFAULT_CACHE_CATALOG, SparkCachedTableCatalog.class.getName());
    }
  }
}
