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
package org.apache.iceberg.flink.sink;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.apache.flink.core.io.SimpleVersionedSerializer;
import org.apache.iceberg.ManifestFile;
import org.apache.iceberg.ManifestFiles;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * {@link DeltaManifests} 的 Flink 版本化序列化器。
 *
 * <p>所属模块：iceberg-flink（sink 侧），实现 {@link SimpleVersionedSerializer}。
 *
 * <p>职责：将一次 checkpoint 产生的数据/删除 manifest 与引用的数据文件列表序列化为字节数组， 用于在 Flink 状态后端持久化与恢复。
 *
 * <p>设计意图：支持 V1（仅数据 manifest）与 V2（数据+删除 manifest+引用文件）两个版本， 通过 {@link ManifestFiles#encode} 复用
 * Iceberg 的 manifest 编码；单例 {@link #INSTANCE} 避免重复创建。
 *
 * <p>上下游关系：被 {@link IcebergFilesCommitter} 在 checkpoint 快照/恢复时调用。
 */
class DeltaManifestsSerializer implements SimpleVersionedSerializer<DeltaManifests> {
  private static final int VERSION_1 = 1;
  private static final int VERSION_2 = 2;
  private static final byte[] EMPTY_BINARY = new byte[0];

  static final DeltaManifestsSerializer INSTANCE = new DeltaManifestsSerializer();

  /** 返回当前序列化版本（V2）。 */
  @Override
  public int getVersion() {
    return VERSION_2;
  }

  /**
   * 将 DeltaManifests 序列化为字节数组。
   *
   * <p>逻辑：分别编码 dataManifest 与 deleteManifest（空则写 0 长度占位）， 再依次写入引用数据文件列表的 UTF 字符串。
   *
   * @param deltaManifests 待序列化对象
   * @return 序列化字节数组
   * @throws IOException 编码失败时抛出
   */
  @Override
  public byte[] serialize(DeltaManifests deltaManifests) throws IOException {
    Preconditions.checkNotNull(
        deltaManifests, "DeltaManifests to be serialized should not be null");

    ByteArrayOutputStream binaryOut = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(binaryOut);

    byte[] dataManifestBinary = EMPTY_BINARY;
    if (deltaManifests.dataManifest() != null) {
      dataManifestBinary = ManifestFiles.encode(deltaManifests.dataManifest());
    }

    out.writeInt(dataManifestBinary.length);
    out.write(dataManifestBinary);

    byte[] deleteManifestBinary = EMPTY_BINARY;
    if (deltaManifests.deleteManifest() != null) {
      deleteManifestBinary = ManifestFiles.encode(deltaManifests.deleteManifest());
    }

    out.writeInt(deleteManifestBinary.length);
    out.write(deleteManifestBinary);

    CharSequence[] referencedDataFiles = deltaManifests.referencedDataFiles();
    out.writeInt(referencedDataFiles.length);
    for (CharSequence referencedDataFile : referencedDataFiles) {
      out.writeUTF(referencedDataFile.toString());
    }

    return binaryOut.toByteArray();
  }

  /**
   * 按版本号反序列化。
   *
   * <p>逻辑：V1 走 {@link #deserializeV1}，V2 走 {@link #deserializeV2}，其余版本抛异常。
   *
   * @param version 序列化版本
   * @param serialized 序列化字节
   * @return DeltaManifests 对象
   * @throws IOException 解码失败时抛出
   */
  @Override
  public DeltaManifests deserialize(int version, byte[] serialized) throws IOException {
    if (version == VERSION_1) {
      return deserializeV1(serialized);
    } else if (version == VERSION_2) {
      return deserializeV2(serialized);
    } else {
      throw new RuntimeException("Unknown serialize version: " + version);
    }
  }

  /** V1 反序列化：整个字节为一个数据 manifest，无删除 manifest。 */
  private DeltaManifests deserializeV1(byte[] serialized) throws IOException {
    return new DeltaManifests(ManifestFiles.decode(serialized), null);
  }

  /**
   * V2 反序列化。
   *
   * <p>逻辑：依次读取数据 manifest 长度与字节、删除 manifest 长度与字节、引用数据文件数量与各 UTF 字符串， 组装为 DeltaManifests。
   */
  private DeltaManifests deserializeV2(byte[] serialized) throws IOException {
    ManifestFile dataManifest = null;
    ManifestFile deleteManifest = null;

    ByteArrayInputStream binaryIn = new ByteArrayInputStream(serialized);
    DataInputStream in = new DataInputStream(binaryIn);

    int dataManifestSize = in.readInt();
    if (dataManifestSize > 0) {
      byte[] dataManifestBinary = new byte[dataManifestSize];
      Preconditions.checkState(in.read(dataManifestBinary) == dataManifestSize);

      dataManifest = ManifestFiles.decode(dataManifestBinary);
    }

    int deleteManifestSize = in.readInt();
    if (deleteManifestSize > 0) {
      byte[] deleteManifestBinary = new byte[deleteManifestSize];
      Preconditions.checkState(in.read(deleteManifestBinary) == deleteManifestSize);

      deleteManifest = ManifestFiles.decode(deleteManifestBinary);
    }

    int referenceDataFileNum = in.readInt();
    CharSequence[] referencedDataFiles = new CharSequence[referenceDataFileNum];
    for (int i = 0; i < referenceDataFileNum; i++) {
      referencedDataFiles[i] = in.readUTF();
    }

    return new DeltaManifests(dataManifest, deleteManifest, referencedDataFiles);
  }
}
