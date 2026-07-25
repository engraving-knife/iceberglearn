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
package org.apache.iceberg.spark.data.vectorized;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.orc.OrcBatchReader;
import org.apache.iceberg.orc.OrcSchemaWithTypeVisitor;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.spark.data.SparkOrcValueReaders;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.ListColumnVector;
import org.apache.orc.storage.ql.exec.vector.MapColumnVector;
import org.apache.orc.storage.ql.exec.vector.StructColumnVector;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarArray;
import org.apache.spark.sql.vectorized.ColumnarBatch;
import org.apache.spark.sql.vectorized.ColumnarMap;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark 向量化 ORC 读取器构建工厂。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，vectorized 子包负责 ORC 格式数据的向量化读取，将 ORC 的
 * ColumnVector 批量转换为 Spark ColumnarBatch）。
 *
 * <p>职责：根据 Iceberg Schema 和 ORC 文件 TypeDescription 构建 {@link OrcBatchReader}，将 ORC 的 {@link
 * VectorizedRowBatch} 转换为 Spark 的 {@link ColumnarBatch}，支持向量化批量读取，避免逐行解码的性能开销。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>通过 {@link OrcSchemaWithTypeVisitor} 遍历 Iceberg schema 与 ORC schema 的对应关系，为每个类型节点构建
 *       Converter。
 *   <li>Converter 将 ORC ColumnVector 适配为 Spark ColumnVector，支持 struct/array/map 的嵌套结构。
 *   <li>支持常量列注入（idToConstant）和元数据列（ROW_POSITION、IS_DELETED）。
 *   <li>处理 ORC 的 selected/isSelectedInUse 过滤机制和 isRepeating 优化。
 * </ul>
 *
 * <p>上下游关系：被 Iceberg Spark 数据源的 ORC 向量化读取路径调用； 依赖 Iceberg ORC 模块和 {@link SparkOrcValueReaders}。
 */
public class VectorizedSparkOrcReaders {

  private VectorizedSparkOrcReaders() {}

  /**
   * 构建向量化 ORC 批量读取器。
   *
   * <p>逻辑：通过 OrcSchemaWithTypeVisitor 遍历 Iceberg expectedSchema 与 ORC fileSchema 的类型对应关系，使用
   * ReadBuilder 构建 Converter 树。 返回的 OrcBatchReader 将每个 VectorizedRowBatch 转换为 ColumnarBatch。
   *
   * @param expectedSchema 期望读取的 Iceberg schema
   * @param fileSchema ORC 文件 schema
   * @param idToConstant 字段 ID 到常量值的映射（用于注入常量列）
   * @return 向量化批量读取器
   */
  public static OrcBatchReader<ColumnarBatch> buildReader(
      Schema expectedSchema, TypeDescription fileSchema, Map<Integer, ?> idToConstant) {
    Converter converter =
        OrcSchemaWithTypeVisitor.visit(expectedSchema, fileSchema, new ReadBuilder(idToConstant));

    return new OrcBatchReader<ColumnarBatch>() {
      private long batchOffsetInFile;
      /** 读取数据。 */
      @Override
      public ColumnarBatch read(VectorizedRowBatch batch) {
        BaseOrcColumnVector cv =
            (BaseOrcColumnVector)
                converter.convert(
                    new StructColumnVector(batch.size, batch.cols),
                    batch.size,
                    batchOffsetInFile,
                    batch.selectedInUse,
                    batch.selected);
        ColumnarBatch columnarBatch =
            new ColumnarBatch(
                IntStream.range(0, expectedSchema.columns().size())
                    .mapToObj(cv::getChild)
                    .toArray(ColumnVector[]::new));
        columnarBatch.setNumRows(batch.size);
        return columnarBatch;
      }
      /** 设置 BatchContext 属性。 */
      @Override
      public void setBatchContext(long batchOffsetInFile) {
        this.batchOffsetInFile = batchOffsetInFile;
      }
    };
  }

