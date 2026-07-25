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
package org.apache.iceberg.view;

/**
 * 视图表示（representation）接口。
 *
 * <p>所属模块：iceberg-api。是 view 模块对视图 SQL 内容的抽象，一个 {@link ViewVersion} 可包含 多个不同方言的表示。
 *
 * <p>职责：定义视图表示的类型标识，当前仅支持 SQL 类型。
 *
 * <p>设计意图：将视图的"逻辑定义"与"具体方言实现"解耦——同一视图可为不同查询引擎方言提供各自的 SQL 文本。Type 内部类集中管理类型常量，便于扩展未来新的表示类型。
 *
 * <p>上下游关系：由 {@link ViewVersion#representations()} 返回；通过 {@link VersionBuilder#withQuery(String,
 * String)} 添加。
 */
public interface ViewRepresentation {

  /**
   * 视图表示类型常量集合。
   *
   * <p>设计意图：以静态常量集中管理类型字符串，私有构造函数防止实例化。当前仅定义 SQL 类型。
   */
  class Type {
    private Type() {}

    /** SQL 表示类型常量。 */
    public static final String SQL = "sql";
  }

  /**
   * 返回本视图表示的类型标识。
   *
   * @return 类型字符串，如 {@link Type#SQL}
   */
  String type();
}
