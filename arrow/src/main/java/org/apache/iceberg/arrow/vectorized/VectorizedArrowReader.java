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
package org.apache.iceberg.arrow.vectorized;

import java.util.Map;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.BitVectorHelper;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.FixedSizeBinaryVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.arrow.ArrowAllocation;
import org.apache.iceberg.arrow.ArrowSchemaUtil;
import org.apache.iceberg.arrow.vectorized.parquet.VectorizedColumnIterator;
import org.apache.iceberg.parquet.ParquetUtil;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Types;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.parquet.schema.OriginalType;
import org.apache.parquet.schema.PrimitiveType;

/**
 * 文件级说明：将一列 Parquet 数据批量读入 Arrow 向量的向量化读取器。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路的单列读取核心）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>委托 {@link VectorizedColumnIterator} 逐页批量解码，按 Iceberg/Parquet 类型 分配对应 Arrow 向量并填充值。
 *   <li>管理向量分配、字典编码判定、空值持有与行组信息设置。
 *   <li>提供 Null/Position/Constant/Deleted 等元数据列的专用子类读取器。
 * </ul>
 *
 * <p>设计意图：按 ReadType 枚举分发到对应 BatchReader，避免运行期反射；当 Parquet 回退 到普通编码时，即使存在字典也会提前解码，故向量是否字典编码以
 * producesDictionaryEncodedVector 为准。内部子类以空对象方式表示不读文件的元数据列，统一读取接口。
 *
 * <p>上下游关系：实现 {@link VectorizedReader}，被 {@link BaseBatchReader}/{@link ArrowBatchReader} 按列持有；下游委托
 * {@link VectorizedColumnIterator} 与 {@link ArrowSchemaUtil}。
 */
public class VectorizedArrowReader implements VectorizedReader<VectorHolder> {
  public static final int DEFAULT_BATCH_SIZE = 5000;
  private static final Integer UNKNOWN_WIDTH = null;
  private static final int AVERAGE_VARIABLE_WIDTH_RECORD_SIZE = 10;

  private final ColumnDescriptor columnDescriptor;
  private final VectorizedColumnIterator vectorizedColumnIterator;
  private final Types.NestedField icebergField;
  private final BufferAllocator rootAlloc;

  private int batchSize;
  private FieldVector vec;
  private Integer typeWidth;
  private ReadType readType;
  private NullabilityHolder nullabilityHolder;

  // In cases when Parquet employs fall back to plain encoding, we eagerly decode the dictionary
  // encoded pages
  // before storing the values in the Arrow vector. This means even if the dictionary is present,
  // data
  // present in the vector may not necessarily be dictionary encoded.
  private Dictionary dictionary;

  /**
   * 构造列读取器。
   *
   * @param desc Parquet 列描述符
   * @param icebergField Iceberg 字段定义
   * @param ra Arrow 内存分配器
   * @param setArrowValidityVector 是否设置 Arrow 有效性向量
   */
  public VectorizedArrowReader(
      ColumnDescriptor desc,
      Types.NestedField icebergField,
      BufferAllocator ra,
      boolean setArrowValidityVector) {
    this.icebergField = icebergField;
    this.columnDescriptor = desc;
    this.rootAlloc = ra;
    this.vectorizedColumnIterator = new VectorizedColumnIterator(desc, "", setArrowValidityVector);
  }

  /** dummy 读取器的构造方法。 */
  private VectorizedArrowReader() {
    this(null);
  }

  /** 元数据列读取器的构造方法（不实际读文件）。 */
  private VectorizedArrowReader(Types.NestedField icebergField) {
    this.icebergField = icebergField;
    this.batchSize = DEFAULT_BATCH_SIZE;
    this.columnDescriptor = null;
    this.rootAlloc = null;
    this.vectorizedColumnIterator = null;
  }

  private enum ReadType {
    FIXED_LENGTH_DECIMAL,
    INT_BACKED_DECIMAL,
    LONG_BACKED_DECIMAL,
    VARCHAR,
    VARBINARY,
    FIXED_WIDTH_BINARY,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    TIMESTAMP_MILLIS,
    TIMESTAMP_INT96,
    TIME_MICROS,
    UUID,
    DICTIONARY
  }

  /** 返回 Iceberg 字段定义。 */
  protected Types.NestedField icebergField() {
    return icebergField;
  }

