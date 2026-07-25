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
package org.apache.iceberg.rest;

/**
 * 文件级说明：REST 消息标记接口，是 REST 请求和响应的共同父接口。
 *
 * <p>所属模块：iceberg-core（REST Catalog 消息抽象层）。
 *
 * <p>职责：定义 {@link #validate()} 方法，要求所有 REST 消息在构造/解析后进行合法性校验。
 *
 * <p>设计意图：从外部数据源（如 JSON 反序列化）构造的消息可能缺少必填字段， validate() 提供统一的校验入口。
 *
 * <p>上下游关系：被 {@link RESTRequest} 和 {@link RESTResponse} 继承。
 */
public interface RESTMessage {

  /**
   * 校验 REST 消息是否符合规范。
   *
   * <p>从外部数据源解析的消息可能缺少必填字段，此方法提供统一的合法性校验入口。
   *
   * @throws IllegalArgumentException 校验失败时抛出
   */
  void validate();
}
