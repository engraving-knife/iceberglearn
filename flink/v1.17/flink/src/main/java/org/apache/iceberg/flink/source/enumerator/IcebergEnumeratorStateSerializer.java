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
import java.util.Collection;
import org.apache.flink.annotation.Internal;
import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.flink.core.memory.DataInputDeserializer;
import org.apache.flink.core.memory.DataOutputSerializer;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitSerializer;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitState;
import org.apache.iceberg.flink.source.split.IcebergSourceSplitStatus;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：Iceberg enumerator 状态的序列化器，支持版本化的序列化/反序列化。
 *
 * <p>所属模块：iceberg-flink（source/enumerator 子包），实现 Flink 的 {@link SimpleVersionedSerializer}。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>序列化 {@link IcebergEnumeratorState}（含枚举位置和待分配 split）。
 *   <li>支持 V1 和 V2 两个序列化版本的读取，V2 为当前写入版本。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>版本化序列化：通过 VERSION 区分序列化格式，deserialize 时按 version 分发。
 *   <li>ThreadLocal 缓存 DataOutputSerializer：避免每次序列化分配缓冲区。
 *   <li>委托 IcebergEnumeratorPositionSerializer 和 IcebergSourceSplitSerializer 分别序列化位置和 split。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergSource} 注册为 enumerator 的状态序列化器； 内部委托 positionSerializer 和
 * splitSerializer 完成具体序列化。
 */
