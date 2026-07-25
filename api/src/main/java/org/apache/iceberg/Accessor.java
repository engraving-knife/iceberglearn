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
package org.apache.iceberg;

import java.io.Serializable;
import org.apache.iceberg.types.Type;

/**
 * 字段访问器接口：从某种容器对象（如 {@link StructLike}、行记录等）中按位置/字段提取值。
 *
 * <p>所属模块：iceberg-api（最顶层公共 API 模块，定义表/元数据/扫描的核心契约，被 core 及各引擎模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>屏蔽容器内部表示差异，对外提供统一的 {@code get(container)} 取值入口。
 *   <li>同时返回该访问器对应字段的 {@link Type}，便于上层做类型检查与表达式求值。
 * </ul>
 *
 * <p>设计意图：通过 {@link Serializable} 标记，使访问器可在 Spark/Flink 等分布式引擎中 序列化传递到执行端；泛型 T
 * 允许同一接口适配多种容器类型，避免为每种容器单独设计 API。
 *
 * <p>上下游关系：由 {@link Schema#accessorForField(int)} 等方法构造，被表达式求值、 分区裁剪、列投影等场景广泛使用。
 *
 * @param <T> 容器类型
 */
public interface Accessor<T> extends Serializable {
  /**
   * 从容器对象中取出本访问器对应的字段值。
   *
   * @param container 持有字段值的容器（如 {@link StructLike}）
   * @return 字段值
   */
  Object get(T container);

  /**
   * 返回本访问器对应字段的 Iceberg 类型。
   *
   * @return 字段类型
   */
  Type type();
}
