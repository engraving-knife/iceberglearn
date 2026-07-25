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
 * Avro GenericRecord 转 Flink RowData 的 MapFunction 实现。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：把 Avro GenericRecord 转换为 Flink RowData。
 *
 * <p>设计意图：适配器模式；被需要 Avro 输入的 sink 算子调用。
 */
public class AvroGenericRecordToRowDataMapper implements MapFunction<GenericRecord, RowData> {

  private final AvroToRowDataConverters.AvroToRowDataConverter converter;

  AvroGenericRecordToRowDataMapper(RowType rowType) {
    this.converter = AvroToRowDataConverters.createRowConverter(rowType);
  }

  @Override
  public RowData map(GenericRecord genericRecord) throws Exception {
    return (RowData) converter.convert(genericRecord);
  }

  /** Create a mapper based on Avro schema. */
  public static AvroGenericRecordToRowDataMapper forAvroSchema(Schema avroSchema) {
    DataType dataType = AvroSchemaConverter.convertToDataType(avroSchema.toString());
    LogicalType logicalType = TypeConversions.fromDataToLogicalType(dataType);
    RowType rowType = RowType.of(logicalType.getChildren().stream().toArray(LogicalType[]::new));
    return new AvroGenericRecordToRowDataMapper(rowType);
  }
}
