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
package org.apache.iceberg.data.orc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.orc.OrcRowReader;
import org.apache.iceberg.orc.OrcSchemaWithTypeVisitor;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.StructColumnVector;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * 通用 ORC 行读取器：将 ORC 的 {@link VectorizedRowBatch} 列式数据解码为 Iceberg 的 {@link Record} 行对象。
 *
 * <p>所属模块：iceberg-orc（ORC 读写支持模块，基于 Apache ORC 与 Hive 列向量实现）。 本类位于 data/orc 子包，提供面向通用 {@code
 * Record} 模型的具体读取实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OrcRowReader}，按行读取 ORC 批数据并组装成 {@link Record}。
 *   <li>依据 Iceberg Schema 与 ORC 文件 schema 的对应关系，通过 schema 访问器构建 树状的 {@link
 *       OrcValueReader}，按字段类型分发到具体读取器。
 *   <li>支持通过 idToConstant 注入常量列（投影场景下用于补充缺失字段）。
 * </ul>
 *
 * <p>设计意图：通过 {@link OrcSchemaWithTypeVisitor} 同步遍历 Iceberg 与 ORC 两套类型 树，对每个叶子节点挑选匹配的
 * reader，从而解耦“类型映射规则”与“实际读值逻辑”。 读取时把整个 batch 包装为 {@link StructColumnVector}，复用字段级 reader 完成 row
 * 级解码。
 *
 * <p>上下游关系：被 {@code GenericReaderFactory} 等读取入口调用；内部依赖 {@link GenericOrcReaders} 提供的具体类型 reader 与
 * {@link OrcValueReaders} 提供的基础 reader。
 */
public class GenericOrcReader implements OrcRowReader<Record> {
  private final OrcValueReader<?> reader;

  /**
   * 构造读取器，依据期望 schema 与 ORC schema 构建内部字段读取树。
   *
   * @param expectedSchema 期望读取的 Iceberg Schema（可做投影裁剪）
   * @param readOrcSchema 实际读取的 ORC TypeDescription（已按投影裁剪）
   * @param idToConstant 字段 id 到常量值的映射，用于补充文件中缺失但 schema 中存在的列
   */
  public GenericOrcReader(
      Schema expectedSchema, TypeDescription readOrcSchema, Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(
            expectedSchema, readOrcSchema, new ReadBuilder(idToConstant));
  }

  /**
   * 构建不带常量列的 reader，便捷工厂方法。
   *
   * @param expectedSchema 期望的 Iceberg Schema
   * @param fileSchema ORC 文件 schema
   * @return 可读取 {@link Record} 的 {@link OrcRowReader}
   */
  public static OrcRowReader<Record> buildReader(
      Schema expectedSchema, TypeDescription fileSchema) {
    return new GenericOrcReader(expectedSchema, fileSchema, Collections.emptyMap());
  }

  /**
   * 构建带常量列映射的 reader，便捷工厂方法。
   *
   * @param expectedSchema 期望的 Iceberg Schema
   * @param fileSchema ORC 文件 schema
   * @param idToConstant 字段 id 到常量值的映射
   * @return 可读取 {@link Record} 的 {@link OrcRowReader}
   */
  public static OrcRowReader<Record> buildReader(
      Schema expectedSchema, TypeDescription fileSchema, Map<Integer, ?> idToConstant) {
    return new GenericOrcReader(expectedSchema, fileSchema, idToConstant);
  }

  /**
   * 读取 batch 中指定行的数据并返回 {@link Record}。
   *
   * <p>逻辑：把整个 batch 的列数组包装成单列 {@link StructColumnVector}，再委托根 reader 读取第 {@code row} 行。
   *
   * @param batch ORC 列式批量数据
   * @param row 当前行号
   * @return 解码后的 {@link Record}
   */
  @Override
  public Record read(VectorizedRowBatch batch, int row) {
    return (Record) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }

  /** {@inheritDoc} 将当前 batch 在文件中的偏移透传给内部 reader，供需要文件位置的 reader 使用。 */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

