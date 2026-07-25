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
package org.apache.iceberg.arrow.vectorized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.arrow.vector.NullCheckingForGet;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Schema;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.encryption.EncryptedFiles;
import org.apache.iceberg.encryption.EncryptedInputFile;
import org.apache.iceberg.encryption.EncryptionManager;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.mapping.NameMappingParser;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.iceberg.util.ExceptionUtil;
import org.apache.iceberg.util.TableScanUtil;
import org.apache.parquet.schema.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：向量化读取 Iceberg 表数据并以 {@link ColumnarBatch} 迭代输出的读取器。
 *
 * <p>所属模块：iceberg-arrow（Iceberg 读取链路与 Arrow 列式内存的桥接，位于扫描执行层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>接收 {@link CombinedScanTask}，逐文件以 Parquet 向量化方式读取，产出 {@link ColumnarBatch} 的 {@link
 *       CloseableIterator}。
 *   <li>管理读取所需的 {@link FileIO}、加密解密、名称映射、批大小与容器复用等配置。
 *   <li>对支持的类型（见 {@link #SUPPORTED_TYPES}）进行校验，对 delete 文件、空投影、 不支持类型抛出 {@link
 *       UnsupportedOperationException}。
 * </ul>
 *
 * <p>设计意图：继承 {@link CloseableGroup} 统一管理多个可关闭资源（迭代器、文件句柄）； 内部委托 {@link
 * VectorizedCombinedScanIterator} 完成实际的逐文件迭代与解密，使读取器 本身保持轻量。reuseContainers 选项允许在迭代中复用 Arrow
 * 向量以减少内存分配。
 *
 * <p>当前已知限制：类型提升未对齐最新 Schema、常量列以字典编码返回 int32、 List/Map/Struct/Fixed/Decimal 与 delete 文件暂不支持。
 *
 * <p>上下游关系：上游为 Iceberg 的 {@link TableScan} 任务规划；下游被 Spark/Flink 等引擎 集成调用以获取列式批次。
 */
public class ArrowReader extends CloseableGroup {
  private static final Logger LOG = LoggerFactory.getLogger(ArrowReader.class);

  private static final Set<TypeID> SUPPORTED_TYPES =
      ImmutableSet.of(
          TypeID.BOOLEAN,
          TypeID.INTEGER,
          TypeID.LONG,
          TypeID.FLOAT,
          TypeID.DOUBLE,
          TypeID.STRING,
          TypeID.TIMESTAMP,
          TypeID.BINARY,
          TypeID.DATE,
          TypeID.UUID,
          TypeID.TIME,
          TypeID.DECIMAL);

  private final Schema schema;
  private final FileIO io;
  private final EncryptionManager encryption;
  private final int batchSize;
  private final boolean reuseContainers;

  /**
   * 构造读取器实例。
   *
   * @param scan 表扫描对象，提供 schema、io、加密管理
   * @param batchSize 每个 Arrow 批次的最大行数
   * @param reuseContainers 是否复用 Arrow 向量；为 false 时每次迭代新建向量，为 true 时
   *     上一批向量可能被下一批复用以避免重复分配；无论取值，新建前都会关闭上一批向量
   */
  public ArrowReader(TableScan scan, int batchSize, boolean reuseContainers) {
    this.schema = scan.schema();
    this.io = scan.table().io();
    this.encryption = scan.table().encryption();
    this.batchSize = batchSize;
    // start planning tasks in the background
    this.reuseContainers = reuseContainers;
  }

  /**
   * 返回 {@link ColumnarBatch} 的新迭代器。
   *
   * <p>读取器拥有 {@link ColumnarBatch} 的生命周期并负责关闭，调用方不应持有或关闭它们。
   *
   * <p>reuseContainers 为 false 时，上一批 Arrow 向量在返回下一批前被关闭；为 true 时， 上一批向量可能被下一批复用，调用方需在使用或深拷贝后再获取下一批。
   *
   * <p>仅当以下条件全部满足时可用：至少查询一列、无 delete 文件、查询类型受支持 （见 {@link #SUPPORTED_TYPES}），否则抛出 {@link
   * UnsupportedOperationException}。
   *
   * @param tasks 合并后的文件扫描任务集合
   * @return 列式批次的可关闭迭代器
   */
  public CloseableIterator<ColumnarBatch> open(CloseableIterable<CombinedScanTask> tasks) {
    CloseableIterator<ColumnarBatch> itr =
        new VectorizedCombinedScanIterator(
            tasks, schema, null, io, encryption, true, batchSize, reuseContainers);
    addCloseable(itr);
    return itr;
  }

