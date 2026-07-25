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
package org.apache.iceberg.hadoop;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.LockManager;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.LockManagers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于文件系统目录结构的 Catalog 实现，用“路径即表名”的方式管理 Iceberg 表。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。继承 {@link BaseMetastoreCatalog} 并实现 {@link
 * SupportsNamespaces}，提供与 Hive/JDBC 等 Catalog 一致的命名空间与表管理接口。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以一个 warehouse 目录为根，按 {@code db/namespace/table} 层级组织表。
 *   <li>支持建表、删表、列举表、命名空间增删查等操作（不支持 rename）。
 *   <li>通过 {@link LockManager} 与文件系统原子 rename 实现并发提交的乐观锁。
 * </ul>
 *
 * <p>设计意图：用文件系统目录直接表达 catalog 层级，避免依赖外部元数据服务。 表目录通过是否存在 {@code metadata/} 子目录且包含 {@code
 * .metadata.json} 文件来识别。 注意：要求底层文件系统支持原子 rename，以保证 {@link HadoopTableOperations} 的提交正确性。
 *
 * <p>上下游关系：被 {@code CatalogUtil} 通过 {@code type=hadoop} 加载；底层使用 {@link HadoopTableOperations}
 * 管理表元数据，使用 {@link FileIO}（默认 {@link HadoopFileIO}）读写数据。
 */
