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
package org.apache.iceberg.avro;

import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION;
import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION_DEFAULT;
import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION_LEVEL;
import static org.apache.iceberg.TableProperties.AVRO_COMPRESSION_LEVEL_DEFAULT;
import static org.apache.iceberg.TableProperties.DELETE_AVRO_COMPRESSION;
import static org.apache.iceberg.TableProperties.DELETE_AVRO_COMPRESSION_LEVEL;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.generic.GenericData;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.specific.SpecificData;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.Table;
import org.apache.iceberg.deletes.EqualityDeleteWriter;
import org.apache.iceberg.deletes.PositionDelete;
import org.apache.iceberg.deletes.PositionDeleteWriter;
import org.apache.iceberg.encryption.EncryptionKeyMetadata;
import org.apache.iceberg.io.DataWriter;
import org.apache.iceberg.io.DeleteSchemaUtil;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ArrayUtil;

/**
 * Avro 文件读写的统一入口与构建器集合。
 *
 * <p>所属模块：iceberg-core（Iceberg 的核心实现模块，位于 api 之下，提供表格式读写、 元数据管理与文件格式实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 {@link #write(OutputFile)}、{@link #writeData(OutputFile)}、 {@link
 *       #writeDeletes(OutputFile)}、{@link #read(InputFile)} 等工厂方法， 返回对应 Builder，屏蔽 Avro 底层细节。
 *   <li>统一注册 Iceberg 自定义 Avro 逻辑类型（{@link LogicalMap}、{@link UUIDConversion}）， 并装配默认的 {@link
 *       SpecificData} 模型与 Decimal/UUID 转换器。
 *   <li>封装压缩编解码器选择逻辑（UNCOMPRESSED/SNAPPY/GZIP/ZSTD），区分数据文件与删除文件 的压缩配置。
 *   <li>构造位置删除（position delete）、等值删除（equality delete）所需的 {@link MetricsAwareDatumWriter}，并与 Iceberg
 *       的 {@link DataWriter}/ {@link EqualityDeleteWriter}/{@link PositionDeleteWriter} 衔接。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Builder 模式集中管理 Avro 写入参数（schema、压缩、metrics、元数据等）， 避免长参数列表，并支持链式调用。
 *   <li>把数据文件与删除文件的差异收敛到不同 Builder（DataWriteBuilder / DeleteWriteBuilder），它们内部复用同一个 {@link
 *       WriteBuilder}，复用底层 {@link AvroFileAppender} 实现。
 *   <li>默认模型使用 {@link SpecificData} 而非 {@code GenericData}，以便支持 Avro 生成的特定类，提升与引擎内部数据模型的兼容性。
 * </ul>
 *
 * <p>上下游关系：上游被 core 内部表写入流程（{@code FileAppenderFactory}、Spark/Flink 等引擎的任务写入模块）调用；下游依赖 Avro
 * 库（{@code DataFileWriter}、 {@code DatumReader/Writer}）与 Iceberg 自有的 {@link AvroSchemaUtil}、 {@link
 * AvroFileAppender}、{@link AvroIterable}、{@link ProjectionDatumReader} 等。
 */
public class Avro {
  private Avro() {}

  /** Avro 支持的压缩算法枚举，与表属性中的字符串配置一一对应。 */
  private enum Codec {
    UNCOMPRESSED,
    SNAPPY,
    GZIP,
    ZSTD
  }

  private static final int ZSTD_COMPRESSION_LEVEL_DEFAULT = 1;
  private static final int GZIP_COMPRESSION_LEVEL_DEFAULT = 9;

  private static final GenericData DEFAULT_MODEL = new SpecificData();

  static {
    // 注册 Iceberg 自定义逻辑类型 Map（用 array<entry> 表示 map 以保留键值顺序），
    // 并为默认模型添加 Decimal、UUID 两种逻辑类型转换器。
    LogicalTypes.register(LogicalMap.NAME, schema -> LogicalMap.get());
    DEFAULT_MODEL.addLogicalTypeConversion(new Conversions.DecimalConversion());
    DEFAULT_MODEL.addLogicalTypeConversion(new UUIDConversion());
  }

  /**
   * 创建 Avro 文件写入构建器。
   *
   * @param file 目标输出文件
   * @return 用于配置并构建 {@link FileAppender} 的 {@link WriteBuilder}
   */
  public static WriteBuilder write(OutputFile file) {
    return new WriteBuilder(file);
  }

