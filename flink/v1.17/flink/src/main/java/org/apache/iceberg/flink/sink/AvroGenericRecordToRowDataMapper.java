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
package org.apache.iceberg.flink.sink;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.formats.avro.AvroToRowDataConverters;
import org.apache.flink.formats.avro.typeutils.AvroSchemaConverter;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;

/**
 * 文件级说明：将 Avro {@link GenericRecord} 转换为 Flink {@link RowData} 的映射器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink 子包）。
 *
 * <p>职责：实现 Flink {@link MapFunction}，把 Avro 通用记录转换为 Flink 行数据。
 *
 * <p>设计意图：内部使用 Flink 的 {@link AvroToRowDataConverters} 完成实际转换。 由于 Iceberg schema 中时间类型精度为微秒，而 Flink
 * 转换器使用毫秒， 不能直接使用由 Iceberg schema 转换得到的 Avro schema， 必须以 Flink 自身从 Avro schema 推导出的 RowType 为准。
 *
 * <p>上下游关系：上游为 Avro 数据源，下游为 Flink sink 算子。
 */
public class AvroGenericRecordToRowDataMapper implements MapFunction<GenericRecord, RowData> {

  private final AvroToRowDataConverters.AvroToRowDataConverter converter;

  /** 构造映射器，按 Flink 行类型创建 Avro 到 RowData 的转换器。 */
  AvroGenericRecordToRowDataMapper(RowType rowType) {
    this.converter = AvroToRowDataConverters.createRowConverter(rowType);
  }

  /** 把单条 Avro GenericRecord 转换为 RowData。 */
  @Override
  public RowData map(GenericRecord genericRecord) throws Exception {
    return (RowData) converter.convert(genericRecord);
  }

  /**
   * 基于 Avro schema 创建映射器。
   *
   * <p>逻辑：把 Avro schema 转为 Flink DataType 再转为 LogicalType， 取其子类型数组构造 RowType，避免 Iceberg 微秒精度的影响。
   */
  public static AvroGenericRecordToRowDataMapper forAvroSchema(Schema avroSchema) {
    DataType dataType = AvroSchemaConverter.convertToDataType(avroSchema.toString());
    LogicalType logicalType = TypeConversions.fromDataToLogicalType(dataType);
    RowType rowType = RowType.of(logicalType.getChildren().stream().toArray(LogicalType[]::new));
    return new AvroGenericRecordToRowDataMapper(rowType);
  }
}