public class HadoopCatalog extends BaseMetastoreCatalog
    implements Closeable, SupportsNamespaces, Configurable {

  private static final Logger LOG = LoggerFactory.getLogger(HadoopCatalog.class);

  private static final String TABLE_METADATA_FILE_EXTENSION = ".metadata.json";
  private static final Joiner SLASH = Joiner.on("/");
  private static final PathFilter TABLE_FILTER =
      path -> path.getName().endsWith(TABLE_METADATA_FILE_EXTENSION);
  private static final String HADOOP_SUPPRESS_PERMISSION_ERROR = "suppress-permission-error";

  private String catalogName;
  private Configuration conf;
  private CloseableGroup closeableGroup;
  private String warehouseLocation;
  private FileSystem fs;
  private FileIO fileIO;
  private LockManager lockManager;
  private boolean suppressPermissionError = false;
  private Map<String, String> catalogProperties;

  public HadoopCatalog() {}

  /**
   * 初始化 HadoopCatalog。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>读取并校验 warehouse 路径非空，去掉尾部斜杠。
   *   <li>获取该路径对应的 {@link FileSystem}。
   *   <li>按 {@code file-io-impl} 属性加载 FileIO，未配置则使用 {@link HadoopFileIO}。
   *   <li>从属性构建 {@link LockManager}，并把可关闭资源汇总到 {@link CloseableGroup}。
   *   <li>解析是否抑制权限错误（用于对象存储等无权限模型的场景）。
   * </ol>
   *
   * @param name catalog 名称
   * @param properties catalog 属性，必须包含 {@link CatalogProperties#WAREHOUSE_LOCATION}
   * @throws IllegalArgumentException warehouse 路径为空时抛出
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    this.catalogProperties = ImmutableMap.copyOf(properties);
    String inputWarehouseLocation = properties.get(CatalogProperties.WAREHOUSE_LOCATION);
    Preconditions.checkArgument(
        inputWarehouseLocation != null && inputWarehouseLocation.length() > 0,
        "Cannot initialize HadoopCatalog because warehousePath must not be null or empty");

    this.catalogName = name;
    this.warehouseLocation = LocationUtil.stripTrailingSlash(inputWarehouseLocation);
    this.fs = Util.getFs(new Path(warehouseLocation), conf);

    String fileIOImpl = properties.get(CatalogProperties.FILE_IO_IMPL);
    this.fileIO =
        fileIOImpl == null
            ? new HadoopFileIO(conf)
            : CatalogUtil.loadFileIO(fileIOImpl, properties, conf);

    this.lockManager = LockManagers.from(properties);

    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(lockManager);
    closeableGroup.setSuppressCloseFailure(true);

    this.suppressPermissionError =
        Boolean.parseBoolean(properties.get(HADOOP_SUPPRESS_PERMISSION_ERROR));
  }

  /**
   * 以 Hadoop 配置与 warehouse 路径构造 HadoopCatalog。
   *
   * @param conf Hadoop 配置
   * @param warehouseLocation 作为 warehouse 的目录路径
   */
  public HadoopCatalog(Configuration conf, String warehouseLocation) {
    setConf(conf);
    initialize("hadoop", ImmutableMap.of(CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));
  }

  @Override
  public String name() {
    return catalogName;
  }

  /**
   * 判断是否应抑制给定的 IO 异常（仅在开启 suppressPermissionError 时生效）。
   *
   * <p>逻辑：当开启抑制且异常为 {@link AccessDeniedException} 或消息包含 {@code AuthorizationPermissionMismatch} 时返回
   * true，用于对象存储等无权限模型场景下 把权限错误降级为“目录不存在”。
   *
   * @param ioException 待判定的 IO 异常
   * @return true 表示应抑制该异常
   */
  private boolean shouldSuppressPermissionError(IOException ioException) {
    if (suppressPermissionError) {
      return ioException instanceof AccessDeniedException
          || (ioException.getMessage() != null
              && ioException.getMessage().contains("AuthorizationPermissionMismatch"));
    }
    return false;
  }

  /**
   * 判断给定路径是否为表目录（即存在 metadata 子目录且至少含一个 {@code .metadata.json} 文件）。
   *
   * <p>逻辑：列出 {@code path/metadata} 下匹配 {@link #TABLE_FILTER} 的文件，数量大于等于 1 视为表目录； 文件不存在返回 false，IO
   * 异常在开启抑制时降级为 false，否则抛出 {@link UncheckedIOException}。
   *
   * @param path 待判定的目录路径
   * @return true 表示是表目录
   */
  private boolean isTableDir(Path path) {
    Path metadataPath = new Path(path, "metadata");
    // Only the path which contains metadata is the path for table, otherwise it could be
    // still a namespace.
    try {
      return fs.listStatus(metadataPath, TABLE_FILTER).length >= 1;
    } catch (FileNotFoundException e) {
      return false;
    } catch (IOException e) {
      if (shouldSuppressPermissionError(e)) {
        LOG.warn("Unable to list metadata directory {}", metadataPath, e);
        return false;
      } else {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * 判断给定路径是否为目录。
   *
   * <p>逻辑：调用 {@link FileSystem#getFileStatus(Path)} 取状态后判断 isDirectory； 文件不存在返回 false，IO
   * 异常在开启抑制时降级为 false，否则抛出 {@link UncheckedIOException}。
   *
   * @param path 待判定的路径
   * @return true 表示是目录
   */
  private boolean isDirectory(Path path) {
    try {
      return fs.getFileStatus(path).isDirectory();
    } catch (FileNotFoundException e) {
      return false;
    } catch (IOException e) {
      if (shouldSuppressPermissionError(e)) {
        LOG.warn("Unable to list directory {}", path, e);
        return false;
      } else {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * 列举指定命名空间下的所有表。
   *
   * <p>逻辑：拼接命名空间到 warehouse 路径，校验命名空间非空且存在； 用迭代器遍历目录下子目录，对每个子目录通过 {@link #isTableDir(Path)} 判断是否为表，
   * 命中则构造 {@link TableIdentifier} 收集返回。
   *
   * @param namespace 命名空间，至少包含一级
   * @return 该命名空间下的表标识符列表
   * @throws NoSuchNamespaceException 命名空间不存在时抛出
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    Preconditions.checkArgument(
        namespace.levels().length >= 1, "Missing database in table identifier: %s", namespace);

    Path nsPath = new Path(warehouseLocation, SLASH.join(namespace.levels()));
    Set<TableIdentifier> tblIdents = Sets.newHashSet();

    try {
      if (!isDirectory(nsPath)) {
        throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
      }
      RemoteIterator<FileStatus> it = fs.listStatusIterator(nsPath);
      while (it.hasNext()) {
        FileStatus status = it.next();
        if (!status.isDirectory()) {
          // Ignore the path which is not a directory.
          continue;
        }

        Path path = status.getPath();
        if (isTableDir(path)) {
          TableIdentifier tblIdent = TableIdentifier.of(namespace, path.getName());
          tblIdents.add(tblIdent);
        }
      }
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Failed to list tables under: %s", namespace);
    }

    return Lists.newArrayList(tblIdents);
  }

  @Override
  protected boolean isValidIdentifier(TableIdentifier identifier) {
    return true;
  }

  /**
   * 创建表操作对象，定位到标识符对应的 warehouse 子目录。
   *
   * @param identifier 表标识符
   * @return 绑定到默认 warehouse 位置的 {@link HadoopTableOperations}
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier identifier) {
    return new HadoopTableOperations(
        new Path(defaultWarehouseLocation(identifier)), fileIO, conf, lockManager);
  }

  /**
   * 计算表标识符对应的默认 warehouse 位置（warehouse/namespace.../tableName）。
   *
   * @param tableIdentifier 表标识符
   * @return 默认表目录路径字符串
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    String tableName = tableIdentifier.name();
    StringBuilder sb = new StringBuilder();

    sb.append(warehouseLocation).append('/');
    for (String level : tableIdentifier.namespace().levels()) {
      sb.append(level).append('/');
    }
    sb.append(tableName);

    return sb.toString();
  }

  /**
   * 删除表，可选清理数据文件。
   *
   * <p>逻辑：定位表目录并读取当前元数据；若 purge 为 true，先调用 {@link CatalogUtil#dropTableData}
   * 删除元数据中引用的数据文件，再递归删除表目录。 表不存在时返回 false。
   *
   * @param identifier 表标识符
   * @param purge 是否清理数据文件
   * @return true 表示删除成功
   * @throws RuntimeIOException 删除失败时抛出
   */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    if (!isValidIdentifier(identifier)) {
      throw new NoSuchTableException("Invalid identifier: %s", identifier);
    }

    Path tablePath = new Path(defaultWarehouseLocation(identifier));
    TableOperations ops = newTableOps(identifier);
    TableMetadata lastMetadata = ops.current();
    try {
      if (lastMetadata == null) {
        LOG.debug("Not an iceberg table: {}", identifier);
        return false;
      } else {
        if (purge) {
          // Since the data files and the metadata files may store in different locations,
          // so it has to call dropTableData to force delete the data file.
          CatalogUtil.dropTableData(ops.io(), lastMetadata);
        }
        return fs.delete(tablePath, true /* recursive */);
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to delete file: %s", tablePath);
    }
  }

  /**
   * 重命名表，HadoopCatalog 不支持该操作。
   *
   * @param from 源表标识符
   * @param to 目标表标识符
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    throw new UnsupportedOperationException("Cannot rename Hadoop tables");
  }

  /**
   * 创建命名空间（即创建对应目录）。
   *
   * <p>逻辑：校验命名空间非空且不支持元数据属性；拼接路径后若已存在则抛 {@link AlreadyExistsException}，否则调用 {@link
   * FileSystem#mkdirs(Path)} 创建。
   *
   * @param namespace 命名空间
   * @param meta 命名空间元数据（不支持，必须为空）
   * @throws UnsupportedOperationException 当 meta 非空时抛出
   * @throws AlreadyExistsException 命名空间已存在时抛出
   */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> meta) {
    Preconditions.checkArgument(
        !namespace.isEmpty(), "Cannot create namespace with invalid name: %s", namespace);
    if (!meta.isEmpty()) {
      throw new UnsupportedOperationException(
          "Cannot create namespace " + namespace + ": metadata is not supported");
    }

    Path nsPath = new Path(warehouseLocation, SLASH.join(namespace.levels()));

    if (isNamespace(nsPath)) {
      throw new AlreadyExistsException("Namespace already exists: %s", namespace);
    }

    try {
      fs.mkdirs(nsPath);

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Create namespace failed: %s", namespace);
    }
  }

  /**
   * 列举指定命名空间下的子命名空间。
   *
   * <p>逻辑：定位到命名空间路径（空则用 warehouse 根），校验存在； 用迭代器遍历子目录，对每个子目录通过 {@link #isNamespace(Path)} 判断是否为命名空间，
   * 命中则追加为子命名空间返回。迭代器方式支持 HDFS 分页与对象存储预取。
   *
   * @param namespace 父命名空间，可为空表示根
   * @return 子命名空间列表
   * @throws NoSuchNamespaceException 父命名空间不存在时抛出
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) {
    Path nsPath =
        namespace.isEmpty()
            ? new Path(warehouseLocation)
            : new Path(warehouseLocation, SLASH.join(namespace.levels()));
    if (!isNamespace(nsPath)) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    try {
      // using the iterator listing allows for paged downloads
      // from HDFS and prefetching from object storage.
      List<Namespace> namespaces = Lists.newArrayList();
      RemoteIterator<FileStatus> it = fs.listStatusIterator(nsPath);
      while (it.hasNext()) {
        Path path = it.next().getPath();
        if (isNamespace(path)) {
          namespaces.add(append(namespace, path.getName()));
        }
      }
      return namespaces;
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Failed to list namespace under: %s", namespace);
    }
  }

  /**
   * 在父命名空间后追加一级名称，构造子命名空间。
   *
   * @param ns 父命名空间
   * @param name 待追加的名称
   * @return 新的子命名空间
   */
  private Namespace append(Namespace ns, String name) {
    String[] levels = Arrays.copyOfRange(ns.levels(), 0, ns.levels().length + 1);
    levels[ns.levels().length] = name;
    return Namespace.of(levels);
  }

  /**
   * 删除命名空间（非递归）。
   *
   * <p>逻辑：校验命名空间存在且非空目录后，检查目录是否为空，非空则抛 {@link NamespaceNotEmptyException}，否则非递归删除。
   *
   * @param namespace 命名空间
   * @return true 表示删除成功
   * @throws NamespaceNotEmptyException 命名空间非空时抛出
   */
  @Override
  public boolean dropNamespace(Namespace namespace) {
    Path nsPath = new Path(warehouseLocation, SLASH.join(namespace.levels()));

    if (!isNamespace(nsPath) || namespace.isEmpty()) {
      return false;
    }

    try {
      if (fs.listStatusIterator(nsPath).hasNext()) {
        throw new NamespaceNotEmptyException("Namespace %s is not empty.", namespace);
      }

      return fs.delete(nsPath, false /* recursive */);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Namespace delete failed: %s", namespace);
    }
  }

  /**
   * 设置命名空间属性，HadoopCatalog 不支持。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties) {
    throw new UnsupportedOperationException(
        "Cannot set namespace properties " + namespace + " : setProperties is not supported");
  }

  /**
   * 移除命名空间属性，HadoopCatalog 不支持。
   *
   * @throws UnsupportedOperationException 始终抛出
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties) {
    throw new UnsupportedOperationException(
        "Cannot remove properties " + namespace + " : removeProperties is not supported");
  }

  /**
   * 加载命名空间的元数据（这里仅返回其 location）。
   *
   * @param namespace 命名空间
   * @return 包含 {@code location} 的属性映射
   * @throws NoSuchNamespaceException 命名空间不存在时抛出
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace) {
    Path nsPath = new Path(warehouseLocation, SLASH.join(namespace.levels()));

    if (!isNamespace(nsPath) || namespace.isEmpty()) {
      throw new NoSuchNamespaceException("Namespace does not exist: %s", namespace);
    }

    return ImmutableMap.of("location", nsPath.toString());
  }

  /**
   * 判断路径是否为命名空间（是目录且不是表目录）。
   *
   * @param path 待判定路径
   * @return true 表示是命名空间
   */
  private boolean isNamespace(Path path) {
    return isDirectory(path) && !isTableDir(path);
  }

  /**
   * 关闭 catalog 及其持有的可关闭资源（如 LockManager）。
   *
   * @throws IOException 关闭失败时抛出
   */
  @Override
  public void close() throws IOException {
    closeableGroup.close();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", catalogName)
        .add("location", warehouseLocation)
        .toString();
  }

  /**
   * 构造表构建器，使用 {@link HadoopCatalogTableBuilder} 限制自定义 location。
   *
   * @param identifier 表标识符
   * @param schema 表 schema
   * @return 表构建器
   */
  @Override
  public TableBuilder buildTable(TableIdentifier identifier, Schema schema) {
    return new HadoopCatalogTableBuilder(identifier, schema);
  }

  /**
   * 注入 Hadoop {@link Configuration}。
   *
   * @param conf Hadoop 配置
   */
  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  /** 获取当前持有的 Hadoop {@link Configuration}。 */
  @Override
  public Configuration getConf() {
    return conf;
  }

  /** 返回 catalog 属性（未初始化时返回空映射）。 */
  @Override
  protected Map<String, String> properties() {
    return catalogProperties == null ? ImmutableMap.of() : catalogProperties;
  }

  /**
   * 内部类：HadoopCatalog 专用的表构建器。
   *
   * <p>设计要点：路径式表的 location 由 warehouse 与表名决定，禁止调用方自定义 location， 因此 {@link #withLocation(String)}
   * 仅允许传入与默认 location 一致的值或 null。
   */
  private class HadoopCatalogTableBuilder extends BaseMetastoreCatalogTableBuilder {
    private final String defaultLocation;

    private HadoopCatalogTableBuilder(TableIdentifier identifier, Schema schema) {
      super(identifier, schema);
      defaultLocation = defaultWarehouseLocation(identifier);
    }

    /**
     * 设置表 location，HadoopCatalog 路径式表不允许自定义 location。
     *
     * @param location 必须为 null 或等于默认 location
     * @return 当前构建器
     * @throws IllegalArgumentException 当 location 与默认值不一致时抛出
     */
    @Override
    public TableBuilder withLocation(String location) {
      Preconditions.checkArgument(
          location == null || location.equals(defaultLocation),
          "Cannot set a custom location for a path-based table. Expected "
              + defaultLocation
              + " but got "
              + location);
      return this;
    }
  }
}
