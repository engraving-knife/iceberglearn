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

import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 重命名表的 REST 请求。
 *
 * <p>所属模块：iceberg-core，REST 请求模型层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载通过 REST 接口重命名表所需的源表与目标表标识；
 *   <li>对字段进行基础校验（源表与目标表标识均不可为 null）；
 *   <li>通过内部 Builder 收敛不可变对象的构造。
 * </ul>
 *
 * <p>设计意图：采用不可变值对象 + Builder 模式，构造完成后只读，保证在并发提交场景下 的安全性；无参构造仅为 Jackson 反序列化保留，外部不应直接使用。
 *
 * <p>上下游关系：由 {@code RESTCatalog} 等上层组件通过 Builder 构造，经 {@link org.apache.iceberg.rest.RESTClient}
 * 序列化后发送至 REST 服务端的 rename 接口。
 */
public class RenameTableRequest implements RESTRequest {

  private TableIdentifier source;
  private TableIdentifier destination;

  /** Jackson 反序列化所需的无参构造器，外部代码不应直接调用。 */
  @SuppressWarnings("unused")
  public RenameTableRequest() {
    // Needed for Jackson Deserialization.
  }

  /**
   * 私有全参构造器，仅由 {@link Builder#build()} 调用。
   *
   * <p>逻辑：赋值后立即调用 {@link #validate()} 进行基础校验。
   *
   * @param source 源表标识
   * @param destination 目标表标识
   */
  private RenameTableRequest(TableIdentifier source, TableIdentifier destination) {
    this.source = source;
    this.destination = destination;
    validate();
  }

  /**
   * 校验请求字段合法性。
   *
   * <p>逻辑：断言源表与目标表标识均不为 null，否则抛出 {@link IllegalArgumentException}。
   */
  @Override
  public void validate() {
    Preconditions.checkArgument(source != null, "Invalid source table: null");
    Preconditions.checkArgument(destination != null, "Invalid destination table: null");
  }

  /** 返回源表标识。 */
  public TableIdentifier source() {
    return source;
  }

  /** 返回目标表标识。 */
  public TableIdentifier destination() {
    return destination;
  }

  /** 返回该请求的可读字符串表示，便于日志输出与调试。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("source", source)
        .add("destination", destination)
        .toString();
  }

  /** 创建并返回一个新的 {@link Builder} 实例，用于构造 {@link RenameTableRequest}。 */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link RenameTableRequest} 的构造器。
   *
   * <p>设计意图：采用链式调用风格，逐步收集源表与目标表标识，最终在 {@link #build()} 一次性构造不可变实例。
   */
  public static class Builder {
    private TableIdentifier source;
    private TableIdentifier destination;

    /** 私有构造器，仅可通过 {@link RenameTableRequest#builder()} 获取实例。 */
    private Builder() {}

    /**
     * 设置源表标识。
     *
     * @param sourceTable 源表标识，不可为 null
     * @return 当前 Builder
     */
    public Builder withSource(TableIdentifier sourceTable) {
      Preconditions.checkNotNull(sourceTable, "Invalid source table identifier: null");
      this.source = sourceTable;
      return this;
    }

    /**
     * 设置目标表标识。
     *
     * @param destinationTable 目标表标识，不可为 null
     * @return 当前 Builder
     */
    public Builder withDestination(TableIdentifier destinationTable) {
      Preconditions.checkNotNull(destinationTable, "Invalid destination table identifier: null");
      this.destination = destinationTable;
      return this;
    }

    /**
     * 构建并返回不可变的 {@link RenameTableRequest} 实例。
     *
     * <p>逻辑：将收集到的源表与目标表标识传入私有构造器；构造器内部会执行 {@link RenameTableRequest#validate()} 校验。
     *
     * @return 新的 {@link RenameTableRequest} 实例
     */
    public RenameTableRequest build() {
      return new RenameTableRequest(source, destination);
    }
  }
}
