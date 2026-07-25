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
package org.apache.iceberg.orc;

import java.io.IOException;
import org.apache.hadoop.mapred.JobConf;
import org.apache.iceberg.io.InputFile;
import org.apache.orc.impl.OrcTail;
import org.apache.orc.impl.ReaderImpl;

/**
 * 文件级说明：ORC 向量化读取所需的工具方法集合。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块）。
 *
 * <p>职责：依赖 org.apache.iceberg.orc 包中的 Iceberg 代码，提供 ORC 向量化读取
 * 所需的辅助方法，例如从 Iceberg InputFile 中提取 OrcTail 元数据。
 *
 * <p>设计意图：Hive 的 ORC 读取器需要 OrcTail 元数据来正确处理文件尾，
 * 但 Iceberg 的 InputFile 抽象未直接暴露 OrcTail。本类通过序列化技巧绕过 API 限制，
 * 取出所需元数据供 Hive 向量化读取使用。
 *
 * <p>上下游关系：上游为 {@code HiveVectorizedReader} 的 ORC 分支，下游为
 * Iceberg ORC 文件读取实现（{@link ORC#newFileReader}）。
 */
public class VectorizedReadUtils {

  /** 私有构造，工具类禁止实例化。 */
  private VectorizedReadUtils() {}

  /**
   * 打开 ORC 输入文件并读取元数据以构造 OrcTail。
   *
   * <p>逻辑：由于 API 不允许直接访问 OrcTail，借助序列化方式间接取得：
   * 先通过 {@link ORC#newFileReader} 创建 reader，再调用
   * {@link ReaderImpl#extractFileTail} 反序列化得到 OrcTail。
   *
   * @param inputFile 待读取的 ORC 文件
   * @param job 当前任务的 JobConf
   * @return 反序列化得到的 OrcTail 元数据
   * @throws IOException 当访问 ORC 文件失败时抛出
   */
  public static OrcTail getOrcTail(InputFile inputFile, JobConf job) throws IOException {

    try (ReaderImpl orcFileReader = (ReaderImpl) ORC.newFileReader(inputFile, job)) {
      return ReaderImpl.extractFileTail(orcFileReader.getSerializedFileFooter());
    }
  }
}