  @Override
  /**
   * 设置批大小，0 时使用默认值，并转发给列迭代器。
   *
   * @param batchSize 每批最大行数
   */
  public void setBatchSize(int batchSize) {
    this.batchSize = (batchSize == 0) ? DEFAULT_BATCH_SIZE : batchSize;
    this.vectorizedColumnIterator.setBatchSize(batchSize);
  }

  @Override
  /**
   * 读取一批值并返回向量持有者。
   *
   * <p>逻辑：判定是否字典编码；需新建向量时（reuse 为空或编码/读取类型不匹配）分配向量 并新建空值持有者，否则复用向量并重置；按字典编码或 ReadType 选择对应
   * BatchReader 写入；最后校验读取行数与预期一致，返回 VectorHolder。
   *
   * @param reuse 可复用的向量持有者
   * @param numValsToRead 待读行数
   * @return 含本批数据的向量持有者
   */
  public VectorHolder read(VectorHolder reuse, int numValsToRead) {
    boolean dictEncoded = vectorizedColumnIterator.producesDictionaryEncodedVector();
    if (reuse == null
        || (!dictEncoded && readType == ReadType.DICTIONARY)
        || (dictEncoded && readType != ReadType.DICTIONARY)) {
      allocateFieldVector(dictEncoded);
      nullabilityHolder = new NullabilityHolder(batchSize);
    } else {
      vec.setValueCount(0);
      nullabilityHolder.reset();
    }
    if (vectorizedColumnIterator.hasNext()) {
      if (dictEncoded) {
        vectorizedColumnIterator.dictionaryBatchReader().nextBatch(vec, -1, nullabilityHolder);
      } else {
        switch (readType) {
          case VARBINARY:
          case VARCHAR:
            vectorizedColumnIterator
                .varWidthTypeBatchReader()
                .nextBatch(vec, -1, nullabilityHolder);
            break;
          case BOOLEAN:
            vectorizedColumnIterator.booleanBatchReader().nextBatch(vec, -1, nullabilityHolder);
            break;
          case INT:
          case INT_BACKED_DECIMAL:
            vectorizedColumnIterator
                .integerBatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case LONG:
          case LONG_BACKED_DECIMAL:
            vectorizedColumnIterator.longBatchReader().nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case FLOAT:
            vectorizedColumnIterator
                .floatBatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case DOUBLE:
            vectorizedColumnIterator
                .doubleBatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case TIMESTAMP_MILLIS:
            vectorizedColumnIterator
                .timestampMillisBatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case TIMESTAMP_INT96:
            vectorizedColumnIterator
                .timestampInt96BatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
          case UUID:
          case FIXED_WIDTH_BINARY:
          case FIXED_LENGTH_DECIMAL:
            vectorizedColumnIterator
                .fixedSizeBinaryBatchReader()
                .nextBatch(vec, typeWidth, nullabilityHolder);
            break;
        }
      }
    }
    Preconditions.checkState(
        vec.getValueCount() == numValsToRead,
        "Number of values read, %s, does not equal expected, %s",
        vec.getValueCount(),
        numValsToRead);
    return new VectorHolder(
        columnDescriptor, vec, dictEncoded, dictionary, nullabilityHolder, icebergField);
  }

  /**
   * 按是否字典编码分配 Arrow 向量。
   *
   * <p>逻辑：字典编码则分配 IntVector；否则转换为物理类型，按 Parquet 原始类型或类型名 分发到具体分配方法。
   *
   * @param dictionaryEncodedVector 是否分配字典编码向量
   */
  private void allocateFieldVector(boolean dictionaryEncodedVector) {
    if (dictionaryEncodedVector) {
      allocateDictEncodedVector();
    } else {
      Field arrowField = ArrowSchemaUtil.convert(getPhysicalType(columnDescriptor, icebergField));
      if (columnDescriptor.getPrimitiveType().getOriginalType() != null) {
        allocateVectorBasedOnOriginalType(columnDescriptor.getPrimitiveType(), arrowField);
      } else {
        allocateVectorBasedOnTypeName(columnDescriptor.getPrimitiveType(), arrowField);
      }
    }
  }

