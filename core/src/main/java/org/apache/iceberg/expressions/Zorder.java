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

import java.util.Arrays;
import java.util.List;

/**
 * 文件级说明：Z-Order 排序表达式项，表示对多个字段做 Z-Order 复合排序。
 *
 * <p>所属模块：iceberg-core（expressions 子包）。职责：实现 {@link Term} 接口， 封装一组 {@link NamedReference} 作为
 * Z-Order 排序的输入列。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Z-Order（Morton 码）把多维数据映射到一维，使多列范围查询能利用排序优势。
 *   <li>本类仅作为表达式项被引用，实际 Z-Order 编码由写入/排序引擎完成。
 * </ul>
 *
 * <p>上下游关系：在排序定义（SortOrder）中被引用；由引擎层解析并执行实际 Z-Order 编码。
 */
public class Zorder implements Term {
  private final NamedReference<?>[] refs;

  /**
   * 构造 Z-Order 排序项。
   *
   * <p>设计要点：把 List 转为数组存储，减少内存开销。
   *
   * @param refs 参与 Z-Order 排序的列引用列表
   */
  public Zorder(List<NamedReference<?>> refs) {
    this.refs = refs.toArray(new NamedReference[0]);
  }

  /**
   * 返回参与 Z-Order 排序的列引用列表。
   *
   * @return 列引用列表（不可变视图）
   */
  public List<NamedReference<?>> refs() {
    return Arrays.asList(refs);
  }
}