  @Override
  /**
   * 关闭读取器及其持有的数据文件资源。
   *
   * @throws IOException 关闭时发生 IO 异常
   */
  public void close() throws IOException {
    super.close(); // close data files
  }

  /**
   * 内部迭代器：逐文件解密并以 Parquet 向量化方式产出 {@link ColumnarBatch}。
   *
   * <p>职责：展平 {@link CombinedScanTask} 为文件任务，校验 delete/空投影/不支持类型， 批量解密输入文件以减少密钥服务器 RPC，并维护当前文件的子迭代器。
   */
  private static final class VectorizedCombinedScanIterator
      implements CloseableIterator<ColumnarBatch> {

    private final Iterator<FileScanTask> fileItr;
    private final Map<String, InputFile> inputFiles;
    private final Schema expectedSchema;
    private final String nameMapping;
    private final boolean caseSensitive;
    private final int batchSize;
    private final boolean reuseContainers;
    private CloseableIterator<ColumnarBatch> currentIterator;
    private FileScanTask currentTask;

    /**
     * 构造内部迭代器。
     *
     * <p>逻辑：展平任务为文件列表；若任一文件含 delete 则抛异常；若投影列为空则抛异常； 计算不支持类型集合，非空则抛异常；收集各文件密钥并批量解密得到 InputFile 映射。
     *
     * @param tasks 合并后的文件扫描任务集合
     * @param expectedSchema 读取 schema，返回数据将具有此 schema
     * @param nameMapping 外部 schema 名到 Iceberg 类型 ID 的映射
     * @param io 文件 IO
     * @param encryptionManager 加密管理器
     * @param caseSensitive 列名是否大小写敏感
     * @param batchSize 批大小（行数）
     * @param reuseContainers 是否复用 Arrow 向量
     */
    VectorizedCombinedScanIterator(
        CloseableIterable<CombinedScanTask> tasks,
        Schema expectedSchema,
        String nameMapping,
        FileIO io,
        EncryptionManager encryptionManager,
        boolean caseSensitive,
        int batchSize,
        boolean reuseContainers) {
      List<FileScanTask> fileTasks =
          StreamSupport.stream(tasks.spliterator(), false)
              .map(CombinedScanTask::files)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      this.fileItr = fileTasks.iterator();

      if (fileTasks.stream().anyMatch(TableScanUtil::hasDeletes)) {
        throw new UnsupportedOperationException(
            "Cannot read files that require applying delete files");
      }

      if (expectedSchema.columns().isEmpty()) {
        throw new UnsupportedOperationException(
            "Cannot read without at least one projected column");
      }

      Set<TypeID> unsupportedTypes =
          Sets.difference(
              expectedSchema.columns().stream()
                  .map(c -> c.type().typeId())
                  .collect(Collectors.toSet()),
              SUPPORTED_TYPES);
      if (!unsupportedTypes.isEmpty()) {
        throw new UnsupportedOperationException(
            "Cannot read unsupported column types: " + unsupportedTypes);
      }

      Map<String, ByteBuffer> keyMetadata = Maps.newHashMap();
      fileTasks.stream()
          .map(FileScanTask::file)
          .forEach(file -> keyMetadata.put(file.path().toString(), file.keyMetadata()));

      Stream<EncryptedInputFile> encrypted =
          keyMetadata.entrySet().stream()
              .map(
                  entry ->
                      EncryptedFiles.encryptedInput(
                          io.newInputFile(entry.getKey()), entry.getValue()));

      // decrypt with the batch call to avoid multiple RPCs to a key server, if possible
      @SuppressWarnings("StreamToIterable")
      Iterable<InputFile> decryptedFiles = encryptionManager.decrypt(encrypted::iterator);

      Map<String, InputFile> files = Maps.newHashMapWithExpectedSize(fileTasks.size());
      decryptedFiles.forEach(decrypted -> files.putIfAbsent(decrypted.location(), decrypted));
      this.inputFiles = ImmutableMap.copyOf(files);
      this.currentIterator = CloseableIterator.empty();
      this.expectedSchema = expectedSchema;
      this.nameMapping = nameMapping;
      this.caseSensitive = caseSensitive;
      this.batchSize = batchSize;
      this.reuseContainers = reuseContainers;
    }

    @Override
    /**
     * 是否还有下一个批次。
     *
     * <p>逻辑：循环判断当前子迭代器是否有数据，若无则关闭它并打开下一个文件任务； 所有任务耗尽则返回 false。发生异常时记录文件位置并抛出。
     *
     * @return 是否还有批次
     */
    public boolean hasNext() {
      try {
        while (true) {
          if (currentIterator.hasNext()) {
            return true;
          } else if (fileItr.hasNext()) {
            this.currentIterator.close();
            this.currentTask = fileItr.next();
            this.currentIterator = open(currentTask);
          } else {
            this.currentIterator.close();
            return false;
          }
        }
      } catch (IOException | RuntimeException e) {
        if (currentTask != null && !currentTask.isDataTask()) {
          LOG.error("Error reading file: {}", getInputFile(currentTask).location(), e);
        }
        ExceptionUtil.castAndThrow(e, RuntimeException.class);
        return false;
      }
    }

    @Override
    /**
     * 返回下一个批次。
     *
     * @return 下一个 {@link ColumnarBatch}
     * @throws NoSuchElementException 若没有更多批次
     */
    public ColumnarBatch next() {
      if (hasNext()) {
        return currentIterator.next();
      } else {
        throw new NoSuchElementException();
      }
    }

    /**
     * 打开单个文件任务并返回其批次迭代器。
     *
     * <p>逻辑：仅支持 Parquet 格式；使用 {@link Parquet#read} 构建读取器，设置投影、分片、 批大小、过滤谓词、大小写敏感等，并通过 {@link
     * #buildReader} 创建列式批读取器函数。
     *
     * @param task 文件扫描任务
     * @return 该文件的列式批次迭代器
     * @throws UnsupportedOperationException 若文件格式非 Parquet
     */
    CloseableIterator<ColumnarBatch> open(FileScanTask task) {
      CloseableIterable<ColumnarBatch> iter;
      InputFile location = getInputFile(task);
      Preconditions.checkNotNull(location, "Could not find InputFile associated with FileScanTask");
      if (task.file().format() == FileFormat.PARQUET) {
        Parquet.ReadBuilder builder =
            Parquet.read(location)
                .project(expectedSchema)
                .split(task.start(), task.length())
                .createBatchedReaderFunc(
                    fileSchema ->
                        buildReader(
                            expectedSchema,
                            fileSchema, /* setArrowValidityVector */
                            NullCheckingForGet.NULL_CHECKING_ENABLED))
                .recordsPerBatch(batchSize)
                .filter(task.residual())
                .caseSensitive(caseSensitive);

        if (reuseContainers) {
          builder.reuseContainers();
        }
        if (nameMapping != null) {
          builder.withNameMapping(NameMappingParser.fromJson(nameMapping));
        }

        iter = builder.build();
      } else {
        throw new UnsupportedOperationException(
            "Format: " + task.file().format() + " not supported for batched reads");
      }
      return iter.iterator();
    }

    @Override
    /**
     * 关闭当前子迭代器并排空剩余任务迭代器，确保资源释放。
     *
     * @throws IOException 关闭时发生 IO 异常
     */
    public void close() throws IOException {
      // close the current iterator
      this.currentIterator.close();

      // exhaust the task iterator
      while (fileItr.hasNext()) {
        fileItr.next();
      }
    }

    /**
     * 根据文件任务获取已解密的 {@link InputFile}。
     *
     * @param task 文件扫描任务
     * @return 解密后的输入文件
     */
    private InputFile getInputFile(FileScanTask task) {
      Preconditions.checkArgument(!task.isDataTask(), "Invalid task type");
      return inputFiles.get(task.file().path().toString());
    }

    /**
     * 根据预期 schema 与文件 schema 构建 {@link ArrowBatchReader}。
     *
     * <p>逻辑：通过 {@link TypeWithSchemaVisitor} 访问预期 schema 与 Parquet 文件 schema， 以 {@link
     * VectorizedReaderBuilder} 构造列读取器，最终包装为 {@link ArrowBatchReader}。
     *
     * @param expectedSchema 预期返回数据的 schema
     * @param fileSchema 数据文件的 Parquet schema
     * @param setArrowValidityVector 是否设置 Arrow 向量的有效性（validity）向量
     * @return 构建好的 {@link ArrowBatchReader}
     */
    private static ArrowBatchReader buildReader(
        Schema expectedSchema, MessageType fileSchema, boolean setArrowValidityVector) {
      return (ArrowBatchReader)
          TypeWithSchemaVisitor.visit(
              expectedSchema.asStruct(),
              fileSchema,
              new VectorizedReaderBuilder(
                  expectedSchema,
                  fileSchema,
                  setArrowValidityVector,
                  ImmutableMap.of(),
                  ArrowBatchReader::new));
    }
  }
}
