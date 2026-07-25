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

import org.apache.iceberg.flink.source.SplitHelpers;
import org.junit.Test;

/**
 * 文件级说明：测试 TestDefaultSplitAssigner 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestDefaultSplitAssigner 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestDefaultSplitAssigner extends SplitAssignerTestBase {
  /** 辅助方法：splitAssigner，split Assigner。 */
  @Override
  protected SplitAssigner splitAssigner() {
    return new DefaultSplitAssigner(null);
  }

  /** Test the assigner when multiple files are in a single split */
  @Test
  public void testMultipleFilesInASplit() throws Exception {
    SplitAssigner assigner = splitAssigner();
    assigner.onDiscoveredSplits(
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 4, 2));

    assertGetNext(assigner, GetSplitResult.Status.AVAILABLE);
    assertSnapshot(assigner, 1);
    assertGetNext(assigner, GetSplitResult.Status.AVAILABLE);
    assertGetNext(assigner, GetSplitResult.Status.UNAVAILABLE);
    assertSnapshot(assigner, 0);
  }
}
