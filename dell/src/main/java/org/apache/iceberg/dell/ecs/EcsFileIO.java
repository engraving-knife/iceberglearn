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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.dell.DellClientFactories;
import org.apache.iceberg.dell.DellClientFactory;
import org.apache.iceberg.dell.DellProperties;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.util.SerializableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Dell EMC ECS 的 {@link FileIO} 实现，提供数据/元数据文件的读写删能力。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #newInputFile(String)} / {@link #newOutputFile(String)} 创建基于 ECS 的 输入/输出文件。
 *   <li>{@link #deleteFile(String)} 删除 ECS 对象。
 *   <li>懒构造并复用 S3 客户端，按需加载 Hadoop 指标上下文。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>URI 规范：location 须符合 ECS URI 约定（如 {@code ecs://bucket/path}）， 同时兼容 s3/s3a/s3n/https
 *       scheme；其他 scheme 会触发 {@link org.apache.iceberg.exceptions.ValidationException}。
 *   <li>序列化与懒构造：FileIO 需序列化到引擎 Executor 端执行，但 {@link S3Client} 不可序列化。故持有 {@link
 *       SerializableSupplier} 工厂引用，到 Executor 端首次使用时 才通过双重检查锁懒构造客户端（{@code transient volatile}）。
 *   <li>可选指标：尝试反射加载 HadoopMetricsContext，缺失时降级为空指标，保证不强依赖 Hadoop。
 *   <li>幂等关闭：用 {@link AtomicBoolean} 保证并发 close 只销毁一次客户端。
 * </ul>
 *
 * <p>上下游关系：被 {@link EcsCatalog} 作为默认 FileIO 使用，也可独立配置； 内部创建 {@link EcsInputFile}/{@link
 * EcsOutputFile}/{@link EcsURI}； 客户端由 {@link DellClientFactories} 提供。
 */
public class EcsFileIO implements FileIO {

  private static final Logger LOG = LoggerFactory.getLogger(EcsFileIO.class);
  private static final String DEFAULT_METRICS_IMPL =
      "org.apache.iceberg.hadoop.HadoopMetricsContext";

  private SerializableSupplier<S3Client> s3;
  private DellProperties dellProperties;
  private DellClientFactory dellClientFactory;
  private transient volatile S3Client client;
  private final AtomicBoolean isResourceClosed = new AtomicBoolean(false);
  private MetricsContext metrics = MetricsContext.nullMetrics();

  /**
   * 创建指定路径的输入文件。
   *
   * @param path ECS 对象 location
   * @return {@link EcsInputFile}
   */
  @Override
  public InputFile newInputFile(String path) {
    return EcsInputFile.fromLocation(path, client(), dellProperties, metrics);
  }

  /**
   * 创建指定路径的输出文件。
   *
   * @param path ECS 对象 location
   * @return {@link EcsOutputFile}
   */
  @Override
  public OutputFile newOutputFile(String path) {
    return EcsOutputFile.fromLocation(path, client(), dellProperties, metrics);
  }

  /**
   * 删除指定路径的 ECS 对象。
   *
   * @param path ECS 对象 location
   */
  @Override
  public void deleteFile(String path) {
    EcsURI uri = new EcsURI(path);

    client().deleteObject(uri.bucket(), uri.name());
  }

  /**
   * 懒获取 S3 客户端，使用双重检查锁保证单例。
   *
   * <p>逻辑：client 为 null 时进入同步块再次检查，仍为 null 则通过 {@link SerializableSupplier} 构造并赋值，避免每次调用都加锁。
   *
   * @return S3 客户端
   */
  private S3Client client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = s3.get();
        }
      }
    }
    return client;
  }

  /**
   * 初始化 FileIO：解析 Dell 配置、加载客户端工厂、绑定客户端工厂引用、初始化指标。
   *
   * @param properties catalog/fileio 配置属性
   */
  @Override
  public void initialize(Map<String, String> properties) {
    this.dellProperties = new DellProperties(properties);
    this.dellClientFactory = DellClientFactories.from(properties);
    this.s3 = dellClientFactory::ecsS3;
    initMetrics(properties);
  }

  /**
   * 尝试加载 Hadoop 指标上下文，失败时降级为空指标。
   *
   * <p>逻辑：通过 {@link DynConstructors} 反射加载 {@code HadoopMetricsContext}（单参 String 构造），成功则初始化并采用； 出现
   * NoClassDefFoundError/NoSuchMethodException/ClassCastException 时记 warn 并降级。
   *
   * @param properties 配置属性
   */
  @SuppressWarnings("CatchBlockLogException")
  private void initMetrics(Map<String, String> properties) {
    // 若 Hadoop 可用则上报 Hadoop 指标
    try {
      DynConstructors.Ctor<MetricsContext> ctor =
          DynConstructors.builder(MetricsContext.class)
              .hiddenImpl(DEFAULT_METRICS_IMPL, String.class)
              .buildChecked();
      MetricsContext context = ctor.newInstance("ecs");
      context.initialize(properties);
      this.metrics = context;
    } catch (NoClassDefFoundError | NoSuchMethodException | ClassCastException e) {
      LOG.warn(
          "Unable to load metrics class: '{}', falling back to null metrics", DEFAULT_METRICS_IMPL);
    }
  }

  /**
   * 关闭资源，销毁 S3 客户端。
   *
   * <p>逻辑：用 {@link AtomicBoolean#compareAndSet} 处理并发 close，保证只销毁一次。
   */
  @Override
  public void close() {
    // 处理并发的 close() 调用
    if (isResourceClosed.compareAndSet(false, true)) {
      client.destroy();
    }
  }
}
