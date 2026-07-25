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
package org.apache.iceberg.puffin;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：Puffin 文件格式支持的压缩编码枚举。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>枚举 Puffin Blob 与 footer 支持的压缩方式：不压缩、LZ4、Zstandard。
 *   <li>提供 codec 名称与枚举值的互查能力（{@link #codecName()} / {@link #forName(String)}）。
 * </ul>
 *
 * <p>设计意图：codec 名称作为 Puffin 文件格式中序列化的标准字符串（如 {@code "zstd"}）， 与具体的压缩库实现解耦；{@code NONE} 用 null
 * 名称表示不压缩，序列化时不输出该字段。
 *
 * <p>上下游关系：被 {@link PuffinWriter} 决定 Blob/footer 压缩方式、被 {@link PuffinReader} 根据 footer
 * 中的压缩名称解压时使用；底层压缩由 {@link PuffinFormat} 实现。
 */
public enum PuffinCompressionCodec {
  /** 不压缩。 */
  NONE(null),

  /** LZ4 单压缩帧（含内容长度字段）。 */
  LZ4("lz4"),

  /** Zstandard 单压缩帧（含内容长度字段）。 */
  ZSTD("zstd"),
/**/ ;

  private static final Map<String, PuffinCompressionCodec> BY_NAME =
      Stream.of(values())
          .collect(
              Collectors.toMap(
                  PuffinCompressionCodec::codecName,
                  Function.identity(),
                  (a, b) -> {
                    throw new UnsupportedOperationException("Two enum instances with same name");
                  },
                  Maps::newHashMap));

  private final String codecName;

  PuffinCompressionCodec(String codecName) {
    this.codecName = codecName;
  }

  /** 返回 codec 的标准名称；{@link #NONE} 返回 null（表示不压缩）。 */
  @Nullable
  public String codecName() {
    return codecName;
  }

  /**
   * 按 codec 名称查找枚举值。
   *
   * @param codecName codec 标准名称，可为 null（对应未压缩 Blob 元信息中无该字段）
   * @return 对应的 {@link PuffinCompressionCodec}
   * @throws IllegalArgumentException 若名称无法识别
   */
  public static PuffinCompressionCodec forName(@Nullable String codecName) {
    PuffinCompressionCodec codec = BY_NAME.get(codecName);
    Preconditions.checkArgument(codec != null, "Unknown codec name: %s", codecName);
    return codec;
  }
}
