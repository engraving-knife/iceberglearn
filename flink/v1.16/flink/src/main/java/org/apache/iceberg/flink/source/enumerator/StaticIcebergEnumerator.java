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
package org.apache.iceberg.flink.source.enumerator;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;
import org.apache.iceberg.flink.source.assigner.SplitAssigner;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;

/** One-time split enumeration at the start-up for batch execution */
@Internal
/**
 * 静态模式 Iceberg enumerator，一次性分配全部分片后结束。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：在启动时完成全部扫描并分配，不进行增量枚举。
 *
 * <p>设计意图：继承 AbstractIcebergEnumerator；被 Flink Source 框架调用。
 */
public class StaticIcebergEnumerator extends AbstractIcebergEnumerator {
  private final SplitAssigner assigner;

  public StaticIcebergEnumerator(
      SplitEnumeratorContext<IcebergSourceSplit> enumeratorContext, SplitAssigner assigner) {
    super(enumeratorContext, assigner);
    this.assigner = assigner;
  }

  @Override
  public void start() {
    super.start();
  }

  @Override
  protected boolean shouldWaitForMoreSplits() {
    return false;
  }

  @Override
  public IcebergEnumeratorState snapshotState(long checkpointId) {
    return new IcebergEnumeratorState(null, assigner.state(), new int[0]);
  }
}
