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
package org.apache.iceberg.data.avro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestDecoderResolver，用于验证 Decoder Resolver 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Decoder Resolver 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestDecoderResolver {

  /** 辅助方法：before。 */
  @BeforeEach
  public void before() {
    DecoderResolver.DECODER_CACHES.get().clear();
  }

  /**
   * 测试场景：decoder caching read schema same as file schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDecoderCachingReadSchemaSameAsFileSchema() throws Exception {
    Decoder dummyDecoder = DecoderFactory.get().binaryDecoder(new byte[] {}, null);
    Schema fileSchema = avroSchema();
    ResolvingDecoder resolvingDecoder =
        DecoderResolver.resolve(dummyDecoder, fileSchema, fileSchema);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(1);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(fileSchema)).hasSize(1);
    checkCached(fileSchema, fileSchema);

    // Equal but new one
    Schema fileSchema1 = avroSchema();
    assertThat(fileSchema1).isEqualTo(fileSchema);
    ResolvingDecoder resolvingDecoder1 =
        DecoderResolver.resolve(dummyDecoder, fileSchema1, fileSchema1);
    assertThat(resolvingDecoder1).isNotSameAs(resolvingDecoder);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(2);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(fileSchema1)).hasSize(1);
    checkCached(fileSchema1, fileSchema1);

    // New one
    Schema fileSchema2 = avroSchema("manifest_path", "manifest_length");
    ResolvingDecoder resolvingDecoder2 =
        DecoderResolver.resolve(dummyDecoder, fileSchema2, fileSchema2);
    assertThat(resolvingDecoder2).isNotSameAs(resolvingDecoder);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(3);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(fileSchema2)).hasSize(1);
    checkCached(fileSchema2, fileSchema2);

    checkCachedSize(3);

    fileSchema = null;
    checkCachedSize(2);

    fileSchema1 = null;
    checkCachedSize(1);

    fileSchema2 = null;
    checkCachedSize(0);
  }

  /**
   * 测试场景：decoder caching read schema not same as file schema。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDecoderCachingReadSchemaNotSameAsFileSchema() throws Exception {
    Decoder dummyDecoder = DecoderFactory.get().binaryDecoder(new byte[] {}, null);
    Schema fileSchema = avroSchema();
    Schema readSchema = avroSchema("manifest_path", "manifest_length", "partition_spec_id");
    ResolvingDecoder resolvingDecoder =
        DecoderResolver.resolve(dummyDecoder, readSchema, fileSchema);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(1);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(readSchema)).hasSize(1);
    checkCached(readSchema, fileSchema);

    // Equal but new one
    Schema fileSchema1 = avroSchema();
    Schema readSchema1 = avroSchema("manifest_path", "manifest_length", "partition_spec_id");
    assertThat(fileSchema1).isEqualTo(fileSchema);
    assertThat(readSchema1).isEqualTo(readSchema);
    ResolvingDecoder resolvingDecoder1 =
        DecoderResolver.resolve(dummyDecoder, readSchema1, fileSchema1);
    assertThat(resolvingDecoder1).isNotSameAs(resolvingDecoder);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(2);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(readSchema1)).hasSize(1);
    checkCached(readSchema1, fileSchema1);

    // New read schema
    Schema readSchema2 = avroSchema("manifest_path", "manifest_length");
    ResolvingDecoder resolvingDecoder2 =
        DecoderResolver.resolve(dummyDecoder, readSchema2, fileSchema);
    assertThat(resolvingDecoder2).isNotSameAs(resolvingDecoder);

    assertThat(DecoderResolver.DECODER_CACHES.get()).hasSize(3);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(readSchema2)).hasSize(1);
    checkCached(readSchema2, fileSchema);

    checkCachedSize(3);

    readSchema = null;
    checkCachedSize(2);

    readSchema1 = null;
    checkCachedSize(1);

    readSchema2 = null;
    checkCachedSize(0);
  }

  /** 辅助方法：avro schema。 */
  private Schema avroSchema(String... columns) {
    if (columns.length == 0) {
      return AvroSchemaUtil.convert(ManifestFile.schema(), "manifest_file");
    } else {
      return AvroSchemaUtil.convert(ManifestFile.schema().select(columns), "manifest_file");
    }
  }

  /** 辅助方法：check cached。 */
  private void checkCached(Schema readSchema, Schema fileSchema) {
    assertThat(DecoderResolver.DECODER_CACHES.get()).containsKey(readSchema);
    assertThat(DecoderResolver.DECODER_CACHES.get().get(readSchema)).containsKey(fileSchema);
  }

  /** 辅助方法：get actual size。 */
  private int getActualSize() {
    // The size of keys included the GCed keys
    Set<Schema> keys = DecoderResolver.DECODER_CACHES.get().keySet();
    Set<Schema> identityKeys = Sets.newIdentityHashSet();
    // Forcefully remove keys that have been garbage collected
    identityKeys.addAll(keys);
    return identityKeys.size();
  }

  /** 辅助方法：check cached size。 */
  private void checkCachedSize(int expected) {
    System.gc();
    // Wait the weak reference keys are GCed
    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInSameThread()
        .untilAsserted(
            () -> {
              assertThat(getActualSize()).isEqualTo(expected);
            });
  }
}
