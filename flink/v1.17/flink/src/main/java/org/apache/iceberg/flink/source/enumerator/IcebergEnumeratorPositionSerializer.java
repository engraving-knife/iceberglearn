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
package org.apache.iceberg.flink.source.enumerator;

import java.io.IOException;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;

/**
 * {@link IcebergEnumeratorPosition} 的版本化序列化器。
 *
 * <p>所属模块：iceberg-flink（source enumerator 侧），实现 {@link SimpleVersionedSerializer}。
 *
 * <p>职责：将枚举位置（快照 id 与时间戳）序列化/反序列化，用于 checkpoint 持久化。
 *
 * <p>设计意图：使用 {@link ThreadLocal} 缓存 {@link DataOutputSerializer} 避免重复分配； 字段以布尔标志位指示是否为 null，紧凑编码。单例
 * {@link #INSTANCE}。
 *
 * <p>上下游关系：被 enumerator 状态序列化器调用。
 */
class IcebergEnumeratorPositionSerializer
    implements SimpleVersionedSerializer<IcebergEnumeratorPosition> {

  /** 单例实例。 */
  public static final IcebergEnumeratorPositionSerializer INSTANCE =
      new IcebergEnumeratorPositionSerializer();

  private static final int VERSION = 1;

  private static final ThreadLocal<DataOutputSerializer> SERIALIZER_CACHE =
      ThreadLocal.withInitial(() -> new DataOutputSerializer(128));

  /** 返回序列化版本。 */
  @Override
  public int getVersion() {
    return VERSION;
  }

  /** 序列化枚举位置为字节数组（委托 V1）。 */
  @Override
  public byte[] serialize(IcebergEnumeratorPosition position) throws IOException {
    return serializeV1(position);
  }

  /** 按版本反序列化；仅支持 V1，其余抛出异常。 */
  @Override
  public IcebergEnumeratorPosition deserialize(int version, byte[] serialized) throws IOException {
    switch (version) {
      case 1:
        return deserializeV1(serialized);
      default:
        throw new IOException("Unknown version: " + version);
    }
  }

  /** V1 序列化：先写快照 id 是否存在的标志与值，再写时间戳标志与值，复用 ThreadLocal 缓冲。 */
  private byte[] serializeV1(IcebergEnumeratorPosition position) throws IOException {
    DataOutputSerializer out = SERIALIZER_CACHE.get();
    out.writeBoolean(position.snapshotId() != null);
    if (position.snapshotId() != null) {
      out.writeLong(position.snapshotId());
    }
    out.writeBoolean(position.snapshotTimestampMs() != null);
    if (position.snapshotTimestampMs() != null) {
      out.writeLong(position.snapshotTimestampMs());
    }
    byte[] result = out.getCopyOfBuffer();
    out.clear();
    return result;
  }

  /** V1 反序列化：按标志位读取快照 id 与时间戳，快照 id 为空时返回 empty 位置。 */
  private IcebergEnumeratorPosition deserializeV1(byte[] serialized) throws IOException {
    DataInputDeserializer in = new DataInputDeserializer(serialized);
    Long snapshotId = null;
    if (in.readBoolean()) {
      snapshotId = in.readLong();
    }

    Long snapshotTimestampMs = null;
    if (in.readBoolean()) {
      snapshotTimestampMs = in.readLong();
    }

    if (snapshotId != null) {
      return IcebergEnumeratorPosition.of(snapshotId, snapshotTimestampMs);
    } else {
      return IcebergEnumeratorPosition.empty();
    }
  }
}
