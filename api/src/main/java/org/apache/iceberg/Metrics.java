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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ByteBuffers;

/**
 * Iceberg 文件格式的统计指标。
 *
 * <p>所属模块：iceberg-api（数据文件元数据层）。
 *
 * <p>职责：承载单个数据文件的列级统计信息，包括行数、各列字节数、值计数、null 计数、 NaN 计数、上下界等，用于查询规划时的谓词下推与文件裁剪。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable} 以支持在引擎中序列化传递（如 Spark 广播）。
 *   <li>上下界以 {@link ByteBuffer} 形式存储（按 Iceberg Spec 附录 D 的单值序列化）， 避免在 metrics 层绑定具体类型；自定义 {@link
 *       #writeObject}/{@link #readObject} 以处理 ByteBuffer 不可直接被 {@link ObjectOutputStream} 序列化的问题。
 * </ul>
 *
 * <p>上下游关系：由文件写入器（parquet/orc/avro 模块）在写文件时收集并写入 manifest； 被扫描规划、查询优化等模块读取。
 */
public class Metrics implements Serializable {

  private Long rowCount = null;
  private Map<Integer, Long> columnSizes = null;
  private Map<Integer, Long> valueCounts = null;
  private Map<Integer, Long> nullValueCounts = null;
  private Map<Integer, Long> nanValueCounts = null;
  private Map<Integer, ByteBuffer> lowerBounds = null;
  private Map<Integer, ByteBuffer> upperBounds = null;

  /** 默认构造器，所有字段为 null。 */
  public Metrics() {}

  /**
   * 构造不含上下界的指标。
   *
   * @param rowCount 文件记录数
   * @param columnSizes 列 ID 到字节数的映射
   * @param valueCounts 列 ID 到值总数的映射（含 null/NaN/重复）
   * @param nullValueCounts 列 ID 到 null 值计数的映射
   * @param nanValueCounts 列 ID 到 NaN 值计数的映射
   */
  public Metrics(
      Long rowCount,
      Map<Integer, Long> columnSizes,
      Map<Integer, Long> valueCounts,
      Map<Integer, Long> nullValueCounts,
      Map<Integer, Long> nanValueCounts) {
    this.rowCount = rowCount;
    this.columnSizes = columnSizes;
    this.valueCounts = valueCounts;
    this.nullValueCounts = nullValueCounts;
    this.nanValueCounts = nanValueCounts;
  }

  /**
   * 构造含上下界的完整指标。
   *
   * @param rowCount 文件记录数
   * @param columnSizes 列 ID 到字节数的映射
   * @param valueCounts 列 ID 到值总数的映射（含 null/NaN/重复）
   * @param nullValueCounts 列 ID 到 null 值计数的映射
   * @param nanValueCounts 列 ID 到 NaN 值计数的映射
   * @param lowerBounds 列 ID 到下界的映射（ByteBuffer 编码）
   * @param upperBounds 列 ID 到上界的映射（ByteBuffer 编码）
   */
  public Metrics(
      Long rowCount,
      Map<Integer, Long> columnSizes,
      Map<Integer, Long> valueCounts,
      Map<Integer, Long> nullValueCounts,
      Map<Integer, Long> nanValueCounts,
      Map<Integer, ByteBuffer> lowerBounds,
      Map<Integer, ByteBuffer> upperBounds) {
    this.rowCount = rowCount;
    this.columnSizes = columnSizes;
    this.valueCounts = valueCounts;
    this.nullValueCounts = nullValueCounts;
    this.nanValueCounts = nanValueCounts;
    this.lowerBounds = lowerBounds;
    this.upperBounds = upperBounds;
  }

  /**
   * 返回文件中的记录（行）数。
   *
   * @return 文件记录数
   */
  public Long recordCount() {
    return rowCount;
  }

  /**
   * 返回各列在文件中的总字节数。
   *
   * @return 列 ID 到字节数的映射
   */
  public Map<Integer, Long> columnSizes() {
    return columnSizes;
  }

  /**
   * 返回各列的值总数（含 null、NaN 与重复值）。
   *
   * @return 列 ID 到值总数的映射
   */
  public Map<Integer, Long> valueCounts() {
    return valueCounts;
  }

  /**
   * 返回各列的 null 值计数。
   *
   * @return 列 ID 到 null 值计数的映射
   */
  public Map<Integer, Long> nullValueCounts() {
    return nullValueCounts;
  }

