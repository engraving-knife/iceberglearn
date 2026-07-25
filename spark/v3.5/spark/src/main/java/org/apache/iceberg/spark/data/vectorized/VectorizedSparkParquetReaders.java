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
import java.util.function.Function;
import org.apache.arrow.vector.NullCheckingForGet;
import org.apache.iceberg.Schema;
import org.apache.iceberg.arrow.vectorized.VectorizedReaderBuilder;
import org.apache.iceberg.data.DeleteFilter;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.parquet.schema.MessageType;
import org.apache.spark.sql.catalyst.InternalRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spark 向量化 Parquet 读取器工厂。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），data.vectorized 子包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造 Iceberg Schema + Parquet MessageType 对应的 {@link ColumnarBatchReader}。
 *   <li>在静态初始化时配置 Arrow 的 unsafe 内存访问与关闭 null 检查以提升读取性能。
 *   <li>通过自定义 {@link ReaderBuilder} 把 {@link DeleteFilter} 注入到 reader。
 * </ul>
 *
 * <p>设计意图：复用 iceberg-core 的 {@link VectorizedReaderBuilder} 与 {@link TypeWithSchemaVisitor} 构造
 * reader 树，仅覆盖 vectorizedReader 方法注入 DeleteFilter；Arrow 属性仅在用户未显式配置时 设置默认值，避免覆盖用户意图。
 *
 * <p>上下游关系：被 Spark 向量化读取路径调用构造 reader；产出 ColumnarBatchReader 被 Spark 向量化执行引擎消费。
 */
public class VectorizedSparkParquetReaders {

  private static final Logger LOG = LoggerFactory.getLogger(VectorizedSparkParquetReaders.class);
  private static final String ENABLE_UNSAFE_MEMORY_ACCESS = "arrow.enable_unsafe_memory_access";
  private static final String ENABLE_UNSAFE_MEMORY_ACCESS_ENV = "ARROW_ENABLE_UNSAFE_MEMORY_ACCESS";
  private static final String ENABLE_NULL_CHECK_FOR_GET = "arrow.enable_null_check_for_get";
  private static final String ENABLE_NULL_CHECK_FOR_GET_ENV = "ARROW_ENABLE_NULL_CHECK_FOR_GET";

  static {
    try {
      enableUnsafeMemoryAccess();
      disableNullCheckForGet();
    } catch (Exception e) {
      LOG.warn("Couldn't set Arrow properties, which may impact read performance", e);
    }
  }

  private VectorizedSparkParquetReaders() {}

  /**
   * 构造向量化 Parquet 读取器。
   *
   * <p>逻辑：用 {@link TypeWithSchemaVisitor} 访问 expectedSchema.asStruct 与 fileSchema， 通过 ReaderBuilder
   * 构造 reader 树，根节点为 ColumnarBatchReader。
   *
   * @param expectedSchema Iceberg 期望 schema
   * @param fileSchema Parquet 文件 schema
   * @param idToConstant 字段 ID 到常量值的映射
   * @param deleteFilter 删除过滤器
   * @return ColumnarBatchReader 实例
   */
  public static ColumnarBatchReader buildReader(
      Schema expectedSchema,
      MessageType fileSchema,
      Map<Integer, ?> idToConstant,
      DeleteFilter<InternalRow> deleteFilter) {
    return (ColumnarBatchReader)
        TypeWithSchemaVisitor.visit(
            expectedSchema.asStruct(),
            fileSchema,
            new ReaderBuilder(
                expectedSchema,
                fileSchema,
                NullCheckingForGet.NULL_CHECKING_ENABLED,
                idToConstant,
                ColumnarBatchReader::new,
                deleteFilter));
  }

  /** 启用 Arrow unsafe 内存访问以跳过昂贵的越界检查（仅当用户未显式配置时）。 */
  private static void enableUnsafeMemoryAccess() {
    String value = confValue(ENABLE_UNSAFE_MEMORY_ACCESS, ENABLE_UNSAFE_MEMORY_ACCESS_ENV);
    if (value == null) {
      LOG.info("Enabling {}", ENABLE_UNSAFE_MEMORY_ACCESS);
      System.setProperty(ENABLE_UNSAFE_MEMORY_ACCESS, "true");
    } else {
      LOG.info("Unsafe memory access was configured explicitly: {}", value);
    }
  }

  /** 关闭 Arrow 每次 get 的 null 检查，改用 Iceberg 自身的可空性管理（仅当用户未显式配置时）。 */
  private static void disableNullCheckForGet() {
    String value = confValue(ENABLE_NULL_CHECK_FOR_GET, ENABLE_NULL_CHECK_FOR_GET_ENV);
    if (value == null) {
      LOG.info("Disabling {}", ENABLE_NULL_CHECK_FOR_GET);
      System.setProperty(ENABLE_NULL_CHECK_FOR_GET, "false");
    } else {
      LOG.info("Null checking for get calls was configured explicitly: {}", value);
    }
  }

  /** 优先从系统属性、其次从环境变量读取配置值。 */
  private static String confValue(String propName, String envName) {
    String propValue = System.getProperty(propName);
    if (propValue != null) {
      return propValue;
    }

    return System.getenv(envName);
  }

  /**
   * 自定义 {@link VectorizedReaderBuilder}：在构造 reader 时注入 {@link DeleteFilter}。
   *
   * <p>设计意图：复用父类的 reader 树构造逻辑，仅覆盖 vectorizedReader 把 DeleteFilter 设置到根 ColumnarBatchReader
   * 上，使读取时能应用位置/等值删除。
   */
  private static class ReaderBuilder extends VectorizedReaderBuilder {
    private final DeleteFilter<InternalRow> deleteFilter;

    /** 构造 ReaderBuilder，保存 deleteFilter。 */
    ReaderBuilder(
        Schema expectedSchema,
        MessageType parquetSchema,
        boolean setArrowValidityVector,
        Map<Integer, ?> idToConstant,
        Function<List<VectorizedReader<?>>, VectorizedReader<?>> readerFactory,
        DeleteFilter<InternalRow> deleteFilter) {
      super(expectedSchema, parquetSchema, setArrowValidityVector, idToConstant, readerFactory);
      this.deleteFilter = deleteFilter;
    }

    /** 调用父类构造 reader，若有 deleteFilter 则注入到 ColumnarBatchReader。 */
    @Override
    protected VectorizedReader<?> vectorizedReader(List<VectorizedReader<?>> reorderedFields) {
      VectorizedReader<?> reader = super.vectorizedReader(reorderedFields);
      if (deleteFilter != null) {
        ((ColumnarBatchReader) reader).setDeleteFilter(deleteFilter);
      }
      return reader;
    }
  }
}
