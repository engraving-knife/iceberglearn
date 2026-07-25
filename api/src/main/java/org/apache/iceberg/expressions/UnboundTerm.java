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

/**
 * 未绑定项（UnboundTerm）：尚未解析到具体字段的“项”表达式，绑定后产出 {@link BoundTerm}。
 *
 * <p>所属模块：iceberg-api（表达式体系“项”分支的未绑定形态；与 {@link BoundTerm} 配对， 是 {@link NamedReference}
 * 等命名引用在未绑定阶段的统称）。
 *
 * <p>职责：继承 {@link Unbound} 与 {@link Term}，把“未绑定契约”（{@link #bind}、{@link #ref}）
 * 与“项”语义组合在一起，使谓词、聚合等表达式在构造阶段可统一持有未绑定项，待绑定阶段再解析为 按字段 id 访问的 {@link BoundTerm}。
 *
 * <p>设计意图：作为组合接口（同时 extends {@link Unbound} 和 {@link Term}）而不新增方法， 仅用于在类型系统中明确“未绑定的项”这一角色，便于 {@link
 * UnboundPredicate}、 {@link UnboundAggregate} 等以 {@code UnboundTerm<T>} 作为操作数类型，保证类型安全。
 *
 * <p>上下游关系：由 {@link Expressions} 工厂方法间接构造（如 {@link Expressions#ref} 产生 {@link
 * NamedReference}，它实现本接口）；被 {@link Binder} 调用 {@link #bind} 绑定为 {@link BoundTerm}，再交由已绑定谓词/聚合使用。
 *
 * @param <T> 该项求值产生的 Java 类型
 */
public interface UnboundTerm<T> extends Unbound<T, BoundTerm<T>>, Term {}