  /**
   * Avro 文件写入构建器：负责收集 schema、压缩、metrics、自定义元数据等参数， 最终构建 {@link AvroFileAppender}。
   *
   * <p>设计意图：将所有写入相关配置项集中在一处，并通过 {@link Context} 内部类解耦 压缩编解码器的解析逻辑，便于数据文件与删除文件分别使用不同的压缩配置策略。
   */
  public static class WriteBuilder {
    private final OutputFile file;
    private final Map<String, String> config = Maps.newHashMap();
    private final Map<String, String> metadata = Maps.newLinkedHashMap();
    private org.apache.iceberg.Schema schema = null;
    private String name = "table";
    private Function<Schema, DatumWriter<?>> createWriterFunc = null;
    private boolean overwrite;
    private MetricsConfig metricsConfig;
    private Function<Map<String, String>, Context> createContextFunc = Context::dataContext;

    private WriteBuilder(OutputFile file) {
      this.file = file;
    }

    /**
     * 一次性应用表的 schema、属性与 metrics 配置，便于针对某张表写入时快速配置。
     *
     * @param table 目标表
     * @return this，便于链式调用
     */
    public WriteBuilder forTable(Table table) {
      schema(table.schema());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    /**
     * 设置写入的数据 schema。
     *
     * @param newSchema Iceberg schema
     * @return this，便于链式调用
     */
    public WriteBuilder schema(org.apache.iceberg.Schema newSchema) {
      this.schema = newSchema;
      return this;
    }

    /**
     * 设置 Avro 记录名（默认 "table"），影响生成的 Avro schema 的 record name。
     *
     * @param newName 记录名
     * @return this，便于链式调用
     */
    public WriteBuilder named(String newName) {
      this.name = newName;
      return this;
    }

    /**
     * 设置自定义的 DatumWriter 创建函数；若不设置，build 时默认使用 {@link GenericAvroWriter}。
     *
     * @param writerFunction 根据 Avro schema 产生 DatumWriter 的函数
     * @return this，便于链式调用
     */
    public WriteBuilder createWriterFunc(Function<Schema, DatumWriter<?>> writerFunction) {
      this.createWriterFunc = writerFunction;
      return this;
    }

    /**
     * 设置单个键值对形式的表属性（用于解析压缩等配置）。
     *
     * @param property 属性名
     * @param value 属性值
     * @return this，便于链式调用
     */
    public WriteBuilder set(String property, String value) {
      config.put(property, value);
      return this;
    }

    /**
     * 批量设置表属性。
     *
     * @param properties 属性集合
     * @return this，便于链式调用
     */
    public WriteBuilder setAll(Map<String, String> properties) {
      config.putAll(properties);
      return this;
    }

    /**
     * 添加一条 Avro 文件元数据（写入文件头 key-value）。
     *
     * @param property 元数据键
     * @param value 元数据值
     * @return this，便于链式调用
     */
    public WriteBuilder meta(String property, String value) {
      metadata.put(property, value);
      return this;
    }

    /**
     * 批量添加 Avro 文件元数据。
     *
     * @param properties 元数据集合
     * @return this，便于链式调用
     */
    public WriteBuilder meta(Map<String, String> properties) {
      metadata.putAll(properties);
      return this;
    }

    /**
     * 设置 metrics 收集配置，控制写入时是否统计各列上下界/Null 计数等。
     *
     * @param newMetricsConfig metrics 配置
     * @return this，便于链式调用
     */
    public WriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      this.metricsConfig = newMetricsConfig;
      return this;
    }

    /** 启用覆写模式（已存在文件会被覆盖）。 */
    public WriteBuilder overwrite() {
      return overwrite(true);
    }

    /**
     * 显式开启/关闭覆写模式。
     *
     * @param enabled 是否覆写
     * @return this，便于链式调用
     */
    public WriteBuilder overwrite(boolean enabled) {
      this.overwrite = enabled;
      return this;
    }

    // supposed to always be a private method used strictly by data and delete write builders
    /**
     * 设置 Context 创建函数，用于切换数据文件与删除文件的压缩解析策略。 仅由 {@link DataWriteBuilder} 与 {@link
     * DeleteWriteBuilder} 内部调用。
     *
     * @param newCreateContextFunc 根据配置生成 {@link Context} 的函数
     * @return this，便于链式调用
     */
    private WriteBuilder createContextFunc(
        Function<Map<String, String>, Context> newCreateContextFunc) {
      this.createContextFunc = newCreateContextFunc;
      return this;
    }

