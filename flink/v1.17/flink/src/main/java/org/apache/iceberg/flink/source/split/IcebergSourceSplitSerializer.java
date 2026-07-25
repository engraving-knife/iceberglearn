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
package org.apache.iceberg.flink.source.split;

import java.io.IOException;
import org.apache.flink.annotation.Internal;
import org.apache.flink.core.io.SimpleVersionedSerializer;

/**
 * 文件级说明：{@link IcebergSourceSplit} 的版本化序列化器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/split 子包）。
 *
 * <p>职责：实现 Flink {@link SimpleVersionedSerializer}，把 split 序列化/反序列化到字节， 并按版本号分发到 V1（Java 序列化）或
 * V2（JSON 序列化）实现。
 *
 * <p>设计意图：通过版本化支持向后兼容，新增字段时可升级 VERSION 与对应的反序列化分支。
 *
 * <p>上下游关系：上游为 Flink Source 框架（split 序列化到 checkpoint）， 下游为 {@link IcebergSourceSplit} 的具体序列化方法。
 */
@Internal
public class IcebergSourceSplitSerializer implements SimpleVersionedSerializer<IcebergSourceSplit> {
  private static final int VERSION = 2;

  private final boolean caseSensitive;

  /** 构造序列化器，传入列名是否大小写敏感（用于 V2 反序列化）。 */
  public IcebergSourceSplitSerializer(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

  /** 返回当前序列化版本号。 */
  @Override
  public int getVersion() {
    return VERSION;
  }

  /** 委托给 split 的 V2 序列化方法。 */
  @Override
  public byte[] serialize(IcebergSourceSplit split) throws IOException {
    return split.serializeV2();
  }

  /**
   * 按版本号反序列化 split。
   *
   * <p>逻辑：V1 走 Java 序列化，V2 走自定义 JSON 格式（带 caseSensitive 参数）， 其他版本抛出异常。
   *
   * @param version 序列化版本
   * @param serialized 序列化字节
   * @return 反序列化得到的 split
   * @throws IOException 不支持的版本或反序列化失败时抛出
   */
  @Override
  public IcebergSourceSplit deserialize(int version, byte[] serialized) throws IOException {
    switch (version) {
      case 1:
        return IcebergSourceSplit.deserializeV1(serialized);
      case 2:
        return IcebergSourceSplit.deserializeV2(serialized, caseSensitive);
      default:
        throw new IOException(
            String.format(
                "Failed to deserialize IcebergSourceSplit. "
                    + "Encountered unsupported version: %d. Supported version are [1]",
                version));
    }
  }
}
