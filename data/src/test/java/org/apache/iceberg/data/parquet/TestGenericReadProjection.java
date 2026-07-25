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
package org.apache.iceberg.data.parquet;

import java.io.File;
import java.io.IOException;
import org.apache.iceberg.Files;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.TestReadProjection;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;

/**
 * 文件级说明：测试 TestGenericReadProjection 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 TestGenericReadProjection 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestGenericReadProjection extends TestReadProjection {
  /** 辅助方法：writeAndRead。 */
  @Override
  protected Record writeAndRead(String desc, Schema writeSchema, Schema readSchema, Record record)
      throws IOException {
    File file = temp.newFile(desc + ".parquet");
    file.delete();

    try (FileAppender<Record> appender =
        Parquet.write(Files.localOutput(file))
            .schema(writeSchema)
            .createWriterFunc(GenericParquetWriter::buildWriter)
            .build()) {
      appender.add(record);
    }

    Iterable<Record> records =
        Parquet.read(Files.localInput(file))
            .project(readSchema)
            .createReaderFunc(
                fileSchema -> GenericParquetReaders.buildReader(readSchema, fileSchema))
            .build();

    return Iterables.getOnlyElement(records);
  }
}
