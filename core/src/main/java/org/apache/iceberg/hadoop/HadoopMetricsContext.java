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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.apache.hadoop.fs.FileSystem;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.io.FileIOMetricsContext;

/**
 * 文件级说明：基于 Hadoop {@link FileSystem.Statistics} 的 FileIO 指标实现。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：实现 {@link FileIOMetricsContext}，把 Iceberg 的读写计数器（读字节数、读操作数、 写字节数、写操作数）代理到 Hadoop {@link
 * FileSystem.Statistics}，从而复用 Hadoop 既有的指标采集与上报通道（例如通过 scheme 关联到对应文件系统的统计实例）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Hadoop 自身已维护 {@link FileSystem.Statistics}，Iceberg 不重复造轮子，仅做适配。
 *   <li>{@link #statistics()} 采用双检锁懒加载，避免序列化后 transient 字段为空时重复创建。
 *   <li>因指标值由 Hadoop 端直接消费，{@code unit()} 等方法被忽略，简化适配成本。
 * </ul>
 *
 * <p>上下游关系：由 {@link HadoopFileIO} 等基于 Hadoop 的 IO 实现创建并注册； 上报数据流向 Hadoop 的统计系统。
 */
public class HadoopMetricsContext implements FileIOMetricsContext {
  /** 配置项 key：用于覆盖指标上报所使用的文件系统 scheme。 */
  public static final String SCHEME = "io.metrics-scheme";

  private String scheme;
  private transient volatile FileSystem.Statistics statistics;

  /**
   * 构造一个针对指定 scheme 的指标上下文。
   *
   * @param scheme 文件系统 scheme（如 hdfs、s3a），用于关联 Hadoop 统计实例
   * @throws ValidationException 当 scheme 为 null 时抛出
   */
  public HadoopMetricsContext(String scheme) {
    ValidationException.check(
        scheme != null, "Scheme is required for Hadoop FileSystem metrics reporting");

    this.scheme = scheme;
  }

  /**
   * 用属性初始化指标上下文，允许通过 {@link #SCHEME} 覆盖默认 scheme。
   *
   * <p>逻辑：从属性中读取 {@link #SCHEME}，缺失则沿用构造时传入的 scheme； 然后调用 {@link FileSystem#getStatistics(String,
   * Class)} 获取（或创建）该 scheme 对应的 {@link FileSystem.Statistics} 实例。
   *
   * @param properties FileIO 属性
   */
  @Override
  public void initialize(Map<String, String> properties) {
    // FileIO has no specific implementation class, but Hadoop will
    // still track and report for the provided scheme.
    this.scheme = properties.getOrDefault(SCHEME, scheme);
    this.statistics = FileSystem.getStatistics(scheme, null);
  }

  /**
   * 创建一个数值型计数器，把增量代理到 Hadoop {@link FileSystem.Statistics} 的对应方法。
   *
   * <p>逻辑：根据 {@code name} 选择对应的 Hadoop 统计方法： {@code READ_BYTES}/{@code WRITE_BYTES} 要求 Long 类型，分别调用
   * {@code incrementBytesRead}/{@code incrementBytesWritten}； {@code READ_OPERATIONS}/{@code
   * WRITE_OPERATIONS} 要求 Integer 类型，分别调用 {@code incrementReadOps}/{@code incrementWriteOps}。{@code
   * unit} 参数被忽略。
   *
   * @param name 指标名称（如 {@link #READ_BYTES}）
   * @param type 计数器数值类型
   * @param unit 单位（被忽略）
   * @param <T> 数值类型
   * @return 计数器实例
   * @throws ValidationException 当类型与指标不匹配时抛出
   * @throws IllegalArgumentException 当指标名不支持时抛出
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T extends Number> Counter<T> counter(String name, Class<T> type, Unit unit) {
    switch (name) {
      case READ_BYTES:
        ValidationException.check(type == Long.class, "'%s' requires Long type", READ_BYTES);
        return (Counter<T>) longCounter(statistics()::incrementBytesRead);
      case READ_OPERATIONS:
        ValidationException.check(
            type == Integer.class, "'%s' requires Integer type", READ_OPERATIONS);
        return (Counter<T>) integerCounter(statistics()::incrementReadOps);
      case WRITE_BYTES:
        ValidationException.check(type == Long.class, "'%s' requires Long type", WRITE_BYTES);
        return (Counter<T>) longCounter(statistics()::incrementBytesWritten);
      case WRITE_OPERATIONS:
        ValidationException.check(
            type == Integer.class, "'%s' requires Integer type", WRITE_OPERATIONS);
        return (Counter<T>) integerCounter(statistics()::incrementWriteOps);
      default:
        throw new IllegalArgumentException(String.format("Unsupported counter: '%s'", name));
    }
  }

  /**
   * 创建一个绑定 Long 增量消费器的计数器。
   *
   * @param consumer 用于接收增量值的回调
   * @return Long 计数器
   */
  private Counter<Long> longCounter(Consumer<Long> consumer) {
    return new Counter<Long>() {
      @Override
      public void increment() {
        increment(1L);
      }

      @Override
      public void increment(Long amount) {
        consumer.accept(amount);
      }
    };
  }

