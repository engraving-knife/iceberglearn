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
package org.apache.iceberg.avro;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.avro.Schema;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.iceberg.mapping.MappingUtil;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.types.TypeUtil;

/**
 * 文件级说明：支持列裁剪（投影）与字段重命名的 Avro DatumReader 包装器。
 *
 * <p>所属模块：iceberg-core（avro 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在读取 Avro 文件前，按期望 schema 裁剪文件 schema（只读必要列，减少 IO）。
 *   <li>处理文件 schema 无字段 ID 的情况：基于 NameMapping 重建 ID 映射。
 *   <li>支持字段重命名：把裁剪后的 schema 按 rename 表转换为读 schema。
 *   <li>委托内部 {@link DatumReader} 完成实际解码。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>组合优于继承：通过 {@code getReader} 函数按读 schema 动态创建底层 reader， 保持对任意 DatumReader 实现的兼容。
 *   <li>投影与演进分离：投影（pruneColumns）只决定读哪些列，演进（buildAvroProjection）处理 列改名/类型兼容，职责清晰。
 *   <li>NameMapping 懒构建：仅当文件 schema 无 ID 且未提供 mapping 时才按 expectedSchema 生成。
 * </ul>
 *
 * <p>上下游关系：由扫描/读取链路（如 ManifestEntry 读取）构造；依赖 {@link AvroSchemaUtil} 做 schema 裁剪与投影，依赖 {@link
 * MappingUtil} 生成 NameMapping。
 *
 * @param <D> 读取出的数据类型
 */
public class ProjectionDatumReader<D> implements DatumReader<D>, SupportsRowPosition {
  private final Function<Schema, DatumReader<?>> getReader;
  private final org.apache.iceberg.Schema expectedSchema;
  private final Map<String, String> renames;
  private NameMapping nameMapping;
  private Schema readSchema = null;
  private Schema fileSchema = null;
  private DatumReader<D> wrapped = null;

  /**
   * 构造投影读取器。
   *
   * @param getReader 按读 schema 创建底层 DatumReader 的工厂
   * @param expectedSchema 期望读出的 Iceberg schema（决定投影列）
   * @param renames 字段重命名映射（旧名 -> 新名）
   * @param nameMapping 字段名到 ID 的映射（可为 null，必要时自动生成）
   */
  public ProjectionDatumReader(
      Function<Schema, DatumReader<?>> getReader,
      org.apache.iceberg.Schema expectedSchema,
      Map<String, String> renames,
      NameMapping nameMapping) {
    this.getReader = getReader;
    this.expectedSchema = expectedSchema;
    this.renames = renames;
    this.nameMapping = nameMapping;
  }

  /**
   * 注入行号 Supplier，转发给底层 reader（若支持）。
   *
   * @param posSupplier 行号提供者
   */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (wrapped instanceof SupportsRowPosition) {
      ((SupportsRowPosition) wrapped).setRowPositionSupplier(posSupplier);
    }
  }

  /**
   * 设置文件 schema，并据此裁剪/投影出读 schema、构建底层 reader。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>若 nameMapping 为空且文件 schema 无 ID，则按 expectedSchema 自动生成 NameMapping。
   *   <li>取 expectedSchema 的投影字段 ID 集合。
   *   <li>对文件 schema 做列裁剪得到 prunedSchema。
   *   <li>按 rename 表把 prunedSchema 转成读 schema（处理改名/类型）。
   *   <li>用读 schema 创建底层 DatumReader 并绑定文件 schema。
   * </ol>
   *
   * @param newFileSchema 文件实际 schema
   */
  @Override
  public void setSchema(Schema newFileSchema) {
    this.fileSchema = newFileSchema;
    if (nameMapping == null && !AvroSchemaUtil.hasIds(fileSchema)) {
      nameMapping = MappingUtil.create(expectedSchema);
    }
    Set<Integer> projectedIds = TypeUtil.getProjectedIds(expectedSchema);
    Schema prunedSchema = AvroSchemaUtil.pruneColumns(newFileSchema, projectedIds, nameMapping);
    this.readSchema = AvroSchemaUtil.buildAvroProjection(prunedSchema, expectedSchema, renames);
    this.wrapped = newDatumReader();
  }

  /**
   * 读取一条记录，委托给底层 reader。
   *
   * @param reuse 可复用对象（可为 null）
   * @param in Avro 解码器
   * @return 读取到的数据
   * @throws IOException 读取失败时抛出
   */
  @Override
  public D read(D reuse, Decoder in) throws IOException {
    return wrapped.read(reuse, in);
  }

  /**
   * 用读 schema 创建底层 DatumReader 并绑定文件 schema。
   *
   * @return 已绑 schema 的 DatumReader
   */
  @SuppressWarnings("unchecked")
  private DatumReader<D> newDatumReader() {
    DatumReader<D> reader = (DatumReader<D>) getReader.apply(readSchema);
    reader.setSchema(fileSchema);
    return reader;
  }
}