  /**
   * 根据 Parquet 底层类型返回用于分配 Arrow 向量的物理字段类型。
   *
   * <p>逻辑：Decimal 类型按底层 INT64/INT32/二进制分别映射为 Long/Integer/Fixed 类型， 其余保持原逻辑类型。
   *
   * @param desc Parquet 列描述符
   * @param logicalType Iceberg 逻辑字段
   * @return 物理字段类型
   */
  private static Types.NestedField getPhysicalType(
      ColumnDescriptor desc, Types.NestedField logicalType) {
    PrimitiveType primitive = desc.getPrimitiveType();
    PrimitiveType.PrimitiveTypeName typeName = primitive.getPrimitiveTypeName();
    Types.NestedField physicalType = logicalType;
    if (OriginalType.DECIMAL.equals(primitive.getOriginalType())) {
      org.apache.iceberg.types.Type type;
      if (PrimitiveType.PrimitiveTypeName.INT64.equals(typeName)) {
        // Use BigIntVector for long backed decimal
        type = Types.LongType.get();
      } else if (PrimitiveType.PrimitiveTypeName.INT32.equals(typeName)) {
        // Use IntVector for int backed decimal
        type = Types.IntegerType.get();
      } else {
        // Use FixedSizeBinaryVector for binary backed decimal
        type = Types.FixedType.ofLength(primitive.getTypeLength());
      }
      physicalType =
          Types.NestedField.of(
              logicalType.fieldId(), logicalType.isOptional(), logicalType.name(), type);
    }

    return physicalType;
  }

  /** 分配存储字典 id 的 IntVector，并设置 typeWidth 与 ReadType.DICTIONARY。 */
  private void allocateDictEncodedVector() {
    Field field =
        new Field(
            icebergField.name(),
            new FieldType(
                icebergField.isOptional(), new ArrowType.Int(Integer.SIZE, true), null, null),
            null);
    this.vec = field.createVector(rootAlloc);
    ((IntVector) vec).allocateNew(batchSize);
    this.typeWidth = (int) IntVector.TYPE_WIDTH;
    this.readType = ReadType.DICTIONARY;
  }

