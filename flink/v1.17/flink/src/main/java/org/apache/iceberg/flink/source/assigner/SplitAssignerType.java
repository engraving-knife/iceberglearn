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

import org.apache.flink.annotation.Internal;

/**
 * 文件级说明：Flink source 分片分配器类型枚举。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source assigner 子包）。
 *
 * <p>职责：枚举可用的分片分配器类型，并提供对应工厂的创建方法。
 *
 * <p>设计意图：以枚举形式集中表达分配器实现，便于配置项与具体实现解耦。
 *
 * <p>上下游关系：上游为 {@code FlinkReadOptions} 中的分配器类型配置， 下游为各 {@link SplitAssignerFactory} 实现。
 */
@Internal
public enum SplitAssignerType {
  /** 简单分配器类型，按 FIFO 顺序把分片分给读取任务。 */
  SIMPLE {
    @Override
    public SplitAssignerFactory factory() {
      return new SimpleSplitAssignerFactory();
    }
  };

  /** 返回当前枚举值对应的分片分配器工厂实例。 */
  public abstract SplitAssignerFactory factory();
}
