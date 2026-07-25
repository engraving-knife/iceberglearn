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
package org.apache.iceberg.actions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.MockFileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableTestBase;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * 测试类：TestBinPackStrategy，用于验证 Bin Pack Strategy 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Bin Pack Strategy 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
@RunWith(Parameterized.class)
public class TestBinPackStrategy extends TableTestBase {

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "formatVersion = {0}")
  public static Object[] parameters() {
    return new Object[] {2}; // We don't actually use the format version since everything is mock
  }

  private static final long MB = 1024 * 1024;

  /** 辅助方法：bin pack strategy。 */
  public TestBinPackStrategy(int formatVersion) {
    super(formatVersion);
  }

  class TestBinPackStrategyImpl extends BinPackStrategy {

    /** 辅助方法：table。 */
    @Override
    public Table table() {
      return table;
    }

    /** 辅助方法：rewrite files。 */
    @Override
    public Set<DataFile> rewriteFiles(List<FileScanTask> filesToRewrite) {
      throw new UnsupportedOperationException();
    }
  }

  /** 辅助方法：files of size。 */
  private List<FileScanTask> filesOfSize(long... sizes) {
    return Arrays.stream(sizes)
        .mapToObj(size -> new MockFileScanTask(size * MB))
        .collect(Collectors.toList());
  }

  /** 辅助方法：default bin pack。 */
  private RewriteStrategy defaultBinPack() {
    return new TestBinPackStrategyImpl().options(Collections.emptyMap());
  }

  /**
   * 测试场景：filtering all valid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilteringAllValid() {
    RewriteStrategy strategy = defaultBinPack();

    Iterable<FileScanTask> testFiles = filesOfSize(100, 100, 100, 100, 1000);
    Iterable<FileScanTask> filtered =
        ImmutableList.copyOf(strategy.selectFilesToRewrite(testFiles));

    Assert.assertEquals("No files should be removed from the set", testFiles, filtered);
  }

  /**
   * 测试场景：filtering remove invalid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilteringRemoveInvalid() {
    RewriteStrategy strategy = defaultBinPack();

    Iterable<FileScanTask> testFiles = filesOfSize(500, 500, 500, 600, 600);
    Iterable<FileScanTask> filtered =
        ImmutableList.copyOf(strategy.selectFilesToRewrite(testFiles));

    Assert.assertEquals(
        "All files should be removed from the set", Collections.emptyList(), filtered);
  }

  /**
   * 测试场景：filtering custom min max file size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilteringCustomMinMaxFileSize() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    BinPackStrategy.MAX_FILE_SIZE_BYTES, Long.toString(550 * MB),
                    BinPackStrategy.MIN_FILE_SIZE_BYTES, Long.toString(490 * MB)));

    Iterable<FileScanTask> testFiles = filesOfSize(500, 500, 480, 480, 560, 520);
    Iterable<FileScanTask> expectedFiles = filesOfSize(480, 480, 560);
    Iterable<FileScanTask> filtered =
        ImmutableList.copyOf(strategy.selectFilesToRewrite(testFiles));

    Assert.assertEquals(
        "Should remove files that exceed or are smaller than new bounds", expectedFiles, filtered);
  }

  /**
   * 测试场景：filtering with deletes。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFilteringWithDeletes() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    BinPackStrategy.MAX_FILE_SIZE_BYTES, Long.toString(550 * MB),
                    BinPackStrategy.MIN_FILE_SIZE_BYTES, Long.toString(490 * MB),
                    BinPackStrategy.DELETE_FILE_THRESHOLD, Integer.toString(2)));

    List<FileScanTask> testFiles = filesOfSize(500, 500, 480, 480, 560, 520);
    testFiles.add(MockFileScanTask.mockTaskWithDeletes(500 * MB, 2));
    Iterable<FileScanTask> expectedFiles = filesOfSize(480, 480, 560, 500);
    Iterable<FileScanTask> filtered =
        ImmutableList.copyOf(strategy.selectFilesToRewrite(testFiles));

    Assert.assertEquals("Should include file with deletes", expectedFiles, filtered);
  }

  /**
   * 测试场景：grouping min input files invalid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGroupingMinInputFilesInvalid() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(ImmutableMap.of(BinPackStrategy.MIN_INPUT_FILES, Integer.toString(5)));

    Iterable<FileScanTask> testFiles = filesOfSize(1, 1, 1, 1);

    Iterable<List<FileScanTask>> grouped = strategy.planFileGroups(testFiles);

    Assert.assertEquals("Should plan 0 groups, not enough input files", 0, Iterables.size(grouped));
  }

  /**
   * 测试场景：grouping min input files as one。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGroupingMinInputFilesAsOne() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    BinPackStrategy.MIN_INPUT_FILES, Integer.toString(1),
                    BinPackStrategy.MAX_FILE_SIZE_BYTES, Long.toString(3 * MB),
                    RewriteDataFiles.TARGET_FILE_SIZE_BYTES, Long.toString(2 * MB),
                    BinPackStrategy.MIN_FILE_SIZE_BYTES, Long.toString(MB),
                    BinPackStrategy.DELETE_FILE_THRESHOLD, Integer.toString(2)));

    Iterable<FileScanTask> testFiles1 = filesOfSize(1);
    Iterable<List<FileScanTask>> grouped1 = strategy.planFileGroups(testFiles1);

    Assert.assertEquals(
        "Should plan 0 groups, 1 file is too small but no deletes are present so rewriting is "
            + "a NOOP",
        0,
        Iterables.size(grouped1));

    Iterable<FileScanTask> testFiles2 = filesOfSize(4);
    Iterable<List<FileScanTask>> grouped2 = strategy.planFileGroups(testFiles2);

    Assert.assertEquals(
        "Should plan 1 group because the file present is larger than maxFileSize and can be "
            + "split",
        1,
        Iterables.size(grouped2));

    List<FileScanTask> testFiles3 = Lists.newArrayList();
    testFiles3.add(MockFileScanTask.mockTaskWithDeletes(MB, 2));
    Iterable<List<FileScanTask>> grouped3 = strategy.planFileGroups(testFiles3);
    Assert.assertEquals(
        "Should plan 1 group, the data file has delete files and can be re-written without "
            + "deleted row",
        1,
        Iterables.size(grouped3));
  }

  /**
   * 测试场景：group with large file min input files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGroupWithLargeFileMinInputFiles() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(ImmutableMap.of(BinPackStrategy.MIN_INPUT_FILES, Integer.toString(5)));

    Iterable<FileScanTask> testFiles1 = filesOfSize(2000);
    Iterable<List<FileScanTask>> grouped1 = strategy.planFileGroups(testFiles1);

    Assert.assertEquals(
        "Should plan 1 group, not enough input files but the input file exceeds our max"
            + "and can be written into at least one new target-file-size files",
        ImmutableList.of(testFiles1),
        grouped1);

    Iterable<FileScanTask> testFiles2 = filesOfSize(500, 500, 500);
    Iterable<List<FileScanTask>> grouped2 = strategy.planFileGroups(testFiles2);

    Assert.assertEquals(
        "Should plan 1 group, not enough input files but the sum of file sizes exceeds "
            + "target-file-size and files within the group is greater than 1",
        ImmutableList.of(testFiles2),
        grouped2);

    Iterable<FileScanTask> testFiles3 = filesOfSize(10, 10, 10);
    Iterable<List<FileScanTask>> grouped3 = strategy.planFileGroups(testFiles3);

    Assert.assertEquals(
        "Should plan 0 groups, not enough input files and the sum of file sizes does not "
            + "exceeds target-file-size and files within the group is greater than 1",
        ImmutableList.of(),
        grouped3);
  }

  /**
   * 测试场景：grouping min input files valid。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGroupingMinInputFilesValid() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(ImmutableMap.of(BinPackStrategy.MIN_INPUT_FILES, Integer.toString(5)));

    Iterable<FileScanTask> testFiles = filesOfSize(1, 1, 1, 1, 1);

    Iterable<List<FileScanTask>> grouped = strategy.planFileGroups(testFiles);

    Assert.assertEquals(
        "Should plan 1 groups since there are enough input files",
        ImmutableList.of(testFiles),
        grouped);
  }

  /**
   * 测试场景：grouping with deletes。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testGroupingWithDeletes() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    BinPackStrategy.MIN_INPUT_FILES, Integer.toString(5),
                    BinPackStrategy.MAX_FILE_SIZE_BYTES, Long.toString(550 * MB),
                    BinPackStrategy.MIN_FILE_SIZE_BYTES, Long.toString(490 * MB),
                    BinPackStrategy.DELETE_FILE_THRESHOLD, Integer.toString(2)));

    List<FileScanTask> testFiles = Lists.newArrayList();
    testFiles.add(MockFileScanTask.mockTaskWithDeletes(500 * MB, 2));
    Iterable<List<FileScanTask>> grouped = strategy.planFileGroups(testFiles);

    Assert.assertEquals(
        "Should plan 1 groups since there are enough input files",
        ImmutableList.of(testFiles),
        grouped);
  }

  /**
   * 测试场景：max group size。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMaxGroupSize() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    RewriteDataFiles.MAX_FILE_GROUP_SIZE_BYTES, Long.toString(1000 * MB)));

    Iterable<FileScanTask> testFiles = filesOfSize(300, 300, 300, 300, 300, 300);

    Iterable<List<FileScanTask>> grouped = strategy.planFileGroups(testFiles);

    Assert.assertEquals(
        "Should plan 2 groups since there is enough data for two groups",
        2,
        Iterables.size(grouped));
  }

  /**
   * 测试场景：num ouput files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNumOuputFiles() {
    BinPackStrategy strategy = (BinPackStrategy) defaultBinPack();
    long targetFileSize = strategy.targetFileSize();
    Assert.assertEquals(
        "Should keep remainder if the remainder is a valid size",
        2,
        strategy.numOutputFiles(targetFileSize + 450 * MB));
    Assert.assertEquals(
        "Should discard remainder file if the remainder is very small",
        1,
        strategy.numOutputFiles(targetFileSize + 40 * MB));
    Assert.assertEquals(
        "Should keep remainder file if it would change average file size greatly",
        2,
        strategy.numOutputFiles((long) (targetFileSize + 0.40 * targetFileSize)));
    Assert.assertEquals(
        "Should discard remainder if file is small and wouldn't change average that much",
        200,
        strategy.numOutputFiles(200 * targetFileSize + 13 * MB));
    Assert.assertEquals(
        "Should keep remainder if it's a valid size",
        201,
        strategy.numOutputFiles(200 * targetFileSize + 499 * MB));
    Assert.assertEquals(
        "Should not return 0 even for very small files", 1, strategy.numOutputFiles(1));
  }

  /**
   * 测试场景：invalid options。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testInvalidOptions() {
    Assertions.assertThatThrownBy(
            () ->
                defaultBinPack()
                    .options(
                        ImmutableMap.of(
                            BinPackStrategy.MAX_FILE_SIZE_BYTES, Long.toString(1 * MB))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Cannot set min-file-size-bytes greater than or equal to max-file-size-bytes");

    Assertions.assertThatThrownBy(
            () ->
                defaultBinPack()
                    .options(
                        ImmutableMap.of(
                            BinPackStrategy.MIN_FILE_SIZE_BYTES, Long.toString(1000 * MB))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Cannot set min-file-size-bytes greater than or equal to max-file-size-bytes");

    Assertions.assertThatThrownBy(
            () ->
                defaultBinPack()
                    .options(ImmutableMap.of(BinPackStrategy.MIN_INPUT_FILES, Long.toString(-5))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Cannot set min-input-files is less than 1. All values less than 1 have the same effect as 1");

    Assertions.assertThatThrownBy(
            () ->
                defaultBinPack()
                    .options(
                        ImmutableMap.of(BinPackStrategy.DELETE_FILE_THRESHOLD, Long.toString(-5))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Cannot set delete-file-threshold is less than 1. All values less than 1 have the same effect as 1");

    Assertions.assertThatThrownBy(
            () ->
                defaultBinPack()
                    .options(
                        ImmutableMap.of(
                            RewriteDataFiles.TARGET_FILE_SIZE_BYTES, Long.toString(-5))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot set min-file-size-bytes to a negative number");
  }

  /**
   * 测试场景：rewrite all select files to rewrite。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRewriteAllSelectFilesToRewrite() {
    RewriteStrategy strategy =
        defaultBinPack().options(ImmutableMap.of(BinPackStrategy.REWRITE_ALL, "true"));

    Iterable<FileScanTask> testFiles = filesOfSize(500, 500, 480, 480, 560, 520);
    Iterable<FileScanTask> expectedFiles = filesOfSize(500, 500, 480, 480, 560, 520);
    Iterable<FileScanTask> filtered =
        ImmutableList.copyOf(strategy.selectFilesToRewrite(testFiles));
    Assert.assertEquals("Should rewrite all files", expectedFiles, filtered);
  }

  /**
   * 测试场景：rewrite all plan file groups。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRewriteAllPlanFileGroups() {
    RewriteStrategy strategy =
        defaultBinPack()
            .options(
                ImmutableMap.of(
                    BinPackStrategy.MIN_INPUT_FILES,
                    Integer.toString(5),
                    BinPackStrategy.REWRITE_ALL,
                    "true"));

    Iterable<FileScanTask> testFiles = filesOfSize(1, 1, 1, 1);
    Iterable<List<FileScanTask>> grouped = strategy.planFileGroups(testFiles);

    Assert.assertEquals("Should plan 1 group to rewrite all files", 1, Iterables.size(grouped));
  }
}
