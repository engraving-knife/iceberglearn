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
package org.apache.iceberg.spark.procedures;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.function.BiFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.Spark3Util;
import org.apache.iceberg.spark.Spark3Util.CatalogAndIdentifier;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.MapData;
import org.apache.spark.sql.connector.catalog.CatalogPlugin;
import org.apache.spark.sql.connector.catalog.Identifier;
import org.apache.spark.sql.connector.catalog.TableCatalog;
import org.apache.spark.sql.connector.iceberg.catalog.ProcedureParameter;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

/**
 * 存储过程输入参数的统一访问封装。
 *
 * <p>所属模块：iceberg-spark（procedures 子包）。封装 Spark {@link InternalRow} 形式的过程实参， 提供按 {@link
 * ProcedureParameter} 安全读取 boolean/long/string/字符串数组/字符串 map/标识符等 类型的方法，并处理默认值与类型校验。
 *
 * <p>设计意图：把"从 InternalRow 按序号取值 + 类型校验 + 默认值"的通用逻辑抽离，让各过程 实现聚焦业务；通过参数名→序号的映射屏蔽实参顺序细节。
 *
 * <p>上下游关系：由各 {@link BaseProcedure} 子类在 call 中构造使用。
 */
class ProcedureInput {

  private static final DataType STRING_ARRAY = DataTypes.createArrayType(DataTypes.StringType);
  private static final DataType STRING_MAP =
      DataTypes.createMapType(DataTypes.StringType, DataTypes.StringType);

  private final SparkSession spark;
  private final TableCatalog catalog;
  private final Map<String, Integer> paramOrdinals;
  private final InternalRow args;

  /** 构造输入封装，计算参数名到序号的映射。 */
  ProcedureInput(
      SparkSession spark, TableCatalog catalog, ProcedureParameter[] params, InternalRow args) {
    this.spark = spark;
    this.catalog = catalog;
    this.paramOrdinals = computeParamOrdinals(params);
    this.args = args;
  }

  /** 判断该参数是否已提供（非空）。 */
  public boolean isProvided(ProcedureParameter param) {
    int ordinal = ordinal(param);
    return !args.isNullAt(ordinal);
  }