  private interface Converter {
    ColumnVector convert(
        org.apache.orc.storage.ql.exec.vector.ColumnVector columnVector,
        int batchSize,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected);
  }

  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<Converter> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }
    /** 执行 record 相关操作。 */
    @Override
    public Converter record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<Converter> fields) {
      return new StructConverter(iStruct, fields, idToConstant);
    }
    /** 执行 list 相关操作。 */
    @Override
    public Converter list(Types.ListType iList, TypeDescription array, Converter element) {
      return new ArrayConverter(iList, element);
    }
    /** 执行 map 相关操作。 */
    @Override
    public Converter map(Types.MapType iMap, TypeDescription map, Converter key, Converter value) {
      return new MapConverter(iMap, key, value);
    }
    /** 执行 primitive 相关操作。 */
    @Override
    public Converter primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      final OrcValueReader<?> primitiveValueReader;
      switch (primitive.getCategory()) {
        case BOOLEAN:
          primitiveValueReader = OrcValueReaders.booleans();
          break;
        case BYTE:
          // Iceberg does not have a byte type. Use int
        case SHORT:
          // Iceberg does not have a short type. Use int
        case DATE:
        case INT:
          primitiveValueReader = OrcValueReaders.ints();
          break;
        case LONG:
          primitiveValueReader = OrcValueReaders.longs();
          break;
        case FLOAT:
          primitiveValueReader = OrcValueReaders.floats();
          break;
        case DOUBLE:
          primitiveValueReader = OrcValueReaders.doubles();
          break;
        case TIMESTAMP_INSTANT:
        case TIMESTAMP:
          primitiveValueReader = SparkOrcValueReaders.timestampTzs();
          break;
        case DECIMAL:
          primitiveValueReader =
              SparkOrcValueReaders.decimals(primitive.getPrecision(), primitive.getScale());
          break;
        case CHAR:
        case VARCHAR:
        case STRING:
          primitiveValueReader = SparkOrcValueReaders.utf8String();
          break;
        case BINARY:
          primitiveValueReader =
              Type.TypeID.UUID == iPrimitive.typeId()
                  ? SparkOrcValueReaders.uuids()
                  : OrcValueReaders.bytes();
          break;
        default:
          throw new IllegalArgumentException("Unhandled type " + primitive);
      }
      return (columnVector, batchSize, batchOffsetInFile, isSelectedInUse, selected) ->
          new PrimitiveOrcColumnVector(
              iPrimitive,
              batchSize,
              columnVector,
              primitiveValueReader,
              batchOffsetInFile,
              isSelectedInUse,
              selected);
    }
  }

  private abstract static class BaseOrcColumnVector extends ColumnVector {
    private final org.apache.orc.storage.ql.exec.vector.ColumnVector vector;
    private final int batchSize;
    private final boolean isSelectedInUse;
    private final int[] selected;
    private Integer numNulls;

    BaseOrcColumnVector(
        Type type,
        int batchSize,
        org.apache.orc.storage.ql.exec.vector.ColumnVector vector,
        boolean isSelectedInUse,
        int[] selected) {
      super(SparkSchemaUtil.convert(type));
      this.vector = vector;
      this.batchSize = batchSize;
      this.isSelectedInUse = isSelectedInUse;
      this.selected = selected;
    }
    /** 关闭资源。 */
    @Override
    public void close() {}
    /** 判断是否存在 Null。 */
    @Override
    public boolean hasNull() {
      return !vector.noNulls;
    }
    /** 执行 numNulls 相关操作。 */
    @Override
    public int numNulls() {
      if (numNulls == null) {
        numNulls = numNullsHelper();
      }
      return numNulls;
    }
    /** 执行 numNullsHelper 相关操作。 */
    private int numNullsHelper() {
      if (vector.isRepeating) {
        if (vector.isNull[0]) {
          return batchSize;
        } else {
          return 0;
        }
      } else if (vector.noNulls) {
        return 0;
      } else {
        int count = 0;
        for (int i = 0; i < batchSize; i++) {
          if (vector.isNull[i]) {
            count++;
          }
        }
        return count;
      }
    }
    /** 返回 RowIndex 属性。 */
    protected int getRowIndex(int rowId) {
      int row = isSelectedInUse ? selected[rowId] : rowId;
      return vector.isRepeating ? 0 : row;
    }
    /** 判断是否 NullAt。 */
    @Override
    public boolean isNullAt(int rowId) {
      return vector.isNull[getRowIndex(rowId)];
    }
    /** 返回 Boolean 属性。 */
    @Override
    public boolean getBoolean(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Byte 属性。 */
    @Override
    public byte getByte(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Short 属性。 */
    @Override
    public short getShort(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Int 属性。 */
    @Override
    public int getInt(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Long 属性。 */
    @Override
    public long getLong(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Float 属性。 */
    @Override
    public float getFloat(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Double 属性。 */
    @Override
    public double getDouble(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Decimal 属性。 */
    @Override
    public Decimal getDecimal(int rowId, int precision, int scale) {
      throw new UnsupportedOperationException();
    }
    /** 返回 UTF8String 属性。 */
    @Override
    public UTF8String getUTF8String(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Binary 属性。 */
    @Override
    public byte[] getBinary(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Array 属性。 */
    @Override
    public ColumnarArray getArray(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Map 属性。 */
    @Override
    public ColumnarMap getMap(int rowId) {
      throw new UnsupportedOperationException();
    }
    /** 返回 Child 属性。 */
    @Override
    public ColumnVector getChild(int ordinal) {
      throw new UnsupportedOperationException();
    }
  }

  private static class PrimitiveOrcColumnVector extends BaseOrcColumnVector {
    private final org.apache.orc.storage.ql.exec.vector.ColumnVector vector;
    private final OrcValueReader<?> primitiveValueReader;
    private final long batchOffsetInFile;

    PrimitiveOrcColumnVector(
        Type type,
        int batchSize,
        org.apache.orc.storage.ql.exec.vector.ColumnVector vector,
        OrcValueReader<?> primitiveValueReader,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected) {
      super(type, batchSize, vector, isSelectedInUse, selected);
      this.vector = vector;
      this.primitiveValueReader = primitiveValueReader;
      this.batchOffsetInFile = batchOffsetInFile;
    }
    /** 返回 Boolean 属性。 */
    @Override
    public boolean getBoolean(int rowId) {
      return (Boolean) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Int 属性。 */
    @Override
    public int getInt(int rowId) {
      return (Integer) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Long 属性。 */
    @Override
    public long getLong(int rowId) {
      return (Long) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Float 属性。 */
    @Override
    public float getFloat(int rowId) {
      return (Float) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Double 属性。 */
    @Override
    public double getDouble(int rowId) {
      return (Double) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Decimal 属性。 */
    @Override
    public Decimal getDecimal(int rowId, int precision, int scale) {
      // TODO: Is it okay to assume that (precision,scale) parameters == (precision,scale) of the
      // decimal type
      // and return a Decimal with (precision,scale) of the decimal type?
      return (Decimal) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 UTF8String 属性。 */
    @Override
    public UTF8String getUTF8String(int rowId) {
      return (UTF8String) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
    /** 返回 Binary 属性。 */
    @Override
    public byte[] getBinary(int rowId) {
      return (byte[]) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
  }

  private static class ArrayConverter implements Converter {
    private final Types.ListType listType;
    private final Converter elementConverter;

    private ArrayConverter(Types.ListType listType, Converter elementConverter) {
      this.listType = listType;
      this.elementConverter = elementConverter;
    }
    /** 执行类型/值转换。 */
    @Override
    public ColumnVector convert(
        org.apache.orc.storage.ql.exec.vector.ColumnVector vector,
        int batchSize,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected) {
      ListColumnVector listVector = (ListColumnVector) vector;
      ColumnVector elementVector =
          elementConverter.convert(listVector.child, batchSize, batchOffsetInFile, false, null);

      return new BaseOrcColumnVector(listType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回 Array 属性。 */
        @Override
        public ColumnarArray getArray(int rowId) {
          int index = getRowIndex(rowId);
          return new ColumnarArray(
              elementVector, (int) listVector.offsets[index], (int) listVector.lengths[index]);
        }
      };
    }
  }

  private static class MapConverter implements Converter {
    private final Types.MapType mapType;
    private final Converter keyConverter;
    private final Converter valueConverter;

    private MapConverter(Types.MapType mapType, Converter keyConverter, Converter valueConverter) {
      this.mapType = mapType;
      this.keyConverter = keyConverter;
      this.valueConverter = valueConverter;
    }
    /** 执行类型/值转换。 */
    @Override
    public ColumnVector convert(
        org.apache.orc.storage.ql.exec.vector.ColumnVector vector,
        int batchSize,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected) {
      MapColumnVector mapVector = (MapColumnVector) vector;
      ColumnVector keyVector =
          keyConverter.convert(mapVector.keys, batchSize, batchOffsetInFile, false, null);
      ColumnVector valueVector =
          valueConverter.convert(mapVector.values, batchSize, batchOffsetInFile, false, null);

      return new BaseOrcColumnVector(mapType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回 Map 属性。 */
        @Override
        public ColumnarMap getMap(int rowId) {
          int index = getRowIndex(rowId);
          return new ColumnarMap(
              keyVector,
              valueVector,
              (int) mapVector.offsets[index],
              (int) mapVector.lengths[index]);
        }
      };
    }
  }

  private static class StructConverter implements Converter {
    private final Types.StructType structType;
    private final List<Converter> fieldConverters;
    private final Map<Integer, ?> idToConstant;

    private StructConverter(
        Types.StructType structType,
        List<Converter> fieldConverters,
        Map<Integer, ?> idToConstant) {
      this.structType = structType;
      this.fieldConverters = fieldConverters;
      this.idToConstant = idToConstant;
    }
    /** 执行类型/值转换。 */
    @Override
    public ColumnVector convert(
        org.apache.orc.storage.ql.exec.vector.ColumnVector vector,
        int batchSize,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected) {
      StructColumnVector structVector = (StructColumnVector) vector;
      List<Types.NestedField> fields = structType.fields();
      List<ColumnVector> fieldVectors = Lists.newArrayListWithExpectedSize(fields.size());
      for (int pos = 0, vectorIndex = 0; pos < fields.size(); pos += 1) {
        Types.NestedField field = fields.get(pos);
        if (idToConstant.containsKey(field.fieldId())) {
          fieldVectors.add(
              new ConstantColumnVector(field.type(), batchSize, idToConstant.get(field.fieldId())));
        } else if (field.equals(MetadataColumns.ROW_POSITION)) {
          fieldVectors.add(new RowPositionColumnVector(batchOffsetInFile));
        } else if (field.equals(MetadataColumns.IS_DELETED)) {
          fieldVectors.add(new ConstantColumnVector(field.type(), batchSize, false));
        } else {
          fieldVectors.add(
              fieldConverters
                  .get(vectorIndex)
                  .convert(
                      structVector.fields[vectorIndex],
                      batchSize,
                      batchOffsetInFile,
                      isSelectedInUse,
                      selected));
          vectorIndex++;
        }
      }

      return new BaseOrcColumnVector(structType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回 Child 属性。 */
        @Override
        public ColumnVector getChild(int ordinal) {
          return fieldVectors.get(ordinal);
        }
      };
    }
  }
}
