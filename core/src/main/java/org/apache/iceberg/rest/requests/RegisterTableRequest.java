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

import org.apache.iceberg.rest.RESTRequest;
import org.immutables.value.Value;

/**
 * 文件级说明：注册已有表的 REST 请求模型（Immutable 接口）。
 *
 * <p>所属模块：iceberg-core（REST Catalog 请求模型层）。
 *
 * <p>职责：封装待注册表的名称与 metadata location，用于将已存在元数据文件的表注册到 Catalog。
 *
 * <p>设计意图：使用 Immutables 框架（{@link Value.Immutable}）自动生成不可变实现与 Builder， 简化样板代码。validate()
 * 默认空实现，因为字段类型已保证非 null。
 *
 * <p>上下游关系：服务端由 {@link org.apache.iceberg.rest.CatalogHandlers#registerTable} 处理。
 */
@Value.Immutable
public interface RegisterTableRequest extends RESTRequest {

  String name();

  String metadataLocation();

  @Override
  default void validate() {
    // nothing to validate as it's not possible to create an invalid instance
  }
}
