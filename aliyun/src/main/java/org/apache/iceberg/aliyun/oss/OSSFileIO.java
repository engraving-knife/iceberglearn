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
package org.apache.iceberg.aliyun.oss;

import com.aliyun.oss.OSS;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.iceberg.aliyun.AliyunClientFactories;
import org.apache.iceberg.aliyun.AliyunClientFactory;
import org.apache.iceberg.aliyun.AliyunProperties;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.util.SerializableSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：基于阿里云 OSS 的 {@link FileIO} 实现。
 *
 * <p>所属模块：iceberg-aliyun（阿里云 OSS 存储集成模块）。本类是 iceberg-aliyun 模块对 iceberg-api 中 {@link FileIO} SPI
 * 的具体实现，位于 Iceberg 文件 IO 抽象层之下、阿里云 OSS SDK 之上，是连接 Iceberg 表格式与阿里云对象存储的核心适配器。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link FileIO} 接口，提供基于 OSS 的 {@link InputFile}/{@link OutputFile} 创建 与对象删除能力。
 *   <li>通过 {@link AliyunClientFactories} 加载并管理 OSS 客户端，支持自定义工厂。
 *   <li>可选地加载 Hadoop 指标上下文，上报 IO 度量数据；失败时回退到空指标。
 *   <li>管理客户端生命周期，提供线程安全的懒初始化与关闭逻辑。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>动态加载：提供无参构造器，便于 Iceberg 通过反射动态加载 FileIO 实现。
 *   <li>懒初始化客户端：{@code client} 字段为 transient volatile，序列化后在执行端 通过双重检查锁（DCL）懒构造，避免序列化 OSS 客户端本身。
 *   <li>可注入 supplier：通过 {@link SerializableSupplier} 注入 OSS 客户端供应器， 便于测试与自定义场景。
 *   <li>并发安全关闭：使用 {@link AtomicBoolean} 保证 {@link #close()} 在并发调用下 只关闭一次。
 * </ul>
 *
 * <p>上下游关系：上游为 Iceberg catalog / 引擎（通过 {@link FileIO} SPI 调用）； 下游依赖 {@link
 * AliyunClientFactories}、{@link OSSInputFile}、{@link OSSOutputFile} 以及阿里云 OSS SDK。
 *
 * <p>路径约定：location 必须符合 OSS URI 规范（如 {@code oss://bucket/path...}）， {@code https} scheme 也会被当作 OSS
 * 路径处理。使用其他 scheme 将抛出 {@link org.apache.iceberg.exceptions.ValidationException}。
 */
public class OSSFileIO implements FileIO {
  private static final Logger LOG = LoggerFactory.getLogger(OSSFileIO.class);
  private static final String DEFAULT_METRICS_IMPL =
      "org.apache.iceberg.hadoop.HadoopMetricsContext";

  private SerializableSupplier<OSS> oss;
  private AliyunProperties aliyunProperties;
  private transient volatile OSS client;
  private MetricsContext metrics = MetricsContext.nullMetrics();
  private final AtomicBoolean isResourceClosed = new AtomicBoolean(false);

  /**
   * 无参构造器，用于 Iceberg 动态加载 FileIO 实现。
   *
   * <p>所有字段在后续调用 {@link #initialize(Map)} 时完成初始化。
   */
  public OSSFileIO() {}

  /**
   * 使用自定义 OSS 客户端供应器构造 FileIO。
   *
   * <p>调用 {@link #initialize(Map)} 会覆盖此构造器设置的信息。
   *
   * @param oss OSS 客户端的序列化供应器
   */
  public OSSFileIO(SerializableSupplier<OSS> oss) {
    this.oss = oss;
    this.aliyunProperties = new AliyunProperties();
  }

  /**
   * 创建 OSS 输入文件（用于读取）。
   *
   * @param path OSS 路径字符串
   * @return 包装为 {@link InputFile} 的 {@link OSSInputFile}
   */
  @Override
  public InputFile newInputFile(String path) {
    return new OSSInputFile(client(), new OSSURI(path), aliyunProperties, metrics);
  }

  /**
   * 创建 OSS 输出文件（用于写入）。
   *
   * @param path OSS 路径字符串
   * @return 包装为 {@link OutputFile} 的 {@link OSSOutputFile}
   */
  @Override
  public OutputFile newOutputFile(String path) {
    return new OSSOutputFile(client(), new OSSURI(path), aliyunProperties, metrics);
  }

  /**
   * 删除指定路径的 OSS 对象。
   *
   * @param path 待删除对象的 OSS 路径
   */
  @Override
  public void deleteFile(String path) {
    OSSURI location = new OSSURI(path);
    client().deleteObject(location.bucket(), location.key());
  }

  /**
   * 懒获取 OSS 客户端实例（双重检查锁）。
   *
   * <p>逻辑：{@code client} 为 transient volatile，序列化后在执行端首次访问时通过 {@link SerializableSupplier#get()}
   * 构造，使用 DCL 保证线程安全。
   *
   * @return OSS 客户端实例
   */
  private OSS client() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = oss.get();
        }
      }
    }
    return client;
  }

  /**
   * 用 catalog 属性初始化 FileIO。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>通过 {@link AliyunClientFactories#from(Map)} 加载客户端工厂，获取 {@link AliyunProperties} 与 OSS
   *       客户端供应器。
   *   <li>尝试反射加载 Hadoop 指标上下文（{@code HadoopMetricsContext}），用于上报 IO 指标。
   *   <li>加载失败时回退到 {@link MetricsContext#nullMetrics()}，并记录警告日志。
   * </ol>
   *
   * @param properties catalog / 引擎传入的属性集合
   */
  @Override
  public void initialize(Map<String, String> properties) {
    AliyunClientFactory factory = AliyunClientFactories.from(properties);
    this.aliyunProperties = factory.aliyunProperties();
    this.oss = factory::newOSSClient;

    // Report Hadoop metrics if Hadoop is available
    try {
      DynConstructors.Ctor<MetricsContext> ctor =
          DynConstructors.builder(MetricsContext.class)
              .hiddenImpl(DEFAULT_METRICS_IMPL, String.class)
              .buildChecked();
      MetricsContext context = ctor.newInstance("oss");
      context.initialize(properties);
      this.metrics = context;
    } catch (NoClassDefFoundError | NoSuchMethodException | ClassCastException e) {
      LOG.warn(
          "Unable to load metrics class: '{}', falling back to null metrics",
          DEFAULT_METRICS_IMPL,
          e);
    }
  }

  /**
   * 关闭 FileIO 资源。
   *
   * <p>逻辑：使用 {@link AtomicBoolean#compareAndSet} 保证并发调用下只关闭一次； 若客户端已构造则调用 {@code shutdown()}
   * 释放连接资源。
   */
  @Override
  public void close() {
    // handles concurrent calls to close()
    if (isResourceClosed.compareAndSet(false, true)) {
      if (client != null) {
        client.shutdown();
      }
    }
  }
}
