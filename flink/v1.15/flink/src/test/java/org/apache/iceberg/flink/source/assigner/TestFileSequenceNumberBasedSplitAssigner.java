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

import java.util.List;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.flink.source.SplitHelpers;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.SerializableComparator;
import org.apache.iceberg.flink.source.split.SplitComparators;
import org.apache.iceberg.util.SerializationUtil;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestFileSequenceNumberBasedSplitAssigner 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.15）。职责：验证 TestFileSequenceNumberBasedSplitAssigner
 * 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestFileSequenceNumberBasedSplitAssigner extends SplitAssignerTestBase {
  /** 辅助方法：splitAssigner，split Assigner。 */
  @Override
  protected SplitAssigner splitAssigner() {
    return new OrderedSplitAssignerFactory(SplitComparators.fileSequenceNumber()).createAssigner();
  }

  /** Test the assigner when multiple files are in a single split */
  @Test
  public void testMultipleFilesInAnIcebergSplit() {
    SplitAssigner assigner = splitAssigner();
    Assertions.assertThatThrownBy(
            () ->
                assigner.onDiscoveredSplits(
                    SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 4, 2, "2")),
            "Multiple files in a split is not allowed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Please use 'split-open-file-cost'");
  }

  /** Test sorted splits */
  @Test
  public void testSplitSort() throws Exception {
    SplitAssigner assigner = splitAssigner();
    List<IcebergSourceSplit> splits =
        SplitHelpers.createSplitsFromTransientHadoopTable(TEMPORARY_FOLDER, 5, 1, "2");

    assigner.onDiscoveredSplits(splits.subList(3, 5));
    assigner.onDiscoveredSplits(splits.subList(0, 1));
    assigner.onDiscoveredSplits(splits.subList(1, 3));

    assertGetNext(assigner, 1L);
    assertGetNext(assigner, 2L);
    assertGetNext(assigner, 3L);
    assertGetNext(assigner, 4L);
    assertGetNext(assigner, 5L);

    assertGetNext(assigner, GetSplitResult.Status.UNAVAILABLE);
  }

  /**
   * 测试场景：Serializable。
   *
   * <p>验证该方法在 Serializable 条件下的行为是否符合预期。
   */
  @Test
  public void testSerializable() {
    byte[] bytes = SerializationUtil.serializeToBytes(SplitComparators.fileSequenceNumber());
    SerializableComparator<IcebergSourceSplit> comparator =
        SerializationUtil.deserializeFromBytes(bytes);
    Assert.assertNotNull(comparator);
  }

  /** 辅助方法：assertGetNext，assert Get Next。 */
  protected void assertGetNext(SplitAssigner assigner, Long expectedSequenceNumber) {
    GetSplitResult result = assigner.getNext(null);
    ContentFile file = result.split().task().files().iterator().next().file();
    Assert.assertEquals(expectedSequenceNumber, file.fileSequenceNumber());
  }
}
