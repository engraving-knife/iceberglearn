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
 * 表达式中的变量引用接口：表示对一个字段的引用，是 {@link Term} 的子接口。
 *
 * <p>所属模块：iceberg-api（term 体系的引用分支；与 {@link Literal} 配对构成谓词）。
 *
 * <p>职责：声明 {@link #name()} 返回引用所指向的字段名。
 *
 * <p>设计意图：把“按名字引用字段”这一共性从具体形态（未绑定 {@link NamedReference} / 已绑定 {@link
 * BoundReference}）中抽出，便于访问者按引用统一处理。
 *
 * <p>上下游关系：由 {@link Expressions#ref} 等构造；被谓词 / 聚合作为 term 使用。
 *
 * @param <T> 引用字段的 Java 类型
 * @see BoundReference
 * @see NamedReference
 */
public interface Reference<T> extends Term {
  /**
   * 返回引用的字段名。
   *
   * @return 字段名
   */
  String name();
}
