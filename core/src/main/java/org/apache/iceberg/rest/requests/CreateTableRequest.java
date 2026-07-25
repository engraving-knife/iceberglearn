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
package org.apache.iceberg.rest.requests;

import java.util.Map;
import java.util.Objects;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.UnboundPartitionSpec;
import org.apache.iceberg.UnboundSortOrder;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 创建表的 REST 请求。
 *
 * <p>所属模块：iceberg-core，REST 请求模型层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载通过 REST 接口创建表所需的全部参数：表名、位置、Schema、分区规则、写排序规则及表属性；
 *   <li>支持两种创建方式：直接提交创建，以及作为事务一部分的暂存式创建（stage-create）；
 *   <li>对自身字段进行基础校验（表名、Schema、stageCreate 标志均不可为 null）；
 *   <li>通过内部 Builder 收敛不可变对象的构造，避免外部直接赋值造成非法状态。
 * </ul>
 *
 * <p>设计意图：采用不可变值对象 + Builder 模式。所有字段在构造完成后只读，保证在多线程 并发提交场景下的安全性；{@link PartitionSpec} 与 {@link
 * SortOrder} 内部以 Unbound 形式 持有，延迟到 {@link #spec()} / {@link #writeOrder()} 调用时才与 Schema 绑定，避免在反序列
 * 化阶段过早校验。无参构造仅为 Jackson 反序列化保留。
 *
 * <p>上下游关系：由 {@code RESTCatalog} 等上层组件通过 Builder 构造，经 {@link org.apache.iceberg.rest.RESTClient}
 * 序列化发送至 REST 服务端；服务端解析后驱动 {@link org.apache.iceberg.TableMetadata} 的生成。
 */
public class CreateTableRequest implements RESTRequest {

  private String name;
  private String location;
  private Schema schema;
  private UnboundPartitionSpec partitionSpec;
  private UnboundSortOrder writeOrder;
  private Map<String, String> properties;
  private Boolean stageCreate = false;

  /** Jackson 反序列化所需的无参构造器，外部代码不应直接调用。 */
  public CreateTableRequest() {
    // Needed for Jackson Deserialization.
  }

  /**
   * 私有全参构造器，仅由 {@link Builder#build()} 调用。
   *
   * <p>逻辑：将外部传入的 {@link PartitionSpec} 与 {@link SortOrder} 转为 Unbound 形式存储， 避免过早与 Schema 绑定；最后调用
   * {@link #validate()} 进行基础校验。
   *
   * @param name 表名
   * @param location 表存储位置，可为 null
   * @param schema 表 Schema
   * @param partitionSpec 分区规则，可为 null
   * @param writeOrder 写排序规则，可为 null
   * @param properties 表属性键值对
   * @param stageCreate 是否为暂存式创建
   */
  private CreateTableRequest(
      String name,
      String location,
      Schema schema,
      PartitionSpec partitionSpec,
      SortOrder writeOrder,
      Map<String, String> properties,
      boolean stageCreate) {
    this.name = name;
    this.location = location;
    this.schema = schema;
    this.partitionSpec = partitionSpec != null ? partitionSpec.toUnbound() : null;
    this.writeOrder = writeOrder != null ? writeOrder.toUnbound() : null;
    this.properties = properties;
    this.stageCreate = stageCreate;
    validate();
  }

  /**
   * 校验请求字段合法性。
   *
   * <p>逻辑：依次断言表名、Schema、stageCreate 标志均不为 null，否则抛出 {@link IllegalArgumentException}。
   */
  @Override
  public void validate() {
    Preconditions.checkArgument(name != null, "Invalid table name: null");
    Preconditions.checkArgument(schema != null, "Invalid schema: null");
    Preconditions.checkArgument(stageCreate != null, "Invalid stageCreate flag: null");
  }

  /** 返回表名。 */
  public String name() {
    return name;
  }

  /** 返回表存储位置，可能为 null。 */
  public String location() {
    return location;
  }

  /** 返回表 Schema。 */
  public Schema schema() {
    return schema;
  }

  /**
   * 返回绑定到当前 Schema 的分区规则。
   *
   * <p>逻辑：当内部 Unbound 分区规则非 null 时，调用其 {@link UnboundPartitionSpec#bind(Schema)} 与 Schema
   * 绑定后返回；否则返回 null。
   *
   * @return 已绑定的 {@link PartitionSpec}，或 null
   */
  public PartitionSpec spec() {
    return partitionSpec != null ? partitionSpec.bind(schema) : null;
  }

  /**
   * 返回绑定到当前 Schema 的写排序规则。
   *
   * <p>逻辑：当内部 Unbound 排序规则非 null 时，调用其 {@link UnboundSortOrder#bind(Schema)} 与 Schema 绑定后返回；否则返回
   * null。
   *
   * @return 已绑定的 {@link SortOrder}，或 null
   */
  public SortOrder writeOrder() {
    return writeOrder != null ? writeOrder.bind(schema) : null;
  }

  /**
   * 返回表属性键值对。
   *
   * @return 不可变的属性 Map；当未设置时返回空 Map 而非 null
   */
  public Map<String, String> properties() {
    return properties != null ? properties : ImmutableMap.of();
  }

  /** 返回是否为暂存式创建。 */
  public boolean stageCreate() {
    return stageCreate;
  }

  /** 返回该请求的可读字符串表示，便于日志输出与调试。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("location", location)
        .add("properties", properties)
        .add("schema", schema)
        .add("partitionSpec", partitionSpec)
        .add("writeOrder", writeOrder)
        .add("stageCreate", stageCreate)
        .toString();
  }

  /** 创建并返回一个新的 {@link Builder} 实例，用于构造 {@link CreateTableRequest}。 */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link CreateTableRequest} 的构造器。
   *
   * <p>设计意图：采用链式调用风格，逐步收集参数；属性键值对通过内部 {@link ImmutableMap.Builder} 收集，最终在 {@link #build()} 一次性构建不可变
   * Map， 保证构造完成的对象不可变、线程安全。
   */
  public static class Builder {
    private String name;
    private String location;
    private Schema schema;
    private PartitionSpec partitionSpec;
    private SortOrder writeOrder;
    private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();
    private boolean stageCreate = false;

    /** 私有构造器，仅可通过 {@link CreateTableRequest#builder()} 获取实例。 */
    private Builder() {}

    /**
     * 设置表名。
     *
     * @param tableName 表名，不可为 null
     * @return 当前 Builder，便于链式调用
     */
    public Builder withName(String tableName) {
      Preconditions.checkNotNull(tableName, "Invalid name: null");
      this.name = tableName;
      return this;
    }

    /**
     * 设置表存储位置。
     *
     * @param newLocation 表存储位置，可为 null
     * @return 当前 Builder
     */
    public Builder withLocation(String newLocation) {
      this.location = newLocation;
      return this;
    }

    /**
     * 追加单个表属性。
     *
     * @param property 属性键，不可为 null
     * @param value 属性值，不可为 null
     * @return 当前 Builder
     */
    public Builder setProperty(String property, String value) {
      Preconditions.checkArgument(property != null, "Invalid property: null");
      Preconditions.checkArgument(value != null, "Invalid value for property %s: null", property);
      properties.put(property, value);
      return this;
    }

    /**
     * 批量追加表属性。
     *
     * <p>逻辑：先对键集合与值集合做非 null 校验（值为 null 时通过 {@link Maps#filterValues(Map,
     * java.util.function.Predicate)} 定位出错的键），再批量 合并到内部 Builder。
     *
     * @param props 属性键值对集合，不可为 null，且不能包含 null 键或 null 值
     * @return 当前 Builder
     */
    public Builder setProperties(Map<String, String> props) {
      Preconditions.checkNotNull(props, "Invalid collection of properties: null");
      Preconditions.checkArgument(!props.containsKey(null), "Invalid property: null");
      Preconditions.checkArgument(
          !props.containsValue(null),
          "Invalid value for properties %s: null",
          Maps.filterValues(props, Objects::isNull).keySet());
      properties.putAll(props);
      return this;
    }

    /**
     * 设置表 Schema。
     *
     * @param tableSchema 表 Schema，不可为 null
     * @return 当前 Builder
     */
    public Builder withSchema(Schema tableSchema) {
      Preconditions.checkNotNull(tableSchema, "Invalid schema: null");
      this.schema = tableSchema;
      return this;
    }

    /**
     * 设置分区规则，可为 null 表示不指定分区。
     *
     * @param tableSpec 分区规则
     * @return 当前 Builder
     */
    public Builder withPartitionSpec(PartitionSpec tableSpec) {
      this.partitionSpec = tableSpec;
      return this;
    }

    /**
     * 设置写排序规则，可为 null 表示使用默认排序。
     *
     * @param order 写排序规则
     * @return 当前 Builder
     */
    public Builder withWriteOrder(SortOrder order) {
      this.writeOrder = order;
      return this;
    }

    /** 标记本次创建为暂存式创建（stage-create）。 */
    public Builder stageCreate() {
      this.stageCreate = true;
      return this;
    }

    /**
     * 构建并返回不可变的 {@link CreateTableRequest} 实例。
     *
     * <p>逻辑：将收集到的属性 Map 构建为不可变形式，连同其余参数传入私有构造器； 构造器内部会执行 {@link CreateTableRequest#validate()} 校验。
     *
     * @return 新的 {@link CreateTableRequest} 实例
     */
    public CreateTableRequest build() {
      return new CreateTableRequest(
          name, location, schema, partitionSpec, writeOrder, properties.build(), stageCreate);
    }
  }
}
