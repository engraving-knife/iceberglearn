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
 * Spark 向量化读取 Iceberg 数据的列式访问组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 VectorizedSparkOrcReaders。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class VectorizedSparkOrcReaders {

  /** 构造 VectorizedSparkOrcReaders 实例。 */
  private VectorizedSparkOrcReaders() {}

  /** 构造并返回目标对象。 */
  public static OrcBatchReader<ColumnarBatch> buildReader(
      Schema expectedSchema, TypeDescription fileSchema, Map<Integer, ?> idToConstant) {
    Converter converter =
        OrcSchemaWithTypeVisitor.visit(expectedSchema, fileSchema, new ReadBuilder(idToConstant));

    return new OrcBatchReader<ColumnarBatch>() {
      private long batchOffsetInFile;

      /**
       * 读取数据。
       *
       * @param batch 参数
       * @return 结果对象
       */
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

      /** 设置batchcontext。 */
      @Override
      public void setBatchContext(long batchOffsetInFile) {
        this.batchOffsetInFile = batchOffsetInFile;
      }
    };
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，负责类型或表达式转换。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：接口 Converter。
   *
   * <p>设计意图：适配器模式，桥接两套 API。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private interface Converter {
    /** 把输入转换为另一种表示。 */
    ColumnVector convert(
        org.apache.orc.storage.ql.exec.vector.ColumnVector columnVector,
        int batchSize,
        long batchOffsetInFile,
        boolean isSelectedInUse,
        int[] selected);
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ReadBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<Converter> {
    private final Map<Integer, ?> idToConstant;

    /** 构造 ReadBuilder 实例。 */
    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iStruct 参数
     * @param record 参数
     * @param names 参数
     * @param fields 参数
     * @return 结果对象
     */
    @Override
    public Converter record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<Converter> fields) {
      /** 执行该方法的具体逻辑。 */
      return new StructConverter(iStruct, fields, idToConstant);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iList 参数
     * @param array 参数
     * @param element 参数
     * @return 结果对象
     */
    @Override
    public Converter list(Types.ListType iList, TypeDescription array, Converter element) {
      /** 执行该方法的具体逻辑。 */
      return new ArrayConverter(iList, element);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iMap 参数
     * @param map 参数
     * @param key 参数
     * @param value 参数
     * @return 结果对象
     */
    @Override
    public Converter map(Types.MapType iMap, TypeDescription map, Converter key, Converter value) {
      /** 执行该方法的具体逻辑。 */
      return new MapConverter(iMap, key, value);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iPrimitive 参数
     * @param primitive 参数
     * @return 结果对象
     */
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

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，封装列向量相关能力。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 BaseOrcColumnVector。
   *
   * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
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

    /** 释放底层资源。 */
    @Override
    public void close() {}

    /** 判断是否包含null。 */
    @Override
    public boolean hasNull() {
      return !vector.noNulls;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public int numNulls() {
      if (numNulls == null) {
        numNulls = numNullsHelper();
      }
      return numNulls;
    }

    /** 执行该方法的具体逻辑。 */
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

    /** 返回rowindex。 */
    protected int getRowIndex(int rowId) {
      int row = isSelectedInUse ? selected[rowId] : rowId;
      return vector.isRepeating ? 0 : row;
    }

    /** 判断是否nullat。 */
    @Override
    public boolean isNullAt(int rowId) {
      return vector.isNull[getRowIndex(rowId)];
    }

    /** 返回boolean。 */
    @Override
    public boolean getBoolean(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回byte。 */
    @Override
    public byte getByte(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回short。 */
    @Override
    public short getShort(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回int。 */
    @Override
    public int getInt(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回long。 */
    @Override
    public long getLong(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回float。 */
    @Override
    public float getFloat(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回double。 */
    @Override
    public double getDouble(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回decimal。 */
    @Override
    public Decimal getDecimal(int rowId, int precision, int scale) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回utf8string。 */
    @Override
    public UTF8String getUTF8String(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回binary。 */
    @Override
    public byte[] getBinary(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回array。 */
    @Override
    public ColumnarArray getArray(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回map。 */
    @Override
    public ColumnarMap getMap(int rowId) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }

    /** 返回child。 */
    @Override
    public ColumnVector getChild(int ordinal) {
      /** 执行该方法的具体逻辑。 */
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，封装列向量相关能力。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 PrimitiveOrcColumnVector。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
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

    /** 返回boolean。 */
    @Override
    public boolean getBoolean(int rowId) {
      return (Boolean) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回int。 */
    @Override
    public int getInt(int rowId) {
      return (Integer) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回long。 */
    @Override
    public long getLong(int rowId) {
      return (Long) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回float。 */
    @Override
    public float getFloat(int rowId) {
      return (Float) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回double。 */
    @Override
    public double getDouble(int rowId) {
      return (Double) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回decimal。 */
    @Override
    public Decimal getDecimal(int rowId, int precision, int scale) {
      // TODO: Is it okay to assume that (precision,scale) parameters == (precision,scale) of the
      // decimal type
      // and return a Decimal with (precision,scale) of the decimal type?
      return (Decimal) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回utf8string。 */
    @Override
    public UTF8String getUTF8String(int rowId) {
      return (UTF8String) primitiveValueReader.read(vector, getRowIndex(rowId));
    }

    /** 返回binary。 */
    @Override
    public byte[] getBinary(int rowId) {
      return (byte[]) primitiveValueReader.read(vector, getRowIndex(rowId));
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，负责类型或表达式转换。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ArrayConverter。
   *
   * <p>设计意图：适配器模式，桥接两套 API。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ArrayConverter implements Converter {
    private final Types.ListType listType;
    private final Converter elementConverter;

    /** 构造 ArrayConverter 实例。 */
    private ArrayConverter(Types.ListType listType, Converter elementConverter) {
      this.listType = listType;
      this.elementConverter = elementConverter;
    }

    /**
     * 把输入转换为另一种表示。
     *
     * @param vector 参数
     * @param batchSize 参数
     * @param batchOffsetInFile 参数
     * @param isSelectedInUse 参数
     * @param selected 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new BaseOrcColumnVector(listType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回array。 */
        @Override
        public ColumnarArray getArray(int rowId) {
          int index = getRowIndex(rowId);
          /** 执行该方法的具体逻辑。 */
          return new ColumnarArray(
              elementVector, (int) listVector.offsets[index], (int) listVector.lengths[index]);
        }
      };
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，负责类型或表达式转换。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 MapConverter。
   *
   * <p>设计意图：适配器模式，桥接两套 API。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class MapConverter implements Converter {
    private final Types.MapType mapType;
    private final Converter keyConverter;
    private final Converter valueConverter;

    /** 构造 MapConverter 实例。 */
    private MapConverter(Types.MapType mapType, Converter keyConverter, Converter valueConverter) {
      this.mapType = mapType;
      this.keyConverter = keyConverter;
      this.valueConverter = valueConverter;
    }

    /**
     * 把输入转换为另一种表示。
     *
     * @param vector 参数
     * @param batchSize 参数
     * @param batchOffsetInFile 参数
     * @param isSelectedInUse 参数
     * @param selected 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new BaseOrcColumnVector(mapType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回map。 */
        @Override
        public ColumnarMap getMap(int rowId) {
          int index = getRowIndex(rowId);
          /** 执行该方法的具体逻辑。 */
          return new ColumnarMap(
              keyVector,
              valueVector,
              (int) mapVector.offsets[index],
              (int) mapVector.lengths[index]);
        }
      };
    }
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，负责类型或表达式转换。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 StructConverter。
   *
   * <p>设计意图：适配器模式，桥接两套 API。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class StructConverter implements Converter {
    private final Types.StructType structType;
    private final List<Converter> fieldConverters;
    private final Map<Integer, ?> idToConstant;

    /** 构造 StructConverter 实例。 */
    private StructConverter(
        Types.StructType structType,
        List<Converter> fieldConverters,
        Map<Integer, ?> idToConstant) {
      this.structType = structType;
      this.fieldConverters = fieldConverters;
      this.idToConstant = idToConstant;
    }

    /**
     * 把输入转换为另一种表示。
     *
     * @param vector 参数
     * @param batchSize 参数
     * @param batchOffsetInFile 参数
     * @param isSelectedInUse 参数
     * @param selected 参数
     * @return 结果对象
     */
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

      /** 执行该方法的具体逻辑。 */
      return new BaseOrcColumnVector(structType, batchSize, vector, isSelectedInUse, selected) {
        /** 返回child。 */
        @Override
        public ColumnVector getChild(int ordinal) {
          return fieldVectors.get(ordinal);
        }
      };
    }
  }
}
