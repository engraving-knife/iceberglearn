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

import java.util.Locale;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Comparators;

/**
 * 文件格式枚举：列出 Iceberg 支持的存储文件格式及其扩展名与可分片属性。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义 ORC / PARQUET / AVRO / METADATA 四种文件格式，分别对应数据文件与元数据文件。
 *   <li>提供扩展名拼接、按文件名识别格式、按字符串解析格式等工具方法。
 * </ul>
 *
 * <p>设计意图：把"格式名 + 扩展名 + 是否可分片"绑定为枚举常量，避免散落的字符串硬编码； {@code splittable}
 * 标志影响扫描任务的切分策略（可分片格式才能按偏移拆分任务）。
 *
 * <p>上下游关系：被表属性读写、文件写入器选择、扫描切分等场景广泛使用。
 */
public enum FileFormat {
  ORC("orc", true),
  PARQUET("parquet", true),
  AVRO("avro", true),
  METADATA("metadata.json", false);

  private final String ext;
  private final boolean splittable;

  FileFormat(String ext, boolean splittable) {
    this.ext = "." + ext;
    this.splittable = splittable;
  }

  /** 返回该格式是否可按字节偏移分片读取。 */
  public boolean isSplittable() {
    return splittable;
  }

  /**
   * 给文件名追加本格式的扩展名（若已存在则原样返回）。
   *
   * @param filename 文件名或路径
   * @return 已含扩展名的文件名
   */
  public String addExtension(String filename) {
    if (filename.endsWith(ext)) {
      return filename;
    }
    return filename + ext;
  }

  /**
   * 根据文件名识别其所属的文件格式。
   *
   * <p>逻辑：遍历所有枚举值，按扩展名（带点）与文件名尾部做字符序列比较，命中即返回； 使用 {@link Comparators#charSequences()} 做大小写敏感匹配以避免
   * String 构造开销。 无匹配则返回 {@code null}。
   *
   * @param filename 文件名字符序列
   * @return 匹配到的格式，或 {@code null}
   */
  public static FileFormat fromFileName(CharSequence filename) {
    for (FileFormat format : FileFormat.values()) {
      int extStart = filename.length() - format.ext.length();
      if (Comparators.charSequences()
              .compare(format.ext, filename.subSequence(extStart, filename.length()))
          == 0) {
        return format;
      }
    }

    return null;
  }

  /**
   * 把字符串解析为 {@link FileFormat}。
   *
   * <p>逻辑：先把字符串转为大写后调用 {@link Enum#valueOf}；解析失败时抛出 {@link IllegalArgumentException} 并附带原始输入便于排查。
   *
   * @param fileFormat 格式名（大小写不敏感）
   * @return 对应的文件格式
   * @throws IllegalArgumentException 若入参为 null 或无法识别
   */
  public static FileFormat fromString(String fileFormat) {
    Preconditions.checkArgument(null != fileFormat, "Invalid file format: null");
    try {
      return FileFormat.valueOf(fileFormat.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format("Invalid file format: %s", fileFormat), e);
    }
  }
}
