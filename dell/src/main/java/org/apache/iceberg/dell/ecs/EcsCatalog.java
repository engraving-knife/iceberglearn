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
package org.apache.iceberg.dell.ecs;

import com.emc.object.s3.S3Client;
import com.emc.object.s3.S3Exception;
import com.emc.object.s3.S3ObjectMetadata;
import com.emc.object.s3.bean.GetObjectResult;
import com.emc.object.s3.bean.ListObjectsResult;
import com.emc.object.s3.bean.S3Object;
import com.emc.object.s3.request.ListObjectsRequest;
import com.emc.object.s3.request.PutObjectRequest;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.iceberg.BaseMetastoreCatalog;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.CatalogUtil;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableOperations;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.SupportsNamespaces;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.dell.DellClientFactories;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.hadoop.Configurable;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.io.ByteStreams;
import org.apache.iceberg.util.LocationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Dell EMC ECS 对象存储的 Catalog 实现，以对象本身作为元数据载体，无需外部 metastore。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>管理 namespace 与 table：以 {@code .namespace}/{@code .table} 后缀对象作为 元数据标记，namespace/table
 *       的属性（含表 metadata location）序列化存于对象内容。
 *   <li>提供 list/create/drop/rename 等 namespace 与 table 操作。
 *   <li>提供基于 E-Tag 的属性读写（CAS）能力，供 {@link EcsTableOperations} 做乐观并发提交。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>对象即目录：表/命名空间不存在独立元数据库，直接以约定后缀对象表达存在性， 属性以 {@link PropertiesSerDesUtil} 序列化进对象体，版本号进 user
 *       metadata。 这使 catalog 完全无状态、可随存储迁移。
 *   <li>CAS 并发控制：创建用 {@code If-None-Match: *}（对象不存在），更新用 {@code If-Match: <eTag>}（E-Tag
 *       匹配），实现乐观锁，失败返回 false 由上层重试。
 *   <li>边界校验：所有 properties 对象必须位于 warehouse 所在 bucket 与前缀下 （{@link #checkURI(EcsURI)}），防止越权操作。
 *   <li>继承 {@link BaseMetastoreCatalog}：复用建表/加载表的公共流程，仅需实现 {@link #newTableOps}、{@link
 *       #defaultWarehouseLocation} 等。
 *   <li>资源统一关闭：用 {@link CloseableGroup} 聚合 S3 客户端与 FileIO 的关闭。
 * </ul>
 *
 * <p>上下游关系：被引擎侧通过 {@code CatalogUtil.loadCatalog} 加载；内部创建 {@link EcsTableOperations} 与 {@link
 * EcsFileIO}；依赖 {@link DellClientFactories} 获取 S3 客户端；属性读写依赖 {@link PropertiesSerDesUtil}。
 */
public class EcsCatalog extends BaseMetastoreCatalog
    implements Closeable, SupportsNamespaces, Configurable<Object> {

  /** 表元数据对象的后缀。 */
  private static final String TABLE_OBJECT_SUFFIX = ".table";

  /** 命名空间元数据对象的后缀。 */
  private static final String NAMESPACE_OBJECT_SUFFIX = ".namespace";

  /** ECS 对象 user metadata 中记录属性序列化版本号的键。 */
  private static final String PROPERTIES_VERSION_USER_METADATA_KEY = "iceberg_properties_version";

  private static final Logger LOG = LoggerFactory.getLogger(EcsCatalog.class);

  private S3Client client;
  private Object hadoopConf;
  private String catalogName;

  /** 仓库根 location，与其他 catalog 一致不含末尾分隔符。 */
  private EcsURI warehouseLocation;

  private FileIO fileIO;
  private CloseableGroup closeableGroup;
  private Map<String, String> catalogProperties;

  /**
   * 无参构造，供动态加载 catalog 使用。
   *
   * <p>所有字段随后通过 {@link #initialize(String, Map)} 注入。
   */
  public EcsCatalog() {}

  /**
   * 初始化 catalog：解析仓库路径、创建 S3 客户端与 FileIO、聚合可关闭资源。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>拷贝配置为不可变 Map，校验 warehouse 非空。
   *   <li>规范化 warehouse（去末尾斜杠）转为 {@link EcsURI}。
   *   <li>通过 {@link DellClientFactories} 创建 S3 客户端。
   *   <li>初始化 FileIO（默认 {@link EcsFileIO}，或按 {@code file-io-impl} 自定义）。
   *   <li>用 {@link CloseableGroup} 聚合 client 与 fileIO 的关闭，并允许部分失败不抛错。
   * </ol>
   *
   * @param name catalog 名称
   * @param properties catalog 配置属性
   */
  @Override
  public void initialize(String name, Map<String, String> properties) {
    this.catalogProperties = ImmutableMap.copyOf(properties);
    String inputWarehouseLocation = properties.get(CatalogProperties.WAREHOUSE_LOCATION);
    Preconditions.checkArgument(
        inputWarehouseLocation != null && inputWarehouseLocation.length() > 0,
        "Cannot initialize EcsCatalog because warehousePath must not be null or empty");

    this.catalogName = name;
    this.warehouseLocation = new EcsURI(LocationUtil.stripTrailingSlash(inputWarehouseLocation));
    this.client = DellClientFactories.from(properties).ecsS3();
    this.fileIO = initializeFileIO(properties);

    this.closeableGroup = new CloseableGroup();
    closeableGroup.addCloseable(client::destroy);
    closeableGroup.addCloseable(fileIO);
    closeableGroup.setSuppressCloseFailure(true);
  }

  /**
   * 初始化 FileIO：未指定 {@code file-io-impl} 时默认使用 {@link EcsFileIO}， 否则按指定实现加载（可注入 Hadoop 配置）。
   *
   * @param properties catalog 配置属性
   * @return 已初始化的 FileIO
   */
  private FileIO initializeFileIO(Map<String, String> properties) {
    String fileIOImpl = properties.get(CatalogProperties.FILE_IO_IMPL);
    if (fileIOImpl == null) {
      FileIO io = new EcsFileIO();
      io.initialize(properties);
      return io;
    } else {
      return CatalogUtil.loadFileIO(fileIOImpl, properties, hadoopConf);
    }
  }

  /**
   * 为指定表创建 {@link TableOperations}。
   *
   * @param tableIdentifier 表标识
   * @return 绑定到该表对象的 {@link EcsTableOperations}
   */
  @Override
  protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
    return new EcsTableOperations(
        String.format("%s.%s", catalogName, tableIdentifier),
        tableURI(tableIdentifier),
        fileIO,
        this);
  }

  /**
   * 返回表的默认仓库 location：namespace 前缀 + 表名。
   *
   * @param tableIdentifier 表标识
   * @return 默认 location 字符串
   */
  @Override
  protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
    return String.format(
        "%s/%s", namespacePrefix(tableIdentifier.namespace()), tableIdentifier.name());
  }

  /**
   * 列出命名空间下的所有表。
   *
   * <p>逻辑：以 namespace 前缀加分隔符做 delimiter 列举，分页跟进 marker， 仅保留以 {@code .table} 结尾的对象，并解析出表名。namespace
   * 不存在时抛异常。
   *
   * @param namespace 命名空间
   * @return 表标识列表
   * @throws NoSuchNamespaceException 当 namespace 不存在时抛出
   */
  @Override
  public List<TableIdentifier> listTables(Namespace namespace) {
    if (!namespace.isEmpty() && !namespaceExists(namespace)) {
      throw new NoSuchNamespaceException("Namespace %s does not exist", namespace);
    }

    String marker = null;
    List<TableIdentifier> results = Lists.newArrayList();
    // delimiter 列举时需要末尾斜杠
    EcsURI prefix = new EcsURI(String.format("%s/", namespacePrefix(namespace)));
    do {
      ListObjectsResult listObjectsResult =
          client.listObjects(
              new ListObjectsRequest(prefix.bucket())
                  .withDelimiter("/")
                  .withPrefix(prefix.name())
                  .withMarker(marker));
      marker = listObjectsResult.getNextMarker();
      results.addAll(
          listObjectsResult.getObjects().stream()
              .filter(s3Object -> s3Object.getKey().endsWith(TABLE_OBJECT_SUFFIX))
              .map(object -> parseTableId(namespace, prefix, object))
              .collect(Collectors.toList()));
    } while (marker != null);

    LOG.debug("Listing of namespace: {} resulted in the following tables: {}", namespace, results);
    return results;
  }

  /** 返回命名空间对应的对象前缀（不含末尾斜杠）。 */
  private String namespacePrefix(Namespace namespace) {
    if (namespace.isEmpty()) {
      return warehouseLocation.location();
    } else {
      // warehouseLocation.name 为空时前导斜杠会被忽略
      return String.format(
          "%s/%s", warehouseLocation.location(), String.join("/", namespace.levels()));
    }
  }

  /**
   * 从列举结果的对象 key 解析出表标识。
   *
   * <p>逻辑：去掉前缀与 {@code .table} 后缀得到表名，结合 namespace 构造 {@link TableIdentifier}。
   *
   * @param namespace 所属命名空间
   * @param prefix 列举前缀
   * @param s3Object 列举到的对象
   * @return 表标识
   */
  private TableIdentifier parseTableId(Namespace namespace, EcsURI prefix, S3Object s3Object) {
    String key = s3Object.getKey();
    Preconditions.checkArgument(
        key.startsWith(prefix.name()), "List result should have same prefix", key, prefix);

    String tableName =
        key.substring(prefix.name().length(), key.length() - TABLE_OBJECT_SUFFIX.length());
    return TableIdentifier.of(namespace, tableName);
  }

  /**
   * 删除表。purge 为 true 时一并删除数据文件。
   *
   * <p>逻辑：表不存在直接返回 false；purge 时加载当前元数据并删除其引用的所有数据文件， 最后删除表对象本身。
   *
   * @param identifier 表标识
   * @param purge 是否清理数据文件
   * @return 删除成功返回 true，表不存在返回 false
   */
  @Override
  public boolean dropTable(TableIdentifier identifier, boolean purge) {
    if (!tableExists(identifier)) {
      return false;
    }

    EcsURI tableObjectURI = tableURI(identifier);
    if (purge) {
      // 复用同一实例时 current() 会抛异常，故新建 ops
      TableOperations ops = newTableOps(identifier);
      TableMetadata current = ops.current();
      if (current == null) {
        return false;
      }

      CatalogUtil.dropTableData(ops.io(), current);
    }

    client.deleteObject(tableObjectURI.bucket(), tableObjectURI.name());
    return true;
  }

  /** 构造表元数据对象 location：namespace 前缀 + 表名 + {@code .table}。 */
  private EcsURI tableURI(TableIdentifier id) {
    return new EcsURI(
        String.format("%s/%s%s", namespacePrefix(id.namespace()), id.name(), TABLE_OBJECT_SUFFIX));
  }

  /**
   * 重命名表：仅移动表对象，数据对象原地保留。
   *
   * <p>逻辑：校验目标 namespace 存在且目标表不存在、源表存在后，加载源表属性， 用 {@code putNewProperties} 在目标位置创建表对象（CAS 保证不存在），
   * 成功后删除源表对象。
   *
   * @param from 源表标识
   * @param to 目标表标识
   * @throws NoSuchNamespaceException 目标 namespace 不存在
   * @throws AlreadyExistsException 目标表已存在
   * @throws NoSuchTableException 源表不存在
   */
  @Override
  public void renameTable(TableIdentifier from, TableIdentifier to) {
    if (!namespaceExists(to.namespace())) {
      throw new NoSuchNamespaceException(
          "Cannot rename %s to %s because namespace %s does not exist", from, to, to.namespace());
    }

    if (tableExists(to)) {
      throw new AlreadyExistsException(
          "Cannot rename %s because destination table %s exists", from, to);
    }

    EcsURI fromURI = tableURI(from);
    if (!objectMetadata(fromURI).isPresent()) {
      throw new NoSuchTableException("Cannot rename table because table %s does not exist", from);
    }

    Properties properties = loadProperties(fromURI);
    EcsURI toURI = tableURI(to);

    if (!putNewProperties(toURI, properties.content())) {
      throw new AlreadyExistsException(
          "Cannot rename %s because destination table %s exists", from, to);
    }

    client.deleteObject(fromURI.bucket(), fromURI.name());
    LOG.info("Rename table {} to {}", from, to);
  }

  /**
   * 创建命名空间，附带属性。
   *
   * <p>逻辑：用 {@code putNewProperties}（{@code If-None-Match: *}）创建 namespace 对象， 已存在则抛 {@link
   * AlreadyExistsException}。
   *
   * @param namespace 命名空间
   * @param properties 命名空间属性
   * @throws AlreadyExistsException 命名空间已存在
   */
  @Override
  public void createNamespace(Namespace namespace, Map<String, String> properties) {
    EcsURI namespaceObject = namespaceURI(namespace);
    if (!putNewProperties(namespaceObject, properties)) {
      throw new AlreadyExistsException(
          "namespace %s(%s) has already existed", namespace, namespaceObject);
    }
  }

  /** 构造命名空间元数据对象 location：namespace 前缀 + {@code .namespace}。 */
  private EcsURI namespaceURI(Namespace namespace) {
    return new EcsURI(String.format("%s%s", namespacePrefix(namespace), NAMESPACE_OBJECT_SUFFIX));
  }

  /**
   * 列出命名空间下的子命名空间。
   *
   * <p>逻辑：以 namespace 前缀加分隔符做 delimiter 列举，分页跟进 marker， 仅保留以 {@code .namespace} 结尾的对象，解析出下一级命名空间名。
   *
   * @param namespace 父命名空间
   * @return 子命名空间列表
   * @throws NoSuchNamespaceException 父命名空间不存在
   */
  @Override
  public List<Namespace> listNamespaces(Namespace namespace) throws NoSuchNamespaceException {
    if (!namespace.isEmpty() && !namespaceExists(namespace)) {
      throw new NoSuchNamespaceException("Namespace %s does not exist", namespace);
    }

    String marker = null;
    List<Namespace> results = Lists.newArrayList();
    // delimiter 列举时需要末尾斜杠
    EcsURI prefix = new EcsURI(String.format("%s/", namespacePrefix(namespace)));
    do {
      ListObjectsResult listObjectsResult =
          client.listObjects(
              new ListObjectsRequest(prefix.bucket())
                  .withDelimiter("/")
                  .withPrefix(prefix.name())
                  .withMarker(marker));
      marker = listObjectsResult.getNextMarker();
      results.addAll(
          listObjectsResult.getObjects().stream()
              .filter(s3Object -> s3Object.getKey().endsWith(NAMESPACE_OBJECT_SUFFIX))
              .map(object -> parseNamespace(namespace, prefix, object))
              .collect(Collectors.toList()));
    } while (marker != null);

    LOG.debug("Listing namespace {} returned namespaces: {}", namespace, results);
    return results;
  }

  /**
   * 从列举结果的对象 key 解析出子命名空间。
   *
   * <p>逻辑：去掉前缀与 {@code .namespace} 后缀得到本级命名空间名，拼接到父级 levels 之后。
   *
   * @param parent 父命名空间
   * @param prefix 列举前缀
   * @param s3Object 列举到的对象
   * @return 子命名空间
   */
  private Namespace parseNamespace(Namespace parent, EcsURI prefix, S3Object s3Object) {
    String key = s3Object.getKey();
    Preconditions.checkArgument(
        key.startsWith(prefix.name()), "List result should have same prefix", key, prefix);

    String namespaceName =
        key.substring(prefix.name().length(), key.length() - NAMESPACE_OBJECT_SUFFIX.length());
    String[] namespace = Arrays.copyOf(parent.levels(), parent.levels().length + 1);
    namespace[namespace.length - 1] = namespaceName;
    return Namespace.of(namespace);
  }

  /**
   * 加载命名空间属性。
   *
   * <p>逻辑：namespace 对象不存在则抛异常，否则读取其序列化属性返回。
   *
   * @param namespace 命名空间
   * @return 属性 Map
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public Map<String, String> loadNamespaceMetadata(Namespace namespace)
      throws NoSuchNamespaceException {
    EcsURI namespaceObject = namespaceURI(namespace);
    if (!objectMetadata(namespaceObject).isPresent()) {
      throw new NoSuchNamespaceException(
          "Namespace %s(%s) properties object is absent", namespace, namespaceObject);
    }

    Map<String, String> result = loadProperties(namespaceObject).content();

    LOG.debug("Loaded metadata for namespace {} found {}", namespace, result);
    return result;
  }

  /**
   * 删除命名空间。非空时抛 {@link NamespaceNotEmptyException}。
   *
   * <p>逻辑：校验存在性后，若仍有子命名空间或表则报错，否则删除 namespace 对象。
   *
   * @param namespace 命名空间
   * @return 删除成功返回 true
   * @throws NamespaceNotEmptyException 命名空间非空
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean dropNamespace(Namespace namespace) throws NamespaceNotEmptyException {
    if (!namespace.isEmpty() && !namespaceExists(namespace)) {
      throw new NoSuchNamespaceException("Namespace %s does not exist", namespace);
    }

    if (!listNamespaces(namespace).isEmpty() || !listTables(namespace).isEmpty()) {
      throw new NamespaceNotEmptyException("Namespace %s is not empty", namespace);
    }

    EcsURI namespaceObject = namespaceURI(namespace);
    client.deleteObject(namespaceObject.bucket(), namespaceObject.name());
    LOG.info("Dropped namespace: {}", namespace);
    return true;
  }

  /**
   * 设置命名空间属性（合并写入）。
   *
   * @param namespace 命名空间
   * @param properties 待设置属性
   * @return 更新成功返回 true
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean setProperties(Namespace namespace, Map<String, String> properties)
      throws NoSuchNamespaceException {
    return updateProperties(namespace, r -> r.putAll(properties));
  }

  /**
   * 移除命名空间属性。
   *
   * @param namespace 命名空间
   * @param properties 待移除的属性键集合
   * @return 更新成功返回 true
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  @Override
  public boolean removeProperties(Namespace namespace, Set<String> properties)
      throws NoSuchNamespaceException {
    return updateProperties(namespace, r -> r.keySet().removeAll(properties));
  }

  /**
   * 通用属性更新：读取旧属性、应用变更函数、CAS 写回。
   *
   * <p>逻辑：加载旧属性与 E-Tag，复制为可变 Map 后应用 {@code propertiesFn}， 再以旧 E-Tag 做条件更新；返回是否成功（CAS 失败返回 false）。
   *
   * @param namespace 命名空间
   * @param propertiesFn 对属性 Map 的变更操作
   * @return 更新成功返回 true
   * @throws NoSuchNamespaceException 命名空间不存在
   */
  public boolean updateProperties(Namespace namespace, Consumer<Map<String, String>> propertiesFn)
      throws NoSuchNamespaceException {

    // 读取旧属性
    Properties oldProperties = loadProperties(namespaceURI(namespace));

    // 写入新属性
    Map<String, String> newProperties = new LinkedHashMap<>(oldProperties.content());
    propertiesFn.accept(newProperties);
    LOG.debug("Successfully set properties {} for {}", newProperties.keySet(), namespace);
    return updatePropertiesObject(namespaceURI(namespace), oldProperties.eTag(), newProperties);
  }

  /** 判断命名空间是否存在（依据 namespace 对象是否存在）。 */
  @Override
  public boolean namespaceExists(Namespace namespace) {
    return objectMetadata(namespaceURI(namespace)).isPresent();
  }

  /** 判断表是否存在（依据 table 对象是否存在）。 */
  @Override
  public boolean tableExists(TableIdentifier identifier) {
    return objectMetadata(tableURI(identifier)).isPresent();
  }

  /**
   * 校验 properties 对象 location 必须与 warehouse 同 bucket 且位于其前缀之下， 防止越权操作仓库外对象。
   *
   * @param uri 待校验 location
   */
  private void checkURI(EcsURI uri) {
    Preconditions.checkArgument(
        uri.bucket().equals(warehouseLocation.bucket()),
        "Properties object %s should be in same bucket %s",
        uri.location(),
        warehouseLocation.bucket());
    Preconditions.checkArgument(
        uri.name().startsWith(warehouseLocation.name()),
        "Properties object %s should have the expected prefix %s",
        uri.location(),
        warehouseLocation.name());
  }

  /**
   * 获取 S3 对象元数据（含 E-Tag、user metadata 等），对象不存在返回 empty。
   *
   * <p>逻辑：先 {@link #checkURI(EcsURI)} 校验边界，再发起 HEAD 请求； HTTP 404 视为不存在，其他异常向上抛出。
   *
   * @param uri 对象 location
   * @return 元数据 Optional，不存在为 empty
   */
  public Optional<S3ObjectMetadata> objectMetadata(EcsURI uri) {
    checkURI(uri);
    try {
      return Optional.of(client.getObjectMetadata(uri.bucket(), uri.name()));
    } catch (S3Exception e) {
      if (e.getHttpCode() == 404) {
        return Optional.empty();
      }

      throw e;
    }
  }

  /**
   * 属性内容与 E-Tag 的记录类，作为读取 properties 对象的统一返回值。
   *
   * <p>设计要点：把对象体反序列化后的属性 Map 与对象 E-Tag 一起携带， 便于后续 CAS 更新使用。
   */
  static class Properties {
    private final String eTag;
    private final Map<String, String> content;

    Properties(String eTag, Map<String, String> content) {
      this.eTag = eTag;
      this.content = content;
    }

    /** 返回对象 E-Tag，用于 CAS 更新。 */
    public String eTag() {
      return eTag;
    }

    /** 返回反序列化后的属性内容。 */
    public Map<String, String> content() {
      return content;
    }
  }

  /**
   * 读取对象的属性内容与 E-Tag。
   *
   * <p>逻辑：{@link #checkURI(EcsURI)} 后 GET 对象，从 user metadata 取版本号， 用 {@link
   * PropertiesSerDesUtil#read(byte[], String)} 反序列化对象体为属性 Map， 连同 E-Tag 一并返回。
   *
   * @param uri 对象 location
   * @return 属性记录（content + eTag）
   */
  Properties loadProperties(EcsURI uri) {
    checkURI(uri);
    GetObjectResult<InputStream> result = client.getObject(uri.bucket(), uri.name());
    S3ObjectMetadata objectMetadata = result.getObjectMetadata();
    String version = objectMetadata.getUserMetadata(PROPERTIES_VERSION_USER_METADATA_KEY);
    Map<String, String> content;
    try (InputStream input = result.getObject()) {
      content = PropertiesSerDesUtil.read(ByteStreams.toByteArray(input), version);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return new Properties(objectMetadata.getETag(), content);
  }

  /**
   * 创建新对象存储属性，使用 {@code If-None-Match: *} 保证对象不存在。
   *
   * <p>逻辑：序列化属性为字节，附带版本号 user metadata，设置 {@code If-None-Match: *} 后 PUT。若返回 {@code
   * PreconditionFailed} 表示对象已存在，返回 false；其他异常向上抛出。
   *
   * @param uri 对象 location
   * @param properties 属性内容
   * @return 创建成功返回 true，对象已存在返回 false
   */
  boolean putNewProperties(EcsURI uri, Map<String, String> properties) {
    checkURI(uri);
    PutObjectRequest request =
        new PutObjectRequest(uri.bucket(), uri.name(), PropertiesSerDesUtil.toBytes(properties));
    request.setObjectMetadata(
        new S3ObjectMetadata()
            .addUserMetadata(
                PROPERTIES_VERSION_USER_METADATA_KEY, PropertiesSerDesUtil.currentVersion()));
    request.setIfNoneMatch("*");
    try {
      client.putObject(request);
      return true;
    } catch (S3Exception e) {
      if ("PreconditionFailed".equals(e.getErrorCode())) {
        return false;
      }

      throw e;
    }
  }

  /**
   * 更新已存在对象的属性，使用 {@code If-Match: <eTag>} 做乐观并发控制。
   *
   * <p>逻辑：序列化新属性为字节，附带版本号 user metadata，设置 {@code If-Match} 为传入 E-Tag 后 PUT。若返回 {@code
   * PreconditionFailed} 表示 E-Tag 失配（被并发修改）， 返回 false；其他异常向上抛出。
   *
   * @param uri 对象 location
   * @param eTag 期望的旧 E-Tag
   * @param properties 新属性内容
   * @return 更新成功返回 true，CAS 失败返回 false
   */
  boolean updatePropertiesObject(EcsURI uri, String eTag, Map<String, String> properties) {
    checkURI(uri);
    // 排除部分内部键
    Map<String, String> newProperties = new LinkedHashMap<>(properties);

    // 替换 properties 对象
    PutObjectRequest request =
        new PutObjectRequest(uri.bucket(), uri.name(), PropertiesSerDesUtil.toBytes(newProperties));
    request.setObjectMetadata(
        new S3ObjectMetadata()
            .addUserMetadata(
                PROPERTIES_VERSION_USER_METADATA_KEY, PropertiesSerDesUtil.currentVersion()));
    request.setIfMatch(eTag);
    try {
      client.putObject(request);
      return true;
    } catch (S3Exception e) {
      if ("PreconditionFailed".equals(e.getErrorCode())) {
        return false;
      }

      throw e;
    }
  }

  @Override
  public String name() {
    return catalogName;
  }

  /**
   * 关闭 catalog，统一释放 S3 客户端与 FileIO 资源。
   *
   * @throws IOException 当关闭过程发生 IO 异常时抛出
   */
  @Override
  public void close() throws IOException {
    closeableGroup.close();
  }

  /**
   * 注入 Hadoop 配置，供自定义 FileIO 实现使用。
   *
   * @param conf Hadoop 配置对象
   */
  @Override
  public void setConf(Object conf) {
    this.hadoopConf = conf;
  }

  @Override
  protected Map<String, String> properties() {
    return catalogProperties == null ? ImmutableMap.of() : catalogProperties;
  }
}
