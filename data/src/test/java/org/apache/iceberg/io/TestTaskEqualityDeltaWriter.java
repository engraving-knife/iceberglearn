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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.FileContent;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.Files;
import org.apache.iceberg.PartitionKey;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.RowDelta;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.TableTestBase;
import org.apache.iceberg.avro.Avro;
import org.apache.iceberg.data.GenericAppenderFactory;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.IcebergGenerics;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.data.avro.DataReader;
import org.apache.iceberg.data.orc.GenericOrcReader;
import org.apache.iceberg.data.parquet.GenericParquetReaders;
import org.apache.iceberg.orc.ORC;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.StructLikeSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
/**
 * 文件级说明：测试 TestTaskEqualityDeltaWriter 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 TestTaskEqualityDeltaWriter 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestTaskEqualityDeltaWriter extends TableTestBase {
  private static final int FORMAT_V2 = 2;
  private static final long TARGET_FILE_SIZE = 128 * 1024 * 1024L;

  private final FileFormat format;
  private final GenericRecord gRecord = GenericRecord.create(SCHEMA);
  private final GenericRecord posRecord = GenericRecord.create(DeleteSchemaUtil.pathPosSchema());

  private OutputFileFactory fileFactory = null;
  private int idFieldId;
  private int dataFieldId;

  /** 辅助方法：parameters。 */
  @Parameterized.Parameters(name = "FileFormat = {0}")
  public static Object[][] parameters() {
    return new Object[][] {{"avro"}, {"orc"}, {"parquet"}};
  }

  /** 辅助方法：TestTaskEqualityDeltaWriter。 */
  public TestTaskEqualityDeltaWriter(String fileFormat) {
    super(FORMAT_V2);
    this.format = FileFormat.fromString(fileFormat);
  }

  /** 辅助方法：setupTable。 */
  @Override
  @Before
  public void setupTable() throws IOException {
    this.tableDir = temp.newFolder();
    Assert.assertTrue(tableDir.delete()); // created by table create

    this.metadataDir = new File(tableDir, "metadata");

    this.table = create(SCHEMA, PartitionSpec.unpartitioned());
    this.fileFactory = OutputFileFactory.builderFor(table, 1, 1).format(format).build();

    this.idFieldId = table.schema().findField("id").fieldId();
    this.dataFieldId = table.schema().findField("data").fieldId();

    table.updateProperties().defaultFormat(format).commit();
  }

  /** 辅助方法：createRecord。 */
  private Record createRecord(Integer id, String data) {
    return gRecord.copy("id", id, "data", data);
  }

  /**
   * 测试场景：Pure Insert。
   *
   * <p>验证该方法在 Pure Insert 条件下的行为是否符合预期。
   */
  @Test
  public void testPureInsert() throws IOException {
    List<Integer> eqDeleteFieldIds = Lists.newArrayList(idFieldId, dataFieldId);
    Schema eqDeleteRowSchema = table.schema();

    GenericTaskDeltaWriter deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    List<Record> expected = Lists.newArrayList();
    for (int i = 0; i < 20; i++) {
      Record record = createRecord(i, String.format("val-%d", i));
      expected.add(record);

      deltaWriter.write(record);
    }

    WriteResult result = deltaWriter.complete();
    Assert.assertEquals("Should only have a data file.", 1, result.dataFiles().length);
    Assert.assertEquals("Should have no delete file", 0, result.deleteFiles().length);
    commitTransaction(result);
    Assert.assertEquals(
        "Should have expected records", expectedRowSet(expected), actualRowSet("*"));

    deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    for (int i = 20; i < 30; i++) {
      Record record = createRecord(i, String.format("val-%d", i));
      expected.add(record);

      deltaWriter.write(record);
    }
    result = deltaWriter.complete();
    Assert.assertEquals("Should only have a data file.", 1, result.dataFiles().length);
    Assert.assertEquals("Should have no delete file", 0, result.deleteFiles().length);
    commitTransaction(result);
    Assert.assertEquals(
        "Should have expected records", expectedRowSet(expected), actualRowSet("*"));
  }

  /**
   * 测试场景：Insert Duplicated Key。
   *
   * <p>验证该方法在 Insert Duplicated Key 条件下的行为是否符合预期。
   */
  @Test
  public void testInsertDuplicatedKey() throws IOException {
    List<Integer> equalityFieldIds = Lists.newArrayList(idFieldId);
    Schema eqDeleteRowSchema = table.schema();

    GenericTaskDeltaWriter deltaWriter = createTaskWriter(equalityFieldIds, eqDeleteRowSchema);
    deltaWriter.write(createRecord(1, "aaa"));
    deltaWriter.write(createRecord(2, "bbb"));
    deltaWriter.write(createRecord(3, "ccc"));
    deltaWriter.write(createRecord(4, "ddd"));
    deltaWriter.write(createRecord(4, "eee"));
    deltaWriter.write(createRecord(3, "fff"));
    deltaWriter.write(createRecord(2, "ggg"));
    deltaWriter.write(createRecord(1, "hhh"));

    WriteResult result = deltaWriter.complete();
    commitTransaction(result);

    Assert.assertEquals("Should have a data file.", 1, result.dataFiles().length);
    Assert.assertEquals("Should have a pos-delete file", 1, result.deleteFiles().length);
    DeleteFile posDeleteFile = result.deleteFiles()[0];
    Assert.assertEquals(
        "Should be a pos-delete file", FileContent.POSITION_DELETES, posDeleteFile.content());
    Assert.assertEquals(1, result.referencedDataFiles().length);
    Assert.assertEquals(
        "Should have expected records",
        expectedRowSet(
            ImmutableList.of(
                createRecord(4, "eee"),
                createRecord(3, "fff"),
                createRecord(2, "ggg"),
                createRecord(1, "hhh"))),
        actualRowSet("*"));

    // Check records in the data file.
    DataFile dataFile = result.dataFiles()[0];
    Assert.assertEquals(
        ImmutableList.of(
            createRecord(1, "aaa"),
            createRecord(2, "bbb"),
            createRecord(3, "ccc"),
            createRecord(4, "ddd"),
            createRecord(4, "eee"),
            createRecord(3, "fff"),
            createRecord(2, "ggg"),
            createRecord(1, "hhh")),
        readRecordsAsList(table.schema(), dataFile.path()));

    // Check records in the pos-delete file.
    Schema posDeleteSchema = DeleteSchemaUtil.pathPosSchema();
    Assert.assertEquals(
        ImmutableList.of(
            posRecord.copy("file_path", dataFile.path(), "pos", 0L),
            posRecord.copy("file_path", dataFile.path(), "pos", 1L),
            posRecord.copy("file_path", dataFile.path(), "pos", 2L),
            posRecord.copy("file_path", dataFile.path(), "pos", 3L)),
        readRecordsAsList(posDeleteSchema, posDeleteFile.path()));
  }

  /**
   * 测试场景：Upsert Same Row。
   *
   * <p>验证该方法在 Upsert Same Row 条件下的行为是否符合预期。
   */
  @Test
  public void testUpsertSameRow() throws IOException {
    List<Integer> eqDeleteFieldIds = Lists.newArrayList(idFieldId, dataFieldId);
    Schema eqDeleteRowSchema = table.schema();

    GenericTaskDeltaWriter deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);

    Record record = createRecord(1, "aaa");
    deltaWriter.write(record);

    // UPSERT <1, 'aaa'> to <1, 'aaa'>
    deltaWriter.delete(record);
    deltaWriter.write(record);

    WriteResult result = deltaWriter.complete();
    Assert.assertEquals("Should have a data file.", 1, result.dataFiles().length);
    Assert.assertEquals("Should have a pos-delete file.", 1, result.deleteFiles().length);
    commitTransaction(result);
    Assert.assertEquals(
        "Should have an expected record",
        expectedRowSet(ImmutableList.of(record)),
        actualRowSet("*"));

    // Check records in the data file.
    DataFile dataFile = result.dataFiles()[0];
    Assert.assertEquals(
        ImmutableList.of(record, record), readRecordsAsList(table.schema(), dataFile.path()));

    // Check records in the pos-delete file.
    DeleteFile posDeleteFile = result.deleteFiles()[0];
    Assert.assertEquals(
        ImmutableList.of(posRecord.copy("file_path", dataFile.path(), "pos", 0L)),
        readRecordsAsList(DeleteSchemaUtil.pathPosSchema(), posDeleteFile.path()));

    deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    deltaWriter.delete(record);
    result = deltaWriter.complete();
    Assert.assertEquals("Should have 0 data file.", 0, result.dataFiles().length);
    Assert.assertEquals("Should have 1 eq-delete file", 1, result.deleteFiles().length);
    commitTransaction(result);
    Assert.assertEquals(
        "Should have no record", expectedRowSet(ImmutableList.of()), actualRowSet("*"));
  }

  /**
   * 测试场景：Upsert Data。
   *
   * <p>验证该方法在 Upsert Data 条件下的行为是否符合预期。
   */
  @Test
  public void testUpsertData() throws IOException {
    List<Integer> eqDeleteFieldIds = Lists.newArrayList(dataFieldId);
    Schema eqDeleteRowSchema = table.schema().select("data");

    GenericTaskDeltaWriter deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    deltaWriter.write(createRecord(1, "aaa"));
    deltaWriter.write(createRecord(2, "bbb"));
    deltaWriter.write(createRecord(3, "aaa"));
    deltaWriter.write(createRecord(3, "ccc"));
    deltaWriter.write(createRecord(4, "ccc"));

    // Commit the 1th transaction.
    WriteResult result = deltaWriter.complete();
    Assert.assertEquals("Should have a data file", 1, result.dataFiles().length);
    Assert.assertEquals(
        "Should have a pos-delete file for deduplication purpose", 1, result.deleteFiles().length);
    Assert.assertEquals(
        "Should be pos-delete file",
        FileContent.POSITION_DELETES,
        result.deleteFiles()[0].content());
    Assert.assertEquals(1, result.referencedDataFiles().length);
    commitTransaction(result);

    Assert.assertEquals(
        "Should have expected records",
        expectedRowSet(
            ImmutableList.of(
                createRecord(2, "bbb"), createRecord(3, "aaa"), createRecord(4, "ccc"))),
        actualRowSet("*"));

    // Start the 2nd transaction.
    deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    GenericRecord keyRecord = GenericRecord.create(eqDeleteRowSchema);
    Function<String, Record> keyFunc = data -> keyRecord.copy("data", data);

    // UPSERT <3,'aaa'> to <5,'aaa'> - (by delete the key)
    deltaWriter.deleteKey(keyFunc.apply("aaa"));
    deltaWriter.write(createRecord(5, "aaa"));

    // UPSERT <5,'aaa'> to <6,'aaa'> - (by delete the key)
    deltaWriter.deleteKey(keyFunc.apply("aaa"));
    deltaWriter.write(createRecord(6, "aaa"));

    // UPSERT <4,'ccc'> to <7,'ccc'> - (by delete the key)
    deltaWriter.deleteKey(keyFunc.apply("ccc"));
    deltaWriter.write(createRecord(7, "ccc"));

    // DELETE <2, 'bbb'> - (by delete the key)
    deltaWriter.deleteKey(keyFunc.apply("bbb"));

    // Commit the 2nd transaction.
    result = deltaWriter.complete();
    Assert.assertEquals(1, result.dataFiles().length);
    Assert.assertEquals(2, result.deleteFiles().length);
    commitTransaction(result);

    Assert.assertEquals(
        "Should have expected records",
        expectedRowSet(ImmutableList.of(createRecord(6, "aaa"), createRecord(7, "ccc"))),
        actualRowSet("*"));

    // Check records in the data file.
    DataFile dataFile = result.dataFiles()[0];
    Assert.assertEquals(
        ImmutableList.of(createRecord(5, "aaa"), createRecord(6, "aaa"), createRecord(7, "ccc")),
        readRecordsAsList(table.schema(), dataFile.path()));

    // Check records in the eq-delete file.
    DeleteFile eqDeleteFile = result.deleteFiles()[0];
    Assert.assertEquals(FileContent.EQUALITY_DELETES, eqDeleteFile.content());
    Assert.assertEquals(
        ImmutableList.of(keyFunc.apply("aaa"), keyFunc.apply("ccc"), keyFunc.apply("bbb")),
        readRecordsAsList(eqDeleteRowSchema, eqDeleteFile.path()));

    // Check records in the pos-delete file.
    DeleteFile posDeleteFile = result.deleteFiles()[1];
    Schema posDeleteSchema = DeleteSchemaUtil.pathPosSchema();
    Assert.assertEquals(FileContent.POSITION_DELETES, posDeleteFile.content());
    Assert.assertEquals(
        ImmutableList.of(posRecord.copy("file_path", dataFile.path(), "pos", 0L)),
        readRecordsAsList(posDeleteSchema, posDeleteFile.path()));
  }

  /**
   * 测试场景：Upsert Data With Full Row Schema。
   *
   * <p>验证该方法在 Upsert Data With Full Row Schema 条件下的行为是否符合预期。
   */
  @Test
  public void testUpsertDataWithFullRowSchema() throws IOException {
    List<Integer> eqDeleteFieldIds = Lists.newArrayList(dataFieldId);
    Schema eqDeleteRowSchema = table.schema();

    GenericTaskDeltaWriter deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);
    deltaWriter.write(createRecord(1, "aaa"));
    deltaWriter.write(createRecord(2, "bbb"));
    deltaWriter.write(createRecord(3, "aaa"));
    deltaWriter.write(createRecord(3, "ccc"));
    deltaWriter.write(createRecord(4, "ccc"));

    // Commit the 1th transaction.
    WriteResult result = deltaWriter.complete();
    Assert.assertEquals("Should have a data file", 1, result.dataFiles().length);
    Assert.assertEquals(
        "Should have a pos-delete file for deduplication purpose", 1, result.deleteFiles().length);
    Assert.assertEquals(
        "Should be pos-delete file",
        FileContent.POSITION_DELETES,
        result.deleteFiles()[0].content());
    Assert.assertEquals(1, result.referencedDataFiles().length);
    commitTransaction(result);

    Assert.assertEquals(
        "Should have expected records",
        expectedRowSet(
            ImmutableList.of(
                createRecord(2, "bbb"), createRecord(3, "aaa"), createRecord(4, "ccc"))),
        actualRowSet("*"));

    // Start the 2nd transaction.
    deltaWriter = createTaskWriter(eqDeleteFieldIds, eqDeleteRowSchema);

    // UPSERT <3,'aaa'> to <5,'aaa'> - (by delete the entire row)
    deltaWriter.delete(createRecord(3, "aaa"));
    deltaWriter.write(createRecord(5, "aaa"));

    // UPSERT <5,'aaa'> to <6,'aaa'> - (by delete the entire row)
    deltaWriter.delete(createRecord(5, "aaa"));
    deltaWriter.write(createRecord(6, "aaa"));

    // UPSERT <4,'ccc'> to <7,'ccc'> - (by delete the entire row)
    deltaWriter.delete(createRecord(4, "ccc"));
    deltaWriter.write(createRecord(7, "ccc"));

    // DELETE <2, 'bbb'> - (by delete the entire row)
    deltaWriter.delete(createRecord(2, "bbb"));

    // Commit the 2nd transaction.
    result = deltaWriter.complete();
    Assert.assertEquals(1, result.dataFiles().length);
    Assert.assertEquals(2, result.deleteFiles().length);
    Assert.assertEquals(1, result.referencedDataFiles().length);
    commitTransaction(result);

    Assert.assertEquals(
        "Should have expected records",
        expectedRowSet(ImmutableList.of(createRecord(6, "aaa"), createRecord(7, "ccc"))),
        actualRowSet("*"));

    // Check records in the data file.
    DataFile dataFile = result.dataFiles()[0];
    Assert.assertEquals(
        ImmutableList.of(createRecord(5, "aaa"), createRecord(6, "aaa"), createRecord(7, "ccc")),
        readRecordsAsList(table.schema(), dataFile.path()));

    // Check records in the eq-delete file.
    DeleteFile eqDeleteFile = result.deleteFiles()[0];
    Assert.assertEquals(FileContent.EQUALITY_DELETES, eqDeleteFile.content());
    Assert.assertEquals(
        ImmutableList.of(createRecord(3, "aaa"), createRecord(4, "ccc"), createRecord(2, "bbb")),
        readRecordsAsList(eqDeleteRowSchema, eqDeleteFile.path()));

    // Check records in the pos-delete file.
    DeleteFile posDeleteFile = result.deleteFiles()[1];
    Schema posDeleteSchema = DeleteSchemaUtil.pathPosSchema();
    Assert.assertEquals(FileContent.POSITION_DELETES, posDeleteFile.content());
    Assert.assertEquals(
        ImmutableList.of(posRecord.copy("file_path", dataFile.path(), "pos", 0L)),
        readRecordsAsList(posDeleteSchema, posDeleteFile.path()));
  }

  /** 辅助方法：commitTransaction。 */
  private void commitTransaction(WriteResult result) {
    RowDelta rowDelta = table.newRowDelta();
    Arrays.stream(result.dataFiles()).forEach(rowDelta::addRows);
    Arrays.stream(result.deleteFiles()).forEach(rowDelta::addDeletes);

    rowDelta
        .validateDeletedFiles()
        .validateDataFilesExist(Lists.newArrayList(result.referencedDataFiles()))
        .commit();
  }

  /** 辅助方法：expectedRowSet。 */
  private StructLikeSet expectedRowSet(Iterable<Record> records) {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    records.forEach(set::add);
    return set;
  }

  /** 辅助方法：actualRowSet。 */
  private StructLikeSet actualRowSet(String... columns) throws IOException {
    StructLikeSet set = StructLikeSet.create(table.schema().asStruct());
    try (CloseableIterable<Record> reader = IcebergGenerics.read(table).select(columns).build()) {
      reader.forEach(set::add);
    }
    return set;
  }

  /**
   * Create a generic task equality delta writer.
   *
   * @param equalityFieldIds defines the equality field ids.
   * @param eqDeleteRowSchema defines the schema of rows that eq-delete writer will write, it could
   *     be the entire fields of the table schema.
   */
  private GenericTaskDeltaWriter createTaskWriter(
      List<Integer> equalityFieldIds, Schema eqDeleteRowSchema) {
    FileAppenderFactory<Record> appenderFactory =
        new GenericAppenderFactory(
            table.schema(),
            table.spec(),
            ArrayUtil.toIntArray(equalityFieldIds),
            eqDeleteRowSchema,
            null);

    List<String> columns = Lists.newArrayList();
    for (Integer fieldId : equalityFieldIds) {
      columns.add(table.schema().findField(fieldId).name());
    }
    Schema deleteSchema = table.schema().select(columns);

    return new GenericTaskDeltaWriter(
        table.schema(),
        deleteSchema,
        table.spec(),
        format,
        appenderFactory,
        fileFactory,
        table.io(),
        TARGET_FILE_SIZE);
  }

  private static class GenericTaskDeltaWriter extends BaseTaskWriter<Record> {
    private final GenericEqualityDeltaWriter deltaWriter;

    /** 辅助方法：GenericTaskDeltaWriter。 */
    private GenericTaskDeltaWriter(
        Schema schema,
        Schema deleteSchema,
        PartitionSpec spec,
        FileFormat format,
        FileAppenderFactory<Record> appenderFactory,
        OutputFileFactory fileFactory,
        FileIO io,
        long targetFileSize) {
      super(spec, format, appenderFactory, fileFactory, io, targetFileSize);
      this.deltaWriter = new GenericEqualityDeltaWriter(null, schema, deleteSchema);
    }

    /** 辅助方法：write。 */
    @Override
    public void write(Record row) throws IOException {
      deltaWriter.write(row);
    }

    /** 辅助方法：delete。 */
    public void delete(Record row) throws IOException {
      deltaWriter.delete(row);
    }

    // The caller of this function is responsible for passing in a record with only the key fields
    public void deleteKey(Record key) throws IOException {
      deltaWriter.deleteKey(key);
    }

    /** 辅助方法：close。 */
    @Override
    public void close() throws IOException {
      deltaWriter.close();
    }

    private class GenericEqualityDeltaWriter extends BaseEqualityDeltaWriter {
      /** 辅助方法：GenericEqualityDeltaWriter。 */
      private GenericEqualityDeltaWriter(
          PartitionKey partition, Schema schema, Schema eqDeleteSchema) {
        super(partition, schema, eqDeleteSchema);
      }

      /** 辅助方法：asStructLike。 */
      @Override
      protected StructLike asStructLike(Record row) {
        return row;
      }

      /** 辅助方法：asStructLikeKey。 */
      @Override
      protected StructLike asStructLikeKey(Record data) {
        return data;
      }
    }
  }

  /** 辅助方法：readRecordsAsList。 */
  private List<Record> readRecordsAsList(Schema schema, CharSequence path) throws IOException {
    CloseableIterable<Record> iterable;

    InputFile inputFile = Files.localInput(path.toString());
    switch (format) {
      case PARQUET:
        iterable =
            Parquet.read(inputFile)
                .project(schema)
                .createReaderFunc(
                    fileSchema -> GenericParquetReaders.buildReader(schema, fileSchema))
                .build();
        break;

      case AVRO:
        iterable =
            Avro.read(inputFile).project(schema).createReaderFunc(DataReader::create).build();
        break;

      case ORC:
        iterable =
            ORC.read(inputFile)
                .project(schema)
                .createReaderFunc(fileSchema -> GenericOrcReader.buildReader(schema, fileSchema))
                .build();
        break;

      default:
        throw new UnsupportedOperationException("Unsupported file format: " + format);
    }

    try (CloseableIterable<Record> closeableIterable = iterable) {
      return Lists.newArrayList(closeableIterable);
    }
  }
}
