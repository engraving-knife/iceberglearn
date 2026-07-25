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
import org.apache.iceberg.arrow.vectorized.VectorizedArrowReader;
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
 * 向量化读取器：返回 Spark {@link ColumnarBatch} 以支持 Spark 向量化读路径。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data.vectorized 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 {@link BaseBatchReader}，委托各列 {@link VectorizedArrowReader} 读取 Arrow 向量。
 *   <li>把 Arrow 向量包装为 Spark {@link ColumnVector} 组成 {@link ColumnarBatch}。
 *   <li>支持位置删除与等值删除过滤：构造 rowIdMapping 跳过已删行，可选产出 _deleted 元数据列。
 * </ul>
 *
 * <p>设计意图：把删除过滤下沉到向量化读取层，避免逐行 Java 对象开销；通过 rowIdMapping 数组 重排存活行，等值删除在 batch 行迭代器上二次过滤；_deleted
 * 列由独立 DeletedColumnVector 提供。
 *
 * <p>上下游关系：被 {@link VectorizedSparkParquetReaders} 构造；产出 ColumnarBatch 被 Spark 向量化执行引擎消费；依赖
 * iceberg-arrow 的 BaseBatchReader 与 deletes 包。
 */
public class ColumnarBatchReader extends BaseBatchReader<ColumnarBatch> {
  private final boolean hasIsDeletedColumn;
  private DeleteFilter<InternalRow> deletes = null;
  private long rowStartPosInBatch = 0;

  /** 构造读取器，检测 readers 中是否包含 DeletedVectorReader 以决定是否产出 _deleted 列。 */
  public ColumnarBatchReader(List<VectorizedReader<?>> readers) {
    super(readers);
    this.hasIsDeletedColumn =
        readers.stream().anyMatch(reader -> reader instanceof DeletedVectorReader);
  }

  /** 设置当前 row group 信息，记录本批起始行位置（用于位置删除的全局行号判断）。 */
  @Override
  public void setRowGroupInfo(
      PageReadStore pageStore, Map<ColumnPath, ColumnChunkMetaData> metaData, long rowPosition) {
    super.setRowGroupInfo(pageStore, metaData, rowPosition);
    this.rowStartPosInBatch = rowPosition;
  }

  /** 设置删除过滤器（含位置删除索引与等值删除谓词）。 */
  public void setDeleteFilter(DeleteFilter<InternalRow> deleteFilter) {
    this.deletes = deleteFilter;
  }

  /**
   * 读取一批数据并返回 ColumnarBatch。
   *
   * <p>逻辑：reuse 为 null 时先关闭旧向量；用 ColumnBatchLoader 加载数据并应用删除过滤； 累加 rowStartPosInBatch。
   *
   * @param reuse 可复用的 batch（当前未真正复用）
   * @param numRowsToRead 待读取行数
   * @return 含存活行的 ColumnarBatch
   */
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
   * 批加载器：负责把各列向量读出并应用删除过滤构造 ColumnarBatch。
   *
   * <p>维护 rowIdMapping（存活行索引重排）与 isDeleted（_deleted 列标记）两个数组。
   */
  private class ColumnBatchLoader {
    private final int numRowsToRead;
    // the rowId mapping to skip deleted rows for all column vectors inside a batch, it is null when
    // there is no deletes
    private int[] rowIdMapping;
    // the array to indicate if a row is deleted or not, it is null when there is no "_deleted"
    // metadata column
    private boolean[] isDeleted;

    /** 构造批加载器，校验行数 > 0，若需 _deleted 列则分配 isDeleted 数组。 */
    ColumnBatchLoader(int numRowsToRead) {
      Preconditions.checkArgument(
          numRowsToRead > 0, "Invalid number of rows to read: %s", numRowsToRead);
      this.numRowsToRead = numRowsToRead;
      if (hasIsDeletedColumn) {
        isDeleted = new boolean[numRowsToRead];
      }
    }

    /**
     * 加载数据到 ColumnarBatch。
     *
     * <p>逻辑：初始化 rowIdMapping 得到存活行数；读各列向量；构造 batch 并设置行数； 若有等值删除则二次过滤；若含 _deleted 列且存在
     * rowIdMapping，重置映射以保留删除行供元数据列展示。
     */
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

    /** 逐列调用 reader.read 并通过 ColumnVectorBuilder 应用 rowIdMapping/isDeleted 构造 Spark ColumnVector。 */
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

    /** 是否配置了等值删除。 */
    boolean hasEqDeletes() {
      return deletes != null && deletes.hasEqDeletes();
    }

    /** 初始化 rowIdMapping：优先用位置删除映射，否则用等值删除初始化（全保留待二次过滤）。 */
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

    /** 若有位置删除则构造映射，否则返回 null。 */
    Pair<int[], Integer> posDelRowIdMapping() {
      if (deletes != null && deletes.hasPosDeletes()) {
        return buildPosDelRowIdMapping(deletes.deletedRowPositions());
      } else {
        return null;
      }
    }

    /**
     * 构造位置删除的 rowIdMapping，跳过被删行。
     *
     * <p>逻辑：遍历本批行，未删的写入映射并推进 currentRowId，已删的标记 isDeleted（若需要） 并累加删除计数；全部未删则返回 null 表示无需映射。
     *
     * <p>示例：8 行中删除第 2、6 行后映射为 [0,1,3,4,5,7,-,-]，行数设为 6。
     *
     * @param deletedRowPositions 位置删除索引
     * @return 映射数组与新行数，无删除时返回 null
     */
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

    /** 等值删除初始化映射为恒等映射（i->i），后续 applyEqDelete 二次过滤。 */
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

    /**
     * 应用等值删除过滤行。
     *
     * <p>逻辑：迭代 batch 行，用 {@code deletes.eqDeletedRowFilter().test(row)} 判断是否保留； 保留的压缩
     * rowIdMapping，删除的标记 isDeleted 并累加计数；最后设置新行数。
     *
     * <p>示例：在位置删除基础上再等值删除 1<=x<=3，映射变为 [0,4,5,7,-,-,-,-]，行数设为 4。
     *
     * @param columnarBatch 待过滤的 batch
     */
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
