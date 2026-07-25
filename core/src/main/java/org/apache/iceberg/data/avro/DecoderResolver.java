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

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.iceberg.avro.ValueReader;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.collect.MapMaker;

/**
 * Avro 解析器：将 {@link Decoder} 解析为 {@link ResolvingDecoder}，以支持读写 schema 差异的兼容读取。
 *
 * <p>所属模块：iceberg-core，data/avro 包内的解码辅助工具。
 *
 * <p>职责：基于读取 schema（readSchema）与文件 schema（fileSchema）创建 ResolvingDecoder， 用于在读取时处理字段重命名、增删列等 schema
 * 演进。
 *
 * <p>设计意图：创建 ResolvingDecoder 开销较大（需做 schema 解析与映射），故使用 {@link ThreadLocal} 缓存按 (readSchema,
 * fileSchema) 维度的解码器实例。外层以 readSchema 为键、内层以 fileSchema 为键， 均使用弱引用 map，避免内存泄漏。ResolvingDecoder
 * 可复用——通过 {@link ResolvingDecoder#configure} 绑定到底层 Decoder 即可，无需重新构建。
 *
 * <p>上下游关系：被 {@link DataReader#read} 调用，在每次读取记录时解析并复用解码器。
 */
public class DecoderResolver {

  @VisibleForTesting
  static final ThreadLocal<Map<Schema, Map<Schema, ResolvingDecoder>>> DECODER_CACHES =
      ThreadLocal.withInitial(() -> new MapMaker().weakKeys().makeMap());

  private DecoderResolver() {}

  /**
   * 解析解码器并读取一条记录。
   *
   * <p>逻辑：通过 {@link #resolve} 获取（或复用）ResolvingDecoder，调用 reader 读取记录， 最后调用 drain
   * 排尽解析器中未消费的残留数据，保证下一次读取从正确位置开始。
   *
   * @param decoder 底层 Avro 解码器
   * @param readSchema 期望的读取 schema
   * @param fileSchema 文件实际写入的 schema
   * @param reader 值读取器
   * @param reuse 可复用的对象
   * @param <T> 读取结果的类型
   * @return 读取到的记录
   * @throws IOException 读取时发生 IO 异常
   */
  public static <T> T resolveAndRead(
      Decoder decoder, Schema readSchema, Schema fileSchema, ValueReader<T> reader, T reuse)
      throws IOException {
    ResolvingDecoder resolver = DecoderResolver.resolve(decoder, readSchema, fileSchema);
    T value = reader.read(resolver, reuse);
    resolver.drain();
    return value;
  }

  /**
   * 获取或创建 ResolvingDecoder 并绑定到底层解码器。
   *
   * <p>逻辑：从 ThreadLocal 缓存中按 readSchema 取外层 map，再按 fileSchema 取内层缓存的解码器； 若不存在则通过 {@link
   * #newResolver} 创建并缓存。最后用 configure 绑定到当前 decoder。
   *
   * @param decoder 底层 Avro 解码器
   * @param readSchema 期望的读取 schema
   * @param fileSchema 文件实际写入的 schema
   * @return 已绑定到 decoder 的 ResolvingDecoder
   * @throws IOException 创建解析器时发生 IO 异常
   */
  @VisibleForTesting
  static ResolvingDecoder resolve(Decoder decoder, Schema readSchema, Schema fileSchema)
      throws IOException {
    Map<Schema, Map<Schema, ResolvingDecoder>> cache = DECODER_CACHES.get();
    Map<Schema, ResolvingDecoder> fileSchemaToResolver =
        cache.computeIfAbsent(readSchema, k -> new WeakHashMap<>());

    ResolvingDecoder resolver =
        fileSchemaToResolver.computeIfAbsent(fileSchema, schema -> newResolver(readSchema, schema));

    resolver.configure(decoder);

    return resolver;
  }

  /**
   * 创建新的 ResolvingDecoder。
   *
   * <p>逻辑：通过 {@link DecoderFactory#resolvingDecoder} 以 fileSchema 为写入 schema、 readSchema 为读取 schema
   * 创建解析器；IO 异常包装为 {@link RuntimeIOException}。
   *
   * @param readSchema 期望的读取 schema
   * @param fileSchema 文件实际写入的 schema
   * @return 新建的 ResolvingDecoder
   */
  private static ResolvingDecoder newResolver(Schema readSchema, Schema fileSchema) {
    try {
      return DecoderFactory.get().resolvingDecoder(fileSchema, readSchema, null);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
}
