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
package org.apache.iceberg.rest.responses;

import java.util.Map;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.RESTResponse;

/**
 * 文件级说明：加载表的 REST 响应模型。
 *
 * <p>所属模块：iceberg-core（REST Catalog 响应模型层）。
 *
 * <p>职责：封装表元数据（{@link org.apache.iceberg.TableMetadata}）及其 metadata location，
 * 用于加载表、创建表、注册表、更新表等操作的统一响应。
 *
 * <p>设计意图：将 metadata 与 metadata-location 一起返回，减少代码重复； 同时携带可选的配置属性（config）用于表级覆盖。使用 Builder
 * 模式保证不可变性。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.rest.CatalogHandlers} 的多个方法返回， 由 {@link
 * org.apache.iceberg.rest.RESTTableOperations} 消费。
 */
public class LoadTableResponse implements RESTResponse {

  private String metadataLocation;
  private TableMetadata metadata;
  private Map<String, String> config;

  public LoadTableResponse() {
    // Required for Jackson deserialization
  }

  private LoadTableResponse(
      String metadataLocation, TableMetadata metadata, Map<String, String> config) {
    this.metadataLocation = metadataLocation;
    this.metadata = metadata;
    this.config = config;
  }

  @Override
  public void validate() {
    Preconditions.checkNotNull(metadata, "Invalid metadata: null");
  }

  public String metadataLocation() {
    return metadataLocation;
  }

  public TableMetadata tableMetadata() {
    return TableMetadata.buildFrom(metadata).withMetadataLocation(metadataLocation).build();
  }

  public Map<String, String> config() {
    return config != null ? config : ImmutableMap.of();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("metadataLocation", metadataLocation)
        .add("metadata", metadata)
        .add("config", config)
        .toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String metadataLocation;
    private TableMetadata metadata;
    private Map<String, String> config = Maps.newHashMap();

    private Builder() {}

    public Builder withTableMetadata(TableMetadata tableMetadata) {
      this.metadataLocation = tableMetadata.metadataFileLocation();
      this.metadata = tableMetadata;
      return this;
    }

    public Builder addConfig(String property, String value) {
      config.put(property, value);
      return this;
    }

    public Builder addAllConfig(Map<String, String> properties) {
      config.putAll(properties);
      return this;
    }

    public LoadTableResponse build() {
      Preconditions.checkNotNull(metadata, "Invalid metadata: null");
      return new LoadTableResponse(metadataLocation, metadata, config);
    }
  }
}
