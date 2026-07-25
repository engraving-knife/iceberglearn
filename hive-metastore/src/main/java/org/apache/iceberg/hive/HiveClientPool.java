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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaHookLoader;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.RetryingMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.iceberg.ClientPoolImpl;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;

/**
 * Hive Metastore 客户端连接池实现。
 *
 * <p>所属模块：iceberg-hive-metastore（Hive Metastore Thrift 客户端连接管理层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link ClientPoolImpl}，管理 {@link IMetaStoreClient} 实例的创建、复用、重连与关闭。
 *   <li>通过反射（{@link DynMethods}）跨 Hive 版本兼容地创建 {@link RetryingMetaStoreClient} 代理。
 *   <li>识别连接异常并在连接失效时触发重连。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>跨版本兼容：Hive 1/2 与 Hive 3 的 {@code RetryingMetaStoreClient.getProxy} 签名不同 （前者用 HiveConf，后者用
 *       Configuration），通过 DynMethods 候选机制屏蔽差异。
 *   <li>不启用 ClientPoolImpl 自带重试（构造器 retry=false），因为底层已使用 RetryingMetaStoreClient 自带重试，避免双重重试。
 *   <li>持有独立的 HiveConf 副本并合并外部 Configuration，确保连接配置隔离且完整。
 * </ul>
 *
 * <p>上下游关系：被 {@link CachedClientPool} 创建并缓存；为 {@link HiveCatalog} 和 {@link HiveTableOperations}
 * 提供实际的 HMS Thrift 调用通道。
 */
public class HiveClientPool extends ClientPoolImpl<IMetaStoreClient, TException> {

  private static final DynMethods.StaticMethod GET_CLIENT =
      DynMethods.builder("getProxy")
          .impl(
              RetryingMetaStoreClient.class,
              HiveConf.class,
              HiveMetaHookLoader.class,
              String.class) // Hive 1 and 2
          .impl(
              RetryingMetaStoreClient.class,
              Configuration.class,
              HiveMetaHookLoader.class,
              String.class) // Hive 3
          .buildStatic();

  private final HiveConf hiveConf;

  /**
   * 构造 Hive Metastore 客户端池。
   *
   * <p>逻辑：调用父类构造器，指定池大小、连接异常类型为 {@link TTransportException}、 关闭父类重试（因底层 RetryingMetaStoreClient
   * 已含重试）；随后构造 HiveConf 并合并外部 Configuration。
   *
   * @param poolSize 连接池大小
   * @param conf Hadoop 配置（含 metastore URI 等）
   */
  public HiveClientPool(int poolSize, Configuration conf) {
    // Do not allow retry by default as we rely on RetryingHiveClient
    super(poolSize, TTransportException.class, false);
    this.hiveConf = new HiveConf(conf, HiveClientPool.class);
    this.hiveConf.addResource(conf);
  }

  /**
   * 创建一个新的 Hive Metastore 客户端实例。
   *
   * <p>逻辑：通过反射调用 {@code RetryingMetaStoreClient.getProxy}（跨版本兼容）创建带重试能力的 代理客户端；若反射抛出的
   * RuntimeException 包装了 {@link MetaException} 则解包重新抛出； 对 MetaException 包装为 {@link
   * RuntimeMetaException}；特别检测嵌入式 Derby 单连接冲突 并给出友好提示。
   *
   * @return 新的 IMetaStoreClient 实例
   * @throws RuntimeMetaException 连接 HMS 失败
   */
  @Override
  protected IMetaStoreClient newClient() {
    try {
      try {
        return GET_CLIENT.invoke(
            hiveConf, (HiveMetaHookLoader) tbl -> null, HiveMetaStoreClient.class.getName());
      } catch (RuntimeException e) {
        // any MetaException would be wrapped into RuntimeException during reflection, so let's
        // double-check type here
        if (e.getCause() instanceof MetaException) {
          throw (MetaException) e.getCause();
        }
        throw e;
      }
    } catch (MetaException e) {
      throw new RuntimeMetaException(e, "Failed to connect to Hive Metastore");
    } catch (Throwable t) {
      if (t.getMessage().contains("Another instance of Derby may have already booted")) {
        throw new RuntimeMetaException(
            t,
            "Failed to start an embedded metastore because embedded "
                + "Derby supports only one client at a time. To fix this, use a metastore that supports "
                + "multiple clients.");
      }

      throw new RuntimeMetaException(t, "Failed to connect to Hive Metastore");
    }
  }

  /**
   * 重连已有的 Hive Metastore 客户端。
   *
   * <p>逻辑：先 close 再 reconnect，复用原客户端对象的连接配置重建底层 Thrift 连接。
   *
   * @param client 需要重连的客户端
   * @return 重连后的同一客户端对象
   * @throws RuntimeMetaException 重连失败
   */
  @Override
  protected IMetaStoreClient reconnect(IMetaStoreClient client) {
    try {
      client.close();
      client.reconnect();
    } catch (MetaException e) {
      throw new RuntimeMetaException(e, "Failed to reconnect to Hive Metastore");
    }
    return client;
  }

  /**
   * 判断异常是否为连接异常（可重连）。
   *
   * <p>逻辑：除父类判定的连接异常外，额外识别消息中包含 TTransportException 的 {@link MetaException}（HMS 有时将传输异常包装为
   * MetaException）。
   *
   * @param e 待判断异常
   * @return true 表示该异常可触发重连
   */
  @Override
  protected boolean isConnectionException(Exception e) {
    return super.isConnectionException(e)
        || (e != null
            && e instanceof MetaException
            && e.getMessage()
                .contains("Got exception: org.apache.thrift.transport.TTransportException"));
  }

  /** 关闭指定的 Hive Metastore 客户端。 */
  @Override
  protected void close(IMetaStoreClient client) {
    client.close();
  }

  /** 返回当前客户端池使用的 HiveConf（仅测试用）。 */
  @VisibleForTesting
  HiveConf hiveConf() {
    return hiveConf;
  }
}
