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
package org.apache.iceberg.orc;

import static org.apache.iceberg.TableProperties.DELETE_ORC_BLOCK_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.DELETE_ORC_COMPRESSION;
import static org.apache.iceberg.TableProperties.DELETE_ORC_COMPRESSION_STRATEGY;
import static org.apache.iceberg.TableProperties.DELETE_ORC_STRIPE_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.DELETE_ORC_WRITE_BATCH_SIZE;
import static org.apache.iceberg.TableProperties.ORC_BLOCK_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.ORC_BLOCK_SIZE_BYTES_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_BLOOM_FILTER_COLUMNS;
import static org.apache.iceberg.TableProperties.ORC_BLOOM_FILTER_COLUMNS_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_BLOOM_FILTER_FPP;
import static org.apache.iceberg.TableProperties.ORC_BLOOM_FILTER_FPP_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION_STRATEGY;
import static org.apache.iceberg.TableProperties.ORC_COMPRESSION_STRATEGY_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_STRIPE_SIZE_BYTES;
import static org.apache.iceberg.TableProperties.ORC_STRIPE_SIZE_BYTES_DEFAULT;
import static org.apache.iceberg.TableProperties.ORC_WRITE_BATCH_SIZE;
import static org.apache.iceberg.TableProperties.ORC_WRITE_BATCH_SIZE_DEFAULT;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableProperties;
import org.apache.iceberg.data.orc.GenericOrcWriter;
import org.apache.iceberg.data.orc.GenericOrcWriters;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.hadoop.HadoopOutputFile;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ArrayUtil;
import org.apache.iceberg.util.PropertyUtil;
import org.apache.orc.CompressionKind;
import org.apache.orc.OrcConf;
import org.apache.orc.OrcFile;
import org.apache.orc.OrcFile.CompressionStrategy;
import org.apache.orc.OrcFile.ReaderOptions;
import org.apache.orc.Reader;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * ORC 文件格式的读写入口与 Builder 工厂。
 *
 * <p>所属模块：iceberg-orc（模块入口类）。本类是 Iceberg 与 Apache ORC 之间的核心桥接层， 提供 ORC 文件的创建、写入、读取、删除文件写入等 Builder
 * API。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link WriteBuilder}：构建通用 ORC {@link FileAppender}，配置压缩/stripe/block/bloom filter 等。
 *   <li>{@link DataWriteBuilder}：构建 {@link DataWriter}，写入数据文件（含分区/排序/加密元数据）。
 *   <li>{@link DeleteWriteBuilder}：构建 {@link EqualityDeleteWriter} 或 {@link PositionDeleteWriter}。
 *   <li>{@link ReadBuilder}：构建 {@link OrcIterable}，配置投影/过滤/split/NameMapping 等。
 *   <li>{@link #newFileReader}/{@link #newFileWriter}：底层 ORC Reader/Writer 创建，适配 Iceberg
 *       InputFile/OutputFile。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Builder 模式：各配置项通过链式调用设置，build() 时校验并组装。
 *   <li>Context 内部类：统一管理 ORC 写入参数（stripe/block/compression/bloom filter）， dataContext 与
 *       deleteContext 分别提供数据文件和删除文件的默认值覆盖。
 *   <li>属性优先级：Iceberg 自定义属性（ORC_*）优先于 ORC 原生属性（OrcConf.*），通过 PropertyUtil 链式回退。
 *   <li>FileIO 适配：非 HadoopInputFile 时用 {@link FileIOFSUtil} 包装为 Hadoop FileSystem。
 * </ul>
 *
 * <p>上下游关系：被 iceberg-core 的 {@code GenericFileAppenderFactory}、{@code FileReaderFactory} 等调用；内部依赖
 * {@link ORCSchemaUtil}、{@link OrcFileAppender}、{@link OrcIterable}、{@link OrcMetrics}。
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class ORC {

  /** @deprecated use {@link TableProperties#ORC_WRITE_BATCH_SIZE} instead */
  @Deprecated private static final String VECTOR_ROW_BATCH_SIZE = "iceberg.orc.vectorbatch.size";

  private ORC() {}

  /**
   * 创建 ORC 写入 Builder。
   *
   * @param file 目标输出文件
   * @return {@link WriteBuilder} 实例
   */
  public static WriteBuilder write(OutputFile file) {
    return new WriteBuilder(file);
  }

  /**
   * ORC 写入参数构建器：配置 schema、压缩、stripe/block 大小、bloom filter、写入函数等， build() 返回 {@link FileAppender}。
   *
   * <p>设计意图：同时服务数据写入和删除写入（通过 createContextFunc 切换 Context）， 删除写入由 {@link DeleteWriteBuilder} 委托本类。
   */
  public static class WriteBuilder {
    private final OutputFile file;
    private final Configuration conf;
    private Schema schema = null;
    private BiFunction<Schema, TypeDescription, OrcRowWriter<?>> createWriterFunc;
    private Map<String, byte[]> metadata = Maps.newHashMap();
    private MetricsConfig metricsConfig;
    private Function<Map<String, String>, Context> createContextFunc = Context::dataContext;
    private final Map<String, String> config = Maps.newLinkedHashMap();
    private boolean overwrite = false;

    private WriteBuilder(OutputFile file) {
      this.file = file;
      if (file instanceof HadoopOutputFile) {
        this.conf = new Configuration(((HadoopOutputFile) file).getConf());
      } else {
        this.conf = new Configuration();
      }
    }

    public WriteBuilder forTable(Table table) {
      schema(table.schema());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    public WriteBuilder metadata(String property, String value) {
      metadata.put(property, value.getBytes(StandardCharsets.UTF_8));
      return this;
    }

    public WriteBuilder set(String property, String value) {
      config.put(property, value);
      return this;
    }

    public WriteBuilder createWriterFunc(
        BiFunction<Schema, TypeDescription, OrcRowWriter<?>> writerFunction) {
      this.createWriterFunc = writerFunction;
      return this;
    }

    public WriteBuilder setAll(Map<String, String> properties) {
      config.putAll(properties);
      return this;
    }

    public WriteBuilder schema(Schema newSchema) {
      this.schema = newSchema;
      return this;
    }

    public WriteBuilder overwrite() {
      return overwrite(true);
    }

    public WriteBuilder overwrite(boolean enabled) {
      this.overwrite = enabled;
      return this;
    }

    public WriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      this.metricsConfig = newMetricsConfig;
      return this;
    }

    // supposed to always be a private method used strictly by data and delete write builders
    private WriteBuilder createContextFunc(
        Function<Map<String, String>, Context> newCreateContextFunc) {
      this.createContextFunc = newCreateContextFunc;
      return this;
    }

    /**
     * 构建 ORC FileAppender。
     *
     * <p>逻辑：把 config 写入 Hadoop Configuration；处理旧属性兼容（VECTOR_ROW_BATCH_SIZE）； 通过 createContextFunc
     * 获取 Context（data 或 delete）；把 Iceberg 参数映射到 OrcConf； 最终构造 {@link OrcFileAppender}。
     *
     * @param <D> 行数据类型
     * @return {@link FileAppender} 实例
     */
    public <D> FileAppender<D> build() {
      Preconditions.checkNotNull(schema, "Schema is required");

      for (Map.Entry<String, String> entry : config.entrySet()) {
        this.conf.set(entry.getKey(), entry.getValue());
      }

      // for compatibility
      if (conf.get(VECTOR_ROW_BATCH_SIZE) != null && config.get(ORC_WRITE_BATCH_SIZE) == null) {
        config.put(ORC_WRITE_BATCH_SIZE, conf.get(VECTOR_ROW_BATCH_SIZE));
      }

      // Map Iceberg properties to pass down to the ORC writer
      Context context = createContextFunc.apply(config);

      OrcConf.STRIPE_SIZE.setLong(conf, context.stripeSize());
      OrcConf.BLOCK_SIZE.setLong(conf, context.blockSize());
      OrcConf.COMPRESS.setString(conf, context.compressionKind().name());
      OrcConf.COMPRESSION_STRATEGY.setString(conf, context.compressionStrategy().name());
      OrcConf.OVERWRITE_OUTPUT_FILE.setBoolean(conf, overwrite);
      OrcConf.BLOOM_FILTER_COLUMNS.setString(conf, context.bloomFilterColumns());
      OrcConf.BLOOM_FILTER_FPP.setDouble(conf, context.bloomFilterFpp());

      return new OrcFileAppender<>(
          schema,
          this.file,
          createWriterFunc,
          conf,
          metadata,
          context.vectorizedRowBatchSize(),
          metricsConfig);
    }

    /**
     * ORC 写入参数上下文：封装 stripe/block 大小、批大小、压缩、bloom filter 等配置。
     *
     * <p>设计意图：dataContext 从表属性读取数据文件配置；deleteContext 在 dataContext
     * 基础上覆盖删除文件专属属性（DELETE_ORC_*），实现配置继承与覆盖。
     */
    private static class Context {
      private final long stripeSize;
      private final long blockSize;
      private final int vectorizedRowBatchSize;
      private final CompressionKind compressionKind;
      private final CompressionStrategy compressionStrategy;
      private final String bloomFilterColumns;
      private final double bloomFilterFpp;

      public long stripeSize() {
        return stripeSize;
      }

      public long blockSize() {
        return blockSize;
      }

      public int vectorizedRowBatchSize() {
        return vectorizedRowBatchSize;
      }

      public CompressionKind compressionKind() {
        return compressionKind;
      }

      public CompressionStrategy compressionStrategy() {
        return compressionStrategy;
      }

      public String bloomFilterColumns() {
        return bloomFilterColumns;
      }

      public double bloomFilterFpp() {
        return bloomFilterFpp;
      }

      private Context(
          long stripeSize,
          long blockSize,
          int vectorizedRowBatchSize,
          CompressionKind compressionKind,
          CompressionStrategy compressionStrategy,
          String bloomFilterColumns,
          double bloomFilterFpp) {
        this.stripeSize = stripeSize;
        this.blockSize = blockSize;
        this.vectorizedRowBatchSize = vectorizedRowBatchSize;
        this.compressionKind = compressionKind;
        this.compressionStrategy = compressionStrategy;
        this.bloomFilterColumns = bloomFilterColumns;
        this.bloomFilterFpp = bloomFilterFpp;
      }

      /**
       * 从配置构建数据文件 Context。
       *
       * <p>逻辑：逐项读取 stripeSize/blockSize/vectorizedRowBatchSize/compressionKind/
       * compressionStrategy/bloomFilterColumns/bloomFilterFpp，Iceberg 属性优先于 OrcConf 属性， 每项校验合法性后构造
       * Context。
       */
      static Context dataContext(Map<String, String> config) {
        long stripeSize =
            PropertyUtil.propertyAsLong(
                config, OrcConf.STRIPE_SIZE.getAttribute(), ORC_STRIPE_SIZE_BYTES_DEFAULT);
        stripeSize = PropertyUtil.propertyAsLong(config, ORC_STRIPE_SIZE_BYTES, stripeSize);
        Preconditions.checkArgument(stripeSize > 0, "Stripe size must be > 0");

        long blockSize =
            PropertyUtil.propertyAsLong(
                config, OrcConf.BLOCK_SIZE.getAttribute(), ORC_BLOCK_SIZE_BYTES_DEFAULT);
        blockSize = PropertyUtil.propertyAsLong(config, ORC_BLOCK_SIZE_BYTES, blockSize);
        Preconditions.checkArgument(blockSize > 0, "Block size must be > 0");

        int vectorizedRowBatchSize =
            PropertyUtil.propertyAsInt(config, ORC_WRITE_BATCH_SIZE, ORC_WRITE_BATCH_SIZE_DEFAULT);
        Preconditions.checkArgument(
            vectorizedRowBatchSize > 0, "VectorizedRow batch size must be > 0");

        String codecAsString =
            PropertyUtil.propertyAsString(
                config, OrcConf.COMPRESS.getAttribute(), ORC_COMPRESSION_DEFAULT);
        codecAsString = PropertyUtil.propertyAsString(config, ORC_COMPRESSION, codecAsString);
        CompressionKind compressionKind = toCompressionKind(codecAsString);

        String strategyAsString =
            PropertyUtil.propertyAsString(
                config,
                OrcConf.COMPRESSION_STRATEGY.getAttribute(),
                ORC_COMPRESSION_STRATEGY_DEFAULT);
        strategyAsString =
            PropertyUtil.propertyAsString(config, ORC_COMPRESSION_STRATEGY, strategyAsString);
        CompressionStrategy compressionStrategy = toCompressionStrategy(strategyAsString);

        String bloomFilterColumns =
            PropertyUtil.propertyAsString(
                config,
                OrcConf.BLOOM_FILTER_COLUMNS.getAttribute(),
                ORC_BLOOM_FILTER_COLUMNS_DEFAULT);
        bloomFilterColumns =
            PropertyUtil.propertyAsString(config, ORC_BLOOM_FILTER_COLUMNS, bloomFilterColumns);

        double bloomFilterFpp =
            PropertyUtil.propertyAsDouble(
                config, OrcConf.BLOOM_FILTER_FPP.getAttribute(), ORC_BLOOM_FILTER_FPP_DEFAULT);
        bloomFilterFpp =
            PropertyUtil.propertyAsDouble(config, ORC_BLOOM_FILTER_FPP, bloomFilterFpp);
        Preconditions.checkArgument(
            bloomFilterFpp > 0.0 && bloomFilterFpp < 1.0,
            "Bloom filter fpp must be > 0.0 and < 1.0");

        return new Context(
            stripeSize,
            blockSize,
            vectorizedRowBatchSize,
            compressionKind,
            compressionStrategy,
            bloomFilterColumns,
            bloomFilterFpp);
      }

      /**
       * 从配置构建删除文件 Context。
       *
       * <p>逻辑：先取 dataContext 作为基线，再用 DELETE_ORC_* 系列属性覆盖对应项， 未设置则继承数据文件配置。
       */
      static Context deleteContext(Map<String, String> config) {
        Context dataContext = dataContext(config);

        long stripeSize =
            PropertyUtil.propertyAsLong(
                config, DELETE_ORC_STRIPE_SIZE_BYTES, dataContext.stripeSize());

        long blockSize =
            PropertyUtil.propertyAsLong(
                config, DELETE_ORC_BLOCK_SIZE_BYTES, dataContext.blockSize());

        int vectorizedRowBatchSize =
            PropertyUtil.propertyAsInt(
                config, DELETE_ORC_WRITE_BATCH_SIZE, dataContext.vectorizedRowBatchSize());

        String codecAsString = config.get(DELETE_ORC_COMPRESSION);
        CompressionKind compressionKind =
            codecAsString != null
                ? toCompressionKind(codecAsString)
                : dataContext.compressionKind();

        String strategyAsString = config.get(DELETE_ORC_COMPRESSION_STRATEGY);
        CompressionStrategy compressionStrategy =
            strategyAsString != null
                ? toCompressionStrategy(strategyAsString)
                : dataContext.compressionStrategy();

        return new Context(
            stripeSize,
            blockSize,
            vectorizedRowBatchSize,
            compressionKind,
            compressionStrategy,
            dataContext.bloomFilterColumns(),
            dataContext.bloomFilterFpp());
      }

      private static CompressionKind toCompressionKind(String codecAsString) {
        try {
          return CompressionKind.valueOf(codecAsString.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Unsupported compression codec: " + codecAsString);
        }
      }

      private static CompressionStrategy toCompressionStrategy(String strategyAsString) {
        try {
          return CompressionStrategy.valueOf(strategyAsString.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "Unsupported compression strategy: " + strategyAsString);
        }
      }
    }
  }

  /** 创建数据文件写入 Builder。 */
  public static DataWriteBuilder writeData(OutputFile file) {
    return new DataWriteBuilder(file);
  }

  /** 数据文件写入 Builder：在 WriteBuilder 基础上增加分区/排序/加密元数据， build() 返回 {@link DataWriter}。 */
  public static class DataWriteBuilder {
    private final WriteBuilder appenderBuilder;
    private final String location;
    private PartitionSpec spec = null;
    private StructLike partition = null;
    private EncryptionKeyMetadata keyMetadata = null;
    private SortOrder sortOrder = null;

    private DataWriteBuilder(OutputFile file) {
      this.appenderBuilder = write(file);
      this.location = file.location();
    }

    public DataWriteBuilder forTable(Table table) {
      schema(table.schema());
      withSpec(table.spec());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    public DataWriteBuilder schema(Schema newSchema) {
      appenderBuilder.schema(newSchema);
      return this;
    }

    public DataWriteBuilder set(String property, String value) {
      appenderBuilder.set(property, value);
      return this;
    }

    public DataWriteBuilder setAll(Map<String, String> properties) {
      appenderBuilder.setAll(properties);
      return this;
    }

    public DataWriteBuilder meta(String property, String value) {
      appenderBuilder.metadata(property, value);
      return this;
    }

    public DataWriteBuilder overwrite() {
      return overwrite(true);
    }

    public DataWriteBuilder overwrite(boolean enabled) {
      appenderBuilder.overwrite(enabled);
      return this;
    }

    public DataWriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      appenderBuilder.metricsConfig(newMetricsConfig);
      return this;
    }

    public DataWriteBuilder createWriterFunc(
        BiFunction<Schema, TypeDescription, OrcRowWriter<?>> writerFunction) {
      appenderBuilder.createWriterFunc(writerFunction);
      return this;
    }

    public DataWriteBuilder withSpec(PartitionSpec newSpec) {
      this.spec = newSpec;
      return this;
    }

    public DataWriteBuilder withPartition(StructLike newPartition) {
      this.partition = newPartition;
      return this;
    }

    public DataWriteBuilder withKeyMetadata(EncryptionKeyMetadata metadata) {
      this.keyMetadata = metadata;
      return this;
    }

    public DataWriteBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder;
      return this;
    }

    /**
     * 构建 {@link DataWriter}。
     *
     * <p>逻辑：校验 spec 和 partition；委托 WriteBuilder 构建 FileAppender； 组装为
     * DataWriter（含格式/位置/分区/排序/加密元数据）。
     */
    public <T> DataWriter<T> build() {
      Preconditions.checkArgument(spec != null, "Cannot create data writer without spec");
      Preconditions.checkArgument(
          spec.isUnpartitioned() || partition != null,
          "Partition must not be null when creating data writer for partitioned spec");

      FileAppender<T> fileAppender = appenderBuilder.build();
      return new DataWriter<>(
          fileAppender, FileFormat.ORC, location, spec, partition, keyMetadata, sortOrder);
    }
  }

  /** 创建删除文件写入 Builder。 */
  public static DeleteWriteBuilder writeDeletes(OutputFile file) {
    return new DeleteWriteBuilder(file);
  }

  /**
   * 删除文件写入 Builder：支持构建等值删除（{@link EqualityDeleteWriter}）和位置删除 （{@link PositionDeleteWriter}）两种写入器。
   *
   * <p>设计意图：根据 equalityFieldIds 是否设置决定构建哪种删除写入器； 位置删除可选择是否写入被删除行的数据（rowSchema + createWriterFunc
   * 同时设置时写入）。
   */
  public static class DeleteWriteBuilder {
    private final WriteBuilder appenderBuilder;
    private final String location;
    private BiFunction<Schema, TypeDescription, OrcRowWriter<?>> createWriterFunc = null;
    private Schema rowSchema = null;
    private PartitionSpec spec = null;
    private StructLike partition = null;
    private EncryptionKeyMetadata keyMetadata = null;
    private int[] equalityFieldIds = null;
    private SortOrder sortOrder;
    private Function<CharSequence, ?> pathTransformFunc = Function.identity();

    private DeleteWriteBuilder(OutputFile file) {
      this.appenderBuilder = write(file);
      this.location = file.location();
    }

    public DeleteWriteBuilder forTable(Table table) {
      rowSchema(table.schema());
      withSpec(table.spec());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    public DeleteWriteBuilder set(String property, String value) {
      appenderBuilder.set(property, value);
      return this;
    }

    public DeleteWriteBuilder setAll(Map<String, String> properties) {
      appenderBuilder.setAll(properties);
      return this;
    }

    public DeleteWriteBuilder meta(String property, String value) {
      appenderBuilder.metadata(property, value);
      return this;
    }

    public DeleteWriteBuilder overwrite() {
      return overwrite(true);
    }

    public DeleteWriteBuilder overwrite(boolean enabled) {
      appenderBuilder.overwrite(enabled);
      return this;
    }

    public DeleteWriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      appenderBuilder.metricsConfig(newMetricsConfig);
      return this;
    }

    public DeleteWriteBuilder createWriterFunc(
        BiFunction<Schema, TypeDescription, OrcRowWriter<?>> newWriterFunc) {
      this.createWriterFunc = newWriterFunc;
      return this;
    }

    public DeleteWriteBuilder rowSchema(Schema newSchema) {
      this.rowSchema = newSchema;
      return this;
    }

    public DeleteWriteBuilder withSpec(PartitionSpec newSpec) {
      this.spec = newSpec;
      return this;
    }

    public DeleteWriteBuilder withPartition(StructLike key) {
      this.partition = key;
      return this;
    }

    public DeleteWriteBuilder withKeyMetadata(EncryptionKeyMetadata metadata) {
      this.keyMetadata = metadata;
      return this;
    }

    public DeleteWriteBuilder equalityFieldIds(List<Integer> fieldIds) {
      this.equalityFieldIds = ArrayUtil.toIntArray(fieldIds);
      return this;
    }

    public DeleteWriteBuilder equalityFieldIds(int... fieldIds) {
      this.equalityFieldIds = fieldIds;
      return this;
    }

    public DeleteWriteBuilder transformPaths(Function<CharSequence, ?> newPathTransformFunc) {
      this.pathTransformFunc = newPathTransformFunc;
      return this;
    }

    public DeleteWriteBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder;
      return this;
    }

    /**
     * 构建等值删除写入器。
     *
     * <p>逻辑：校验 rowSchema/equalityFieldIds/createWriterFunc/spec/partition； 写入 delete-type=equality
     * 和 delete-field-ids 元数据； 设置 appender schema 为 rowSchema 并切换 Context 为 deleteContext； 组装为
     * EqualityDeleteWriter。
     */
    public <T> EqualityDeleteWriter<T> buildEqualityWriter() {
      Preconditions.checkState(
          rowSchema != null, "Cannot create equality delete file without a schema");
      Preconditions.checkState(
          equalityFieldIds != null, "Cannot create equality delete file without delete field ids");
      Preconditions.checkState(
          createWriterFunc != null,
          "Cannot create equality delete file unless createWriterFunc is set");
      Preconditions.checkArgument(
          spec != null, "Spec must not be null when creating equality delete writer");
      Preconditions.checkArgument(
          spec.isUnpartitioned() || partition != null,
          "Partition must not be null for partitioned writes");

      meta("delete-type", "equality");
      meta(
          "delete-field-ids",
          IntStream.of(equalityFieldIds)
              .mapToObj(Objects::toString)
              .collect(Collectors.joining(", ")));

      // the appender uses the row schema without extra columns
      appenderBuilder.schema(rowSchema);
      appenderBuilder.createWriterFunc(createWriterFunc);
      appenderBuilder.createContextFunc(WriteBuilder.Context::deleteContext);

      return new EqualityDeleteWriter<>(
          appenderBuilder.build(),
          FileFormat.ORC,
          location,
          spec,
          partition,
          keyMetadata,
          sortOrder,
          equalityFieldIds);
    }

    /**
     * 构建位置删除写入器。
     *
     * <p>逻辑：校验无 equalityFieldIds；写 delete-type=position 元数据； 若 rowSchema+createWriterFunc
     * 同时设置，构造含被删行数据的 schema（path+pos+row）， 用 {@link GenericOrcWriters#positionDelete} 包装；否则仅写
     * path+pos 两列。 切换 Context 为 deleteContext 后组装为 PositionDeleteWriter。
     */
    public <T> PositionDeleteWriter<T> buildPositionWriter() {
      Preconditions.checkState(
          equalityFieldIds == null, "Cannot create position delete file using delete field ids");
      Preconditions.checkArgument(
          spec != null, "Spec must not be null when creating position delete writer");
      Preconditions.checkArgument(
          spec.isUnpartitioned() || partition != null,
          "Partition must not be null for partitioned writes");
      Preconditions.checkArgument(
          rowSchema == null || createWriterFunc != null,
          "Create function should be provided if we write row data");

      meta("delete-type", "position");

      if (rowSchema != null && createWriterFunc != null) {
        Schema deleteSchema = DeleteSchemaUtil.posDeleteSchema(rowSchema);
        appenderBuilder.schema(deleteSchema);

        appenderBuilder.createWriterFunc(
            (schema, typeDescription) ->
                GenericOrcWriters.positionDelete(
                    createWriterFunc.apply(deleteSchema, typeDescription), pathTransformFunc));
      } else {
        appenderBuilder.schema(DeleteSchemaUtil.pathPosSchema());

        // We ignore the 'createWriterFunc' and 'rowSchema' even if is provided, since we do not
        // write row data itself
        appenderBuilder.createWriterFunc(
            (schema, typeDescription) ->
                GenericOrcWriters.positionDelete(
                    GenericOrcWriter.buildWriter(schema, typeDescription), Function.identity()));
      }

      appenderBuilder.createContextFunc(WriteBuilder.Context::deleteContext);

      return new PositionDeleteWriter<>(
          appenderBuilder.build(), FileFormat.ORC, location, spec, partition, keyMetadata);
    }
  }

  /**
   * 创建 ORC 读取 Builder。
   *
   * @param file 输入文件
   * @return {@link ReadBuilder} 实例
   */
  public static ReadBuilder read(InputFile file) {
    return new ReadBuilder(file);
  }

  /**
   * ORC 读取参数构建器：配置投影 schema、过滤表达式、split 范围、批大小、NameMapping、 reader/batchReader 函数等，build() 返回
   * {@link CloseableIterable}。
   *
   * <p>设计意图：readerFunc 与 batchedReaderFunc 互斥，分别对应逐行和向量化读取模式。 默认关闭 positional schema
   * evolution（使用基于列名的 schema evolution 做投影）。
   */
  public static class ReadBuilder {
    private final InputFile file;
    private final Configuration conf;
    private Schema schema = null;
    private Long start = null;
    private Long length = null;
    private Expression filter = null;
    private boolean caseSensitive = true;
    private NameMapping nameMapping = null;

    private Function<TypeDescription, OrcRowReader<?>> readerFunc;
    private Function<TypeDescription, OrcBatchReader<?>> batchedReaderFunc;
    private int recordsPerBatch = VectorizedRowBatch.DEFAULT_SIZE;

    private ReadBuilder(InputFile file) {
      Preconditions.checkNotNull(file, "Input file cannot be null");
      this.file = file;
      if (file instanceof HadoopInputFile) {
        this.conf = new Configuration(((HadoopInputFile) file).getConf());
      } else {
        this.conf = new Configuration();
      }

      // We need to turn positional schema evolution off since we use column name based schema
      // evolution for projection
      this.conf.setBoolean(OrcConf.FORCE_POSITIONAL_EVOLUTION.getHiveConfName(), false);
    }

    /**
     * Restricts the read to the given range: [start, start + length).
     *
     * @param newStart the start position for this read
     * @param newLength the length of the range this read should scan
     * @return this builder for method chaining
     */
    public ReadBuilder split(long newStart, long newLength) {
      this.start = newStart;
      this.length = newLength;
      return this;
    }

    public ReadBuilder project(Schema newSchema) {
      this.schema = newSchema;
      return this;
    }

    public ReadBuilder caseSensitive(boolean newCaseSensitive) {
      OrcConf.IS_SCHEMA_EVOLUTION_CASE_SENSITIVE.setBoolean(this.conf, newCaseSensitive);
      this.caseSensitive = newCaseSensitive;
      return this;
    }

    public ReadBuilder config(String property, String value) {
      conf.set(property, value);
      return this;
    }

    public ReadBuilder createReaderFunc(Function<TypeDescription, OrcRowReader<?>> readerFunction) {
      Preconditions.checkArgument(
          this.batchedReaderFunc == null,
          "Reader function cannot be set since the batched version is already set");
      this.readerFunc = readerFunction;
      return this;
    }

    public ReadBuilder filter(Expression newFilter) {
      this.filter = newFilter;
      return this;
    }

    public ReadBuilder createBatchedReaderFunc(
        Function<TypeDescription, OrcBatchReader<?>> batchReaderFunction) {
      Preconditions.checkArgument(
          this.readerFunc == null,
          "Batched reader function cannot be set since the non-batched version is already set");
      this.batchedReaderFunc = batchReaderFunction;
      return this;
    }

    public ReadBuilder recordsPerBatch(int numRecordsPerBatch) {
      this.recordsPerBatch = numRecordsPerBatch;
      return this;
    }

    public ReadBuilder withNameMapping(NameMapping newNameMapping) {
      this.nameMapping = newNameMapping;
      return this;
    }

    /**
     * 构建 {@link OrcIterable} 作为读取结果的可迭代。
     *
     * <p>逻辑：校验 schema 非空后，把所有参数透传给 {@link OrcIterable} 构造。
     */
    public <D> CloseableIterable<D> build() {
      Preconditions.checkNotNull(schema, "Schema is required");
      return new OrcIterable<>(
          file,
          conf,
          schema,
          nameMapping,
          start,
          length,
          readerFunc,
          caseSensitive,
          filter,
          batchedReaderFunc,
          recordsPerBatch);
    }
  }

  /**
   * 创建底层 ORC {@link Reader}。
   *
   * <p>逻辑：构建 ReaderOptions 并启用 UTC 时间戳；若为 HadoopInputFile 则用其 FileSystem， 否则用 {@link
   * FileIOFSUtil.InputFileSystem} 包装并设 maxLength 避免 stat 调用。
   *
   * @param file Iceberg 输入文件
   * @param config Hadoop 配置
   * @return ORC Reader
   * @throws RuntimeIOException 打开文件失败
   */
  static Reader newFileReader(InputFile file, Configuration config) {
    ReaderOptions readerOptions = OrcFile.readerOptions(config).useUTCTimestamp(true);
    if (file instanceof HadoopInputFile) {
      readerOptions.filesystem(((HadoopInputFile) file).getFileSystem());
    } else {
      // In case of any other InputFile we wrap the InputFile with InputFileSystem that only
      // supports the creation of an InputStream. To prevent a file status call to determine the
      // length we supply the length as input
      readerOptions.filesystem(new FileIOFSUtil.InputFileSystem(file)).maxLength(file.getLength());
    }
    try {
      return OrcFile.createReader(new Path(file.location()), readerOptions);
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Failed to open file: %s", file.location());
    }
  }

  /**
   * 创建底层 ORC {@link Writer} 并写入用户元数据。
   *
   * <p>逻辑：若为 HadoopOutputFile 则用其 FileSystem，否则用 {@link FileIOFSUtil.OutputFileSystem} 包装； 调用
   * OrcFile.createWriter 创建 writer；把 metadata 逐项写入 writer 的用户元数据。
   *
   * @param file Iceberg 输出文件
   * @param options ORC WriterOptions
   * @param metadata 用户元数据键值对
   * @return ORC Writer
   * @throws RuntimeIOException 创建文件失败
   */
  static Writer newFileWriter(
      OutputFile file, OrcFile.WriterOptions options, Map<String, byte[]> metadata) {
    if (file instanceof HadoopOutputFile) {
      options.fileSystem(((HadoopOutputFile) file).getFileSystem());
    } else {
      options.fileSystem(new FileIOFSUtil.OutputFileSystem(file));
    }
    final Path locPath = new Path(file.location());
    final Writer writer;

    try {
      writer = OrcFile.createWriter(locPath, options);
    } catch (IOException ioe) {
      throw new RuntimeIOException(ioe, "Can't create file %s", locPath);
    }

    metadata.forEach((key, value) -> writer.addUserMetadata(key, ByteBuffer.wrap(value)));

    return writer;
  }
}
