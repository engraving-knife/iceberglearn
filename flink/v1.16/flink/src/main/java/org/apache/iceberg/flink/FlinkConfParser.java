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
 * Iceberg 表配置解析器，支持从表属性、SQL 选项和 Flink 全局配置中按优先级读取配置值。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：构造布尔/整数/长整型/字符串/枚举/时长等类型化的 配置解析器（ConfParser），用于 {@link
 * FlinkReadConf}/{@link FlinkWriteConf} 解析参数。
 *
 * <p>设计意图：构建器（Builder）+ 类型化解析器；优先级为 SQL 选项 > Flink 全局配置 > Iceberg 表属性 > 默认值。上下游：由
 * ReadConf/WriteConf 创建，向下读取 {@link ReadableConfig}、{@link Table#properties()} 与用户传入的 options。
 */
class FlinkConfParser {

  private final Map<String, String> tableProperties;
  private final Map<String, String> options;
  private final ReadableConfig readableConfig;

  /** 构造解析器，绑定 Iceberg 表属性、用户 SQL 选项与 Flink 可读配置。 */
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

  /** 布尔型配置解析器，必须提供默认值。 */
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

    /** 解析配置为布尔值，未设置时返回默认值。 */
    public boolean parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Boolean::parseBoolean, defaultValue);
    }
  }

  /** 整型配置解析器。 */
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

    /** 解析配置为 int，未设置时返回默认值。 */
    public int parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Integer::parseInt, defaultValue);
    }

    /** 解析可选 int 配置，未设置时返回 null。 */
    public Integer parseOptional() {
      return parse(Integer::parseInt, null);
    }
  }

  /** 长整型配置解析器。 */
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

    /** 解析配置为 long，未设置时返回默认值。 */
    public long parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Long::parseLong, defaultValue);
    }

    /** 解析可选 long 配置，未设置时返回 null。 */
    public Long parseOptional() {
      return parse(Long::parseLong, null);
    }
  }

  /** 字符串型配置解析器。 */
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

    /** 解析配置为字符串，未设置时返回默认值。 */
    public String parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Function.identity(), defaultValue);
    }

    /** 解析可选字符串配置，未设置时返回 null。 */
    public String parseOptional() {
      return parse(Function.identity(), null);
    }
  }

  /** 枚举型配置解析器。 */
  class EnumConfParser<E extends Enum<E>> extends ConfParser<EnumConfParser<E>, E> {
    private E defaultValue;
    private final Class<E> enumClass;

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

    /** 解析配置为枚举值，未设置时返回默认值。 */
    public E parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(s -> Enum.valueOf(enumClass, s), defaultValue);
    }

    /** 解析可选枚举配置，未设置时返回 null。 */
    public E parseOptional() {
      return parse(s -> Enum.valueOf(enumClass, s), null);
    }
  }

  /** 时长型配置解析器，使用 Flink 的 {@link TimeUtils#parseDuration} 解析字符串。 */
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

    /** 解析配置为 Duration，未设置时返回默认值。 */
    public Duration parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(TimeUtils::parseDuration, defaultValue);
    }

    /** 解析可选 Duration 配置，未设置时返回 null。 */
    public Duration parseOptional() {
      return parse(TimeUtils::parseDuration, null);
    }
  }

  /** 抽象配置解析器基类，定义 option/flinkConfig/tableProperty 三种来源， 解析时按该优先级查找。 */
  abstract class ConfParser<ThisT, T> {
    private final List<String> optionNames = Lists.newArrayList();
    private String tablePropertyName;
    private ConfigOption<T> configOption;

    /** 子类返回自身类型以支持链式调用。 */
    protected abstract ThisT self();

    /** 添加一个 SQL 选项 key（按添加顺序作为优先级）。 */
    public ThisT option(String name) {
      this.optionNames.add(name);
      return self();
    }

    /** 添加一个 Flink 全局 {@link ConfigOption} 作为来源。 */
    public ThisT flinkConfig(ConfigOption<T> newConfigOption) {
      this.configOption = newConfigOption;
      return self();
    }

    /** 添加一个 Iceberg 表属性 key 作为来源。 */
    public ThisT tableProperty(String name) {
      this.tablePropertyName = name;
      return self();
    }

    /**
     * 按优先级解析配置值。
     *
     * <p>逻辑：先依次查询 optionNames 中的 SQL 选项；其次查询 Flink 全局 configOption； 再次查询表属性 tablePropertyName；最后回退到
     * defaultValue。
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
