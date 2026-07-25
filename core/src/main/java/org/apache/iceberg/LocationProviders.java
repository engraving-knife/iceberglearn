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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.hash.HashCode;
import org.apache.iceberg.relocated.com.google.common.hash.HashFunction;
import org.apache.iceberg.relocated.com.google.common.hash.Hashing;
import org.apache.iceberg.relocated.com.google.common.io.BaseEncoding;
import org.apache.iceberg.util.LocationUtil;
import org.apache.iceberg.util.PropertyUtil;

/**
 * 数据文件位置提供者工厂：根据表属性决定使用哪种 {@link LocationProvider} 来生成数据文件路径。
 *
 * <p>所属模块：iceberg-core（文件位置策略层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据表属性选择 DefaultLocationProvider（哈希目录结构）或 ObjectStoreLocationProvider （对象存储友好）或自定义实现；
 *   <li>DefaultLocationProvider 按"分区路径/文件名"组织；
 *   <li>ObjectStoreLocationProvider 通过哈希文件名生成均匀分布的子目录，优化对象存储性能。
 * </ul>
 *
 * <p>设计意图：不同存储系统对目录深度/分布有不同要求（如 S3 性能受前缀分布影响）， 通过可插拔 LocationProvider 让 Iceberg 适配多种存储。支持通过表属性
 * {@code WRITE_LOCATION_PROVIDER_IMPL} 指定自定义实现。
 *
 * <p>上下游关系：被表写入操作（append/overwrite）调用生成文件路径；底层依赖 {@link PartitionSpec#partitionToPath} 生成分区路径。
 */
public class LocationProviders {

  private LocationProviders() {}

  /**
   * 根据表位置与属性创建合适的 {@link LocationProvider}。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若设置了 WRITE_LOCATION_PROVIDER_IMPL，通过反射加载自定义实现（优先双参构造器， 回退无参构造器）；
   *   <li>否则若 OBJECT_STORE_ENABLED 为 true，使用 ObjectStoreLocationProvider；
   *   <li>否则使用 DefaultLocationProvider。
   * </ul>
   *
   * @param inputLocation 表位置
   * @param properties 表属性
   * @return 位置提供者实例
   */
  public static LocationProvider locationsFor(
      String inputLocation, Map<String, String> properties) {
    String location = LocationUtil.stripTrailingSlash(inputLocation);
    if (properties.containsKey(TableProperties.WRITE_LOCATION_PROVIDER_IMPL)) {
      String impl = properties.get(TableProperties.WRITE_LOCATION_PROVIDER_IMPL);
      DynConstructors.Ctor<LocationProvider> ctor;
      try {
        ctor =
            DynConstructors.builder(LocationProvider.class)
                .impl(impl, String.class, Map.class)
                .impl(impl)
                .buildChecked(); // fall back to no-arg constructor
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(
            String.format(
                "Unable to find a constructor for implementation %s of %s. "
                    + "Make sure the implementation is in classpath, and that it either "
                    + "has a public no-arg constructor or a two-arg constructor "
                    + "taking in the string base table location and its property string map.",
                impl, LocationProvider.class),
            e);
      }
      try {
        return ctor.newInstance(location, properties);
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(
            String.format(
                "Provided implementation for dynamic instantiation should implement %s.",
                LocationProvider.class),
            e);
      }
    } else if (PropertyUtil.propertyAsBoolean(
        properties,
        TableProperties.OBJECT_STORE_ENABLED,
        TableProperties.OBJECT_STORE_ENABLED_DEFAULT)) {
      return new ObjectStoreLocationProvider(location, properties);
    } else {
      return new DefaultLocationProvider(location, properties);
    }
  }

  /**
   * 默认位置提供者：按"数据目录/分区路径/文件名"组织文件。
   *
   * <p>设计意图：适用于 HDFS 等层次式文件系统，分区目录清晰可遍历。
   */
  static class DefaultLocationProvider implements LocationProvider {
    private final String dataLocation;

    /**
     * 构造默认位置提供者。
     *
     * @param tableLocation 表位置
     * @param properties 表属性
     */
    DefaultLocationProvider(String tableLocation, Map<String, String> properties) {
      this.dataLocation = LocationUtil.stripTrailingSlash(dataLocation(properties, tableLocation));
    }

    /** 解析数据目录：优先 WRITE_DATA_LOCATION，其次 WRITE_FOLDER_STORAGE_LOCATION，最后 "表位置/data"。 */
    private static String dataLocation(Map<String, String> properties, String tableLocation) {
      String dataLocation = properties.get(TableProperties.WRITE_DATA_LOCATION);
      if (dataLocation == null) {
        dataLocation = properties.get(TableProperties.WRITE_FOLDER_STORAGE_LOCATION);
        if (dataLocation == null) {
          dataLocation = String.format("%s/data", tableLocation);
        }
      }
      return dataLocation;
    }