  /**
   * 按 Parquet 原始类型（逻辑类型）分配 Arrow 向量并设置 ReadType/typeWidth。
   *
   * @param primitive Parquet 原始类型
   * @param arrowField Arrow 字段定义
   */
  private void allocateVectorBasedOnOriginalType(PrimitiveType primitive, Field arrowField) {
    switch (primitive.getOriginalType()) {
      case ENUM:
      case JSON:
      case UTF8:
      case BSON:
        this.vec = arrowField.createVector(rootAlloc);
        // TODO: Possibly use the uncompressed page size info to set the initial capacity
        vec.setInitialCapacity(batchSize * AVERAGE_VARIABLE_WIDTH_RECORD_SIZE);
        vec.allocateNewSafe();
        this.readType = ReadType.VARCHAR;
        this.typeWidth = UNKNOWN_WIDTH;
        break;
      case INT_8:
      case INT_16:
      case INT_32:
        this.vec = arrowField.createVector(rootAlloc);
        ((IntVector) vec).allocateNew(batchSize);
        this.readType = ReadType.INT;
        this.typeWidth = (int) IntVector.TYPE_WIDTH;
        break;
      case DATE:
        this.vec = arrowField.createVector(rootAlloc);
        ((DateDayVector) vec).allocateNew(batchSize);
        this.readType = ReadType.INT;
        this.typeWidth = (int) IntVector.TYPE_WIDTH;
        break;
      case INT_64:
        this.vec = arrowField.createVector(rootAlloc);
        ((BigIntVector) vec).allocateNew(batchSize);
        this.readType = ReadType.LONG;
        this.typeWidth = (int) BigIntVector.TYPE_WIDTH;
        break;
      case TIMESTAMP_MILLIS:
        this.vec = arrowField.createVector(rootAlloc);
        ((BigIntVector) vec).allocateNew(batchSize);
        this.readType = ReadType.TIMESTAMP_MILLIS;
        this.typeWidth = (int) BigIntVector.TYPE_WIDTH;
        break;
      case TIMESTAMP_MICROS:
        this.vec = arrowField.createVector(rootAlloc);
        if (((Types.TimestampType) icebergField.type()).shouldAdjustToUTC()) {
          ((TimeStampMicroTZVector) vec).allocateNew(batchSize);
        } else {
          ((TimeStampMicroVector) vec).allocateNew(batchSize);
        }
        this.readType = ReadType.LONG;
        this.typeWidth = (int) BigIntVector.TYPE_WIDTH;
        break;
      case TIME_MICROS:
        this.vec = arrowField.createVector(rootAlloc);
        ((TimeMicroVector) vec).allocateNew(batchSize);
        this.readType = ReadType.LONG;
        this.typeWidth = (int) TimeMicroVector.TYPE_WIDTH;
        break;
      case DECIMAL:
        this.vec = arrowField.createVector(rootAlloc);
        switch (primitive.getPrimitiveTypeName()) {
          case BINARY:
          case FIXED_LEN_BYTE_ARRAY:
            ((FixedSizeBinaryVector) vec).allocateNew(batchSize);
            this.readType = ReadType.FIXED_LENGTH_DECIMAL;
            this.typeWidth = primitive.getTypeLength();
            break;
          case INT64:
            ((BigIntVector) vec).allocateNew(batchSize);
            this.readType = ReadType.LONG_BACKED_DECIMAL;
            this.typeWidth = (int) BigIntVector.TYPE_WIDTH;
            break;
          case INT32:
            ((IntVector) vec).allocateNew(batchSize);
            this.readType = ReadType.INT_BACKED_DECIMAL;
            this.typeWidth = (int) IntVector.TYPE_WIDTH;
            break;
          default:
            throw new UnsupportedOperationException(
                "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
        }
        break;
      default:
        throw new UnsupportedOperationException(
            "Unsupported logical type: " + primitive.getOriginalType());
    }
  }

  /**
   * 按 Parquet 底层类型名分配 Arrow 向量并设置 ReadType/typeWidth。
   *
   * @param primitive Parquet 原始类型
   * @param arrowField Arrow 字段定义
   */
  private void allocateVectorBasedOnTypeName(PrimitiveType primitive, Field arrowField) {
    switch (primitive.getPrimitiveTypeName()) {
      case FIXED_LEN_BYTE_ARRAY:
        int len;
        if (icebergField.type() instanceof Types.UUIDType) {
          len = 16;
          this.readType = ReadType.UUID;
        } else {
          len = ((Types.FixedType) icebergField.type()).length();
          this.readType = ReadType.FIXED_WIDTH_BINARY;
        }
        this.vec = arrowField.createVector(rootAlloc);
        vec.setInitialCapacity(batchSize * len);
        vec.allocateNew();
        this.typeWidth = len;
        break;
      case BINARY:
        this.vec = arrowField.createVector(rootAlloc);
        // TODO: Possibly use the uncompressed page size info to set the initial capacity
        vec.setInitialCapacity(batchSize * AVERAGE_VARIABLE_WIDTH_RECORD_SIZE);
        vec.allocateNewSafe();
        this.readType = ReadType.VARBINARY;
        this.typeWidth = UNKNOWN_WIDTH;
        break;
      case INT32:
        Field intField =
            new Field(
                icebergField.name(),
                new FieldType(
                    icebergField.isOptional(), new ArrowType.Int(Integer.SIZE, true), null, null),
                null);
        this.vec = intField.createVector(rootAlloc);
        ((IntVector) vec).allocateNew(batchSize);
        this.readType = ReadType.INT;
        this.typeWidth = (int) IntVector.TYPE_WIDTH;
        break;
      case INT96:
        // Impala & Spark used to write timestamps as INT96 by default. For backwards
        // compatibility we try to read INT96 as timestamps. But INT96 is not recommended
        // and deprecated (see https://issues.apache.org/jira/browse/PARQUET-323)
        int length = BigIntVector.TYPE_WIDTH;
        this.readType = ReadType.TIMESTAMP_INT96;
        this.vec = arrowField.createVector(rootAlloc);
        vec.setInitialCapacity(batchSize * length);
        vec.allocateNew();
        this.typeWidth = length;
        break;
      case FLOAT:
        Field floatField =
            new Field(
                icebergField.name(),
                new FieldType(
                    icebergField.isOptional(),
                    new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE),
                    null,
                    null),
                null);
        this.vec = floatField.createVector(rootAlloc);
        ((Float4Vector) vec).allocateNew(batchSize);
        this.readType = ReadType.FLOAT;
        this.typeWidth = (int) Float4Vector.TYPE_WIDTH;
        break;
      case BOOLEAN:
        this.vec = arrowField.createVector(rootAlloc);
        ((BitVector) vec).allocateNew(batchSize);
        this.readType = ReadType.BOOLEAN;
        this.typeWidth = UNKNOWN_WIDTH;
        break;
      case INT64:
        this.vec = arrowField.createVector(rootAlloc);
        ((BigIntVector) vec).allocateNew(batchSize);
        this.readType = ReadType.LONG;
        this.typeWidth = (int) BigIntVector.TYPE_WIDTH;
        break;
      case DOUBLE:
        this.vec = arrowField.createVector(rootAlloc);
        ((Float8Vector) vec).allocateNew(batchSize);
        this.readType = ReadType.DOUBLE;
        this.typeWidth = (int) Float8Vector.TYPE_WIDTH;
        break;
      default:
        throw new UnsupportedOperationException("Unsupported type: " + primitive);
    }
  }

