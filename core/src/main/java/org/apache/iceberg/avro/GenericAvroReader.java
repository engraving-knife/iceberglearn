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
import java.util.List;
import java.util.function.Supplier;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.iceberg.common.DynClasses;
import org.apache.iceberg.data.avro.DecoderResolver;

/**
 * 文件级说明：通用 Avro 数据读取器，将 Avro 编码流解码为内存对象。
 *
 * <p>所属模块：iceberg-core（avro 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link DatumReader}，按 Avro Schema 从 {@link Decoder} 读取对象树。
 *   <li>支持 schema 演进：用读 schema 与文件 schema 做别名映射，缺失/新增字段可被解析。
 *   <li>实现 {@link SupportsRowPosition}，可注入行号信息（用于扫描时定位文件内行号）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>读写 schema 分离：{@code readSchema} 是消费方期望的 schema，{@code fileSchema} 是文件 实际写入时的 schema（经
 *       {@link Schema#applyAliases} 做别名映射后与 readSchema 对齐）， 二者结合支持字段重命名、新增等演进场景。
 *   <li>延迟构建 reader：reader 在 {@link #setSchema} 时按 readSchema 一次性构建，避免每行重建。
 *   <li>ClassLoader 可替换：引擎集成时可能需要用特定 ClassLoader 加载 IndexedRecord 子类， 通过 {@link #setClassLoader}
 *       注入。
 * </ul>
 *
 * <p>上下游关系：由 Iceberg Avro 读取链路（Manifest/数据文件读取）调用；依赖 {@link AvroSchemaVisitor} 遍历 schema、{@link
 * DecoderResolver} 做兼容性解码。
 *
 * @param <T> 读取出的数据类型
 */
public class GenericAvroReader<T> implements DatumReader<T>, SupportsRowPosition {

  private final Schema readSchema;
  private ClassLoader loader = Thread.currentThread().getContextClassLoader();
  private Schema fileSchema = null;
  private ValueReader<T> reader = null;

  /**
   * 工厂方法，按读 schema 创建读取器。
   *
   * @param schema 读 schema
   * @param <D> 数据类型
   * @return 新的 {@link GenericAvroReader}
   */
  public static <D> GenericAvroReader<D> create(Schema schema) {
    return new GenericAvroReader<>(schema);
  }

  /**
   * 构造器，仅记录 readSchema，reader 延迟到 {@link #setSchema} 时构建。
   *
   * @param readSchema 读 schema
   */
  GenericAvroReader(Schema readSchema) {
    this.readSchema = readSchema;
  }

  /**
   * 按 readSchema 构建 ValueReader 树。
   *
   * <p>逻辑步骤：通过 {@link AvroSchemaVisitor#visit} 遍历 readSchema，由 {@link ReadBuilder} 为各节点构造 reader。
   */
  @SuppressWarnings("unchecked")
  private void initReader() {
    this.reader = (ValueReader<T>) AvroSchemaVisitor.visit(readSchema, new ReadBuilder(loader));
  }

  /**
   * 设置文件 schema 并构建 reader。
   *
   * <p>逻辑步骤：先用 {@link Schema#applyAliases} 把文件 schema 的字段映射到 readSchema（处理 字段重命名/演进），再 {@link
   * #initReader} 构建 reader。
   *
   * @param schema 文件实际 schema
   */
  @Override
  public void setSchema(Schema schema) {
    this.fileSchema = Schema.applyAliases(schema, readSchema);
    initReader();
  }

  /**
   * 设置类加载器，用于加载 Avro record 实现类。
   *
   * @param newClassLoader 新的类加载器
   */
  public void setClassLoader(ClassLoader newClassLoader) {
    this.loader = newClassLoader;
  }

  /**
   * 注入行号 Supplier，传递给支持行号的子 reader。
   *
   * @param posSupplier 行号提供者
   */
  @Override
  public void setRowPositionSupplier(Supplier<Long> posSupplier) {
    if (reader instanceof SupportsRowPosition) {
      ((SupportsRowPosition) reader).setRowPositionSupplier(posSupplier);
    }
  }

  /**
   * 从 decoder 读取一条记录。
   *
   * <p>委托给 {@link DecoderResolver#resolveAndRead}，处理读写 schema 差异后调用 reader。
   *
   * @param reuse 可复用对象（可为 null）
   * @param decoder Avro 解码器
   * @return 读取到的对象
   * @throws IOException 读取失败时抛出
   */
  @Override
  public T read(T reuse, Decoder decoder) throws IOException {
    return DecoderResolver.resolveAndRead(decoder, readSchema, fileSchema, reader, reuse);
  }

