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
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：Puffin 文件读写的入口工厂类。
 *
 * <p>所属模块：iceberg-core（Puffin 文件格式读写实现模块，被统计信息/删除向量等场景使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #write(OutputFile)} 创建 {@link PuffinWriter} 的构建器。
 *   <li>提供 {@link #read(InputFile)} 创建 {@link PuffinReader} 的构建器。
 * </ul>
 *
 * <p>设计意图：采用 Builder 模式屏蔽 writer/reader 的复杂初始化参数（压缩、属性、 文件大小提示等），使调用方代码简洁；私有构造禁止实例化，仅作静态工厂。
 *
 * <p>上下游关系：上游为统计信息收集/读取、删除向量加载等业务；下游通过 {@link PuffinWriter} 写入 Puffin 文件，通过 {@link PuffinReader}
 * 读取。
 */
public final class Puffin {
  private Puffin() {}

  /**
   * 创建写入构建器。
   *
   * @param outputFile 目标输出文件
   * @return {@link WriteBuilder}
   */
  public static WriteBuilder write(OutputFile outputFile) {
    return new WriteBuilder(outputFile);
  }

  /** {@link PuffinWriter} 的构建器，用于配置写入参数。 */
  public static class WriteBuilder {
    private final OutputFile outputFile;
    private final Map<String, String> properties = Maps.newLinkedHashMap();
    private boolean compressFooter = false;
    private PuffinCompressionCodec defaultBlobCompression = PuffinCompressionCodec.NONE;

    private WriteBuilder(OutputFile outputFile) {
      this.outputFile = outputFile;
    }

    /** 设置一个文件级属性。 */
    public WriteBuilder set(String property, String value) {
      properties.put(property, value);
      return this;
    }

    /** 批量设置文件级属性。 */
    public WriteBuilder setAll(Map<String, String> props) {
      this.properties.putAll(props);
      return this;
    }

    /** 设置文件级 {@value StandardPuffinProperties#CREATED_BY_PROPERTY} 属性。 */
    public WriteBuilder createdBy(String applicationIdentifier) {
      this.properties.put(StandardPuffinProperties.CREATED_BY_PROPERTY, applicationIdentifier);
      return this;
    }

    /** 配置 writer 在写入时压缩 footer。 */
    public WriteBuilder compressFooter() {
      this.compressFooter = true;
      return this;
    }

    /**
     * 配置 writer 对 Blob 默认采用的压缩方式。
     *
     * <p>设计要点：此为文件级默认值，可被单个 {@link Blob#requestedCompression()} 覆盖， 从而支持文件级与 Blob 级两层压缩配置。
     *
     * @param compression 默认压缩方式
     */
    public WriteBuilder compressBlobs(PuffinCompressionCodec compression) {
      this.defaultBlobCompression = compression;
      return this;
    }

    /** 构建 {@link PuffinWriter}。 */
    public PuffinWriter build() {
      return new PuffinWriter(outputFile, properties, compressFooter, defaultBlobCompression);
    }
  }

  /**
   * 创建读取构建器。
   *
   * @param inputFile 待读取的 Puffin 文件
   * @return {@link ReadBuilder}
   */
  public static ReadBuilder read(InputFile inputFile) {
    return new ReadBuilder(inputFile);
  }

  /** {@link PuffinReader} 的构建器，用于配置读取参数。 */
  public static final class ReadBuilder {
    private final InputFile inputFile;
    private Long fileSize;
    private Long footerSize;

    private ReadBuilder(InputFile inputFile) {
      this.inputFile = inputFile;
    }

    /** 传入已知文件大小，可能提升读取性能（避免多余 seek/读取）。 */
    public ReadBuilder withFileSize(long size) {
      this.fileSize = size;
      return this;
    }

    /** 传入已知 footer 大小，可能提升读取性能（避免多余 seek/读取）。 */
    public ReadBuilder withFooterSize(long size) {
      this.footerSize = size;
      return this;
    }

    /** 构建 {@link PuffinReader}。 */
    public PuffinReader build() {
      return new PuffinReader(inputFile, fileSize, footerSize);
    }
  }
}
