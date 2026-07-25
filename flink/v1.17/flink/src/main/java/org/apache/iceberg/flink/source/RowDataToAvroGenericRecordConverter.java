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
package org.apache.iceberg.flink.source;

import java.util.function.Function;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.annotation.Internal;
import org.apache.flink.formats.avro.RowDataToAvroConverters;
import org.apache.flink.formats.avro.typeutils.AvroSchemaConverter;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;
import org.apache.iceberg.avro.AvroSchemaUtil;
import org.apache.iceberg.flink.FlinkSchemaUtil;

/**
 * 文件级说明：把 Flink {@link RowData} 转换为 Avro {@link GenericRecord} 的转换器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source 子包）。
 *
 * <p>职责：基于 Flink 的 {@link RowDataToAvroConverters}， 把每条 RowData 转换为 Avro GenericRecord，供需要 Avro
 * 输出的下游使用。
 *
 * <p>设计意图：Avro {@link Schema} 实质上不可序列化（即便实现了 Serializable）， 因此本转换器不应直接序列化，调用方需使用懒加载模式。
 *
 * <p>上下游关系：上游为 {@link AvroGenericRecordReaderFunction}（持有本转换器）， 下游为 Flink 的 {@link
 * RowDataToAvroConverters}。
 */
@Internal
public class RowDataToAvroGenericRecordConverter implements Function<RowData, GenericRecord> {
  private final RowDataToAvroConverters.RowDataToAvroConverter converter;
  private final Schema avroSchema;

  /** 私有构造，传入 RowType 与 Avro Schema。 */
  private RowDataToAvroGenericRecordConverter(RowType rowType, Schema avroSchema) {
    this.converter = RowDataToAvroConverters.createConverter(rowType);
    this.avroSchema = avroSchema;
  }

  /**
   * 把 RowData 转换为 Avro GenericRecord。
   *
   * @param rowData 输入的 Flink RowData
   * @return Avro GenericRecord
   */
  @Override
  public GenericRecord apply(RowData rowData) {
    return (GenericRecord) converter.convert(avroSchema, rowData);
  }

  /**
   * 由 Iceberg schema 创建转换器。
   *
   * <p>逻辑：把 Iceberg schema 转为 Flink RowType 与 Avro Schema，再构造转换器。
   *
   * @param tableName 表名
   * @param icebergSchema Iceberg schema
   * @return RowData→Avro 转换器
   */
  public static RowDataToAvroGenericRecordConverter fromIcebergSchema(
      String tableName, org.apache.iceberg.Schema icebergSchema) {
    RowType rowType = FlinkSchemaUtil.convert(icebergSchema);
    Schema avroSchema = AvroSchemaUtil.convert(icebergSchema, tableName);
    return new RowDataToAvroGenericRecordConverter(rowType, avroSchema);
  }

  /**
   * 由 Avro schema 创建转换器。
   *
   * <p>逻辑：把 Avro schema 转为 Flink DataType 再到 RowType，构造转换器。
   *
   * @param avroSchema Avro schema
   * @return RowData→Avro 转换器
   */
  public static RowDataToAvroGenericRecordConverter fromAvroSchema(Schema avroSchema) {
    DataType dataType = AvroSchemaConverter.convertToDataType(avroSchema.toString());
    LogicalType logicalType = TypeConversions.fromDataToLogicalType(dataType);
    RowType rowType = RowType.of(logicalType.getChildren().stream().toArray(LogicalType[]::new));
    return new RowDataToAvroGenericRecordConverter(rowType, avroSchema);
  }
}
