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
package org.apache.iceberg;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;

/**
 * 测试类：ManifestReadBenchmark，用于验证 Manifest Read Benchmark 相关功能。
 *
 * <p>所属模块：iceberg-core（基准测试目录 src/jmh）。 职责：针对 Manifest Read Benchmark 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JMH 基准测试框架，通过构造典型数据集与读取场景， 测量清单读取/指标计数等操作的性能基线。
 */
@Fork(1)
@State(Scope.Benchmark)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 1000, timeUnit = TimeUnit.HOURS)
public class ManifestReadBenchmark {

  private static final int NUM_FILES = 10;
  private static final int NUM_ROWS = 100000;
  private static final int NUM_COLS = 10;

  private String baseDir;
  private String manifestListFile;

  /** 辅助方法：before。 */
  @Setup
  public void before() {
    baseDir =
        Paths.get(new File(System.getProperty("java.io.tmpdir")).getAbsolutePath()).toString();
    manifestListFile = String.format("%s/%s.avro", baseDir, UUID.randomUUID());

    Random random = new Random(System.currentTimeMillis());
    ManifestListWriter listWriter =
        ManifestLists.write(1, org.apache.iceberg.Files.localOutput(manifestListFile), 0, 1L, 0);

    try {
      for (int i = 0; i < NUM_FILES; i++) {
        OutputFile manifestFile =
            org.apache.iceberg.Files.localOutput(
                String.format("%s/%s.avro", baseDir, UUID.randomUUID()));

        ManifestWriter<DataFile> writer =
            ManifestFiles.write(1, PartitionSpec.unpartitioned(), manifestFile, 1L);
        try (ManifestWriter<DataFile> finalWriter = writer) {
          for (int j = 0; j < NUM_ROWS; j++) {
            DataFile dataFile =
                DataFiles.builder(PartitionSpec.unpartitioned())
                    .withFormat(FileFormat.PARQUET)
                    .withPath(String.format("/path/to/data-%s-%s.parquet", i, j))
                    .withFileSizeInBytes(j)
                    .withRecordCount(j)
                    .withMetrics(randomMetrics(random))
                    .build();
            finalWriter.add(dataFile);
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }

        listWriter.add(writer.toManifestFile());
      }

      listWriter.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** 辅助方法：after。 */
  @TearDown
  public void after() throws IOException {
    if (baseDir != null) {
      try (Stream<Path> walk = Files.walk(Paths.get(baseDir))) {
        walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
      baseDir = null;
    }

    manifestListFile = null;
  }

  /** 辅助方法：read manifest file。 */
  @Benchmark
  @Threads(1)
  public void readManifestFile() throws IOException {
    List<ManifestFile> manifests =
        ManifestLists.read(org.apache.iceberg.Files.localInput(manifestListFile));
    TestTables.LocalFileIO fileIO = new TestTables.LocalFileIO();
    Map<Integer, PartitionSpec> specs =
        ImmutableMap.of(PartitionSpec.unpartitioned().specId(), PartitionSpec.unpartitioned());
    long recordCount = 0L;
    for (ManifestFile manifestFile : manifests) {
      ManifestReader<DataFile> reader = ManifestFiles.read(manifestFile, fileIO, specs);
      try (CloseableIterator<DataFile> it = reader.iterator()) {
        while (it.hasNext()) {
          recordCount += it.next().recordCount();
        }
      }
    }
  }

  /** 辅助方法：random metrics。 */
  private Metrics randomMetrics(Random random) {
    long rowCount = 100000L + random.nextInt(1000);
    Map<Integer, Long> columnSizes = Maps.newHashMap();
    Map<Integer, Long> valueCounts = Maps.newHashMap();
    Map<Integer, Long> nullValueCounts = Maps.newHashMap();
    Map<Integer, Long> nanValueCounts = Maps.newHashMap();
    Map<Integer, ByteBuffer> lowerBounds = Maps.newHashMap();
    Map<Integer, ByteBuffer> upperBounds = Maps.newHashMap();
    for (int i = 0; i < NUM_COLS; i++) {
      columnSizes.put(i, 1000000L + random.nextInt(100000));
      valueCounts.put(i, 100000L + random.nextInt(100));
      nullValueCounts.put(i, (long) random.nextInt(5));
      nanValueCounts.put(i, (long) random.nextInt(5));
      byte[] lower = new byte[8];
      random.nextBytes(lower);
      lowerBounds.put(i, ByteBuffer.wrap(lower));
      byte[] upper = new byte[8];
      random.nextBytes(upper);
      upperBounds.put(i, ByteBuffer.wrap(upper));
    }

    return new Metrics(
        rowCount,
        columnSizes,
        valueCounts,
        nullValueCounts,
        nanValueCounts,
        lowerBounds,
        upperBounds);
  }
}
