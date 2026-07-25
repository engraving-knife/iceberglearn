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
package org.apache.iceberg.spark.source;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.util.Utf8;
import org.apache.iceberg.ContentFile;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.DeleteFile;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Partitioning;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.data.DeleteFilter;
import org.apache.iceberg.deletes.DeleteCounter;
import org.apache.iceberg.encryption.EncryptedFiles;
import org.apache.iceberg.encryption.EncryptedInputFile;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.spark.SparkSchemaUtil;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.PartitionUtil;
import org.apache.spark.rdd.InputFileBlockHolder;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.unsafe.types.UTF8String;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iceberg 表在 Spark DataSource V2 中的实现的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 BaseReader。
 *
 * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
 *
 * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
 */
abstract class BaseReader<T, TaskT extends ScanTask> implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(BaseReader.class);

  private final Table table;
  private final Schema tableSchema;
  private final Schema expectedSchema;
  private final boolean caseSensitive;
  private final NameMapping nameMapping;
  private final ScanTaskGroup<TaskT> taskGroup;
  private final Iterator<TaskT> tasks;
  private final DeleteCounter counter;

  private Map<String, InputFile> lazyInputFiles;
  private CloseableIterator<T> currentIterator;
  private T current = null;
  private TaskT currentTask = null;

  BaseReader(
      Table table,
      ScanTaskGroup<TaskT> taskGroup,
      Schema tableSchema,
      Schema expectedSchema,
      boolean caseSensitive) {
    this.table = table;
    this.taskGroup = taskGroup;
    this.tasks = taskGroup.tasks().iterator();
    this.currentIterator = CloseableIterator.empty();
    this.tableSchema = tableSchema;
    this.expectedSchema = expectedSchema;
    this.caseSensitive = caseSensitive;
    String nameMappingString = table.properties().get(TableProperties.DEFAULT_NAME_MAPPING);
    this.nameMapping =
        nameMappingString != null ? NameMappingParser.fromJson(nameMappingString) : null;
    this.counter = new DeleteCounter();
  }

  /** 执行该方法的具体逻辑。 */
  protected abstract CloseableIterator<T> open(TaskT task);

  /** 执行该方法的具体逻辑。 */
  protected abstract Stream<ContentFile<?>> referencedFiles(TaskT task);

  /** 执行该方法的具体逻辑。 */
  protected Schema expectedSchema() {
    return expectedSchema;
  }

  /** 执行该方法的具体逻辑。 */
  protected boolean caseSensitive() {
    return caseSensitive;
  }

  /** 执行该方法的具体逻辑。 */
  protected NameMapping nameMapping() {
    return nameMapping;
  }

  /** 执行该方法的具体逻辑。 */
  protected Table table() {
    return table;
  }

  /** 执行该方法的具体逻辑。 */
  protected DeleteCounter counter() {
    return counter;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 对应结果
   */
  public boolean next() throws IOException {
    try {
      while (true) {
        if (currentIterator.hasNext()) {
          this.current = currentIterator.next();
          return true;
        } else if (tasks.hasNext()) {
          this.currentIterator.close();
          this.currentTask = tasks.next();
          this.currentIterator = open(currentTask);
        } else {
          this.currentIterator.close();
          return false;
        }
      }
    } catch (IOException | RuntimeException e) {
      if (currentTask != null && !currentTask.isDataTask()) {
        String filePaths =
            referencedFiles(currentTask)
                .map(file -> file.path().toString())
                .collect(Collectors.joining(", "));
        LOG.error("Error reading file(s): {}", filePaths, e);
      }
      throw e;
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 对应结果
   */
  public T get() {
    return current;
  }

  /** 释放底层资源。 */
  @Override
  public void close() throws IOException {
    InputFileBlockHolder.unset();

    // close the current iterator
    this.currentIterator.close();

    // exhaust the task iterator
    while (tasks.hasNext()) {
      tasks.next();
    }
  }

  /** 返回inputfile。 */
  protected InputFile getInputFile(String location) {
    return inputFiles().get(location);
  }

  /** 执行该方法的具体逻辑。 */
  private Map<String, InputFile> inputFiles() {
    if (lazyInputFiles == null) {
      Stream<EncryptedInputFile> encryptedFiles =
          taskGroup.tasks().stream().flatMap(this::referencedFiles).map(this::toEncryptedInputFile);

      // decrypt with the batch call to avoid multiple RPCs to a key server, if possible
      Iterable<InputFile> decryptedFiles = table.encryption().decrypt(encryptedFiles::iterator);

      Map<String, InputFile> files = Maps.newHashMapWithExpectedSize(taskGroup.tasks().size());
      decryptedFiles.forEach(decrypted -> files.putIfAbsent(decrypted.location(), decrypted));
      this.lazyInputFiles = ImmutableMap.copyOf(files);
    }

    return lazyInputFiles;
  }

  /** 转换为encryptedinputfile。 */
  private EncryptedInputFile toEncryptedInputFile(ContentFile<?> file) {
    InputFile inputFile = table.io().newInputFile(file.path().toString());
    return EncryptedFiles.encryptedInput(inputFile, file.keyMetadata());
  }

  /** 执行该方法的具体逻辑。 */
  protected Map<Integer, ?> constantsMap(ContentScanTask<?> task, Schema readSchema) {
    if (readSchema.findField(MetadataColumns.PARTITION_COLUMN_ID) != null) {
      StructType partitionType = Partitioning.partitionType(table);
      return PartitionUtil.constantsMap(task, partitionType, BaseReader::convertConstant);
    } else {
      return PartitionUtil.constantsMap(task, BaseReader::convertConstant);
    }
  }

  /** 把输入转换为另一种表示。 */
  protected static Object convertConstant(Type type, Object value) {
    if (value == null) {
      return null;
    }

    switch (type.typeId()) {
      case DECIMAL:
        return Decimal.apply((BigDecimal) value);
      case STRING:
        if (value instanceof Utf8) {
          Utf8 utf8 = (Utf8) value;
          return UTF8String.fromBytes(utf8.getBytes(), 0, utf8.getByteLength());
        }
        return UTF8String.fromString(value.toString());
      case FIXED:
        if (value instanceof byte[]) {
          return value;
        } else if (value instanceof GenericData.Fixed) {
          return ((GenericData.Fixed) value).bytes();
        }
        return ByteBuffers.toByteArray((ByteBuffer) value);
      case BINARY:
        return ByteBuffers.toByteArray((ByteBuffer) value);
      case STRUCT:
        StructType structType = (StructType) type;

        if (structType.fields().isEmpty()) {
          /** 执行该方法的具体逻辑。 */
          return new GenericInternalRow();
        }

        List<NestedField> fields = structType.fields();
        Object[] values = new Object[fields.size()];
        StructLike struct = (StructLike) value;

        for (int index = 0; index < fields.size(); index++) {
          NestedField field = fields.get(index);
          Type fieldType = field.type();
          values[index] =
              convertConstant(fieldType, struct.get(index, fieldType.typeId().javaClass()));
        }

        /** 执行该方法的具体逻辑。 */
        return new GenericInternalRow(values);
      default:
    }
    return value;
  }

  /**
   * Iceberg 表在 Spark DataSource V2 中的实现，实现 DELETE 行级操作。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkDeleteFilter。
   *
   * <p>上下游：被 SparkCatalog 创建，依赖 Iceberg Table API 与底层扫描/写入组件。
   */
  protected class SparkDeleteFilter extends DeleteFilter<InternalRow> {
    private final InternalRowWrapper asStructLike;

    SparkDeleteFilter(String filePath, List<DeleteFile> deletes, DeleteCounter counter) {
      super(filePath, deletes, tableSchema, expectedSchema, counter);
      this.asStructLike = new InternalRowWrapper(SparkSchemaUtil.convert(requiredSchema()));
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected StructLike asStructLike(InternalRow row) {
      return asStructLike.wrap(row);
    }

    /** 返回inputfile。 */
    @Override
    protected InputFile getInputFile(String location) {
      return BaseReader.this.getInputFile(location);
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected void markRowDeleted(InternalRow row) {
      if (!row.getBoolean(columnIsDeletedPosition())) {
        row.setBoolean(columnIsDeletedPosition(), true);
        counter().increment();
      }
    }
  }
}
