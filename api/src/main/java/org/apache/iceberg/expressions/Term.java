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
package org.apache.iceberg.expressions;

import java.io.Serializable;

/**
 * 表达式项（Term）：求值为一个值的表达式的顶层标记接口。
 *
 * <p>所属模块：iceberg-api（表达式体系中“项”分支的根接口，与谓词 {@link Predicate} 分支并列， 共同构成 {@link Expression} 树）。
 *
 * <p>职责：标记“可求值为值”的表达式节点，作为 {@link UnboundTerm}、{@link BoundTerm}、 {@link NamedReference}、{@link
 * BoundTransform} 等的共同父类型，便于在谓词中统一引用一个“项” 作为操作数。
 *
 * <p>设计意图：仅继承 {@link Serializable} 而无方法，是典型的标记接口（marker interface）。 继承 Serializable 是因为表达式树需要在引擎与
 * Iceberg 之间序列化传递（如任务序列化）。 将“项”与“谓词”分开，使类型系统能区分“产生值的子表达式”与“产生布尔的判断”， 例如 {@code lessThan(term,
 * value)} 中 term 必须是 Term。
 *
 * <p>上下游关系：被 {@link Predicate} 持有为操作数；具体实现有 {@link UnboundTerm}（未绑定项）与 {@link BoundTerm}（已绑定项）。
 */
public interface Term extends Serializable {}
