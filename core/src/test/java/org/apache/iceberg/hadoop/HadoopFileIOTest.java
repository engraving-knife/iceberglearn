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
package org.apache.iceberg.hadoop;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Vector;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.io.BulkDeletionFailureException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.ResolvingFileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Streams;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 测试类：HadoopFileIOTest，用于验证 Hadoop File IO 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Hadoop File IO 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class HadoopFileIOTest {
  private final Random random = new Random(1);

  private FileSystem fs;
  private HadoopFileIO hadoopFileIO;

  @TempDir private File tempDir;

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() throws Exception {
    Configuration conf = new Configuration();
    fs = FileSystem.getLocal(conf);

    hadoopFileIO = new HadoopFileIO(conf);
  }

  /**
   * 测试场景：list prefix。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testListPrefix() {
    Path parent = new Path(tempDir.toURI());

    List<Integer> scaleSizes = Lists.newArrayList(1, 1000, 2500);

    scaleSizes
        .parallelStream()
        .forEach(
            scale -> {
              Path scalePath = new Path(parent, Integer.toString(scale));

              createRandomFiles(scalePath, scale);
              Assertions.assertThat(
                      Streams.stream(hadoopFileIO.listPrefix(scalePath.toUri().toString())).count())
                  .isEqualTo((long) scale);
            });

    long totalFiles = scaleSizes.stream().mapToLong(Integer::longValue).sum();
    Assertions.assertThat(
            Streams.stream(hadoopFileIO.listPrefix(parent.toUri().toString())).count())
        .isEqualTo(totalFiles);
  }

  /**
   * 测试场景：file exists。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFileExists() throws IOException {
    Path parent = new Path(tempDir.toURI());
    Path randomFilePath = new Path(parent, "random-file-" + UUID.randomUUID().toString());
    fs.createNewFile(randomFilePath);

    // check existence of the created file
    Assertions.assertThat(hadoopFileIO.newInputFile(randomFilePath.toUri().toString()).exists())
        .isTrue();
    fs.delete(randomFilePath, false);
    Assertions.assertThat(hadoopFileIO.newInputFile(randomFilePath.toUri().toString()).exists())
        .isFalse();
  }

  /**
   * 测试场景：delete prefix。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeletePrefix() {
    Path parent = new Path(tempDir.toURI());

    List<Integer> scaleSizes = Lists.newArrayList(1, 1000, 2500);

    scaleSizes
        .parallelStream()
        .forEach(
            scale -> {
              Path scalePath = new Path(parent, Integer.toString(scale));

              createRandomFiles(scalePath, scale);
              hadoopFileIO.deletePrefix(scalePath.toUri().toString());

              // Hadoop filesystem will throw if the path does not exist
              Assertions.assertThatThrownBy(
                      () -> hadoopFileIO.listPrefix(scalePath.toUri().toString()).iterator())
                  .isInstanceOf(UncheckedIOException.class)
                  .hasMessageContaining("java.io.FileNotFoundException");
            });

    hadoopFileIO.deletePrefix(parent.toUri().toString());
    // Hadoop filesystem will throw if the path does not exist
    Assertions.assertThatThrownBy(
            () -> hadoopFileIO.listPrefix(parent.toUri().toString()).iterator())
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("java.io.FileNotFoundException");
  }

  /**
   * 测试场景：delete files。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeleteFiles() {
    Path parent = new Path(tempDir.toURI());
    List<Path> filesCreated = createRandomFiles(parent, 10);
    hadoopFileIO.deleteFiles(
        filesCreated.stream().map(Path::toString).collect(Collectors.toList()));
    filesCreated.forEach(
        file ->
            Assertions.assertThat(hadoopFileIO.newInputFile(file.toString()).exists()).isFalse());
  }

  /**
   * 测试场景：delete files error handling。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeleteFilesErrorHandling() {
    List<String> filesCreated =
        random.ints(2).mapToObj(x -> "fakefsnotreal://file-" + x).collect(Collectors.toList());
    Assertions.assertThatThrownBy(() -> hadoopFileIO.deleteFiles(filesCreated))
        .isInstanceOf(BulkDeletionFailureException.class)
        .hasMessage("Failed to delete 2 files");
  }

  /**
   * 测试场景：hadoop file io kryo serialization。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testHadoopFileIOKryoSerialization() throws IOException {
    FileIO testHadoopFileIO = new HadoopFileIO();

    // hadoop fileIO should be serializable when properties are passed as immutable map
    testHadoopFileIO.initialize(ImmutableMap.of("k1", "v1"));
    FileIO roundTripSerializedFileIO = TestHelpers.KryoHelpers.roundTripSerialize(testHadoopFileIO);

    Assertions.assertThat(roundTripSerializedFileIO.properties())
        .isEqualTo(testHadoopFileIO.properties());
  }

  /**
   * 测试场景：hadoop file io java serialization。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testHadoopFileIOJavaSerialization() throws IOException, ClassNotFoundException {
    FileIO testHadoopFileIO = new HadoopFileIO();

    // hadoop fileIO should be serializable when properties are passed as immutable map
    testHadoopFileIO.initialize(ImmutableMap.of("k1", "v1"));
    FileIO roundTripSerializedFileIO = TestHelpers.roundTripSerialize(testHadoopFileIO);

    Assertions.assertThat(roundTripSerializedFileIO.properties())
        .isEqualTo(testHadoopFileIO.properties());
  }

  /**
   * 测试场景：resolving file io load。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testResolvingFileIOLoad() {
    ResolvingFileIO resolvingFileIO = new ResolvingFileIO();
    resolvingFileIO.setConf(new Configuration());
    resolvingFileIO.initialize(ImmutableMap.of());
    FileIO result =
        DynMethods.builder("io")
            .hiddenImpl(ResolvingFileIO.class, String.class)
            .build(resolvingFileIO)
            .invoke("hdfs://foo/bar");
    Assertions.assertThat(result).isInstanceOf(HadoopFileIO.class);
  }

  /** 辅助方法：create random files。 */
  private List<Path> createRandomFiles(Path parent, int count) {
    Vector<Path> paths = new Vector<>();
    random
        .ints(count)
        .parallel()
        .forEach(
            i -> {
              try {
                Path path = new Path(parent, "file-" + i);
                paths.add(path);
                fs.createNewFile(path);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    return paths;
  }
}
