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
package org.apache.iceberg.spark;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.apache.iceberg.Table;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.spark.sql.RuntimeConfig;
import org.apache.spark.sql.SparkSession;

/**
 * Spark 配置解析器：按优先级从 read option -> Spark 会话配置 -> 表属性 解析参数。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：提供类型安全的配置解析构造器（boolean/int/long/string），按统一优先级链路 （read option > session conf > table
 * property > default）解析最终值。
 *
 * <p>设计意图：采用建造者模式 + CRTP 让链式 API 返回子类型本身；统一三种来源的查找顺序， 避免各调用点重复实现。option 名做小写化比较以兼容 Spark 2 的
 * DataSourceOptions 行为。
 *
 * <p>上下游关系：被 Spark 读写选项解析（{@link SparkReadOptions} / {@link SparkWriteOptions}）等使用； 依赖 Spark
 * RuntimeConfig 与 Iceberg Table 属性。
 */
class SparkConfParser {

  private final Map<String, String> properties;
  private final RuntimeConfig sessionConf;
  private final Map<String, String> options;

  /** 构造解析器，绑定表属性、Spark 会话配置与 read option。 */
  SparkConfParser(SparkSession spark, Table table, Map<String, String> options) {
    this.properties = table.properties();
    this.sessionConf = spark.conf();
    this.options = options;
  }

  /** 创建布尔配置解析器。 */
  public BooleanConfParser booleanConf() {
    return new BooleanConfParser();
  }

  /** 创建整数配置解析器。 */
  public IntConfParser intConf() {
    return new IntConfParser();
  }

  /** 创建长整数配置解析器。 */
  public LongConfParser longConf() {
    return new LongConfParser();
  }

  /** 创建字符串配置解析器。 */
  public StringConfParser stringConf() {
    return new StringConfParser();
  }

  /** 布尔配置解析器，支持 negate 反转语义。 */
  class BooleanConfParser extends ConfParser<BooleanConfParser, Boolean> {
    private Boolean defaultValue;
    private boolean negate = false;
    /** 执行 self 相关操作。 */
    @Override
    protected BooleanConfParser self() {
      return this;
    }
    /** 执行 defaultValue 相关操作。 */
    public BooleanConfParser defaultValue(boolean value) {
      this.defaultValue = value;
      return self();
    }
    /** 执行 defaultValue 相关操作。 */
    public BooleanConfParser defaultValue(String value) {
      this.defaultValue = Boolean.parseBoolean(value);
      return self();
    }
    /** 执行 negate 相关操作。 */
    public BooleanConfParser negate() {
      this.negate = true;
      return self();
    }

    /** 解析为 boolean，要求必须设置默认值，可选反转。 */
    public boolean parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      boolean value = parse(Boolean::parseBoolean, defaultValue);
      return negate ? !value : value;
    }
  }

  /** 整数配置解析器。 */
  class IntConfParser extends ConfParser<IntConfParser, Integer> {
    private Integer defaultValue;
    /** 执行 self 相关操作。 */
    @Override
    protected IntConfParser self() {
      return this;
    }
    /** 执行 defaultValue 相关操作。 */
    public IntConfParser defaultValue(int value) {
      this.defaultValue = value;
      return self();
    }
    /** 解析输入。 */
    public int parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Integer::parseInt, defaultValue);
    }
    /** 执行 parseOptional 相关操作。 */
    public Integer parseOptional() {
      return parse(Integer::parseInt, defaultValue);
    }
  }

  /** 长整数配置解析器。 */
  class LongConfParser extends ConfParser<LongConfParser, Long> {
    private Long defaultValue;
    /** 执行 self 相关操作。 */
    @Override
    protected LongConfParser self() {
      return this;
    }
    /** 执行 defaultValue 相关操作。 */
    public LongConfParser defaultValue(long value) {
      this.defaultValue = value;
      return self();
    }
    /** 解析输入。 */
    public long parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Long::parseLong, defaultValue);
    }
    /** 执行 parseOptional 相关操作。 */
    public Long parseOptional() {
      return parse(Long::parseLong, defaultValue);
    }
  }

  /** 字符串配置解析器。 */
  class StringConfParser extends ConfParser<StringConfParser, String> {
    private String defaultValue;
    /** 执行 self 相关操作。 */
    @Override
    protected StringConfParser self() {
      return this;
    }
    /** 执行 defaultValue 相关操作。 */
    public StringConfParser defaultValue(String value) {
      this.defaultValue = value;
      return self();
    }
    /** 解析输入。 */
    public String parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Function.identity(), defaultValue);
    }
    /** 执行 parseOptional 相关操作。 */
    public String parseOptional() {
      return parse(Function.identity(), defaultValue);
    }
  }

  /**
   * 配置解析器抽象基类（CRTP）。
   *
   * <p>维护多个候选 option 名、单个 sessionConf 名、单个 tableProperty 名， 解析时按 option -> sessionConf ->
   * tableProperty -> default 顺序取首个命中值。
   */
  abstract class ConfParser<ThisT, T> {
    private final List<String> optionNames = Lists.newArrayList();
    private String sessionConfName;
    private String tablePropertyName;
    /** 执行 self 相关操作。 */
    protected abstract ThisT self();
    /** 执行 option 相关操作。 */
    public ThisT option(String name) {
      this.optionNames.add(name);
      return self();
    }
    /** 执行 sessionConf 相关操作。 */
    public ThisT sessionConf(String name) {
      this.sessionConfName = name;
      return self();
    }
    /** 执行 tableProperty 相关操作。 */
    public ThisT tableProperty(String name) {
      this.tablePropertyName = name;
      return self();
    }

    /**
     * 按优先级解析配置值。
     *
     * <p>逻辑：依次查找 optionNames（小写比较）、sessionConfName、tablePropertyName， 命中即用 conversion 转换返回；全部未命中返回
     * defaultValue。
     *
     * @param conversion 字符串到目标类型的转换函数
     * @param defaultValue 默认值
     * @return 解析得到的值
     */
    protected T parse(Function<String, T> conversion, T defaultValue) {
      if (!optionNames.isEmpty()) {
        for (String optionName : optionNames) {
          // use lower case comparison as DataSourceOptions.asMap() in Spark 2 returns a lower case
          // map
          String optionValue = options.get(optionName.toLowerCase(Locale.ROOT));
          if (optionValue != null) {
            return conversion.apply(optionValue);
          }
        }
      }

      if (sessionConfName != null) {
        String sessionConfValue = sessionConf.get(sessionConfName, null);
        if (sessionConfValue != null) {
          return conversion.apply(sessionConfValue);
        }
      }

      if (tablePropertyName != null) {
        String propertyValue = properties.get(tablePropertyName);
        if (propertyValue != null) {
          return conversion.apply(propertyValue);
        }
      }

      return defaultValue;
    }
  }
}