    /**
     * 构建 {@link AvroFileAppender}。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验 schema 与 name 非空。
     *   <li>若未提供 createWriterFunc，则默认使用 {@link GenericAvroWriter}。
     *   <li>把 Iceberg schema 序列化为 JSON 写入 Avro 文件元数据 "iceberg.schema"， 以便读取时重建 schema。
     *   <li>根据配置解析压缩编解码器，构造并返回 {@link AvroFileAppender}。
     * </ol>
     *
     * @param <D> 写入数据类型
     * @return 已就绪的 {@link FileAppender}
     * @throws IOException 若创建输出流失败
     */
    public <D> FileAppender<D> build() throws IOException {
      Preconditions.checkNotNull(schema, "Schema is required");
      Preconditions.checkNotNull(name, "Table name is required and cannot be null");

      Function<Schema, DatumWriter<?>> writerFunc;
      if (createWriterFunc != null) {
        writerFunc = createWriterFunc;
      } else {
        writerFunc = GenericAvroWriter::new;
      }

      // add the Iceberg schema to keyValueMetadata
      meta("iceberg.schema", SchemaParser.toJson(schema));

      Context context = createContextFunc.apply(config);
      CodecFactory codec = context.codec();

      return new AvroFileAppender<>(
          schema,
          AvroSchemaUtil.convert(schema, name),
          file,
          writerFunc,
          codec,
          metadata,
          metricsConfig,
          overwrite);
    }

    /**
     * 写入上下文：封装解析后的压缩编解码器。
     *
     * <p>设计意图：通过 {@link #dataContext(Map)} 与 {@link #deleteContext(Map)} 两个工厂方法
     * 实现数据文件与删除文件的差异化压缩配置——删除文件在未显式配置时回退到数据文件配置。
     */
    private static class Context {
      private final CodecFactory codec;

      private Context(CodecFactory codec) {
        this.codec = codec;
      }

      /**
       * 根据数据文件压缩配置创建上下文。
       *
       * @param config 表属性
       * @return 包含数据文件压缩编解码器的 {@link Context}
       */
      static Context dataContext(Map<String, String> config) {
        String codecAsString = config.getOrDefault(AVRO_COMPRESSION, AVRO_COMPRESSION_DEFAULT);
        String compressionLevel =
            config.getOrDefault(AVRO_COMPRESSION_LEVEL, AVRO_COMPRESSION_LEVEL_DEFAULT);
        CodecFactory codec = toCodec(codecAsString, compressionLevel);

        return new Context(codec);
      }

      /**
       * 根据删除文件压缩配置创建上下文。
       *
       * <p>逻辑：先按数据文件配置生成基线上下文；若表属性中显式设置了删除文件压缩算法 （{@code
       * DELETE_AVRO_COMPRESSION}），则覆盖编解码器，否则沿用数据文件配置。
       *
       * @param config 表属性
       * @return 包含删除文件压缩编解码器的 {@link Context}
       */
      static Context deleteContext(Map<String, String> config) {
        // default delete config using data config
        Context dataContext = dataContext(config);

        String codecAsString = config.get(DELETE_AVRO_COMPRESSION);
        String compressionLevel =
            config.getOrDefault(DELETE_AVRO_COMPRESSION_LEVEL, AVRO_COMPRESSION_LEVEL_DEFAULT);
        CodecFactory codec =
            codecAsString != null ? toCodec(codecAsString, compressionLevel) : dataContext.codec();

        return new Context(codec);
      }

