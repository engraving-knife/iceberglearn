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
package org.apache.iceberg;

import static org.apache.iceberg.TableProperties.GC_ENABLED;
import static org.apache.iceberg.TableProperties.GC_ENABLED_DEFAULT;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.common.DynClasses;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.metrics.LoggingMetricsReporter;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.MapMaker;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.Tasks;
import org.apache.iceberg.util.ThreadPools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog 与 FileIO 的加载/工具方法集合。
 *
 * <p>所属模块：iceberg-core。提供跨引擎的统一入口，把"通过类名反射构造 Catalog/FileIO/Reporter" 与"删除表数据文件"等通用逻辑集中维护，避免各
 * Catalog 实现重复编码。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>按类型简称（hive/hadoop/rest）或完整类名加载 {@link Catalog} 实现。
 *   <li>反射加载 {@link FileIO}、{@link MetricsReporter} 实现，并注入 Hadoop 配置。
 *   <li>提供 {@link #dropTableData} 删除表所有数据/元数据文件的清理逻辑。
 *   <li>提供文件批量/并发删除的辅助方法。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 {@code Dyn*} 系列反射工具，屏蔽跨版本/跨 ClassLoader 的构造器查找差异。
 *   <li>对 Hadoop {@code Configurable} 做动态探测，避免在核心模块硬依赖 Hadoop 类。
 *   <li>删除文件时通过 {@code suppressFailureWhenFinished} 尽力删除，避免因部分失败留下孤儿文件。
 * </ul>
 *
 * <p>上下游关系：被引擎集成模块（Spark/Flink/Hive）和各 Catalog 实现调用。
 */
public class CatalogUtil {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogUtil.class);

  /**
   * 通过简短类型名加载 Catalog 实现的快捷属性，作为 {@link CatalogProperties#CATALOG_IMPL} 完整类名的替代。当前支持：
   *
   * <ul>
   *   <li>hive: org.apache.iceberg.hive.HiveCatalog
   *   <li>hadoop: org.apache.iceberg.hadoop.HadoopCatalog
   * </ul>
   */
  public static final String ICEBERG_CATALOG_TYPE = "type";

  public static final String ICEBERG_CATALOG_TYPE_HADOOP = "hadoop";
  public static final String ICEBERG_CATALOG_TYPE_HIVE = "hive";
  public static final String ICEBERG_CATALOG_TYPE_REST = "rest";

  public static final String ICEBERG_CATALOG_HADOOP = "org.apache.iceberg.hadoop.HadoopCatalog";
  public static final String ICEBERG_CATALOG_HIVE = "org.apache.iceberg.hive.HiveCatalog";
  public static final String ICEBERG_CATALOG_REST = "org.apache.iceberg.rest.RESTCatalog";

  private CatalogUtil() {}

  /**
   * 删除表元数据引用到的所有数据文件、manifest 文件、manifest list 以及历史 metadata 文件。
   *
   * <p>逻辑：遍历所有快照收集 manifest 与 manifest list；当表的 GC 开启时才删除数据文件 （避免破坏共享存储的其他表）；manifest、manifest
   * list、历史 metadata 与当前 metadata 文件 始终删除。所有删除通过 {@code
   * Tasks.foreach(...).suppressFailureWhenFinished} 尽力完成， 单个失败不影响其他删除。
   *
   * <p>设计意图：用于 {@code dropTable} 实现在 metastore 删除表后清理底层文件， 避免留下孤儿文件；GC 开关保护了共享数据文件不被误删。
   *
   * @param io 用于删除文件的 FileIO
   * @param metadata 已删除表的最后一份有效 TableMetadata
   */
  public static void dropTableData(FileIO io, TableMetadata metadata) {
    // Reads and deletes are done using Tasks.foreach(...).suppressFailureWhenFinished to complete
    // as much of the delete work as possible and avoid orphaned data or manifest files.

    Set<String> manifestListsToDelete = Sets.newHashSet();
    Set<ManifestFile> manifestsToDelete = Sets.newHashSet();
    for (Snapshot snapshot : metadata.snapshots()) {
      // add all manifests to the delete set because both data and delete files should be removed
      Iterables.addAll(manifestsToDelete, snapshot.allManifests(io));
      // add the manifest list to the delete set, if present
      if (snapshot.manifestListLocation() != null) {
        manifestListsToDelete.add(snapshot.manifestListLocation());
      }
    }

    LOG.info("Manifests to delete: {}", Joiner.on(", ").join(manifestsToDelete));

    // run all of the deletes

    boolean gcEnabled =
        PropertyUtil.propertyAsBoolean(metadata.properties(), GC_ENABLED, GC_ENABLED_DEFAULT);

    if (gcEnabled) {
      // delete data files only if we are sure this won't corrupt other tables
      deleteFiles(io, manifestsToDelete);
    }

    deleteFiles(io, Iterables.transform(manifestsToDelete, ManifestFile::path), "manifest", true);
    deleteFiles(io, manifestListsToDelete, "manifest list", true);
    deleteFiles(
        io,
        Iterables.transform(metadata.previousFiles(), TableMetadata.MetadataLogEntry::file),
        "previous metadata",
        true);
    deleteFile(io, metadata.metadataFileLocation(), "metadata");
  }

  /**
   * 删除一组 manifest 内引用的所有数据/删除文件。
   *
   * <p>逻辑：并发遍历每个 manifest，用 {@link ManifestReader} 读出条目，把文件路径 intern 后放入 weak-key 的去重 map（按
   * identity 比较，故需 intern），仅删除尚未被删除的文件。
   *
   * <p>设计要点：weak-key map 在内存紧张时可被 GC 回收条目，避免大表删除时占用过多内存； {@code intern} 字符串是因为 {@link
   * MapMaker#weakKeys()} 使用 identity 而非 equals。
   *
   * @param io FileIO
   * @param allManifests 待清理的 manifest 集合
   */
  @SuppressWarnings("DangerousStringInternUsage")
  private static void deleteFiles(FileIO io, Set<ManifestFile> allManifests) {
    // keep track of deleted files in a map that can be cleaned up when memory runs low
    Map<String, Boolean> deletedFiles =
        new MapMaker().concurrencyLevel(ThreadPools.WORKER_THREAD_POOL_SIZE).weakKeys().makeMap();

    Tasks.foreach(allManifests)
        .noRetry()
        .suppressFailureWhenFinished()
        .executeWith(ThreadPools.getWorkerPool())
        .onFailure(
            (item, exc) ->
                LOG.warn("Failed to get deleted files: this may cause orphaned data files", exc))
        .run(
            manifest -> {
              try (ManifestReader<?> reader = ManifestFiles.open(manifest, io)) {
                List<String> pathsToDelete = Lists.newArrayList();
                for (ManifestEntry<?> entry : reader.entries()) {
                  // intern the file path because the weak key map uses identity (==) instead of
                  // equals
                  String path = entry.file().path().toString().intern();
                  Boolean alreadyDeleted = deletedFiles.putIfAbsent(path, true);
                  if (alreadyDeleted == null || !alreadyDeleted) {
                    pathsToDelete.add(path);
                  }
                }

                String type = reader.isDeleteManifestReader() ? "delete" : "data";
                deleteFiles(io, pathsToDelete, type, false);
              } catch (IOException e) {
                throw new RuntimeIOException(
                    e, "Failed to read manifest file: %s", manifest.path());
              }
            });
  }

  /**
   * 删除文件辅助方法：若 FileIO 支持批量删除则批量删除，否则按是否并发选择策略。
   *
   * @param io FileIO
   * @param files 待删除文件路径
   * @param type 文件类型描述（用于日志）
   * @param concurrent 是否并发删除，仅对非批量 FileIO 生效
   */
  public static void deleteFiles(
      FileIO io, Iterable<String> files, String type, boolean concurrent) {
    if (io instanceof SupportsBulkOperations) {
      try {
        SupportsBulkOperations bulkIO = (SupportsBulkOperations) io;
        bulkIO.deleteFiles(files);
      } catch (RuntimeException e) {
        LOG.warn("Failed to bulk delete {} files", type, e);
      }
    } else {
      if (concurrent) {
        deleteFiles(io, files, type);
      } else {
        files.forEach(file -> deleteFile(io, file, type));
      }
    }
  }

  /** 并发删除文件集合，使用工作线程池，失败仅记录日志不抛出。 */
  private static void deleteFiles(FileIO io, Iterable<String> files, String type) {
    Tasks.foreach(files)
        .executeWith(ThreadPools.getWorkerPool())
        .noRetry()
        .suppressFailureWhenFinished()
        .onFailure((file, exc) -> LOG.warn("Failed to delete {} file {}", type, file, exc))
        .run(io::deleteFile);
  }

  /** 删除单个文件，失败仅记录日志不抛出。 */
  private static void deleteFile(FileIO io, String file, String type) {
    try {
      io.deleteFile(file);
    } catch (RuntimeException e) {
      LOG.warn("Failed to delete {} file {}", type, file, e);
    }
  }

  /**
   * 加载自定义 Catalog 实现。
   *
   * <p>逻辑：要求实现有无参构造器，通过 {@link DynConstructors} 反射构造实例；若对象实现了 {@link Configurable}（Iceberg 自带或
   * Hadoop 版本）则注入 Hadoop 配置；最后调用 {@link Catalog#initialize(String, Map)} 完成初始化。
   *
   * @param impl catalog 实现的全限定类名
   * @param catalogName catalog 名称
   * @param properties catalog 属性
   * @param hadoopConf 可选的 Hadoop 配置
   * @return 已初始化的 catalog 对象
   * @throws IllegalArgumentException 找不到无参构造器、类型不兼容或初始化失败
   */
  public static Catalog loadCatalog(
      String impl, String catalogName, Map<String, String> properties, Object hadoopConf) {
    Preconditions.checkNotNull(impl, "Cannot initialize custom Catalog, impl class name is null");
    DynConstructors.Ctor<Catalog> ctor;
    try {
      ctor = DynConstructors.builder(Catalog.class).impl(impl).buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize Catalog implementation %s: %s", impl, e.getMessage()),
          e);
    }

    Catalog catalog;
    try {
      catalog = ctor.newInstance();

    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize Catalog, %s does not implement Catalog.", impl), e);
    }

    configureHadoopConf(catalog, hadoopConf);

    catalog.initialize(catalogName, properties);
    return catalog;
  }

  /**
   * 根据属性 map 与可选 Hadoop 配置构建 Iceberg {@link Catalog}。
   *
   * <p>逻辑：优先使用 {@link CatalogProperties#CATALOG_IMPL} 指定的完整类名；若未设置则按 {@link #ICEBERG_CATALOG_TYPE}
   * 解析简称（hive/hadoop/rest），默认 hive； 两者不能同时设置，否则抛出异常。
   *
   * @param name catalog 名称
   * @param options catalog 属性
   * @param conf Hadoop Configuration
   * @return 已初始化的 catalog
   */
  public static Catalog buildIcebergCatalog(String name, Map<String, String> options, Object conf) {
    String catalogImpl = options.get(CatalogProperties.CATALOG_IMPL);
    if (catalogImpl == null) {
      String catalogType =
          PropertyUtil.propertyAsString(options, ICEBERG_CATALOG_TYPE, ICEBERG_CATALOG_TYPE_HIVE);
      switch (catalogType.toLowerCase(Locale.ENGLISH)) {
        case ICEBERG_CATALOG_TYPE_HIVE:
          catalogImpl = ICEBERG_CATALOG_HIVE;
          break;
        case ICEBERG_CATALOG_TYPE_HADOOP:
          catalogImpl = ICEBERG_CATALOG_HADOOP;
          break;
        case ICEBERG_CATALOG_TYPE_REST:
          catalogImpl = ICEBERG_CATALOG_REST;
          break;
        default:
          throw new UnsupportedOperationException("Unknown catalog type: " + catalogType);
      }
    } else {
      String catalogType = options.get(ICEBERG_CATALOG_TYPE);
      Preconditions.checkArgument(
          catalogType == null,
          "Cannot create catalog %s, both type and catalog-impl are set: type=%s, catalog-impl=%s",
          name,
          catalogType,
          catalogImpl);
    }

    return CatalogUtil.loadCatalog(catalogImpl, name, options, conf);
  }

  /**
   * 加载自定义 {@link FileIO} 实现。
   *
   * <p>逻辑：要求实现有无参构造器，反射构造实例；若实现 {@link Configurable} 则注入 Hadoop 配置； 最后调用 {@link
   * FileIO#initialize(Map)} 完成初始化。
   *
   * @param impl 自定义 FileIO 实现的全限定类名
   * @param properties 用于初始化 FileIO 的属性
   * @param hadoopConf 可选的 Hadoop Configuration
   * @return 已初始化的 FileIO
   * @throws IllegalArgumentException 类未找到、缺少无参构造器或类型不兼容
   */
  public static FileIO loadFileIO(String impl, Map<String, String> properties, Object hadoopConf) {
    LOG.info("Loading custom FileIO implementation: {}", impl);
    DynConstructors.Ctor<FileIO> ctor;
    try {
      ctor =
          DynConstructors.builder(FileIO.class)
              .loader(CatalogUtil.class.getClassLoader())
              .impl(impl)
              .buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize FileIO, missing no-arg constructor: %s", impl), e);
    }

    FileIO fileIO;
    try {
      fileIO = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize FileIO, %s does not implement FileIO.", impl), e);
    }

    configureHadoopConf(fileIO, hadoopConf);

    fileIO.initialize(properties);
    return fileIO;
  }

  /**
   * 动态探测对象是否为 Hadoop {@code Configurable}，若是则调用其 {@code setConf}。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>conf 为 null 直接返回。
   *   <li>若对象实现了 Iceberg 自带的 {@link Configurable}，直接调用其 setConf。
   *   <li>否则尝试通过对象的 ClassLoader 动态加载 Hadoop 的 {@code Configurable} 与 {@code Configuration}，并用反射调用
   *       setConf。
   * </ol>
   *
   * <p>设计意图：核心模块不直接依赖 Hadoop 类，通过反射按需探测，避免引入硬依赖。
   *
   * @param maybeConfigurable 可能是 Configurable 的对象
   * @param conf Hadoop Configuration
   */
  @SuppressWarnings("unchecked")
  public static void configureHadoopConf(Object maybeConfigurable, Object conf) {
    Preconditions.checkArgument(maybeConfigurable != null, "Cannot configure: null Configurable");
    if (conf == null) {
      return;
    }

    if (maybeConfigurable instanceof Configurable) {
      // use the Iceberg configurable interface to pass the conf
      ((Configurable<Object>) maybeConfigurable).setConf(conf);
      return;
    }

    // try to use Hadoop's Configurable interface dynamically
    // use the classloader of the object that may be configurable
    ClassLoader maybeConfigurableLoader = maybeConfigurable.getClass().getClassLoader();

    Class<?> configurableInterface;
    try {
      // load the Configurable interface
      configurableInterface =
          DynClasses.builder()
              .loader(maybeConfigurableLoader)
              .impl("org.apache.hadoop.conf.Configurable")
              .buildChecked();
    } catch (ClassNotFoundException e) {
      // not Configurable because it was loaded and Configurable is not present in its classloader
      return;
    }

    if (!configurableInterface.isInstance(maybeConfigurable)) {
      // not Configurable because the object does not implement the Configurable interface
      return;
    }

    Class<?> configurationClass;
    try {
      configurationClass =
          DynClasses.builder()
              .loader(maybeConfigurableLoader)
              .impl("org.apache.hadoop.conf.Configuration")
              .buildChecked();
    } catch (ClassNotFoundException e) {
      // this shouldn't happen because Configurable cannot be loaded without first loading
      // Configuration
      throw new UnsupportedOperationException(
          "Failed to load Configuration after loading Configurable", e);
    }

    ValidationException.check(
        configurationClass.isInstance(conf),
        "%s is not an instance of Configuration from the classloader for %s",
        conf,
        maybeConfigurable);

    DynMethods.BoundMethod setConf;
    try {
      setConf =
          DynMethods.builder("setConf")
              .impl(configurableInterface, configurationClass)
              .buildChecked()
              .bind(maybeConfigurable);
    } catch (NoSuchMethodException e) {
      // this shouldn't happen because Configurable was loaded and defines setConf
      throw new UnsupportedOperationException(
          "Failed to load Configuration.setConf after loading Configurable", e);
    }

    setConf.invoke(conf);
  }

  /**
   * 加载自定义 {@link MetricsReporter} 实现。
   *
   * <p>逻辑：从属性中读取实现类名，若未配置则返回 {@link LoggingMetricsReporter}； 否则反射构造实例并调用 {@link
   * MetricsReporter#initialize(Map)} 完成初始化。
   *
   * @param properties catalog 属性，可能包含 reporter 实现类名
   * @return 已初始化的 MetricsReporter
   * @throws IllegalArgumentException 类未找到、缺少无参构造器或类型不兼容
   */
  public static MetricsReporter loadMetricsReporter(Map<String, String> properties) {
    String impl = properties.get(CatalogProperties.METRICS_REPORTER_IMPL);
    if (impl == null) {
      return LoggingMetricsReporter.instance();
    }

    LOG.info("Loading custom MetricsReporter implementation: {}", impl);
    DynConstructors.Ctor<MetricsReporter> ctor;
    try {
      ctor =
          DynConstructors.builder(MetricsReporter.class)
              .loader(CatalogUtil.class.getClassLoader())
              .impl(impl)
              .buildChecked();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Cannot initialize MetricsReporter, missing no-arg constructor: %s", impl),
          e);
    }

    MetricsReporter reporter;
    try {
      reporter = ctor.newInstance();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException(
          String.format(
              "Cannot initialize MetricsReporter, %s does not implement MetricsReporter.", impl),
          e);
    }

    reporter.initialize(properties);

    return reporter;
  }
}
