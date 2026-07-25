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
package org.apache.iceberg.io;

import java.io.IOException;
import java.util.List;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.TableTestBase;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.encryption.EncryptedOutputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.StructLikeSet;

/**
 * 文件级说明：测试 WriterTestBase 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 WriterTestBase 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public abstract class WriterTestBase<T> extends TableTestBase {

  /** 辅助方法：WriterTestBase。 */
  public WriterTestBase(int formatVersion) {
    super(formatVersion);
  }

  /** 辅助方法：newWriterFactory。 */
  protected abstract FileWriterFactory<T> newWriterFactory(
      Schema dataSchema,
      List<Integer> equalityFieldIds,
      Schema equalityDeleteRowSchema,
      Schema positionDeleteRowSchema);

  /** 辅助方法：newWriterFactory。 */
  protected FileWriterFactory<T> newWriterFactory(
      Schema dataSchema, List<Integer> equalityFieldIds, Schema equalityDeleteRowSchema) {
    return newWriterFactory(dataSchema, equalityFieldIds, equalityDeleteRowSchema, null);
  }

  /** 辅助方法：newWriterFactory。 */
  protected FileWriterFactory<T> newWriterFactory(
      Schema dataSchema, Schema positionDeleteRowSchema) {
    return newWriterFactory(dataSchema, null, null, positionDeleteRowSchema);
  }

  /** 辅助方法：newWriterFactory。 */
  protected FileWriterFactory<T> newWriterFactory(Schema dataSchema) {
    return newWriterFactory(dataSchema, null, null, null);
  }

  /** 辅助方法：toRow。 */
  protected abstract T toRow(Integer id, String data);

  /** 辅助方法：partitionKey。 */
  protected PartitionKey partitionKey(PartitionSpec spec, String value) {
    Record record = GenericRecord.create(table.schema()).copy(ImmutableMap.of("data", value));

    PartitionKey partitionKey = new PartitionKey(spec, table.schema());
    partitionKey.partition(record);

    return partitionKey;
  }

  /** 辅助方法：actualRowSet。 */
  protected StructLikeSet actualRowSet(String... columns) throws IOException {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    try (CloseableIterable<Record> reader = IcebergGenerics.read(table).select(columns).build()) {
      reader.forEach(set::add);
    }
    return set;
  }

  /** 辅助方法：writeData。 */
  protected DataFile writeData(
      FileWriterFactory<T> writerFactory,
      OutputFileFactory fileFactory,
      List<T> rows,
      PartitionSpec spec,
      StructLike partitionKey)
      throws IOException {

    EncryptedOutputFile file = fileFactory.newOutputFile(spec, partitionKey);
    DataWriter<T> writer = writerFactory.newDataWriter(file, spec, partitionKey);

    try (DataWriter<T> closeableWriter = writer) {
      for (T row : rows) {
        closeableWriter.write(row);
      }
    }

    return writer.toDataFile();
  }
}