      /**
       * 将字符串压缩算法名与压缩级别解析为 Avro {@link CodecFactory}。
       *
       * <p>逻辑：将 codec 名转大写后匹配枚举；ZSTD/GZIP 还需将压缩级别转为整数， 缺省时分别使用 {@link
       * #ZSTD_COMPRESSION_LEVEL_DEFAULT} 和 {@link #GZIP_COMPRESSION_LEVEL_DEFAULT}。
       *
       * @param codecAsString 压缩算法名（uncompressed/snappy/gzip/zstd）
       * @param compressionLevel 压缩级别字符串
       * @return 对应的 {@link CodecFactory}
       * @throws IllegalArgumentException 若算法名不被支持
       */
      private static CodecFactory toCodec(String codecAsString, String compressionLevel) {
        CodecFactory codecFactory;
        try {
          switch (Codec.valueOf(codecAsString.toUpperCase(Locale.ENGLISH))) {
            case UNCOMPRESSED:
              codecFactory = CodecFactory.nullCodec();
              break;
            case SNAPPY:
              codecFactory = CodecFactory.snappyCodec();
              break;
            case ZSTD:
              codecFactory =
                  CodecFactory.zstandardCodec(
                      compressionLevelAsInt(compressionLevel, ZSTD_COMPRESSION_LEVEL_DEFAULT));
              break;
            case GZIP:
              codecFactory =
                  CodecFactory.deflateCodec(
                      compressionLevelAsInt(compressionLevel, GZIP_COMPRESSION_LEVEL_DEFAULT));
              break;
            default:
              throw new IllegalArgumentException("Unsupported compression codec: " + codecAsString);
          }
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Unsupported compression codec: " + codecAsString);
        }
        return codecFactory;
      }

      /**
       * 将字符串压缩级别转为整数；为 null 时返回默认级别。
       *
       * @param tableCompressionLevel 表中配置的压缩级别字符串
       * @param defaultCompressionLevel 缺省级别
       * @return 解析后的整数压缩级别
       */
      private static int compressionLevelAsInt(
          String tableCompressionLevel, int defaultCompressionLevel) {
        return tableCompressionLevel != null
            ? Integer.parseInt(tableCompressionLevel)
            : defaultCompressionLevel;
      }

      /** 返回上下文中的压缩编解码器。 */
      CodecFactory codec() {
        return codec;
      }
    }
  }

  /**
   * 创建数据文件写入构建器，用于生成 Iceberg 数据文件（{@link DataWriter}）。
   *
   * @param file 目标输出文件
   * @return {@link DataWriteBuilder}
   */
  public static DataWriteBuilder writeData(OutputFile file) {
    return new DataWriteBuilder(file);
  }

  /** 数据文件写入构建器：在 {@link WriteBuilder} 之上扩展分区、加密元数据、排序顺序等 数据文件专属字段，最终构建 {@link DataWriter}。 */
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

    /**
     * 一次性应用表的 schema、分区、属性与 metrics 配置。
     *
     * @param table 目标表
     * @return this，便于链式调用
     */
    public DataWriteBuilder forTable(Table table) {
      schema(table.schema());
      withSpec(table.spec());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    /** 设置数据 schema，委托给底层 {@link WriteBuilder}。 */
    public DataWriteBuilder schema(org.apache.iceberg.Schema newSchema) {
      appenderBuilder.schema(newSchema);
      return this;
    }

    /** 设置单个表属性。 */
    public DataWriteBuilder set(String property, String value) {
      appenderBuilder.set(property, value);
      return this;
    }

    /** 批量设置表属性。 */
    public DataWriteBuilder setAll(Map<String, String> properties) {
      appenderBuilder.setAll(properties);
      return this;
    }

    /** 添加单条 Avro 文件元数据。 */
    public DataWriteBuilder meta(String property, String value) {
      appenderBuilder.meta(property, value);
      return this;
    }

    /** 启用覆写模式。 */
    public DataWriteBuilder overwrite() {
      return overwrite(true);
    }

    /** 显式开启/关闭覆写模式。 */
    public DataWriteBuilder overwrite(boolean enabled) {
      appenderBuilder.overwrite(enabled);
      return this;
    }

    /** 设置 metrics 配置。 */
    public DataWriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      appenderBuilder.metricsConfig(newMetricsConfig);
      return this;
    }

    /** 设置自定义 DatumWriter 创建函数。 */
    public DataWriteBuilder createWriterFunc(Function<Schema, DatumWriter<?>> newCreateWriterFunc) {
      appenderBuilder.createWriterFunc(newCreateWriterFunc);
      return this;
    }

    /** 设置分区 spec。 */
    public DataWriteBuilder withSpec(PartitionSpec newSpec) {
      this.spec = newSpec;
      return this;
    }

    /** 设置分区值。 */
    public DataWriteBuilder withPartition(StructLike newPartition) {
      this.partition = newPartition;
      return this;
    }

    /** 设置加密密钥元数据。 */
    public DataWriteBuilder withKeyMetadata(EncryptionKeyMetadata metadata) {
      this.keyMetadata = metadata;
      return this;
    }

