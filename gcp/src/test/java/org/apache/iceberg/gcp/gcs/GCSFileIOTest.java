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
package org.apache.iceberg.gcp.gcs;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.stream.StreamSupport;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.gcp.GCPProperties;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.IOUtil;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.ResolvingFileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 GCSFileIOTest 的功能。
 *
 * <p>所属模块：iceberg-gcp。职责：验证 GCSFileIOTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class GCSFileIOTest {
  private static final String TEST_BUCKET = "TEST_BUCKET";
  private final Random random = new Random(1);

  private final Storage storage = spy(LocalStorageHelper.getOptions().getService());
  private GCSFileIO io;

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    // LocalStorageHelper doesn't support batch operations, so mock that here
    doAnswer(
            invoke -> {
              Iterable<BlobId> iter = invoke.getArgument(0);
              List<Boolean> answer = Lists.newArrayList();
              iter.forEach(
                  blobId -> {
                    answer.add(storage.delete(blobId));
                  });
              return answer;
            })
        .when(storage)
        .delete(any(Iterable.class));

    io = new GCSFileIO(() -> storage, new GCPProperties());
  }

  /**
   * 测试场景：new Input File。
   *
   * <p>验证该方法在 new Input File 条件下的行为是否符合预期。
   */
  @Test
  public void newInputFile() throws IOException {
    String location = format("gs://%s/path/to/file.txt", TEST_BUCKET);
    byte[] expected = new byte[1024 * 1024];
    random.nextBytes(expected);

    InputFile in = io.newInputFile(location);
    assertThat(in.exists()).isFalse();

    OutputFile out = io.newOutputFile(location);
    try (OutputStream os = out.createOrOverwrite()) {
      IOUtil.writeFully(os, ByteBuffer.wrap(expected));
    }

    assertThat(in.exists()).isTrue();
    byte[] actual = new byte[1024 * 1024];

    try (InputStream is = in.newStream()) {
      IOUtil.readFully(is, actual, 0, actual.length);
    }

    assertThat(expected).isEqualTo(actual);

    io.deleteFile(in);

    assertThat(io.newInputFile(location).exists()).isFalse();
  }

  /**
   * 测试场景：Delete。
   *
   * <p>验证该方法在 Delete 条件下的行为是否符合预期。
   */
  @Test
  public void testDelete() {
    String path = "delete/path/data.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path).build());

    // There should be one blob in the bucket
    assertThat(
            StreamSupport.stream(storage.list(TEST_BUCKET).iterateAll().spliterator(), false)
                .count())
        .isEqualTo(1);

    io.deleteFile(gsUri(path));

    // The bucket should now be empty
    assertThat(
            StreamSupport.stream(storage.list(TEST_BUCKET).iterateAll().spliterator(), false)
                .count())
        .isZero();
  }

  /** 辅助方法：gsUri。 */
  private String gsUri(String path) {
    return format("gs://%s/%s", TEST_BUCKET, path);
  }

  /**
   * 测试场景：List Prefix。
   *
   * <p>验证该方法在 List Prefix 条件下的行为是否符合预期。
   */
  @Test
  public void testListPrefix() {
    String prefix = "list/path/";
    String path1 = prefix + "data1.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path1).build());
    String path2 = prefix + "data2.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path2).build());
    String path3 = "list/skip/data3.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path3).build());

    assertThat(StreamSupport.stream(io.listPrefix(gsUri("list/")).spliterator(), false).count())
        .isEqualTo(3);

    assertThat(StreamSupport.stream(io.listPrefix(gsUri(prefix)).spliterator(), false).count())
        .isEqualTo(2);

    assertThat(StreamSupport.stream(io.listPrefix(gsUri(path1)).spliterator(), false).count())
        .isEqualTo(1);
  }

  /**
   * 测试场景：Delete Files。
   *
   * <p>验证该方法在 Delete Files 条件下的行为是否符合预期。
   */
  @Test
  public void testDeleteFiles() {
    String prefix = "del/path/";
    String path1 = prefix + "data1.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path1).build());
    String path2 = prefix + "data2.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path2).build());
    String path3 = "del/skip/data3.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path3).build());

    assertThat(StreamSupport.stream(io.listPrefix(gsUri("del/")).spliterator(), false).count())
        .isEqualTo(3);

    Iterable<String> deletes =
        () -> ImmutableList.of(gsUri(path1), gsUri(path3)).stream().iterator();
    io.deleteFiles(deletes);

    assertThat(StreamSupport.stream(io.listPrefix(gsUri("del/")).spliterator(), false).count())
        .isEqualTo(1);
  }

  /**
   * 测试场景：Delete Prefix。
   *
   * <p>验证该方法在 Delete Prefix 条件下的行为是否符合预期。
   */
  @Test
  public void testDeletePrefix() {
    String prefix = "del/path/";
    String path1 = prefix + "data1.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path1).build());
    String path2 = prefix + "data2.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path2).build());
    String path3 = "del/skip/data3.dat";
    storage.create(BlobInfo.newBuilder(TEST_BUCKET, path3).build());

    assertThat(StreamSupport.stream(io.listPrefix(gsUri("del/")).spliterator(), false).count())
        .isEqualTo(3);

    io.deletePrefix(gsUri(prefix));

    assertThat(StreamSupport.stream(io.listPrefix(gsUri("del/")).spliterator(), false).count())
        .isEqualTo(1);
  }

  /**
   * 测试场景：GCS File IO Kryo Serialization。
   *
   * <p>验证该方法在 GCS File IO Kryo Serialization 条件下的行为是否符合预期。
   */
  @Test
  public void testGCSFileIOKryoSerialization() throws IOException {
    FileIO testGCSFileIO = new GCSFileIO();

    // gcs fileIO should be serializable when properties are passed as immutable map
    testGCSFileIO.initialize(ImmutableMap.of("k1", "v1"));
    FileIO roundTripSerializedFileIO = TestHelpers.KryoHelpers.roundTripSerialize(testGCSFileIO);

    assertThat(testGCSFileIO.properties()).isEqualTo(roundTripSerializedFileIO.properties());
  }

  /**
   * 测试场景：GCS File IO Java Serialization。
   *
   * <p>验证该方法在 GCS File IO Java Serialization 条件下的行为是否符合预期。
   */
  @Test
  public void testGCSFileIOJavaSerialization() throws IOException, ClassNotFoundException {
    FileIO testGCSFileIO = new GCSFileIO();

    // gcs fileIO should be serializable when properties are passed as immutable map
    testGCSFileIO.initialize(ImmutableMap.of("k1", "v1"));
    FileIO roundTripSerializedFileIO = TestHelpers.roundTripSerialize(testGCSFileIO);

    assertThat(testGCSFileIO.properties()).isEqualTo(roundTripSerializedFileIO.properties());
  }

  /**
   * 测试场景：Resolving File IO Load。
   *
   * <p>验证该方法在 Resolving File IO Load 条件下的行为是否符合预期。
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
            .invoke("gs://foo/bar");
    assertThat(result).isInstanceOf(GCSFileIO.class);
  }
}
