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
package org.apache.iceberg.hive;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.ClientPool;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.iceberg.util.ThreadPools;
import org.apache.thrift.TException;
import org.immutables.value.Value;

/**
 * 带 Caffeine 缓存的 Hive 客户端池实现。
 *
 * <p>所属模块：iceberg-hive-metastore（基于 Hive Metastore Thrift 客户端的 Catalog 实现， 位于 Iceberg 各 Catalog
 * 实现层；本类是该模块客户端连接管理的核心组件）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link ClientPool} 接口，对外提供在 {@link IMetaStoreClient} 上执行 Action 的能力。
 *   <li>对底层 {@link HiveClientPool} 实例做按"缓存键"维度的复用缓存，避免每个 Catalog 实例 都重复创建连接池。
 *   <li>支持基于 UGI、用户名、Hadoop 配置项等多种元素组合缓存键，使不同用户/配置互不干扰。
 *   <li>对长时间未访问的客户端池按淘汰间隔自动关闭并回收资源。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>连接复用：HMS Thrift 连接建立成本较高，多个 Catalog 实例共享同一物理连接池可降低开销。
 *   <li>隔离粒度可控：通过 {@link CatalogProperties#CLIENT_POOL_CACHE_KEYS} 让调用方自定义缓存键，
 *       在"全局共享"与"完全隔离"之间取得平衡。
 *   <li>Caffeine + 调度线程：Caffeine 不保证过期后立即触发 removalListener，故额外注册一个单线程 {@link Scheduler}
 *       主动清理过期客户端，确保连接被及时关闭。
 *   <li>静态缓存字段：clientPoolCache 为静态变量，进程级共享，构造时通过 init() 懒初始化。
 * </ul>
 *
 * <p>上下游关系：被 {@link HiveCatalog} 创建并持有，作为其访问 Hive Metastore 的客户端池； 内部委托给 {@link HiveClientPool}
 * 执行真正的 Thrift 调用。
 *
 * <p>缓存键元素（通过 {@link CatalogProperties#CLIENT_POOL_CACHE_KEYS} 指定，逗号分隔）：
 *
 * <ul>
 *   <li>ugi —— 当前 Hadoop {@link UserGroupInformation} 实例，区分执行用户主体。
 *   <li>user_name —— 仅取用户名（{@link UserGroupInformation#getUserName}），粒度比 ugi 粗。
 *   <li>conf:xxx —— 以 "conf:" 为前缀的任意配置名，将该配置值纳入缓存键， 例如 "conf:a.b.c" 会把 a.b.c 的值加入键，使不同默认 Catalog
 *       的配置不复用同一连接池。 可指定多个 conf 元素。
 * </ul>
 */
public class CachedClientPool implements ClientPool<IMetaStoreClient, TException> {

  private static final String CONF_ELEMENT_PREFIX = "conf:";

  private static Cache<Key, HiveClientPool> clientPoolCache;

  private final Configuration conf;
  private final int clientPoolSize;
  private final long evictionInterval;
  private final Key key;

  /**
   * 根据配置构造一个缓存型客户端池。
   *
   * <p>逻辑：从 properties 读取连接池大小、淘汰间隔与缓存键描述，提取缓存键后调用 {@link #init()} 懒初始化静态 Caffeine 缓存。
   *
   * @param conf Hadoop/Hive 配置，用于获取 metastore URI 等信息
   * @param properties Catalog 属性，包含连接池大小、淘汰间隔、缓存键等配置
   */
  CachedClientPool(Configuration conf, Map<String, String> properties) {
    this.conf = conf;
    this.clientPoolSize =
        PropertyUtil.propertyAsInt(
            properties,
            CatalogProperties.CLIENT_POOL_SIZE,
            CatalogProperties.CLIENT_POOL_SIZE_DEFAULT);
    this.evictionInterval =
        PropertyUtil.propertyAsLong(
            properties,
            CatalogProperties.CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS,
            CatalogProperties.CLIENT_POOL_CACHE_EVICTION_INTERVAL_MS_DEFAULT);
    this.key = extractKey(properties.get(CatalogProperties.CLIENT_POOL_CACHE_KEYS), conf);
    init();
  }

  /**
   * 获取与当前缓存键关联的 {@link HiveClientPool}，不存在则按池大小新建并缓存。
   *
   * @return 当前缓存键对应的客户端池实例
   */
  @VisibleForTesting
  HiveClientPool clientPool() {
    return clientPoolCache.get(key, k -> new HiveClientPool(clientPoolSize, conf));
  }

  /**
   * 懒初始化静态 Caffeine 缓存。
   *
   * <p>逻辑：若 clientPoolCache 尚未创建，则构建一个带"访问后过期"策略和 removalListener 的 Caffeine
   * 缓存，并注册单线程调度器主动清理过期客户端池。
   *
   * <p>设计要点：Caffeine 不保证过期条目的 removalListener 被及时回调，故显式提供 {@link Scheduler} 以驱动清理，确保过期连接池的 close()
   * 被执行，避免 Thrift 连接泄漏。
   */
  private synchronized void init() {
    if (clientPoolCache == null) {
      // Since Caffeine does not ensure that removalListener will be involved after expiration
      // We use a scheduler with one thread to clean up expired clients.
      clientPoolCache =
          Caffeine.newBuilder()
              .expireAfterAccess(evictionInterval, TimeUnit.MILLISECONDS)
              .removalListener((ignored, value, cause) -> ((HiveClientPool) value).close())
              .scheduler(
                  Scheduler.forScheduledExecutorService(
                      ThreadPools.newScheduledPool("hive-metastore-cleaner", 1)))
              .build();
    }
  }

