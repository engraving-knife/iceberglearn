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

import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 行级操作模式枚举（iceberg-core 核心层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义表行级操作（UPDATE/DELETE/MERGE）的两种写入策略
 *   <li>提供基于名称字符串的枚举反序列化能力
 * </ul>
 *
 * <p>设计意图：
 *
 * <p>Iceberg 支持两种修改记录的方式：copy-on-write 与 merge-on-read。
 *
 * <p>copy-on-write：变更立即物化，匹配的数据文件被替换为反映新表状态的新数据文件。
 * 例如删除某条记录时，包含该记录的数据文件必须被不含该记录的新文件替换，所有未变更行都需复制到新文件。
 *
 * <p>merge-on-read：变更不立即物化，被删除和被更新记录的 ID 写入删除文件（在读时应用）， 被更新/插入的记录写入新数据文件，并与删除文件一起提交。
 *
 * <p>copy-on-write 写入更耗时、占用更多资源，但读取无额外开销；merge-on-read 写入更快， 但读时需应用删除文件，开销更大。
 *
 * <p>上下游关系：被表属性 {@link TableProperties} 配置项引用，由写入引擎（如 {@link RowDelta}、{@link
 * OverwriteFiles}）在执行行级操作时读取并决定实现路径。
 */
public enum RowLevelOperationMode {
  COPY_ON_WRITE("copy-on-write"),
  MERGE_ON_READ("merge-on-read");

  private final String modeName;

  /**
   * 枚举构造方法。
   *
   * @param modeName 模式的字符串名称，用于序列化与配置解析
   */
  RowLevelOperationMode(String modeName) {
    this.modeName = modeName;
  }

  /**
   * 根据模式名称字符串解析为对应的枚举实例。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验入参非空；
   *   <li>依次与 {@link #COPY_ON_WRITE}、{@link #MERGE_ON_READ} 的 {@link #modeName()} 进行大小写不敏感比较；
   *   <li>匹配则返回对应枚举，否则抛出 {@link IllegalArgumentException}。
   * </ol>
   *
   * @param modeName 模式名称字符串
   * @return 解析得到的行级操作模式枚举
   */
  public static RowLevelOperationMode fromName(String modeName) {
    Preconditions.checkArgument(modeName != null, "Mode name is null");
    if (COPY_ON_WRITE.modeName().equalsIgnoreCase(modeName)) {
      return COPY_ON_WRITE;
    } else if (MERGE_ON_READ.modeName().equalsIgnoreCase(modeName)) {
      return MERGE_ON_READ;
    } else {
      throw new IllegalArgumentException("Unknown row-level operation mode: " + modeName);
    }
  }

  /**
   * 返回该模式的字符串名称。
   *
   * @return 模式名称，用于持久化到表属性或元数据 JSON
   */
  public String modeName() {
    return modeName;
  }
}