    /** 设置排序顺序。 */
    public DataWriteBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder;
      return this;
    }

    /**
     * 构建 {@link DataWriter}。
     *
     * <p>逻辑：校验 spec 与 partition（分区表必须提供 partition 值），然后通过 {@link WriteBuilder#build()} 获取 {@link
     * FileAppender}，并包装为 {@link DataWriter}（包含文件格式、位置、分区、加密元数据、排序顺序等信息）。
     *
     * @param <T> 写入数据类型
     * @return 数据文件写入器
     * @throws IOException 若构建底层 appender 失败
     */
    public <T> DataWriter<T> build() throws IOException {
      Preconditions.checkArgument(spec != null, "Cannot create data writer without spec");
      Preconditions.checkArgument(
          spec.isUnpartitioned() || partition != null,
          "Partition must not be null when creating data writer for partitioned spec");

      FileAppender<T> fileAppender = appenderBuilder.build();
      return new DataWriter<>(
          fileAppender, FileFormat.AVRO, location, spec, partition, keyMetadata, sortOrder);
    }
  }

  /**
   * 创建删除文件写入构建器，可构建等值删除或位置删除写入器。
   *
   * @param file 目标输出文件
   * @return {@link DeleteWriteBuilder}
   */
  public static DeleteWriteBuilder writeDeletes(OutputFile file) {
    return new DeleteWriteBuilder(file);
  }

  /**
   * 删除文件写入构建器：支持构建等值删除（{@link #buildEqualityWriter()}）与 位置删除（{@link #buildPositionWriter()}）两种写入器。
   *
   * <p>设计意图：复用 {@link WriteBuilder} 的底层 {@link AvroFileAppender}，但在 schema、 元数据与 DatumWriter
   * 上做删除文件专属处理（例如写入 "delete-type" 元数据、 切换 delete 压缩上下文）。
   */
  public static class DeleteWriteBuilder {
    private final WriteBuilder appenderBuilder;
    private final String location;
    private Function<Schema, DatumWriter<?>> createWriterFunc = null;
    private org.apache.iceberg.Schema rowSchema;
    private PartitionSpec spec;
    private StructLike partition;
    private EncryptionKeyMetadata keyMetadata = null;
    private int[] equalityFieldIds = null;
    private SortOrder sortOrder;

    private DeleteWriteBuilder(OutputFile file) {
      this.appenderBuilder = write(file);
      this.location = file.location();
    }

    /** 一次性应用表的 schema、分区、属性与 metrics 配置。 */
    public DeleteWriteBuilder forTable(Table table) {
      rowSchema(table.schema());
      withSpec(table.spec());
      setAll(table.properties());
      metricsConfig(MetricsConfig.forTable(table));
      return this;
    }

    /** 设置单个表属性。 */
    public DeleteWriteBuilder set(String property, String value) {
      appenderBuilder.set(property, value);
      return this;
    }

    /** 批量设置表属性。 */
    public DeleteWriteBuilder setAll(Map<String, String> properties) {
      appenderBuilder.setAll(properties);
      return this;
    }

    /** 添加单条 Avro 文件元数据。 */
    public DeleteWriteBuilder meta(String property, String value) {
      appenderBuilder.meta(property, value);
      return this;
    }

    /** 批量添加 Avro 文件元数据。 */
    public DeleteWriteBuilder meta(Map<String, String> properties) {
      appenderBuilder.meta(properties);
      return this;
    }

    /** 启用覆写模式。 */
    public DeleteWriteBuilder overwrite() {
      return overwrite(true);
    }

    /** 显式开启/关闭覆写模式。 */
    public DeleteWriteBuilder overwrite(boolean enabled) {
      appenderBuilder.overwrite(enabled);
      return this;
    }

    /** 设置 metrics 配置。 */
    public DeleteWriteBuilder metricsConfig(MetricsConfig newMetricsConfig) {
      appenderBuilder.metricsConfig(newMetricsConfig);
      return this;
    }

    /** 设置自定义 DatumWriter 创建函数（等值删除必填）。 */
    public DeleteWriteBuilder createWriterFunc(Function<Schema, DatumWriter<?>> writerFunction) {
      this.createWriterFunc = writerFunction;
      return this;
    }

    /** 设置行 schema（等值删除时使用）。 */
    public DeleteWriteBuilder rowSchema(org.apache.iceberg.Schema newRowSchema) {
      this.rowSchema = newRowSchema;
      return this;
    }

    /** 设置分区 spec。 */
    public DeleteWriteBuilder withSpec(PartitionSpec newSpec) {
      this.spec = newSpec;
      return this;
    }

    /** 设置分区值。 */
    public DeleteWriteBuilder withPartition(StructLike key) {
      this.partition = key;
      return this;
    }

    /** 设置加密密钥元数据。 */
    public DeleteWriteBuilder withKeyMetadata(EncryptionKeyMetadata metadata) {
      this.keyMetadata = metadata;
      return this;
    }

    /** 设置等值删除字段 id 列表（List 版本）。 */
    public DeleteWriteBuilder equalityFieldIds(List<Integer> fieldIds) {
      this.equalityFieldIds = ArrayUtil.toIntArray(fieldIds);
      return this;
    }

    /** 设置等值删除字段 id 列表（可变参数版本）。 */
    public DeleteWriteBuilder equalityFieldIds(int... fieldIds) {
      this.equalityFieldIds = fieldIds;
      return this;
    }

    /** 设置排序顺序。 */
    public DeleteWriteBuilder withSortOrder(SortOrder newSortOrder) {
      this.sortOrder = newSortOrder;
      return this;
    }

    /**
     * 构建等值删除写入器 {@link EqualityDeleteWriter}。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验 rowSchema、equalityFieldIds、createWriterFunc、spec、partition 等必填项。
     *   <li>写入元数据 "delete-type"="equality" 与 "delete-field-ids"（逗号分隔）。
     *   <li>把 rowSchema 作为 appender 的 schema，并切换为删除文件压缩上下文。
     *   <li>构建底层 appender 并包装为 {@link EqualityDeleteWriter}。
     * </ol>
     *
     * @param <T> 写入数据类型
     * @return 等值删除写入器
     * @throws IOException 若构建失败
     */
    public <T> EqualityDeleteWriter<T> buildEqualityWriter() throws IOException {
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
          FileFormat.AVRO,
          location,
          spec,
          partition,
          keyMetadata,
          sortOrder,
          equalityFieldIds);
    }

    /**
     * 构建位置删除写入器 {@link PositionDeleteWriter}。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>校验不能同时设置 equalityFieldIds；分区表必须提供 partition。
     *   <li>写入元数据 "delete-type"="position"。
     *   <li>若同时提供 rowSchema 与 createWriterFunc，则写入带行数据的位置删除： schema 用 {@link
     *       DeleteSchemaUtil#posDeleteSchema} 包装，DatumWriter 用 {@link PositionAndRowDatumWriter}
     *       包装行写入器。
     *   <li>否则仅写入 path+pos 的位置删除，schema 用 {@link DeleteSchemaUtil#pathPosSchema}， DatumWriter 用
     *       {@link PositionDatumWriter}。
     *   <li>切换为删除文件压缩上下文并构建 {@link PositionDeleteWriter}。
     * </ol>
     *
     * @param <T> 写入数据类型
     * @return 位置删除写入器
     * @throws IOException 若构建失败
     */
    public <T> PositionDeleteWriter<T> buildPositionWriter() throws IOException {
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
        // the appender uses the row schema wrapped with position fields
        appenderBuilder.schema(DeleteSchemaUtil.posDeleteSchema(rowSchema));

        appenderBuilder.createWriterFunc(
            avroSchema -> new PositionAndRowDatumWriter<>(createWriterFunc.apply(avroSchema)));

      } else {
        appenderBuilder.schema(DeleteSchemaUtil.pathPosSchema());

        // We ignore the 'createWriterFunc' and 'rowSchema' even if is provided, since we do not
        // write row data itself
        appenderBuilder.createWriterFunc(ignored -> new PositionDatumWriter());
      }

      appenderBuilder.createContextFunc(WriteBuilder.Context::deleteContext);

      return new PositionDeleteWriter<>(
          appenderBuilder.build(), FileFormat.AVRO, location, spec, partition, keyMetadata);
    }
  }

  /**
   * 仅写入 path 与 pos 的位置删除 DatumWriter（不含被删行数据）。
   *
   * <p>设计要点：path 用字符串写入器、pos 用 long 写入器；metrics 只统计这两列。
   */
  private static class PositionDatumWriter implements MetricsAwareDatumWriter<PositionDelete<?>> {
    private static final ValueWriter<Object> PATH_WRITER = ValueWriters.strings();
    private static final ValueWriter<Long> POS_WRITER = ValueWriters.longs();

    @Override
    public void setSchema(Schema schema) {}

    /**
     * 写入一条位置删除记录：先写文件路径，再写行位置。
     *
     * @param delete 位置删除对象
     * @param out Avro 编码器
     */
    @Override
    public void write(PositionDelete<?> delete, Encoder out) throws IOException {
      PATH_WRITER.write(delete.path(), out);
      POS_WRITER.write(delete.pos(), out);
    }

    /** 返回 path 与 pos 两列的 metrics 流。 */
    @Override
    public Stream<FieldMetrics> metrics() {
      return Stream.concat(PATH_WRITER.metrics(), POS_WRITER.metrics());
    }
  }

  /**
   * 写入 path、pos 与被删行数据的位置删除 DatumWriter。
   *
   * <p>设计要点：在 {@link PositionDatumWriter} 基础上额外委托一个行写入器 （{@code rowWriter}）写入被删的原始行。
   *
   * @param <D> 被删行的 Java 类型
   */
  private static class PositionAndRowDatumWriter<D>
      implements MetricsAwareDatumWriter<PositionDelete<D>> {
    private static final ValueWriter<Object> PATH_WRITER = ValueWriters.strings();
    private static final ValueWriter<Long> POS_WRITER = ValueWriters.longs();

    private final DatumWriter<D> rowWriter;

    private PositionAndRowDatumWriter(DatumWriter<D> rowWriter) {
      this.rowWriter = rowWriter;
    }

    /**
     * 设置 Avro schema，并把 "row" 字段的子 schema 传递给行写入器。
     *
     * @param schema Avro 文件 schema
     */
    @Override
    public void setSchema(Schema schema) {
      Schema.Field rowField = schema.getField("row");
      if (rowField != null) {
        rowWriter.setSchema(rowField.schema());
      }
    }

    /**
     * 写入一条带行的位置删除：path、pos、row 顺序写入。
     *
     * @param delete 位置删除对象（含被删行）
     * @param out Avro 编码器
     */
    @Override
    public void write(PositionDelete<D> delete, Encoder out) throws IOException {
      PATH_WRITER.write(delete.path(), out);
      POS_WRITER.write(delete.pos(), out);
      rowWriter.write(delete.row(), out);
    }

    /** 返回 path 与 pos 两列的 metrics 流（行数据 metrics 由 rowWriter 自行管理）。 */
    @Override
    public Stream<FieldMetrics> metrics() {
      return Stream.concat(PATH_WRITER.metrics(), POS_WRITER.metrics());
    }
  }

  /**
   * 创建 Avro 文件读取构建器。
   *
   * @param file 输入文件
   * @return {@link ReadBuilder}
   */
  public static ReadBuilder read(InputFile file) {
    return new ReadBuilder(file);
  }

  /**
   * Avro 文件读取构建器：支持列投影、字段重命名、NameMapping、ClassLoader 设置、 区间读取（split）与容器复用等。
   *
   * <p>设计意图：通过 {@link ProjectionDatumReader} 把 Iceberg schema 投影、字段重命名、 NameMapping 等逻辑统一封装，调用方只需提供
   * projectedSchema 与 readerFunc。
   */
  public static class ReadBuilder {
    private final InputFile file;
    private final Map<String, String> renames = Maps.newLinkedHashMap();
    private ClassLoader loader = Thread.currentThread().getContextClassLoader();
    private NameMapping nameMapping;
    private boolean reuseContainers = false;
    private org.apache.iceberg.Schema schema = null;
    private Function<Schema, DatumReader<?>> createReaderFunc = null;
    private BiFunction<org.apache.iceberg.Schema, Schema, DatumReader<?>> createReaderBiFunc = null;

    /** 默认 reader 创建函数：使用 {@link GenericAvroReader} 并设置 ClassLoader。 */
    @SuppressWarnings("UnnecessaryLambda")
    private final Function<Schema, DatumReader<?>> defaultCreateReaderFunc =
        readSchema -> {
          GenericAvroReader<?> reader = new GenericAvroReader<>(readSchema);
          reader.setClassLoader(loader);
          return reader;
        };

    private Long start = null;
    private Long length = null;

    private ReadBuilder(InputFile file) {
      Preconditions.checkNotNull(file, "Input file cannot be null");
      this.file = file;
    }

    /**
     * 设置单参数 reader 创建函数（仅接收 Avro schema）。
     *
     * @param readerFunction reader 创建函数
     * @return this，便于链式调用
     */
    public ReadBuilder createReaderFunc(Function<Schema, DatumReader<?>> readerFunction) {
      Preconditions.checkState(createReaderBiFunc == null, "Cannot set multiple createReaderFunc");
      this.createReaderFunc = readerFunction;
      return this;
    }

    /**
     * 设置双参数 reader 创建函数（同时接收 Iceberg schema 与 Avro schema），用于需要 同时访问两种 schema 的自定义 reader。
     *
     * @param readerFunction reader 创建函数
     * @return this，便于链式调用
     */
    public ReadBuilder createReaderFunc(
        BiFunction<org.apache.iceberg.Schema, Schema, DatumReader<?>> readerFunction) {
      Preconditions.checkState(createReaderFunc == null, "Cannot set multiple createReaderFunc");
      this.createReaderBiFunc = readerFunction;
      return this;
    }

    /**
     * 限制读取范围为 {@code [start, start + length)} 的字节区间，用于并行分片读取。
     *
     * @param newStart 起始字节位置
     * @param newLength 读取长度
     * @return this，便于链式调用
     */
    public ReadBuilder split(long newStart, long newLength) {
      this.start = newStart;
      this.length = newLength;
      return this;
    }

    /** 设置投影 schema，用于列裁剪与字段重排。 */
    public ReadBuilder project(org.apache.iceberg.Schema projectedSchema) {
      this.schema = projectedSchema;
      return this;
    }

    /** 启用容器对象复用以减少 GC。 */
    public ReadBuilder reuseContainers() {
      this.reuseContainers = true;
      return this;
    }

    /** 显式开启/关闭容器对象复用。 */
    public ReadBuilder reuseContainers(boolean shouldReuse) {
      this.reuseContainers = shouldReuse;
      return this;
    }

    /**
     * 注册一个字段重命名映射：读取时把文件中的 {@code fullName} 字段当作 {@code newName}。
     *
     * @param fullName 文件中的字段全名
     * @param newName 期望的字段名
     * @return this，便于链式调用
     */
    public ReadBuilder rename(String fullName, String newName) {
      renames.put(fullName, newName);
      return this;
    }

    /** 设置 NameMapping，用于根据字段名匹配字段 id（兼容无 id 的旧 Avro 文件）。 */
    public ReadBuilder withNameMapping(NameMapping newNameMapping) {
      this.nameMapping = newNameMapping;
      return this;
    }

    /** 设置 ClassLoader，用于加载 Avro 生成的特定类。 */
    public ReadBuilder classLoader(ClassLoader classLoader) {
      this.loader = classLoader;
      return this;
    }

    /**
     * 构建 {@link AvroIterable}。
     *
     * <p>逻辑：按优先级选择 reader 创建函数（双参数 > 单参数 > 默认），然后构造 {@link ProjectionDatumReader} 包装
     * readerFunc，连同文件、区间、reuseContainers 等参数生成 {@link AvroIterable}。
     *
     * @param <D> 读取数据类型
     * @return 可迭代的 {@link AvroIterable}
     */
    public <D> AvroIterable<D> build() {
      Preconditions.checkNotNull(schema, "Schema is required");
      Function<Schema, DatumReader<?>> readerFunc;
      if (createReaderBiFunc != null) {
        readerFunc = avroSchema -> createReaderBiFunc.apply(schema, avroSchema);
      } else if (createReaderFunc != null) {
        readerFunc = createReaderFunc;
      } else {
        readerFunc = defaultCreateReaderFunc;
      }

      return new AvroIterable<>(
          file,
          new ProjectionDatumReader<>(readerFunc, schema, renames, nameMapping),
          start,
          length,
          reuseContainers);
    }
  }

  /**
   * 返回指定 Avro 文件的总行数。
   *
   * <p>实现：调用 {@link AvroIO#findStartingRowPos} 并传入 {@link Long#MAX_VALUE}， 等价于扫描整个文件累计所有 block 的行数。
   *
   * @param file Avro 输入文件
   * @return 文件总行数
   */
  public static long rowCount(InputFile file) {
    return AvroIO.findStartingRowPos(file::newStream, Long.MAX_VALUE);
  }
}
