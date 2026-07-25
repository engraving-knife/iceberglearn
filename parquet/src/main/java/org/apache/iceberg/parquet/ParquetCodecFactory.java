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
package org.apache.iceberg.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.parquet.hadoop.BadConfigurationException;
import org.apache.parquet.hadoop.CodecFactory;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;

/**
 * 文件级说明：Parquet 读取侧的压缩编解码器工厂，扩展 parquet-mr 的 CodecFactory。
 *
 * <p>所属模块：iceberg-parquet（读取侧压缩解码，被 Parquet 读取器使用）。
 *
 * <p>职责：在 parquet-mr {@link CodecFactory} 基础上，按“编解码器名 + 压缩级别” 缓存 {@link
 * CompressionCodec}，避免相同配置重复反射创建。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>缓存键增强：parquet-mr 原生仅按 codec 名缓存，会忽略压缩级别差异导致 误命中。本类把级别纳入缓存键，使不同级别的同一 codec 各自独立缓存。
 *   <li>临时变通：注释标明该 workaround 在 parquet-mr 上游修复后可移除。
 * </ul>
 *
 * <p>上下游关系：被 {@link Parquet} 读取入口构造，用于为列读取器提供解码器。
 */
public class ParquetCodecFactory extends CodecFactory {

  /**
   * 构造编解码器工厂。
   *
   * @param configuration Hadoop 配置，用于加载 codec 类与压缩级别
   * @param pageSize Parquet 页大小
   */
  public ParquetCodecFactory(Configuration configuration, int pageSize) {
    super(configuration, pageSize);
  }

  /**
   * 按 codec 名 + 压缩级别获取或创建 {@link CompressionCodec}。
   *
   * <p>逻辑：先构造含级别的缓存键 {@link #cacheKey}；从缓存命中则直接返回； 否则反射加载 codec 类（先 Class.forName，失败再用 Hadoop
   * ClassLoader）， 通过 ReflectionUtils 实例化并放入缓存。
   *
   * <p>与 parquet-mr 原生实现的差异：缓存键包含压缩级别。
   *
   * @param codecName Parquet 压缩编解码器名
   * @return 编解码器实例，UNCOMPRESSED 等无 Hadoop 类时返回 null
   * @throws BadConfigurationException codec 类未找到
   */
  @Override
  protected CompressionCodec getCodec(CompressionCodecName codecName) {
    String codecClassName = codecName.getHadoopCompressionCodecClassName();
    if (codecClassName == null) {
      return null;
    }
    String cacheKey = cacheKey(codecName);
    CompressionCodec codec = CODEC_BY_NAME.get(cacheKey);
    if (codec != null) {
      return codec;
    }

    try {
      Class<?> codecClass;
      try {
        codecClass = Class.forName(codecClassName);
      } catch (ClassNotFoundException e) {
        // 回退到 job classloader 加载
        codecClass = configuration.getClassLoader().loadClass(codecClassName);
      }
      codec = (CompressionCodec) ReflectionUtils.newInstance(codecClass, configuration);
      CODEC_BY_NAME.put(cacheKey, codec);
      return codec;
    } catch (ClassNotFoundException e) {
      throw new BadConfigurationException("Class " + codecClassName + " was not found", e);
    }
  }

  /**
   * 构造包含压缩级别的缓存键。
   *
   * <p>逻辑：按 codec 类型从 Hadoop 配置读取对应压缩级别参数； 有级别时返回 "codecClass:level"，无级别时仅返回 codecClass。
   *
   * @param codecName 编解码器名
   * @return 缓存键
   */
  private String cacheKey(CompressionCodecName codecName) {
    String level = null;
    switch (codecName) {
      case GZIP:
        level = configuration.get("zlib.compress.level");
        break;
      case BROTLI:
        level = configuration.get("compression.brotli.quality");
        break;
      case ZSTD:
        level = configuration.get("parquet.compression.codec.zstd.level");
        if (level == null) {
          // 兼容旧参数名 io.compression.codec.zstd.level
          level = configuration.get("io.compression.codec.zstd.level");
        }
        break;
      default:
        // 该 codec 不支持压缩级别，忽略
    }
    String codecClass = codecName.getHadoopCompressionCodecClassName();
    return level == null ? codecClass : codecClass + ":" + level;
  }
}
