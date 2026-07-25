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

import java.util.Map;
import org.apache.iceberg.PendingUpdate;

/**
 * 更新视图属性的 API。
 *
 * <p>所属模块：iceberg-api。继承自 {@link PendingUpdate}（产出属性 Map），是 view 模块的属性变更入口。
 *
 * <p>职责：以键值对方式新增/修改、删除视图属性，提交时将变更应用到当前视图元数据。
 *
 * <p>设计意图：apply 阶段返回更新后的属性 Map 供调用方校验；提交冲突时，把待提交变更重新应用到 新视图元数据以实现乐观并发冲突解决。
 *
 * <p>上下游关系：由 {@link View#updateProperties()} 创建；提交后影响 {@link View} 的属性。
 */
public interface UpdateViewProperties extends PendingUpdate<Map<String, String>> {

  /**
   * 为视图添加一个键值对属性。
   *
   * @param key 属性键
   * @param value 属性值
   * @return this，便于链式调用
   * @throws NullPointerException 当 key 或 value 为 null 时
   */
  UpdateViewProperties set(String key, String value);

  /**
   * 从视图中移除指定键的属性。
   *
   * @param key 属性键
   * @return this，便于链式调用
   * @throws NullPointerException 当 key 为 null 时
   */
  UpdateViewProperties remove(String key);
}
