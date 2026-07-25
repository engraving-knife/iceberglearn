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
package org.apache.iceberg.flink.source.assigner;

import java.io.Serializable;
import java.util.Collection;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;

/**
 * split assigner 的工厂接口。
 *
 * <p>所属模块：iceberg-flink（source assigner 侧）。
 *
 * <p>职责：定义创建 {@link SplitAssigner} 的契约，支持新建与从状态恢复两种方式。
 *
 * <p>设计意图：将 assigner 的实例化抽象为工厂，便于 {@link IcebergSource} 按需替换分配策略 （如简单分配、有序分配等）。
 *
 * <p>上下游关系：被 {@link IcebergSource} 持有并调用。
 */
public interface SplitAssignerFactory extends Serializable {

  /** 创建全新的 assigner。 */
  SplitAssigner createAssigner();

  /** 从已有 split 状态恢复创建 assigner。 */
  SplitAssigner createAssigner(Collection<IcebergSourceSplitState> assignerState);
}