  /**
   * 返回静态共享的客户端池缓存（仅用于测试）。
   *
   * @return 进程级 Caffeine 缓存实例
   */
  @VisibleForTesting
  static Cache<Key, HiveClientPool> clientPoolCache() {
    return clientPoolCache;
  }

  /**
   * 在缓存的客户端池上执行 Action（默认重试）。
   *
   * @param action 要在 {@link IMetaStoreClient} 上执行的操作
   * @param <R> 返回值类型
   * @return action 的执行结果
   * @throws TException Thrift 调用异常
   * @throws InterruptedException 线程被中断
   */
  @Override
  public <R> R run(Action<R, IMetaStoreClient, TException> action)
      throws TException, InterruptedException {
    return clientPool().run(action);
  }

  /**
   * 在缓存的客户端池上执行 Action，可指定是否在连接异常时重试。
   *
   * @param action 要在 {@link IMetaStoreClient} 上执行的操作
   * @param retry 是否在连接异常时重试
   * @param <R> 返回值类型
   * @return action 的执行结果
   * @throws TException Thrift 调用异常
   * @throws InterruptedException 线程被中断
   */
  @Override
  public <R> R run(Action<R, IMetaStoreClient, TException> action, boolean retry)
      throws TException, InterruptedException {
    return clientPool().run(action, retry);
  }

  /**
   * 根据配置解析出缓存键。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>固定以 metastore URI 和 Hive Catalog 名作为基础元素，保证不同 metastore / catalog 必然不共享连接池。
   *   <li>若 cacheKeys 为空，直接返回仅含基础元素的 Key。
   *   <li>否则按逗号拆分 cacheKeys，对每个元素：
   *       <ul>
   *         <li>以 "conf:" 开头：提取配置名，校验不重复后把对应配置值封装为 {@link ConfElement}。
   *         <li>否则视作枚举类型（UGI/USER_NAME），校验不重复后收集到有序集合。
   *       </ul>
   *   <li>按枚举顺序追加 UGI / 用户名到元素列表。
   *   <li>按配置名顺序追加所有 ConfElement。
   *   <li>用最终元素列表构造不可变 {@link Key}。
   * </ol>
   *
   * <p>设计要点：所有元素按确定顺序加入列表，保证 Key 可比较且哈希稳定；conf 元素使用 TreeMap 排序以避免配置项顺序差异导致键不同。
   *
   * @param cacheKeys 来自 {@link CatalogProperties#CLIENT_POOL_CACHE_KEYS} 的原始字符串，逗号分隔
   * @param conf Hadoop 配置，用于读取 metastore URI、catalog 名及自定义配置项
   * @return 解析得到的缓存键
   * @throws ValidationException 当配置元素重复或类型未知时
   */
  @VisibleForTesting
  static Key extractKey(String cacheKeys, Configuration conf) {
    // generate key elements in a certain order, so that the Key instances are comparable
    List<Object> elements = Lists.newArrayList();
    elements.add(conf.get(HiveConf.ConfVars.METASTOREURIS.varname, ""));
    elements.add(conf.get(HiveCatalog.HIVE_CONF_CATALOG, "hive"));
    if (cacheKeys == null || cacheKeys.isEmpty()) {
      return Key.of(elements);
    }

    Set<KeyElementType> types = Sets.newTreeSet(Comparator.comparingInt(Enum::ordinal));
    Map<String, String> confElements = Maps.newTreeMap();
    for (String element : cacheKeys.split(",", -1)) {
      String trimmed = element.trim();
      if (trimmed.toLowerCase(Locale.ROOT).startsWith(CONF_ELEMENT_PREFIX)) {
        String key = trimmed.substring(CONF_ELEMENT_PREFIX.length());
        ValidationException.check(
            !confElements.containsKey(key), "Conf key element %s already specified", key);
        confElements.put(key, conf.get(key));
      } else {
        KeyElementType type = KeyElementType.valueOf(trimmed.toUpperCase());
        switch (type) {
          case UGI:
          case USER_NAME:
            ValidationException.check(
                !types.contains(type), "%s key element already specified", type.name());
            types.add(type);
            break;
          default:
            throw new ValidationException("Unknown key element %s", trimmed);
        }
      }
    }
    for (KeyElementType type : types) {
      switch (type) {
        case UGI:
          try {
            elements.add(UserGroupInformation.getCurrentUser());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          break;
        case USER_NAME:
          try {
            elements.add(UserGroupInformation.getCurrentUser().getUserName());
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          break;
        default:
          throw new RuntimeException("Unexpected key element " + type.name());
      }
    }
    for (String key : confElements.keySet()) {
      elements.add(ConfElement.of(key, confElements.get(key)));
    }
    return Key.of(elements);
  }

  @Value.Immutable
  abstract static class Key {

    abstract List<Object> elements();

    private static Key of(Iterable<?> elements) {
      return ImmutableKey.builder().elements(elements).build();
    }
  }

  @Value.Immutable
  abstract static class ConfElement {
    abstract String key();

    @Nullable
    abstract String value();

    static ConfElement of(String key, String value) {
      return ImmutableConfElement.builder().key(key).value(value).build();
    }
  }

  private enum KeyElementType {
    UGI,
    USER_NAME,
    CONF
  }
}