  /**
   * 返回各 float/double 列的 NaN 值计数。
   *
   * @return 列 ID 到 NaN 值计数的映射
   */
  public Map<Integer, Long> nanValueCounts() {
    return nanValueCounts;
  }

  /**
   * 返回各列的非 null 下界值。
   *
   * <p>设计要点：值以 {@link ByteBuffer} 形式存储，按 Iceberg Spec 附录 D 单值序列化。 反序列化为具体值时使用 {@link
   * org.apache.iceberg.types.Conversions#fromByteBuffer}。
   *
   * @return 列 ID 到下界 ByteBuffer 的映射
   * @see <a href="https://iceberg.apache.org/spec/#appendix-d-single-value-serialization">Iceberg
   *     Spec - Appendix D: Single-value serialization</a>
   */
  public Map<Integer, ByteBuffer> lowerBounds() {
    return lowerBounds;
  }

  /**
   * 返回各列的非 null 上界值。
   *
   * @return 列 ID 到上界 ByteBuffer 的映射
   */
  public Map<Integer, ByteBuffer> upperBounds() {
    return upperBounds;
  }

  /**
   * 自定义序列化：将 ByteBuffer Map 转换为可序列化的字节数组形式写入。
   *
   * <p>设计意图：{@link ByteBuffer} 默认不可被 {@link ObjectOutputStream} 序列化， 故对 lowerBounds/upperBounds
   * 单独处理，其余字段直接写出。
   *
   * @param out 输出流
   * @throws IOException 序列化失败
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeObject(rowCount);
    out.writeObject(columnSizes);
    out.writeObject(valueCounts);
    out.writeObject(nullValueCounts);
    out.writeObject(nanValueCounts);

    writeByteBufferMap(out, lowerBounds);
    writeByteBufferMap(out, upperBounds);
  }

  /**
   * 将 ByteBuffer Map 序列化写入输出流。
   *
   * <p>逻辑：null 时写入 -1；非 null 时先写入 size，再逐项写入 key 与值转换后的 byte[]。
   *
   * @param out 输出流
   * @param byteBufferMap 待写入的 ByteBuffer Map
   * @throws IOException 写入失败
   */
  private static void writeByteBufferMap(
      ObjectOutputStream out, Map<Integer, ByteBuffer> byteBufferMap) throws IOException {
    if (byteBufferMap == null) {
      out.writeInt(-1);

    } else {
      // Write the size
      out.writeInt(byteBufferMap.size());

      for (Map.Entry<Integer, ByteBuffer> entry : byteBufferMap.entrySet()) {
        // Write the key and the value converted to byte[]
        out.writeObject(entry.getKey());
        out.writeObject(ByteBuffers.toByteArray(entry.getValue()));
      }
    }
  }

  /**
   * 自定义反序列化：读取各字段并还原 ByteBuffer Map。
   *
   * @param in 输入流
   * @throws IOException 反序列化失败
   * @throws ClassNotFoundException 类未找到
   */
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    rowCount = (Long) in.readObject();
    columnSizes = (Map<Integer, Long>) in.readObject();
    valueCounts = (Map<Integer, Long>) in.readObject();
    nullValueCounts = (Map<Integer, Long>) in.readObject();
    nanValueCounts = (Map<Integer, Long>) in.readObject();

    lowerBounds = readByteBufferMap(in);
    upperBounds = readByteBufferMap(in);
  }

  /**
   * 从输入流反序列化为 ByteBuffer Map。
   *
   * <p>逻辑：先读 size，若为 -1 返回 null；否则按 size 循环读取 key 与 byte[]，将 byte[] 包装为 ByteBuffer 放入结果 Map。
   *
   * @param in 输入流
   * @return 反序列化后的 ByteBuffer Map，可能为 null
   * @throws IOException 读取失败
   * @throws ClassNotFoundException 类未找到
   */
  private static Map<Integer, ByteBuffer> readByteBufferMap(ObjectInputStream in)
      throws IOException, ClassNotFoundException {
    int size = in.readInt();

    if (size == -1) {
      return null;

    } else {
      Map<Integer, ByteBuffer> result = Maps.newHashMapWithExpectedSize(size);

      for (int i = 0; i < size; ++i) {
        Integer key = (Integer) in.readObject();
        byte[] data = (byte[]) in.readObject();

        if (data != null) {
          result.put(key, ByteBuffer.wrap(data));
        } else {
          result.put(key, null);
        }
      }

      return result;
    }
  }
}
