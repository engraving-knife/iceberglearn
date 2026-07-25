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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 定义不同的指标收集模式，控制各列的 value_counts、null_value_counts、nan_value_counts、 lower_bounds、upper_bounds
 * 等统计信息是否持久化及如何持久化。
 *
 * <p>所属模块：iceberg-core，是表属性 {@code write.metadata.metrics.default} 等配置的解析与表示层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #fromString(String)} 将字符串配置解析为 {@link MetricsMode}。
 *   <li>定义四种模式：{@link None}（不收集）、{@link Counts}（仅计数）、 {@link Truncate}（计数 + 截断上下界）、{@link Full}（计数
 *       + 完整上下界）。
 * </ul>
 *
 * <p>设计意图：模式实现均为不可变单例或不可变值对象；通过 {@link ProxySerializableMetricsMode} 的 writeReplace 将自身代理为 {@link
 * MetricsModeProxy}，避免 Java 序列化破坏单例引用相等性。
 *
 * <p>上下游关系：由表属性解析得到，被写入器（如 {@code MetricsConfig}）与扫描流程读取以决定 统计信息收集与利用策略。
 */
public class MetricsModes {

  private static final Pattern TRUNCATE = Pattern.compile("truncate\\((\\d+)\\)");

  /** 私有构造器，禁止实例化。 */
  private MetricsModes() {}

  /**
   * 将字符串解析为 {@link MetricsMode}，支持 none/counts/full 及 truncate(N) 形式。
   *
   * <p>逻辑：忽略大小写匹配 none/counts/full；否则用正则匹配 truncate(N) 并解析截断长度； 均不匹配则抛 {@link
   * IllegalArgumentException}。
   *
   * @param mode 模式字符串
   * @return 对应的指标模式
   * @throws IllegalArgumentException 模式字符串非法
   */
  public static MetricsMode fromString(String mode) {
    if ("none".equalsIgnoreCase(mode)) {
      return None.get();
    } else if ("counts".equalsIgnoreCase(mode)) {
      return Counts.get();
    } else if ("full".equalsIgnoreCase(mode)) {
      return Full.get();
    }

    Matcher truncateMatcher = TRUNCATE.matcher(mode.toLowerCase(Locale.ENGLISH));
    if (truncateMatcher.matches()) {
      int length = Integer.parseInt(truncateMatcher.group(1));
      return Truncate.withLength(length);
    }

    throw new IllegalArgumentException("Invalid metrics mode: " + mode);
  }

  /** 指标计算模式接口，所有实现必须不可变。 */
  public interface MetricsMode extends Serializable {}

  /** 不收集任何指标模式：value_counts、null_value_counts、nan_value_counts、lower_bounds、upper_bounds 均不持久化。 */
  public static class None extends ProxySerializableMetricsMode {
    private static final None INSTANCE = new None();

    /** 返回 {@link None} 单例。 */
    public static None get() {
      return INSTANCE;
    }

    /** @return "none" */
    @Override
    public String toString() {
      return "none";
    }
  }

  /** 仅计数模式：持久化 value_counts、null_value_counts、nan_value_counts，不保存上下界。 */
  public static class Counts extends ProxySerializableMetricsMode {
    private static final Counts INSTANCE = new Counts();

    /** 返回 {@link Counts} 单例。 */
    public static Counts get() {
      return INSTANCE;
    }

    /** @return "counts" */
    @Override
    public String toString() {
      return "counts";
    }
  }

  /** 截断模式：持久化计数与截断后的 lower_bounds、upper_bounds，截断长度由 {@link #length()} 决定。 */
  public static class Truncate extends ProxySerializableMetricsMode {
    private final int length;

    /**
     * 私有构造器，指定截断长度。
     *
     * @param length 截断长度
     */
    private Truncate(int length) {
      this.length = length;
    }

    /**
     * 创建指定截断长度的 {@link Truncate} 实例，长度必须为正。
     *
     * @param length 截断长度
     * @return 截断模式实例
     */
    public static Truncate withLength(int length) {
      Preconditions.checkArgument(length > 0, "Truncate length should be positive");
      return new Truncate(length);
    }

    /** 返回 截断长度。 */
    public int length() {
      return length;
    }

    /** @return 形如 truncate(N) 的字符串 */
    @Override
    public String toString() {
      return String.format("truncate(%d)", length);
    }

    /** 按截断长度判断相等。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if (!(other instanceof Truncate)) {
        return false;
      }
      Truncate truncate = (Truncate) other;
      return length == truncate.length;
    }

    /** 按截断长度计算哈希。 */
    @Override
    public int hashCode() {
      return Integer.hashCode(length);
    }
  }

  /** 完整模式：持久化计数与完整的 lower_bounds、upper_bounds（不截断）。 */
  public static class Full extends ProxySerializableMetricsMode {
    private static final Full INSTANCE = new Full();

    /** 返回 {@link Full} 单例。 */
    public static Full get() {
      return INSTANCE;
    }

    /** @return "full" */
    @Override
    public String toString() {
      return "full";
    }
  }

  /** 可序列化的指标模式抽象基类，通过 writeReplace 代理为 {@link MetricsModeProxy}， 避免直接序列化单例破坏引用相等性。 */
  // we cannot serialize/deserialize MetricsMode directly as it breaks reference equality used in
  // metrics utils
  private abstract static class ProxySerializableMetricsMode implements MetricsMode {
    /**
     * 序列化时替换为基于字符串的代理对象。
     *
     * @return {@link MetricsModeProxy} 代理
     * @throws ObjectStreamException 不会抛出
     */
    Object writeReplace() throws ObjectStreamException {
      return new MetricsModeProxy(toString());
    }
  }

  /** 指标模式序列化代理：保存模式的字符串表示，反序列化时通过 {@link MetricsModes#fromString} 还原。 */
  private static class MetricsModeProxy implements Serializable {
    private String modeAsString;

    /**
     * 构造代理，记录模式字符串。
     *
     * @param modeAsString 模式字符串
     */
    MetricsModeProxy(String modeAsString) {
      this.modeAsString = modeAsString;
    }

    /**
     * 反序列化时还原为对应的 {@link MetricsMode}。
     *
     * @return 还原后的指标模式
     * @throws ObjectStreamException 不会抛出
     */
    Object readResolve() throws ObjectStreamException {
      return MetricsModes.fromString(modeAsString);
    }
  }
}