  /**
   * Schema 访问者，按 Avro Schema 节点类型构建 {@link ValueReader} 树。
   *
   * <p>设计意图：与 {@link GenericAvroWriter.WriteBuilder} 对称，把读侧逻辑按结构类型分派； 区别在于读取侧需处理 schema 演进（如
   * timestamp-millis 转 micros）。
   */
  private static class ReadBuilder extends AvroSchemaVisitor<ValueReader<?>> {
    private final ClassLoader loader;

    private ReadBuilder(ClassLoader loader) {
      this.loader = loader;
    }

    /**
     * 为 record 构建 reader；优先尝试加载命名 record 实现类（IndexedRecord），失败则回退通用实现。
     *
     * @param record record schema
     * @param names 字段名列表
     * @param fields 各字段 reader
     * @return record reader
     */
    @Override
    @SuppressWarnings("unchecked")
    public ValueReader<?> record(Schema record, List<String> names, List<ValueReader<?>> fields) {
      try {
        Class<?> recordClass =
            DynClasses.builder().loader(loader).impl(record.getFullName()).buildChecked();
        if (IndexedRecord.class.isAssignableFrom(recordClass)) {
          return ValueReaders.record(fields, (Class<? extends IndexedRecord>) recordClass, record);
        }

        return ValueReaders.record(fields, record);

      } catch (ClassNotFoundException e) {
        return ValueReaders.record(fields, record);
      }
    }

    /**
     * 为 union 构建 reader（Iceberg 仅允许 nullable union）。
     *
     * @param union union schema
     * @param options 各分支 reader
     * @return union reader
     */
    @Override
    public ValueReader<?> union(Schema union, List<ValueReader<?>> options) {
      return ValueReaders.union(options);
    }

    /**
     * 为 array 构建 reader；若携带 {@link LogicalMap} 标记则按 map-as-array 读取。
     *
     * <p>逻辑步骤：识别 LogicalMap 后取 struct 的第 0/1 字段作为 key/value reader； 若 key 是 utf8 则转为 string reader。
     *
     * @param array array schema
     * @param elementReader 元素 reader
     * @return array 或 arrayMap reader
     */
    @Override
    public ValueReader<?> array(Schema array, ValueReader<?> elementReader) {
      if (array.getLogicalType() instanceof LogicalMap) {
        ValueReaders.StructReader<?> keyValueReader = (ValueReaders.StructReader) elementReader;
        ValueReader<?> keyReader = keyValueReader.reader(0);
        ValueReader<?> valueReader = keyValueReader.reader(1);

        if (keyReader == ValueReaders.utf8s()) {
          return ValueReaders.arrayMap(ValueReaders.strings(), valueReader);
        }

        return ValueReaders.arrayMap(keyReader, valueReader);
      }

      return ValueReaders.array(elementReader);
    }

    /**
     * 为 map 构建 reader，键固定为字符串。
     *
     * @param map map schema
     * @param valueReader 值 reader
     * @return map reader
     */
    @Override
    public ValueReader<?> map(Schema map, ValueReader<?> valueReader) {
      return ValueReaders.map(ValueReaders.strings(), valueReader);
    }

    /**
     * 为基本类型构建 reader。
     *
     * <p>逻辑步骤：先按逻辑类型（date/decimal/uuid 等）匹配专用 reader，必要时做单位转换 （如 timestamp-millis 乘 1000 转
     * micros）；无逻辑类型则按 Avro 基础类型匹配。
     *
     * @param primitive 基本 schema
     * @return 对应的 ValueReader
     */
    @Override
    public ValueReader<?> primitive(Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            // Spark uses the same representation
            return ValueReaders.ints();

          case "time-micros":
            return ValueReaders.longs();

          case "timestamp-millis":
            // adjust to microseconds
            ValueReader<Long> longs = ValueReaders.longs();
            return (ValueReader<Long>) (decoder, ignored) -> longs.read(decoder, null) * 1000L;

          case "timestamp-micros":
            // Spark uses the same representation
            return ValueReaders.longs();

          case "decimal":
            return ValueReaders.decimal(
                ValueReaders.decimalBytesReader(primitive),
                ((LogicalTypes.Decimal) logicalType).getScale());

          case "uuid":
            return ValueReaders.uuids();

          default:
            throw new IllegalArgumentException("Unknown logical type: " + logicalType);
        }
      }

      switch (primitive.getType()) {
        case NULL:
          return ValueReaders.nulls();
        case BOOLEAN:
          return ValueReaders.booleans();
        case INT:
          return ValueReaders.ints();
        case LONG:
          return ValueReaders.longs();
        case FLOAT:
          return ValueReaders.floats();
        case DOUBLE:
          return ValueReaders.doubles();
        case STRING:
          return ValueReaders.utf8s();
        case FIXED:
          return ValueReaders.fixed(primitive);
        case BYTES:
          return ValueReaders.byteBuffers();
        case ENUM:
          return ValueReaders.enums(primitive.getEnumSymbols());
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
