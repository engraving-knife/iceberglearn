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
 * 文件级说明：REST 响应标记接口。
 *
 * <p>所属模块：iceberg-core（REST Catalog 消息抽象层）。
 *
 * <p>职责：标记一个类为 REST 响应体，继承 {@link RESTMessage} 的 validate() 契约。
 *
 * <p>设计意图：空标记接口，使 {@link RESTClient} 的方法签名能通过泛型约束响应体类型。
 */
public interface RESTResponse extends RESTMessage {}
