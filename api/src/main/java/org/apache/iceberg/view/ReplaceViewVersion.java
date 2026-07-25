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

import org.apache.iceberg.PendingUpdate;

/**
 * 替换视图版本的 API。
 *
 * <p>所属模块：iceberg-api。本接口是 view 模块用于"替换视图版本"的变更入口，继承 {@link PendingUpdate}（产出 {@link ViewVersion}）与
 * {@link VersionBuilder}（用于配置版本内容）。
 *
 * <p>职责：构建一个新的视图版本并替换当前版本，提交时将变更应用到当前视图元数据。
 *
 * <p>设计意图：apply 阶段返回更新后的视图版本供调用方校验；提交冲突时，会把待提交变更重新应用到 新的视图元数据上以实现乐观并发冲突解决。这是一个空的标记接口（仅组合父接口能力），具体行为
 * 由父接口与实现类提供。
 *
 * <p>上下游关系：由 {@link View#replaceVersion()} 创建；提交后影响 {@link View} 的版本与历史。
 */
public interface ReplaceViewVersion
    extends PendingUpdate<ViewVersion>, VersionBuilder<ReplaceViewVersion> {}
