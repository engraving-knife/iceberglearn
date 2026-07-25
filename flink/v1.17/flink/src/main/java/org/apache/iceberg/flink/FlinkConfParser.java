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
package org.apache.iceberg.flink;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.util.TimeUtils;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Iceberg Flink 配置解析器，提供链式 API 从多个来源解析配置值。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块根包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>统一从 SQL Hint 选项、Flink 全局配置、Iceberg 表属性三种来源按优先级解析配置值。
 *   <li>按类型提供布尔/整数/长整/字符串/枚举/时长等专用解析子类。
 *   <li>支持必填（带默认值）与可选（返回 Optional）两种解析模式。
 * </ul>
 *
 * <p>设计意图：将配置解析逻辑集中到一个链式构造器风格的对象中， 使用方代码更具可读性，避免重复解析样板代码。
 *
 * <p>上下游关系：上游为 {@link FlinkReadConf} 与 {@link FlinkWriteConf}， 下游为 Flink 的 {@link ReadableConfig} 与
 * Iceberg 表属性。
 */
class FlinkConfParser {

  private final Map<String, String> tableProperties;
  private final Map<String, String> options;
  private final ReadableConfig readableConfig;

  /** 构造解析器，绑定表属性、SQL Hint 选项和 Flink 全局配置。 */
  FlinkConfParser(Table table, Map<String, String> options, ReadableConfig readableConfig) {
    this.tableProperties = table.properties();
    this.options = options;
    this.readableConfig = readableConfig;
  }

  /** 创建布尔型配置解析器。 */
  public BooleanConfParser booleanConf() {
    return new BooleanConfParser();
  }

  /** 创建整型配置解析器。 */
  public IntConfParser intConf() {
    return new IntConfParser();
  }

  /** 创建长整型配置解析器。 */
  public LongConfParser longConf() {
    return new LongConfParser();
  }

  /** 创建指定枚举类型的配置解析器。 */
  public <E extends Enum<E>> EnumConfParser<E> enumConfParser(Class<E> enumClass) {
    return new EnumConfParser<>(enumClass);
  }

  /** 创建字符串型配置解析器。 */
  public StringConfParser stringConf() {
    return new StringConfParser();
  }

  /** 创建时长型配置解析器。 */
  public DurationConfParser durationConf() {
    return new DurationConfParser();
  }

  /** 布尔型配置解析子类。 */
  class BooleanConfParser extends ConfParser<BooleanConfParser, Boolean> {
    private Boolean defaultValue;

    @Override
    protected BooleanConfParser self() {
      return this;
    }

    /** 设置布尔默认值。 */
    public BooleanConfParser defaultValue(boolean value) {
      this.defaultValue = value;
      return self();
    }

    /** 以字符串形式设置布尔默认值。 */
    public BooleanConfParser defaultValue(String value) {
      this.defaultValue = Boolean.parseBoolean(value);
      return self();
    }

