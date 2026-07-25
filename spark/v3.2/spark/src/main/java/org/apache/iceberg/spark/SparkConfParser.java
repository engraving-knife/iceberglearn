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
 * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkConfParser。
 */
class SparkConfParser {

  private final Map<String, String> properties;
  private final RuntimeConfig sessionConf;
  private final Map<String, String> options;

  SparkConfParser(SparkSession spark, Table table, Map<String, String> options) {
    this.properties = table.properties();
    this.sessionConf = spark.conf();
    this.options = options;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public BooleanConfParser booleanConf() {
    /** 执行该方法的具体逻辑。 */
    return new BooleanConfParser();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public IntConfParser intConf() {
    /** 执行该方法的具体逻辑。 */
    return new IntConfParser();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public LongConfParser longConf() {
    /** 执行该方法的具体逻辑。 */
    return new LongConfParser();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  public StringConfParser stringConf() {
    /** 执行该方法的具体逻辑。 */
    return new StringConfParser();
  }

  /**
   * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 BooleanConfParser。
   */
  class BooleanConfParser extends ConfParser<BooleanConfParser, Boolean> {
    private Boolean defaultValue;

    /** 执行该方法的具体逻辑。 */
    @Override
    protected BooleanConfParser self() {
      return this;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param value 参数
     * @return 结果对象
     */
    public BooleanConfParser defaultValue(boolean value) {
      this.defaultValue = value;
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param value 参数
     * @return 结果对象
     */
    public BooleanConfParser defaultValue(String value) {
      this.defaultValue = Boolean.parseBoolean(value);
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public boolean parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Boolean::parseBoolean, defaultValue);
    }
  }

  /**
   * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 IntConfParser。
   */
  class IntConfParser extends ConfParser<IntConfParser, Integer> {
    private Integer defaultValue;

    /** 执行该方法的具体逻辑。 */
    @Override
    protected IntConfParser self() {
      return this;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param value 参数
     * @return 结果对象
     */
    public IntConfParser defaultValue(int value) {
      this.defaultValue = value;
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public int parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Integer::parseInt, defaultValue);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public Integer parseOptional() {
      return parse(Integer::parseInt, null);
    }
  }

  /**
   * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 LongConfParser。
   */
  class LongConfParser extends ConfParser<LongConfParser, Long> {
    private Long defaultValue;

    /** 执行该方法的具体逻辑。 */
    @Override
    protected LongConfParser self() {
      return this;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param value 参数
     * @return 结果对象
     */
    public LongConfParser defaultValue(long value) {
      this.defaultValue = value;
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public long parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Long::parseLong, defaultValue);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public Long parseOptional() {
      return parse(Long::parseLong, null);
    }
  }

  /**
   * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 StringConfParser。
   */
  class StringConfParser extends ConfParser<StringConfParser, String> {
    private String defaultValue;

    /** 执行该方法的具体逻辑。 */
    @Override
    protected StringConfParser self() {
      return this;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param value 参数
     * @return 结果对象
     */
    public StringConfParser defaultValue(String value) {
      this.defaultValue = value;
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public String parse() {
      Preconditions.checkArgument(defaultValue != null, "Default value cannot be null");
      return parse(Function.identity(), defaultValue);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    public String parseOptional() {
      return parse(Function.identity(), null);
    }
  }

  /**
   * Iceberg Spark 集成相关组件，扩展 Spark SQL 解析能力。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ConfParser。
   */
  abstract class ConfParser<ThisT, T> {
    private final List<String> optionNames = Lists.newArrayList();
    private String sessionConfName;
    private String tablePropertyName;

    /** 执行该方法的具体逻辑。 */
    protected abstract ThisT self();

    /**
     * 执行该方法的具体逻辑。
     *
     * @param name 参数
     * @return 结果对象
     */
    public ThisT option(String name) {
      this.optionNames.add(name);
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param name 参数
     * @return 结果对象
     */
    public ThisT sessionConf(String name) {
      this.sessionConfName = name;
      return self();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param name 参数
     * @return 结果对象
     */
    public ThisT tableProperty(String name) {
      this.tablePropertyName = name;
      return self();
    }

    /** 执行该方法的具体逻辑。 */
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