  /**
   * Schema 同步访问器：在 Iceberg 与 ORC 类型树配对遍历过程中，按节点类型构造对应的 {@link OrcValueReader}。
   *
   * <p>设计意图：把“类型→reader”映射集中在此处，便于维护 Iceberg 与 ORC 类型之间的 对应关系（例如 BYTE/SHORT 在 Iceberg 中统一为
   * int，TIMESTAMP_INSTANT 对应带时区时间等）。
   */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<OrcValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /** 构造 struct 类型 reader：复用 {@link GenericOrcReaders#struct} 创建 StructReader， 注入常量列映射以支持投影。 */
    @Override
    public OrcValueReader<?> record(
        Types.StructType expected,
        TypeDescription record,
        List<String> names,
        List<OrcValueReader<?>> fields) {
      return GenericOrcReaders.struct(fields, expected, idToConstant);
    }

    /** 构造 list 类型 reader：包装元素 reader 为数组 reader。 */
    @Override
    public OrcValueReader<?> list(
        Types.ListType iList, TypeDescription array, OrcValueReader<?> elementReader) {
      return GenericOrcReaders.array(elementReader);
    }

    /** 构造 map 类型 reader：组合 key/value reader。 */
    @Override
    public OrcValueReader<?> map(
        Types.MapType iMap,
        TypeDescription map,
        OrcValueReader<?> keyReader,
        OrcValueReader<?> valueReader) {
      return GenericOrcReaders.map(keyReader, valueReader);
    }

    /**
     * 构造叶子类型 reader：按 ORC 类别分发。
     *
     * <p>逻辑：以 ORC category 为分支依据，再结合 Iceberg 实际类型做二次区分 （如 LONG 类别可能是 TIME 或 LONG；BINARY 类别可能是
     * UUID/FIXED/BINARY）， 命中即返回对应 reader；未覆盖的类型抛出异常。
     *
     * @param iPrimitive Iceberg 侧的原始类型
     * @param primitive ORC 侧的原始类型描述
     * @return 对应的 {@link OrcValueReader}
     * @throws IllegalArgumentException 出现未覆盖的 ORC 类别
     * @throws IllegalStateException 同一 ORC 类别下 Iceberg 类型不匹配
     */
    @Override
    public OrcValueReader<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      switch (primitive.getCategory()) {
        case BOOLEAN:
          return OrcValueReaders.booleans();
        case BYTE:
          // Iceberg does not have a byte type. Use int
        case SHORT:
          // Iceberg does not have a short type. Use int
        case INT:
          return OrcValueReaders.ints();
        case LONG:
          switch (iPrimitive.typeId()) {
            case TIME:
              return GenericOrcReaders.times();
            case LONG:
              return OrcValueReaders.longs();
            default:
              throw new IllegalStateException(
                  String.format(
                      "Invalid iceberg type %s corresponding to ORC type %s",
                      iPrimitive, primitive));
          }

        case FLOAT:
          return OrcValueReaders.floats();
        case DOUBLE:
          return OrcValueReaders.doubles();
        case DATE:
          return GenericOrcReaders.dates();
        case TIMESTAMP:
          return GenericOrcReaders.timestamps();
        case TIMESTAMP_INSTANT:
          return GenericOrcReaders.timestampTzs();
        case DECIMAL:
          return GenericOrcReaders.decimals();
        case CHAR:
        case VARCHAR:
        case STRING:
          return GenericOrcReaders.strings();
        case BINARY:
          switch (iPrimitive.typeId()) {
            case UUID:
              return GenericOrcReaders.uuids();
            case FIXED:
              return OrcValueReaders.bytes();
            case BINARY:
              return GenericOrcReaders.bytes();
            default:
              throw new IllegalStateException(
                  String.format(
                      "Invalid iceberg type %s corresponding to ORC type %s",
                      iPrimitive, primitive));
          }
        default:
          throw new IllegalArgumentException("Unhandled type " + primitive);
      }
    }
  }
}