    /** 解析配置并返回布尔值，无值时返回默认值。 */
    public boolean parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Boolean::parseBoolean, defaultValue);
    }
  }

  /** 整型配置解析子类。 */
  class IntConfParser extends ConfParser<IntConfParser, Integer> {
    private Integer defaultValue;

    @Override
    protected IntConfParser self() {
      return this;
    }

    /** 设置整型默认值。 */
    public IntConfParser defaultValue(int value) {
      this.defaultValue = value;
      return self();
    }

    /** 解析配置并返回 int 值，无值时返回默认值。 */
    public int parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Integer::parseInt, defaultValue);
    }

    /** 可选解析：未配置时返回 null。 */
    public Integer parseOptional() {
      return parse(Integer::parseInt, null);
    }
  }

  /** 长整型配置解析子类。 */
  class LongConfParser extends ConfParser<LongConfParser, Long> {
    private Long defaultValue;

    @Override
    protected LongConfParser self() {
      return this;
    }

    /** 设置长整型默认值。 */
    public LongConfParser defaultValue(long value) {
      this.defaultValue = value;
      return self();
    }

    /** 解析配置并返回 long 值，无值时返回默认值。 */
    public long parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Long::parseLong, defaultValue);
    }

    /** 可选解析：未配置时返回 null。 */
    public Long parseOptional() {
      return parse(Long::parseLong, null);
    }
  }

  /** 字符串型配置解析子类。 */
  class StringConfParser extends ConfParser<StringConfParser, String> {
    private String defaultValue;

    @Override
    protected StringConfParser self() {
      return this;
    }

    /** 设置字符串默认值。 */
    public StringConfParser defaultValue(String value) {
      this.defaultValue = value;
      return self();
    }

    /** 解析配置并返回字符串，无值时返回默认值。 */
    public String parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Function.identity(), defaultValue);
    }

    /** 可选解析：未配置时返回 null。 */
    public String parseOptional() {
      return parse(Function.identity(), null);
    }
  }

  /** 枚举型配置解析子类。 */
  class EnumConfParser<E extends Enum<E>> extends ConfParser<EnumConfParser<E>, E> {
    private E defaultValue;
    private final Class<E> enumClass;

    /** 构造枚举解析器，需指定枚举的 Class。 */
    EnumConfParser(Class<E> enumClass) {
      this.enumClass = enumClass;
    }

    @Override
    protected EnumConfParser<E> self() {
      return this;
    }

    /** 设置枚举默认值。 */
    public EnumConfParser<E> defaultValue(E value) {
      this.defaultValue = value;
      return self();
    }

    /** 解析配置并返回枚举值，无值时返回默认值。 */
    public E parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(s -> Enum.valueOf(enumClass, s), defaultValue);
    }

    /** 可选解析：未配置时返回 null。 */
    public E parseOptional() {
      return parse(s -> Enum.valueOf(enumClass, s), null);
    }
  }

  /** 时长型配置解析子类。 */
  class DurationConfParser extends ConfParser<DurationConfParser, Duration> {
    private Duration defaultValue;

    @Override
    protected DurationConfParser self() {
      return this;
    }

    /** 设置时长默认值。 */
    public DurationConfParser defaultValue(Duration value) {
      this.defaultValue = value;
      return self();
    }

    /** 解析配置并返回 Duration，无值时返回默认值。 */
    public Duration parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(TimeUtils::parseDuration, defaultValue);
    }

    /** 可选解析：未配置时返回 null。 */
    public Duration parseOptional() {
      return parse(TimeUtils::parseDuration, null);
    }
  }

  /**
   * 所有类型化解析器的抽象基类，定义配置来源注册与统一的解析算法。
   *
   * <p>逻辑：按优先级顺序解析配置—— SQL Hint 选项 &gt; Flink 全局配置 &gt; Iceberg 表属性 &gt; 默认值。
   */
  abstract class ConfParser<ThisT, T> {
    private final List<String> optionNames = Lists.newArrayList();
    private String tablePropertyName;
    private ConfigOption<T> configOption;

    /** 子类返回自身类型，支持链式 API。 */
    protected abstract ThisT self();

    /** 注册一个 SQL Hint 选项名。可多次调用注册多个等价选项名。 */
    public ThisT option(String name) {
      this.optionNames.add(name);
      return self();
    }

    /** 注册一个 Flink 全局配置项。 */
    public ThisT flinkConfig(ConfigOption<T> newConfigOption) {
      this.configOption = newConfigOption;
      return self();
    }

    /** 注册一个 Iceberg 表属性名。 */
    public ThisT tableProperty(String name) {
      this.tablePropertyName = name;
      return self();
    }

    /**
     * 按优先级解析配置值。
     *
     * <p>逻辑：依次在 SQL Hint 选项、Flink 全局配置、Iceberg 表属性中查找； 找到第一个非空值即返回并应用类型转换；都未命中则返回默认值。
     *
     * @param conversion 字符串到目标类型的转换函数
     * @param defaultValue 未命中时的默认值，可为 null
     * @return 解析得到的配置值
     */
    protected T parse(Function<String, T> conversion, T defaultValue) {
      if (!optionNames.isEmpty()) {
        for (String optionName : optionNames) {
          String optionValue = options.get(optionName);
          if (optionValue != null) {
            return conversion.apply(optionValue);
          }
        }
      }

      if (configOption != null) {
        T propertyValue = readableConfig.get(configOption);
        if (propertyValue != null) {
          return propertyValue;
        }
      }

      if (tablePropertyName != null) {
        String propertyValue = tableProperties.get(tablePropertyName);
        if (propertyValue != null) {
          return conversion.apply(propertyValue);
        }
      }

      return defaultValue;
    }
  }
}
