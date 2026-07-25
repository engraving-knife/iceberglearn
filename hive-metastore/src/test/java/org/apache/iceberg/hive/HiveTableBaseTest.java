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
package org.apache.iceberg.hive;

import static org.apache.iceberg.PartitionSpec.builderFor;
import static org.apache.iceberg.TableMetadataParser.getFileExtension;
import static org.apache.iceberg.types.Types.NestedField.optional;
import static org.apache.iceberg.types.Types.NestedField.required;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * 文件级说明：测试 HiveTableBaseTest 的功能。
 *
 * <p>所属模块：iceberg-hive-metastore。职责：验证 HiveTableBaseTest 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class HiveTableBaseTest extends HiveMetastoreTest {

  static final String TABLE_NAME = "tbl";
  static final TableIdentifier TABLE_IDENTIFIER = TableIdentifier.of(DB_NAME, TABLE_NAME);

  static final Schema schema =
      new Schema(Types.StructType.of(required(1, "id", Types.LongType.get())).fields());

  static final Schema altered =
      new Schema(
          Types.StructType.of(
                  required(1, "id", Types.LongType.get()),
                  optional(2, "data", Types.LongType.get()))
              .fields());

  private static final PartitionSpec partitionSpec = builderFor(schema).identity("id").build();

  private Path tableLocation;

  /** 辅助方法：createTestTable。 */
  @BeforeEach
  public void createTestTable() {
    this.tableLocation =
        new Path(catalog.createTable(TABLE_IDENTIFIER, schema, partitionSpec).location());
  }

  /** 辅助方法：dropTestTable。 */
  @AfterEach
  public void dropTestTable() throws Exception {
    // drop the table data
    tableLocation.getFileSystem(hiveConf).delete(tableLocation, true);
    catalog.dropTable(TABLE_IDENTIFIER, false /* metadata only, location was already deleted */);
  }

  /** 辅助方法：getTableBasePath。 */
  private static String getTableBasePath(String tableName) {
    String databasePath = metastore.getDatabasePath(DB_NAME);
    return Paths.get(databasePath, tableName).toAbsolutePath().toString();
  }

  /** 辅助方法：getTableLocationPath。 */
  protected static Path getTableLocationPath(String tableName) {
    return new Path("file", null, Paths.get(getTableBasePath(tableName)).toString());
  }

  /** 辅助方法：getTableLocation。 */
  protected static String getTableLocation(String tableName) {
    return getTableLocationPath(tableName).toString();
  }

  /** 辅助方法：metadataLocation。 */
  protected static String metadataLocation(String tableName) {
    return Paths.get(getTableBasePath(tableName), "metadata").toString();
  }

  /** 辅助方法：metadataFiles。 */
  private static List<String> metadataFiles(String tableName) {
    return Arrays.stream(new File(metadataLocation(tableName)).listFiles())
        .map(File::getAbsolutePath)
        .collect(Collectors.toList());
  }

  /** 辅助方法：metadataVersionFiles。 */
  protected static List<String> metadataVersionFiles(String tableName) {
    return filterByExtension(tableName, getFileExtension(TableMetadataParser.Codec.NONE));
  }

  /** 辅助方法：manifestFiles。 */
  protected static List<String> manifestFiles(String tableName) {
    return filterByExtension(tableName, ".avro");
  }

  /** 辅助方法：filterByExtension。 */
  private static List<String> filterByExtension(String tableName, String extension) {
    return metadataFiles(tableName).stream()
        .filter(f -> f.endsWith(extension))
        .collect(Collectors.toList());
  }
}
