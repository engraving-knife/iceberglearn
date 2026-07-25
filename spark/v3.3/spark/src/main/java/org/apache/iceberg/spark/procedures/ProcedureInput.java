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
 * Iceberg 存储过程，通过 Spark SQL CALL 调用，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ProcedureInput。
 *
 * <p>上下游：由 SparkSessionProcedures 注册，被 Spark SQL CALL 语句调用。
 */
class ProcedureInput {

  private static final DataType STRING_ARRAY = DataTypes.createArrayType(DataTypes.StringType);
  private static final DataType STRING_MAP =
      DataTypes.createMapType(DataTypes.StringType, DataTypes.StringType);

  private final SparkSession spark;
  private final TableCatalog catalog;
  private final Map<String, Integer> paramOrdinals;
  private final InternalRow args;

  ProcedureInput(
      SparkSession spark, TableCatalog catalog, ProcedureParameter[] params, InternalRow args) {
    this.spark = spark;
    this.catalog = catalog;
    this.paramOrdinals = computeParamOrdinals(params);
    this.args = args;
  }

  /** 判断是否provided。 */
  public boolean isProvided(ProcedureParameter param) {
    int ordinal = ordinal(param);
    return !args.isNullAt(ordinal);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultValue 参数
   * @return 结果对象
   */
  public Boolean asBoolean(ProcedureParameter param, Boolean defaultValue) {
    validateParamType(param, DataTypes.BooleanType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : (Boolean) args.getBoolean(ordinal);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @return 结果对象
   */
  public long asLong(ProcedureParameter param) {
    Long value = asLong(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultValue 参数
   * @return 结果对象
   */
  public Long asLong(ProcedureParameter param, Long defaultValue) {
    validateParamType(param, DataTypes.LongType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : (Long) args.getLong(ordinal);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @return 结果对象
   */
  public String asString(ProcedureParameter param) {
    String value = asString(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultValue 参数
   * @return 结果对象
   */
  public String asString(ProcedureParameter param, String defaultValue) {
    validateParamType(param, DataTypes.StringType);
    int ordinal = ordinal(param);
    return args.isNullAt(ordinal) ? defaultValue : args.getString(ordinal);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @return 结果对象
   */
  public String[] asStringArray(ProcedureParameter param) {
    String[] value = asStringArray(param, null);
    Preconditions.checkArgument(value != null, "Parameter '%s' is not set", param.name());
    return value;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultValue 参数
   * @return 结果对象
   */
  public String[] asStringArray(ProcedureParameter param, String[] defaultValue) {
    validateParamType(param, STRING_ARRAY);
    return array(
        param,
        (array, ordinal) -> array.getUTF8String(ordinal).toString(),
        String.class,
        defaultValue);
  }

  /** 执行该方法的具体逻辑。 */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultValue 参数
   * @return 结果对象
   */
  public Map<String, String> asStringMap(
      ProcedureParameter param, Map<String, String> defaultValue) {
    validateParamType(param, STRING_MAP);
    return map(
        param,
        (keys, ordinal) -> keys.getUTF8String(ordinal).toString(),
        (values, ordinal) -> values.getUTF8String(ordinal).toString(),
        defaultValue);
  }

  /** 执行该方法的具体逻辑。 */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @return 结果对象
   */
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

  /**
   * 执行该方法的具体逻辑。
   *
   * @param param 参数
   * @param defaultCatalog 参数
   * @return 结果对象
   */
  public Identifier ident(ProcedureParameter param, CatalogPlugin defaultCatalog) {
    CatalogAndIdentifier catalogAndIdent = catalogAndIdent(param, defaultCatalog);
    return catalogAndIdent.identifier();
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 执行该方法的具体逻辑。 */
  private int ordinal(ProcedureParameter param) {
    return paramOrdinals.get(param.name());
  }

  /** 执行该方法的具体逻辑。 */
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

  /** 校验前置条件或参数。 */
  private void validateParamType(ProcedureParameter param, DataType expectedDataType) {
    Preconditions.checkArgument(
        expectedDataType.sameType(param.dataType()),
        "Parameter '%s' must be of type %s",
        param.name(),
        expectedDataType.catalogString());
  }
}