  /**
   * 创建一个绑定 Integer 增量消费器的计数器。
   *
   * @param consumer 用于接收增量值的回调
   * @return Integer 计数器
   */
  private Counter<Integer> integerCounter(Consumer<Integer> consumer) {
    return new Counter<Integer>() {
      @Override
      public void increment() {
        increment(1);
      }

      @Override
      public void increment(Integer amount) {
        consumer.accept(amount);
      }
    };
  }

  /**
   * 创建一个 Iceberg {@link org.apache.iceberg.metrics.Counter}，同时支持增量和取值。
   *
   * <p>逻辑：根据 {@code name} 选择对应的 Hadoop 统计方法，并提供 {@code value()} 读取当前累计值。 {@code unit} 参数被忽略。
   *
   * @param name 指标名称
   * @param unit 单位（被忽略）
   * @return Iceberg 计数器
   * @throws IllegalArgumentException 当指标名不支持时抛出
   */
  @Override
  public org.apache.iceberg.metrics.Counter counter(String name, Unit unit) {
    switch (name) {
      case READ_BYTES:
        return counter(statistics()::incrementBytesRead, statistics()::getBytesRead);
      case READ_OPERATIONS:
        return counter((long x) -> statistics.incrementReadOps((int) x), statistics()::getReadOps);
      case WRITE_BYTES:
        return counter(statistics()::incrementBytesWritten, statistics()::getBytesWritten);
      case WRITE_OPERATIONS:
        return counter(
            (long x) -> statistics.incrementWriteOps((int) x), statistics()::getWriteOps);
      default:
        throw new IllegalArgumentException(String.format("Unsupported counter: '%s'", name));
    }
  }

  /**
   * 创建一个同时支持增量与取值的 Iceberg {@link org.apache.iceberg.metrics.Counter}。
   *
   * @param consumer 接收增量值的回调
   * @param supplier 提供当前累计值的回调
   * @return Iceberg 计数器
   */
  private org.apache.iceberg.metrics.Counter counter(LongConsumer consumer, LongSupplier supplier) {
    return new org.apache.iceberg.metrics.Counter() {
      @Override
      public void increment() {
        increment(1L);
      }

      @Override
      public void increment(long amount) {
        consumer.accept(amount);
      }

      @Override
      public long value() {
        return supplier.getAsLong();
      }
    };
  }

  /**
   * 获取（必要时懒创建）Hadoop {@link FileSystem.Statistics} 实例。
   *
   * <p>逻辑：因 {@code statistics} 是 {@code transient volatile}，反序列化后可能为 null， 故采用双检锁按当前 {@code scheme}
   * 重新获取。
   *
   * @return 当前 scheme 对应的统计实例
   */
  private FileSystem.Statistics statistics() {
    if (statistics == null) {
      synchronized (this) {
        if (statistics == null) {
          this.statistics = FileSystem.getStatistics(scheme, null);
        }
      }
    }

    return statistics;
  }
}
