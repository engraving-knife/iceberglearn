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
package org.apache.iceberg.snowflake;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.iceberg.relocated.com.google.common.base.Objects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * Snowflake 表元数据记录，不可变值对象。
 *
 * <p>所属模块：iceberg-snowflake。职责：封装从 Snowflake 查询到的表元数据，主要包含两个 metadata location——Snowflake
 * 路径语法（{@code snowflakeMetadataLocation}）与 Iceberg 路径语法 （{@code
 * icebergMetadataLocation}），以及表状态（{@code status}）。
 *
 * <p>设计意图：Snowflake 与 Iceberg 对云存储路径的表述存在差异（如 {@code azure://} vs {@code wasbs://}、{@code gcs://}
 * vs {@code gs://}），构造时即通过 {@link #snowflakeLocationToIcebergLocation(String)} 完成转换并缓存，后续读取零开销。
 * {@code rawJsonVal} 仅作调试用途，不参与 equals/hashCode。
 *
 * <p>上下游关系：由 {@link SnowflakeClient} 查询 Snowflake 后通过 {@link #parseJson(String)} 构造， 被 {@link
 * SnowflakeTableOperations} 用于定位 Iceberg 元数据文件。
 */
class SnowflakeTableMetadata {
  public static final Pattern SNOWFLAKE_AZURE_PATTERN =
      Pattern.compile("azure://([^/]+)/([^/]+)/(.*)");

  private final String snowflakeMetadataLocation;
  private final String icebergMetadataLocation;
  private final String status;

  // Note: Since not all sources will necessarily come from a raw JSON representation, this raw
  // JSON should only be considered a convenient debugging field. Equality of two
  // SnowflakeTableMetadata instances should not depend on equality of this field.
  private final String rawJsonVal;

  /**
   * 构造 Snowflake 表元数据记录。
   *
   * @param snowflakeMetadataLocation Snowflake 路径语法的元数据位置
   * @param icebergMetadataLocation Iceberg 路径语法的元数据位置（已转换）
   * @param status 表状态
   * @param rawJsonVal 原始 JSON 字符串，仅用于调试，不参与相等性判断
   */
  SnowflakeTableMetadata(
      String snowflakeMetadataLocation,
      String icebergMetadataLocation,
      String status,
      String rawJsonVal) {
    this.snowflakeMetadataLocation = snowflakeMetadataLocation;
    this.icebergMetadataLocation = icebergMetadataLocation;
    this.status = status;
    this.rawJsonVal = rawJsonVal;
  }

  /** 返回 Snowflake 路径语法的表元数据存储位置。 */
  public String snowflakeMetadataLocation() {
    return snowflakeMetadataLocation;
  }

  /** 返回 Iceberg 路径语法的表元数据存储位置（已从 Snowflake 语法转换）。 */
  public String icebergMetadataLocation() {
    return icebergMetadataLocation;
  }

  /** 返回表状态。 */
  public String getStatus() {
    return status;
  }

  /**
   * 相等性比较：仅比较 snowflakeMetadataLocation、icebergMetadataLocation、status 三个解析字段， 不比较 rawJsonVal（原始
   * JSON 可能并非实例来源，不应影响相等性）。
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (!(o instanceof SnowflakeTableMetadata)) {
      return false;
    }

    // Only consider parsed fields, not the raw JSON that may or may not be the original source of
    // this instance.
    SnowflakeTableMetadata that = (SnowflakeTableMetadata) o;
    return Objects.equal(this.snowflakeMetadataLocation, that.snowflakeMetadataLocation)
        && Objects.equal(this.icebergMetadataLocation, that.icebergMetadataLocation)
        && Objects.equal(this.status, that.status);
  }

  /** 返回基于三个解析字段的哈希值（与 equals 保持一致，不含 rawJsonVal）。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(snowflakeMetadataLocation, icebergMetadataLocation, status);
  }

  /** 返回包含三个解析字段的可读字符串表示（不含 rawJsonVal）。 */
  @Override
  public String toString() {
    return String.format(
        "snowflakeMetadataLocation: '%s', icebergMetadataLocation: '%s', status: '%s'",
        snowflakeMetadataLocation, icebergMetadataLocation, status);
  }

  /** 返回包含 rawJsonVal 的调试用字符串，比 {@link #toString()} 多输出原始 JSON。 */
  public String toDebugString() {
    return String.format("%s, rawJsonVal: %s", toString(), rawJsonVal);
  }

  /**
   * 把 Snowflake 路径语法转换为 Iceberg 路径语法。
   *
   * <p>逻辑：对已知的不兼容前缀做转换——{@code azure://account/container/path} 转为 {@code
   * wasbs://container@account/path}（用 {@link #SNOWFLAKE_AZURE_PATTERN} 匹配并重组）； {@code gcs://} 转为
   * {@code gs://}（仅替换 scheme）。其余路径原样返回。
   *
   * @param snowflakeLocation Snowflake 路径语法的 location
   * @return Iceberg 路径语法的 location
   * @throws IllegalArgumentException 若前缀为 {@code azure://} 但路径不匹配预期格式
   */
  public static String snowflakeLocationToIcebergLocation(String snowflakeLocation) {
    if (snowflakeLocation.startsWith("azure://")) {
      // Convert from expected path of the form:
      // azure://account.blob.core.windows.net/container/volumepath
      // to:
      // wasbs://container@account.blob.core.windows.net/volumepath
      Matcher matcher = SNOWFLAKE_AZURE_PATTERN.matcher(snowflakeLocation);
      Preconditions.checkArgument(
          matcher.matches(),
          "Location '%s' failed to match pattern '%s'",
          snowflakeLocation,
          SNOWFLAKE_AZURE_PATTERN);
      return String.format(
          "wasbs://%s@%s/%s", matcher.group(2), matcher.group(1), matcher.group(3));
    } else if (snowflakeLocation.startsWith("gcs://")) {
      // Convert from expected path of the form:
      // gcs://bucket/path
      // to:
      // gs://bucket/path
      return "gs" + snowflakeLocation.substring(3);
    }

    return snowflakeLocation;
  }

  /**
   * 工厂方法：把 Snowflake 返回的 JSON 字符串解析为 {@link SnowflakeTableMetadata} 对象。
   *
   * <p>逻辑：用 {@link JsonUtil#mapper()} 解析 JSON，读取 {@code metadataLocation} 与 {@code status}； 调用
   * {@link #snowflakeLocationToIcebergLocation(String)} 转换路径语法后构造实例， 原始 JSON 作为 rawJsonVal 保留供调试。
   *
   * @param json Snowflake 表元数据的 JSON 字符串
   * @return 解析得到的 {@link SnowflakeTableMetadata}
   * @throws IllegalArgumentException JSON 格式错误或缺少必填字段
   */
  public static SnowflakeTableMetadata parseJson(String json) {
    JsonNode parsedVal;
    try {
      parsedVal = JsonUtil.mapper().readValue(json, JsonNode.class);
    } catch (IOException ioe) {
      throw new IllegalArgumentException(String.format("Malformed JSON: %s", json), ioe);
    }

    String snowflakeMetadataLocation = JsonUtil.getString("metadataLocation", parsedVal);
    String status = JsonUtil.getStringOrNull("status", parsedVal);

    String icebergMetadataLocation = snowflakeLocationToIcebergLocation(snowflakeMetadataLocation);

    return new SnowflakeTableMetadata(
        snowflakeMetadataLocation, icebergMetadataLocation, status, json);
  }
}