@Internal
public class IcebergEnumeratorStateSerializer
    implements SimpleVersionedSerializer<IcebergEnumeratorState> {

  private static final int VERSION = 2;

  private static final ThreadLocal<DataOutputSerializer> SERIALIZER_CACHE =
      ThreadLocal.withInitial(() -> new DataOutputSerializer(1024));

  private final IcebergEnumeratorPositionSerializer positionSerializer =
      IcebergEnumeratorPositionSerializer.INSTANCE;
  private final IcebergSourceSplitSerializer splitSerializer;

  /**
   * 构造方法。
   *
   * @param caseSensitive 是否大小写敏感（影响 split 序列化器）
   */
  public IcebergEnumeratorStateSerializer(boolean caseSensitive) {
    this.splitSerializer = new IcebergSourceSplitSerializer(caseSensitive);
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  /** 序列化 enumerator 状态为字节数组（使用 V2 格式）。 */
  @Override
  public byte[] serialize(IcebergEnumeratorState enumState) throws IOException {
    return serializeV2(enumState);
  }

  /**
   * 反序列化 enumerator 状态。
   *
   * <p>逻辑：按 version 分发到 V1 或 V2 反序列化方法，支持向后兼容。
   *
   * @param version 序列化版本
   * @param serialized 序列化字节数组
   * @return enumerator 状态
   */
  @Override
  public IcebergEnumeratorState deserialize(int version, byte[] serialized) throws IOException {
    switch (version) {
      case 1:
        return deserializeV1(serialized);
      case 2:
        return deserializeV2(serialized);
      default:
        throw new IOException("Unknown version: " + version);
    }
  }

  @VisibleForTesting
  byte[] serializeV1(IcebergEnumeratorState enumState) throws IOException {
    DataOutputSerializer out = SERIALIZER_CACHE.get();
    serializeEnumeratorPosition(out, enumState.lastEnumeratedPosition(), positionSerializer);
    serializePendingSplits(out, enumState.pendingSplits(), splitSerializer);
    byte[] result = out.getCopyOfBuffer();
    out.clear();
    return result;
  }

  @VisibleForTesting
  IcebergEnumeratorState deserializeV1(byte[] serialized) throws IOException {
    DataInputDeserializer in = new DataInputDeserializer(serialized);
    IcebergEnumeratorPosition enumeratorPosition =
        deserializeEnumeratorPosition(in, positionSerializer);
    Collection<IcebergSourceSplitState> pendingSplits =
        deserializePendingSplits(in, splitSerializer);
    return new IcebergEnumeratorState(enumeratorPosition, pendingSplits);
  }

  @VisibleForTesting
  byte[] serializeV2(IcebergEnumeratorState enumState) throws IOException {
    DataOutputSerializer out = SERIALIZER_CACHE.get();
    serializeEnumeratorPosition(out, enumState.lastEnumeratedPosition(), positionSerializer);
    serializePendingSplits(out, enumState.pendingSplits(), splitSerializer);
    serializeEnumerationSplitCountHistory(out, enumState.enumerationSplitCountHistory());
    byte[] result = out.getCopyOfBuffer();
    out.clear();
    return result;
  }

  @VisibleForTesting
  IcebergEnumeratorState deserializeV2(byte[] serialized) throws IOException {
    DataInputDeserializer in = new DataInputDeserializer(serialized);
    IcebergEnumeratorPosition enumeratorPosition =
        deserializeEnumeratorPosition(in, positionSerializer);
    Collection<IcebergSourceSplitState> pendingSplits =
        deserializePendingSplits(in, splitSerializer);
    int[] enumerationSplitCountHistory = deserializeEnumerationSplitCountHistory(in);
    return new IcebergEnumeratorState(
        enumeratorPosition, pendingSplits, enumerationSplitCountHistory);
  }

  private static void serializeEnumeratorPosition(
      DataOutputSerializer out,
      IcebergEnumeratorPosition enumeratorPosition,
      IcebergEnumeratorPositionSerializer positionSerializer)
      throws IOException {
    out.writeBoolean(enumeratorPosition != null);
    if (enumeratorPosition != null) {
      out.writeInt(positionSerializer.getVersion());
      byte[] positionBytes = positionSerializer.serialize(enumeratorPosition);
      out.writeInt(positionBytes.length);
      out.write(positionBytes);
    }
  }

  private static IcebergEnumeratorPosition deserializeEnumeratorPosition(
      DataInputDeserializer in, IcebergEnumeratorPositionSerializer positionSerializer)
      throws IOException {
    IcebergEnumeratorPosition enumeratorPosition = null;
    if (in.readBoolean()) {
      int version = in.readInt();
      byte[] positionBytes = new byte[in.readInt()];
      in.read(positionBytes);
      enumeratorPosition = positionSerializer.deserialize(version, positionBytes);
    }
    return enumeratorPosition;
  }

  private static void serializePendingSplits(
      DataOutputSerializer out,
      Collection<IcebergSourceSplitState> pendingSplits,
      IcebergSourceSplitSerializer splitSerializer)
      throws IOException {
    out.writeInt(splitSerializer.getVersion());
    out.writeInt(pendingSplits.size());
    for (IcebergSourceSplitState splitState : pendingSplits) {
      byte[] splitBytes = splitSerializer.serialize(splitState.split());
      out.writeInt(splitBytes.length);
      out.write(splitBytes);
      out.writeUTF(splitState.status().name());
    }
  }

  private static Collection<IcebergSourceSplitState> deserializePendingSplits(
      DataInputDeserializer in, IcebergSourceSplitSerializer splitSerializer) throws IOException {
    int splitSerializerVersion = in.readInt();
    int splitCount = in.readInt();
    Collection<IcebergSourceSplitState> pendingSplits = Lists.newArrayListWithCapacity(splitCount);
    for (int i = 0; i < splitCount; ++i) {
      byte[] splitBytes = new byte[in.readInt()];
      in.read(splitBytes);
      IcebergSourceSplit split = splitSerializer.deserialize(splitSerializerVersion, splitBytes);
      String statusName = in.readUTF();
      pendingSplits.add(
          new IcebergSourceSplitState(split, IcebergSourceSplitStatus.valueOf(statusName)));
    }
    return pendingSplits;
  }

  private static void serializeEnumerationSplitCountHistory(
      DataOutputSerializer out, int[] enumerationSplitCountHistory) throws IOException {
    out.writeInt(enumerationSplitCountHistory.length);
    if (enumerationSplitCountHistory.length > 0) {
      for (int enumerationSplitCount : enumerationSplitCountHistory) {
        out.writeInt(enumerationSplitCount);
      }
    }
  }

  private static int[] deserializeEnumerationSplitCountHistory(DataInputDeserializer in)
      throws IOException {
    int historySize = in.readInt();
    int[] history = new int[historySize];
    if (historySize > 0) {
      for (int i = 0; i < historySize; ++i) {
        history[i] = in.readInt();
      }
    }

    return history;
  }
}