    /**
     * 返回带分区的文件路径："数据目录/分区路径/文件名"。
     *
     * @param spec 分区规格
     * @param partitionData 分区数据
     * @param filename 文件名
     * @return 完整路径
     */
    @Override
    public String newDataLocation(PartitionSpec spec, StructLike partitionData, String filename) {
      return String.format("%s/%s/%s", dataLocation, spec.partitionToPath(partitionData), filename);
    }

    /**
     * 返回不带分区的文件路径："数据目录/文件名"。
     *
     * @param filename 文件名
     * @return 完整路径
     */
    @Override
    public String newDataLocation(String filename) {
      return String.format("%s/%s", dataLocation, filename);
    }
  }

  /**
   * 对象存储位置提供者：通过对文件名哈希生成均匀分布的子目录，优化对象存储（如 S3）的读写性能。
   *
   * <p>设计意图：对象存储在大量文件使用同一前缀时性能下降（请求限流），通过哈希分散文件到 不同子目录可避免热点。哈希基于 murmur3_32 并 Base64 编码为目录名。
   */
  static class ObjectStoreLocationProvider implements LocationProvider {

    private static final HashFunction HASH_FUNC = Hashing.murmur3_32_fixed();
    private static final BaseEncoding BASE64_ENCODER = BaseEncoding.base64Url().omitPadding();
    private static final ThreadLocal<byte[]> TEMP = ThreadLocal.withInitial(() -> new byte[4]);
    private final String storageLocation;
    private final String context;

    /**
     * 构造对象存储位置提供者。
     *
     * <p>逻辑：解析存储位置；若存储位置在表位置之下则 context 为 null，否则用表位置的父/当前 目录名作为 context 前缀，便于区分不同表的数据。
     *
     * @param tableLocation 表位置
     * @param properties 表属性
     */
    ObjectStoreLocationProvider(String tableLocation, Map<String, String> properties) {
      this.storageLocation =
          LocationUtil.stripTrailingSlash(dataLocation(properties, tableLocation));
      // if the storage location is within the table prefix, don't add table and database name
      // context
      if (storageLocation.startsWith(tableLocation)) {
        this.context = null;
      } else {
        this.context = pathContext(tableLocation);
      }
    }

    /**
     * 解析数据目录：优先 WRITE_DATA_LOCATION，其次 OBJECT_STORE_PATH、WRITE_FOLDER_STORAGE_LOCATION，最后
     * "表位置/data"。
     */
    private static String dataLocation(Map<String, String> properties, String tableLocation) {
      String dataLocation = properties.get(TableProperties.WRITE_DATA_LOCATION);
      if (dataLocation == null) {
        dataLocation = properties.get(TableProperties.OBJECT_STORE_PATH);
        if (dataLocation == null) {
          dataLocation = properties.get(TableProperties.WRITE_FOLDER_STORAGE_LOCATION);
          if (dataLocation == null) {
            dataLocation = String.format("%s/data", tableLocation);
          }
        }
      }
      return dataLocation;
    }

    /**
     * 返回带分区的文件路径："存储位置/分区路径/文件名"（哈希目录在最外层）。
     *
     * @param spec 分区规格
     * @param partitionData 分区数据
     * @param filename 文件名
     * @return 完整路径
     */
    @Override
    public String newDataLocation(PartitionSpec spec, StructLike partitionData, String filename) {
      return newDataLocation(String.format("%s/%s", spec.partitionToPath(partitionData), filename));
    }

    /**
     * 返回带哈希目录的文件路径："存储位置/哈希/[context/]文件名"。
     *
     * <p>逻辑：对文件名做 murmur3_32 哈希并 Base64 编码为目录名；若 context 非 null 则插入 context 层。
     *
     * @param filename 文件名
     * @return 完整路径
     */
    @Override
    public String newDataLocation(String filename) {
      String hash = computeHash(filename);
      if (context != null) {
        return String.format("%s/%s/%s/%s", storageLocation, hash, context, filename);
      } else {
        return String.format("%s/%s/%s", storageLocation, hash, filename);
      }
    }

    /**
     * 从表位置提取上下文路径（父目录名/表名），用于区分不同表的数据。
     *
     * @param tableLocation 表位置
     * @return 上下文路径字符串
     */
    private static String pathContext(String tableLocation) {
      Path dataPath = new Path(tableLocation);
      Path parent = dataPath.getParent();
      String resolvedContext;
      if (parent != null) {
        // remove the data folder
        resolvedContext = String.format("%s/%s", parent.getName(), dataPath.getName());
      } else {
        resolvedContext = dataPath.getName();
      }

      Preconditions.checkState(
          !resolvedContext.endsWith("/"), "Path context must not end with a slash.");

      return resolvedContext;
    }

    /**
     * 对文件名计算哈希并返回 Base64 编码的目录名。
     *
     * @param fileName 文件名
     * @return 哈希目录名（4 字节 Base64）
     */
    private String computeHash(String fileName) {
      byte[] bytes = TEMP.get();
      HashCode hash = HASH_FUNC.hashString(fileName, StandardCharsets.UTF_8);
      hash.writeBytesTo(bytes, 0, 4);
      return BASE64_ENCODER.encode(bytes);
    }
  }
}
