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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.arrow.vectorized.BaseBatchReader;
import org.apache.iceberg.arrow.vectorized.VectorizedArrowReader.DeletedVectorReader;
import org.apache.iceberg.data.DeleteFilter;
import org.apache.iceberg.deletes.PositionDeleteIndex;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.Pair;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.hadoop.metadata.ColumnChunkMetaData;
import org.apache.parquet.hadoop.metadata.ColumnPath;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.vectorized.ColumnVector;
import org.apache.spark.sql.vectorized.ColumnarBatch;

/**
 * Spark 向量化读取 Iceberg 数据的列式访问组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ColumnarBatchReader。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class ColumnarBatchReader extends BaseBatchReader<ColumnarBatch> {
  private final boolean hasIsDeletedColumn;
  private DeleteFilter<InternalRow> deletes = null;
  private long rowStartPosInBatch = 0;

  /** 构造 ColumnarBatchReader 实例。 */
  public ColumnarBatchReader(List<VectorizedReader<?>> readers) {
    super(readers);
    this.hasIsDeletedColumn =
        readers.stream().anyMatch(reader -> reader instanceof DeletedVectorReader);
  }

  /** 设置rowgroupinfo。 */
  @Override
  public void setRowGroupInfo(
      PageReadStore pageStore, Map<ColumnPath, ColumnChunkMetaData> metaData, long rowPosition) {
    super.setRowGroupInfo(pageStore, metaData, rowPosition);
    this.rowStartPosInBatch = rowPosition;
  }

  /** 设置deletefilter。 */
  public void setDeleteFilter(DeleteFilter<InternalRow> deleteFilter) {
    this.deletes = deleteFilter;
  }

  /** 读取数据。 */
  @Override
  public final ColumnarBatch read(ColumnarBatch reuse, int numRowsToRead) {
    if (reuse == null) {
      closeVectors();
    }

    ColumnarBatch columnarBatch = new ColumnBatchLoader(numRowsToRead).loadDataToColumnBatch();
    rowStartPosInBatch += numRowsToRead;
    return columnarBatch;
  }

  /**
   * Spark 向量化读取 Iceberg 数据的列式访问组件，处理列式批量数据。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 ColumnBatchLoader。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private class ColumnBatchLoader {
    private final int numRowsToRead;
    // the rowId mapping to skip deleted rows for all column vectors inside a batch, it is null when
    // there is no deletes
    private int[] rowIdMapping;
    // the array to indicate if a row is deleted or not, it is null when there is no "_deleted"
    // metadata column
    private boolean[] isDeleted;

    ColumnBatchLoader(int numRowsToRead) {
      Preconditions.checkArgument(
          numRowsToRead > 0, "Invalid number of rows to read: %s", numRowsToRead);
      this.numRowsToRead = numRowsToRead;
      if (hasIsDeletedColumn) {
        isDeleted = new boolean[numRowsToRead];
      }
    }

    /** 执行该方法的具体逻辑。 */
    ColumnarBatch loadDataToColumnBatch() {
      int numRowsUndeleted = initRowIdMapping();

      ColumnVector[] arrowColumnVectors = readDataToColumnVectors();

      ColumnarBatch newColumnarBatch = new ColumnarBatch(arrowColumnVectors);
      newColumnarBatch.setNumRows(numRowsUndeleted);

      if (hasEqDeletes()) {
        applyEqDelete(newColumnarBatch);
      }

      if (hasIsDeletedColumn && rowIdMapping != null) {
        // reset the row id mapping array, so that it doesn't filter out the deleted rows
        for (int i = 0; i < numRowsToRead; i++) {
          rowIdMapping[i] = i;
        }
        newColumnarBatch.setNumRows(numRowsToRead);
      }

      return newColumnarBatch;
    }

    /** 读取数据。 */
    ColumnVector[] readDataToColumnVectors() {
      ColumnVector[] arrowColumnVectors = new ColumnVector[readers.length];

      ColumnVectorBuilder columnVectorBuilder = new ColumnVectorBuilder();
      for (int i = 0; i < readers.length; i += 1) {
        vectorHolders[i] = readers[i].read(vectorHolders[i], numRowsToRead);
        int numRowsInVector = vectorHolders[i].numValues();
        Preconditions.checkState(
            numRowsInVector == numRowsToRead,
            "Number of rows in the vector %s didn't match expected %s ",
            numRowsInVector,
            numRowsToRead);

        arrowColumnVectors[i] =
            columnVectorBuilder
                .withDeletedRows(rowIdMapping, isDeleted)
                .build(vectorHolders[i], numRowsInVector);
      }
      return arrowColumnVectors;
    }

    /** 判断是否包含eqdeletes。 */
    boolean hasEqDeletes() {
      return deletes != null && deletes.hasEqDeletes();
    }

    /** 执行初始化。 */
    int initRowIdMapping() {
      Pair<int[], Integer> posDeleteRowIdMapping = posDelRowIdMapping();
      if (posDeleteRowIdMapping != null) {
        rowIdMapping = posDeleteRowIdMapping.first();
        return posDeleteRowIdMapping.second();
      } else {
        rowIdMapping = initEqDeleteRowIdMapping();
        return numRowsToRead;
      }
    }

    /** 执行该方法的具体逻辑。 */
    Pair<int[], Integer> posDelRowIdMapping() {
      if (deletes != null && deletes.hasPosDeletes()) {
        return buildPosDelRowIdMapping(deletes.deletedRowPositions());
      } else {
        return null;
      }
    }

    /** 构造并返回目标对象。 */
    Pair<int[], Integer> buildPosDelRowIdMapping(PositionDeleteIndex deletedRowPositions) {
      if (deletedRowPositions == null) {
        return null;
      }

      int[] posDelRowIdMapping = new int[numRowsToRead];
      int originalRowId = 0;
      int currentRowId = 0;
      while (originalRowId < numRowsToRead) {
        if (!deletedRowPositions.isDeleted(originalRowId + rowStartPosInBatch)) {
          posDelRowIdMapping[currentRowId] = originalRowId;
          currentRowId++;
        } else {
          if (hasIsDeletedColumn) {
            isDeleted[originalRowId] = true;
          }

          deletes.incrementDeleteCount();
        }
        originalRowId++;
      }

      if (currentRowId == numRowsToRead) {
        // there is no delete in this batch
        return null;
      } else {
        return Pair.of(posDelRowIdMapping, currentRowId);
      }
    }

    /** 执行初始化。 */
    int[] initEqDeleteRowIdMapping() {
      int[] eqDeleteRowIdMapping = null;
      if (hasEqDeletes()) {
        eqDeleteRowIdMapping = new int[numRowsToRead];
        for (int i = 0; i < numRowsToRead; i++) {
          eqDeleteRowIdMapping[i] = i;
        }
      }

      return eqDeleteRowIdMapping;
    }

    /** 执行核心逻辑。 */
    void applyEqDelete(ColumnarBatch columnarBatch) {
      Iterator<InternalRow> it = columnarBatch.rowIterator();
      int rowId = 0;
      int currentRowId = 0;
      while (it.hasNext()) {
        InternalRow row = it.next();
        if (deletes.eqDeletedRowFilter().test(row)) {
          // the row is NOT deleted
          // skip deleted rows by pointing to the next undeleted row Id
          rowIdMapping[currentRowId] = rowIdMapping[rowId];
          currentRowId++;
        } else {
          if (hasIsDeletedColumn) {
            isDeleted[rowIdMapping[rowId]] = true;
          }

          deletes.incrementDeleteCount();
        }

        rowId++;
      }

      columnarBatch.setNumRows(currentRowId);
    }
  }
}