  @Override
  /**
   * 设置当前行组的页存储与元数据，并转发给列迭代器。
   *
   * @param source 页读取存储
   * @param metadata 列块元数据映射
   * @param rowPosition 当前行组起始行位置
   */
  public void setRowGroupInfo(
      PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {
    ColumnChunkMetaData chunkMetaData = metadata.get(ColumnPath.get(columnDescriptor.getPath()));
    this.dictionary =
        vectorizedColumnIterator.setRowGroupInfo(
            source.getPageReader(columnDescriptor),
            !ParquetUtil.hasNonDictionaryPages(chunkMetaData));
  }

  @Override
  /** 关闭列读取器，释放列迭代器与向量资源。 */
  public void close() {
    if (vec != null) {
      vec.close();
    }
  }

  @Override
  /** 返回读取器的字符串描述。 */
  public String toString() {
    return columnDescriptor.toString();
  }

  /** 创建不读文件的 null 占位读取器。 */
  public static VectorizedArrowReader nulls() {
    return NullVectorReader.INSTANCE;
  }

  /** 创建行位置列读取器（不设置有效性向量）。 */
  public static VectorizedArrowReader positions() {
    return new PositionVectorReader(false);
  }

  /** 创建行位置列读取器（设置有效性向量）。 */
  public static VectorizedArrowReader positionsWithSetArrowValidityVector() {
    return new PositionVectorReader(true);
  }

  /** null 占位读取器单例，read 返回 dummy 持有者，无实际读取。 */
  private static final class NullVectorReader extends VectorizedArrowReader {
    private static final NullVectorReader INSTANCE = new NullVectorReader();

    @Override
    /** 返回 dummy 占位持有者。 */
    public VectorHolder read(VectorHolder reuse, int numValsToRead) {
      return VectorHolder.dummyHolder(numValsToRead);
    }

    @Override
    /** 空实现：无行组信息需设置。 */
    public void setRowGroupInfo(
        PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {}

    @Override
    /** 返回 "NullReader"。 */
    public String toString() {
      return "NullReader";
    }

    @Override
    /** 空实现。 */
    public void setBatchSize(int batchSize) {}
  }

  /** 行位置列读取器：按行号生成连续 long 值写入 BigIntVector。 */
  private static final class PositionVectorReader extends VectorizedArrowReader {
    private static final Field ROW_POSITION_ARROW_FIELD =
        ArrowSchemaUtil.convert(MetadataColumns.ROW_POSITION);
    private final boolean setArrowValidityVector;
    private long rowStart;
    private int batchSize;
    private NullabilityHolder nulls;

    PositionVectorReader(boolean setArrowValidityVector) {
      super(MetadataColumns.ROW_POSITION);
      this.setArrowValidityVector = setArrowValidityVector;
    }

    @Override
    /**
     * 生成一批行位置值写入向量。
     *
     * <p>逻辑：复用或新建向量；将 rowStart 起的连续行号写入数据缓冲；按需设置有效性位； 推进 rowStart 并返回 PositionVectorHolder。
     *
     * @param reuse 可复用持有者
     * @param numValsToRead 待读行数
     * @return 行位置向量持有者
     */
    public VectorHolder read(VectorHolder reuse, int numValsToRead) {
      FieldVector vec;
      if (reuse == null) {
        vec = newVector(batchSize);
      } else {
        vec = reuse.vector();
        vec.setValueCount(0);
      }

      ArrowBuf dataBuffer = vec.getDataBuffer();
      for (int i = 0; i < numValsToRead; i += 1) {
        dataBuffer.setLong((long) i * Long.BYTES, rowStart + i);
      }

      if (setArrowValidityVector) {
        ArrowBuf validityBuffer = vec.getValidityBuffer();
        for (int i = 0; i < numValsToRead; i += 1) {
          BitVectorHelper.setBit(validityBuffer, i);
        }
      }

      rowStart += numValsToRead;
      vec.setValueCount(numValsToRead);

      return new VectorHolder.PositionVectorHolder(vec, MetadataColumns.ROW_POSITION, nulls);
    }

    /** 分配指定容量的 BigIntVector。 */
    private static BigIntVector newVector(int valueCount) {
      BigIntVector vector =
          (BigIntVector) ROW_POSITION_ARROW_FIELD.createVector(ArrowAllocation.rootAllocator());
      vector.allocateNew(valueCount);
      return vector;
    }

    /** 创建全部非空的空值持有者。 */
    private static NullabilityHolder newNullabilityHolder(int size) {
      NullabilityHolder nullabilityHolder = new NullabilityHolder(size);
      nullabilityHolder.setNotNulls(0, size);
      return nullabilityHolder;
    }

    @Override
    /** 记录当前行组起始行位置。 */
    public void setRowGroupInfo(
        PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {
      this.rowStart = rowPosition;
    }

    @Override
    /** 返回类名字符串。 */
    public String toString() {
      return getClass().toString();
    }

    @Override
    /**
     * 设置批大小，并按需重建空值持有者。
     *
     * @param batchSize 每批最大行数
     */
    public void setBatchSize(int batchSize) {
      if (nulls == null || nulls.size() < batchSize) {
        this.nulls = newNullabilityHolder(batchSize);
      }
      this.batchSize = (batchSize == 0) ? DEFAULT_BATCH_SIZE : batchSize;
    }

    @Override
    /** 空实现：向量不归读取器所有，不关闭。 */
    public void close() {
      // don't close vectors as they are not owned by readers
    }
  }

  /**
   * 常量列读取器：不实际读文件，返回指示常量值的 dummy 持有者。
   *
   * @param <T> 常量值类型
   */
  public static class ConstantVectorReader<T> extends VectorizedArrowReader {
    private final T value;

    /** @deprecated since 1.4.0, will be removed in 1.5.0; use typed constant readers. */
    @Deprecated
    /**
     * 已废弃：构造无类型常量读取器。
     *
     * @deprecated since 1.4.0，将在 1.5.0 移除，请使用类型化构造方法
     * @param value 常量值
     */
    public ConstantVectorReader(T value) {
      this.value = value;
    }

    /**
     * 构造类型化常量读取器。
     *
     * @param icebergField Iceberg 字段定义
     * @param value 常量值
     */
    public ConstantVectorReader(Types.NestedField icebergField, T value) {
      super(icebergField);
      this.value = value;
    }

    @Override
    /** 返回携带常量值的常量持有者。 */
    public VectorHolder read(VectorHolder reuse, int numValsToRead) {
      return VectorHolder.constantHolder(icebergField(), numValsToRead, value);
    }

    @Override
    /** 空实现。 */
    public void setRowGroupInfo(
        PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {}

    @Override
    /** 返回常量读取器描述。 */
    public String toString() {
      return String.format("ConstantReader: %s", value);
    }

    @Override
    /** 空实现。 */
    public void setBatchSize(int batchSize) {}
  }

  /** 删除标记列读取器：不实际读文件，返回指示行是否删除的持有者。 */
  public static class DeletedVectorReader extends VectorizedArrowReader {
    /** 构造删除标记列读取器，绑定 IS_DELETED 元数据列。 */
    public DeletedVectorReader() {
      super(MetadataColumns.IS_DELETED);
    }

    @Override
    /** 返回删除标记向量持有者。 */
    public VectorHolder read(VectorHolder reuse, int numValsToRead) {
      return VectorHolder.deletedVectorHolder(numValsToRead);
    }

    @Override
    /** 空实现。 */
    public void setRowGroupInfo(
        PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {}

    @Override
    /** 返回 "DeletedVectorReader"。 */
    public String toString() {
      return "DeletedVectorReader";
    }

    @Override
    /** 空实现。 */
    public void setBatchSize(int batchSize) {}
  }
}