  /** 读取 boolean 参数，空则返回默认值。 */
  public Boolean asBoolean(ProcedureParameter param, Boolean defaultValue) {
    validateParamType(param, DataTypes.BooleanType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : (Boolean) args.getBoolean(ordinal);
  }

  /** 读取 long 参数，未设置抛异常。 */
  public long asLong(ProcedureParameter param) {
    Long value = asLong(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /** 读取 long 参数，空则返回默认值。 */
  public Long asLong(ProcedureParameter param, Long defaultValue) {
    validateParamType(param, DataTypes.LongType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : (Long) args.getLong(ordinal);
  }

  /** 读取 string 参数，未设置抛异常。 */
  public String asString(ProcedureParameter param) {
    String value = asString(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /** 读取 string 参数，空则返回默认值。 */
  public String asString(ProcedureParameter param, String defaultValue) {
    validateParamType(param, DataTypes.StringType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : args.getString(ordinal);
  }

  /** 读取字符串数组参数，未设置抛异常。 */
  public String[] asStringArray(ProcedureParameter param) {
    String[] value = asStringArray(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /** 读取字符串数组参数，空则返回默认值。 */
  public String[] asStringArray(ProcedureParameter param, String[] defaultValue) {
    validateParamType(param, STRING_ARRAY);
    return array(
        param,
        (array, ordinal) -> array.getUTF8String(ordinal).toString(),
        String.class,
        defaultValue);
  }

  /** 通用数组读取：按元素转换函数将 ArrayData 转为目标类型数组，空则返回默认值。 */
  @SuppressWarnings("unchecked")
  private <T> T[] array(
      ProcedureParameter param,
      BiFunction<ArrayData, Integer, T> convertElement,
      Class<T> elementClass,
      T[] defaultValue) {

    int ordinal = ordinal(param);

    if (args.isNullAt(ordinal)) {
      return defaultValue;
    }

    ArrayData arrayData = args.getArray(ordinal);

    T[] convertedArray = (T[]) Array.newInstance(elementClass, arrayData.numElements());

    for (int index = 0; index < arrayData.numElements(); index++) {
      convertedArray[index] = convertElement.apply(arrayData, index);
    }

    return convertedArray;
  }

  /** 读取字符串 map 参数，空则返回默认值。 */
  public Map<String, String> asStringMap(
      ProcedureParameter param, Map<String, String> defaultValue) {
    validateParamType(param, STRING_MAP);
    return map(
        param,
        (keys, ordinal) -> keys.getUTF8String(ordinal).toString(),
        (values, ordinal) -> values.getUTF8String(ordinal).toString(),
        defaultValue);
  }

  /** 通用 map 读取：按键/值转换函数将 MapData 转为 Map，空则返回默认值。 */
  private <K, V> Map<K, V> map(
      ProcedureParameter param,
      BiFunction<ArrayData, Integer, K> convertKey,
      BiFunction<ArrayData, Integer, V> convertValue,
      Map<K, V> defaultValue) {

    int ordinal = ordinal(param);

    if (args.isNullAt(ordinal)) {
      return defaultValue;
    }

    MapData mapData = args.getMap(ordinal);

    Map<K, V> convertedMap = Maps.newHashMap();

    for (int index = 0; index < mapData.numElements(); index++) {
      K convertedKey = convertKey.apply(mapData.keyArray(), index);
      V convertedValue = convertValue.apply(mapData.valueArray(), index);
      convertedMap.put(convertedKey, convertedValue);
    }

    return convertedMap;
  }

  /** 将参数解析为标识符，并校验其所属 Catalog 与当前过程 Catalog 一致。 */
  public Identifier ident(ProcedureParameter param) {
    CatalogAndIdentifier catalogAndIdent = catalogAndIdent(param, catalog);

    Preconditions.checkArgument(
        catalogAndIdent.catalog().equals(catalog),
        "Cannot run procedure in catalog '%s': '%s' is a table in catalog '%s'",
        catalog.name(),
        catalogAndIdent.identifier(),
        catalogAndIdent.catalog().name());

    return catalogAndIdent.identifier();
  }

  /** 将参数解析为标识符，使用指定默认 Catalog。 */
  public Identifier ident(ProcedureParameter param, CatalogPlugin defaultCatalog) {
    CatalogAndIdentifier catalogAndIdent = catalogAndIdent(param, defaultCatalog);
    return catalogAndIdent.identifier();
  }

  /** 将字符串参数解析为 CatalogAndIdentifier，空串抛异常。 */
  private CatalogAndIdentifier catalogAndIdent(
      ProcedureParameter param, CatalogPlugin defaultCatalog) {

    String identAsString = asString(param);

    Preconditions.checkArgument(
        StringUtils.isNotBlank(identAsString),
        "Cannot handle an empty identifier for parameter '%s'",
        param.name());

    String desc = String.format("identifier for parameter '%s'", param.name());
    return Spark3Util.catalogAndIdentifier(desc, spark, identAsString, defaultCatalog);
  }

  /** 返回参数对应的序号。 */
  private int ordinal(ProcedureParameter param) {
    return paramOrdinals.get(param.name());
  }

  /** 构建参数名到序号的映射，检测重名。 */
  private Map<String, Integer> computeParamOrdinals(ProcedureParameter[] params) {
    Map<String, Integer> ordinals = Maps.newHashMap();

    for (int index = 0; index < params.length; index++) {
      String paramName = params[index].name();

      Preconditions.checkArgument(
          !ordinals.containsKey(paramName),
          "Detected multiple parameters named as '%s'",
          paramName);

      ordinals.put(paramName, index);
    }

    return ordinals;
  }

  /** 校验参数类型与期望类型一致。 */
  private void validateParamType(ProcedureParameter param, DataType expectedDataType) {
    Preconditions.checkArgument(
        expectedDataType.sameType(param.dataType()),
        "Parameter '%s' must be of type %s",
        param.name(),
        expectedDataType.catalogString());
  }
}
