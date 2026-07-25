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
package org.apache.iceberg.pig;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.TableScan;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.hadoop.HadoopInputFile;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.parquet.Parquet;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.SerializationUtil;
import org.apache.pig.data.DataByteArray;
import org.apache.pig.impl.util.ObjectSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Apache Pig 读取 Iceberg 表所用的 MapReduce InputFormat 实现。
 *
 * <p>所属模块：iceberg-pig（Pig 引擎集成模块，位于 Hadoop MapReduce 之上，依赖 iceberg-core、 iceberg-parquet，向上由 {@link
 * IcebergStorage} LoadFunc 调用，向下驱动 Iceberg 表扫描与 Parquet 读取）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Iceberg 的 {@link CombinedScanTask} 包装成 Hadoop {@link InputSplit}，供 Pig 的 MapReduce
 *       执行引擎调度。
 *   <li>把 Pig 过滤表达式从 JobConf 反序列化并下推到 {@link TableScan}，实现谓词下推。
 *   <li>为每个 split 创建 {@link IcebergRecordReader}，逐文件读取 Iceberg 数据并产出 Pig 可消费的 Tuple/记录。
 *   <li>对分区表的分区列做"虚拟列"注入：从文件 partition value 中取出分区列取值， 与数据文件读取结果合并，形成完整行。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>桥接 Pig（Hadoop MR v2 API）与 Iceberg 表扫描抽象。Pig 仅认 InputFormat 接口， Iceberg
 *       的扫描抽象（TableScan/CombinedScanTask）与 MR 无直接关联，故在此做适配。
 *   <li>Configuration key 通过 {@code key + '.' + signature} 加 scope，使同一 Job 内多个 IcebergStorage 实例（不同
 *       signature）互不干扰，避免 UDF 上下文串扰。
 *   <li>缓存 splits：避免同一 InputFormat 被多次调用 getSplits 时重复规划任务。
 *   <li>IcebergSplit 实现 {@link Writable}：因 split 需跨 JVM 序列化分发到 Mapper 端， 故用 Iceberg 自带的 {@link
 *       SerializationUtil} 二进制序列化 {@link CombinedScanTask}。
 * </ul>
 *
 * <p>上下游关系：被 {@link IcebergStorage#getInputFormat()} 创建；其 IcebergRecordReader 调用 {@link
 * PigParquetReader#buildReader} 构造 Parquet 列读取器；上游依赖 {@link Table}、 {@link TableScan}、{@link
 * HadoopInputFile}。
 *
 * @param <T> 输出记录类型（实际为 Pig Tuple）
 */
public class IcebergPigInputFormat<T> extends InputFormat<Void, T> {
  private static final Logger LOG = LoggerFactory.getLogger(IcebergPigInputFormat.class);

  /** Configuration key 前缀：Iceberg 表 Schema 序列化字符串。 */
  static final String ICEBERG_SCHEMA = "iceberg.schema";
  /** Configuration key 前缀：Pig 下推的投影字段名列表。 */
  static final String ICEBERG_PROJECTED_FIELDS = "iceberg.projected.fields";
  /** Configuration key 前缀：Pig 下推的过滤表达式。 */
  static final String ICEBERG_FILTER_EXPRESSION = "iceberg.filter.expression";

  private final Table table;
  private final String signature;
  private List<InputSplit> splits;

  /**
   * 构造 InputFormat 实例。
   *
   * @param table 待读取的 Iceberg 表
   * @param signature UDF context 签名，用于隔离同一 Job 内不同 IcebergStorage 实例的配置
   */
  IcebergPigInputFormat(Table table, String signature) {
    this.table = table;
    this.signature = signature;
  }

  /**
   * 计算并返回所有输入分片（splits）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若已缓存 splits 则直接返回，避免重复规划。
   *   <li>从 Configuration 反序列化 Pig 下推的过滤表达式 {@link Expression}， 若存在则下推到 {@link
   *       TableScan#filter(Expression)}。
   *   <li>调用 {@link TableScan#planTasks()} 规划出 {@link CombinedScanTask} 集合， 每个 CombinedScanTask
   *       包装为一个 {@link IcebergSplit}。
   * </ol>
   *
   * @param context Job 上下文，用于读取 Configuration
   * @return InputSplit 列表
   * @throws IOException 序列化反序列化或扫描规划失败
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<InputSplit> getSplits(JobContext context) throws IOException {
    if (splits != null) {
      LOG.info("Returning cached splits: {}", splits.size());
      return splits;
    }

    splits = Lists.newArrayList();

    TableScan scan = table.newScan();

    // Apply Filters
    Expression filterExpression =
        (Expression)
            ObjectSerializer.deserialize(
                context.getConfiguration().get(scope(ICEBERG_FILTER_EXPRESSION)));
    LOG.info("[{}]: iceberg filter expressions: {}", signature, filterExpression);

    if (filterExpression != null) {
      LOG.info("Filter Expression: {}", filterExpression);
      scan = scan.filter(filterExpression);
    }

    // Wrap in Splits
    try (CloseableIterable<CombinedScanTask> tasks = scan.planTasks()) {
      tasks.forEach(scanTask -> splits.add(new IcebergSplit(scanTask)));
    }

    return splits;
  }

  /**
   * 创建 RecordReader，由 MR 框架在每个 Mapper 上调用。
   *
   * @param split 输入分片（必须是 {@link IcebergSplit}）
   * @param context TaskAttempt 上下文
   * @return 新的 {@link IcebergRecordReader} 实例
   */
  @Override
  public RecordReader<Void, T> createRecordReader(InputSplit split, TaskAttemptContext context) {
    return new IcebergRecordReader<>();
  }

  /**
   * Iceberg 的 Hadoop InputSplit 实现：包装一个 {@link CombinedScanTask}， 并实现 {@link Writable} 以支持跨 JVM 的
   * shuffle 序列化。
   *
   * <p>设计意图：MR 框架要求 split 可序列化，本类通过 Iceberg 自带的 {@link SerializationUtil} 把 CombinedScanTask
   * 序列化为字节数组传输。
   */
  private static class IcebergSplit extends InputSplit implements Writable {
    private static final String[] ANYWHERE = new String[] {"*"};

    private CombinedScanTask task;

    IcebergSplit(CombinedScanTask task) {
      this.task = task;
    }

    /**
     * 返回分片总字节数：累加该任务内所有文件扫描任务的文件长度。
     *
     * @return 总字节长度
     */
    @Override
    public long getLength() {
      return task.files().stream().mapToLong(FileScanTask::length).sum();
    }

    /**
     * 返回数据位置提示。Iceberg 表的文件可能分布在多个节点，且具体块位置信息 在此无法获取，故返回 "*" 表示任意位置。
     *
     * @return {{"*"}} 表示不限定位置
     */
    @Override
    public String[] getLocations() {
      return ANYWHERE;
    }

    /**
     * 序列化分片：把 {@link CombinedScanTask} 序列化为字节数组，先写长度再写数据。
     *
     * @param out DataOutput
     * @throws IOException 序列化失败
     */
    @Override
    public void write(DataOutput out) throws IOException {
      byte[] data = SerializationUtil.serializeToBytes(this.task);
      out.writeInt(data.length);
      out.write(data);
    }

    /**
     * 反序列化分片：先读长度，再读取对应字节数据并反序列化为 {@link CombinedScanTask}。
     *
     * @param in DataInput
     * @throws IOException 反序列化失败
     */
    @Override
    public void readFields(DataInput in) throws IOException {
      byte[] data = new byte[in.readInt()];
      in.readFully(data);

      this.task = SerializationUtil.deserializeFromBytes(data);
    }
  }

  /**
   * 给 Configuration key 加上 signature 后缀以隔离不同 UDF 实例的配置。
   *
   * @param key 原始 key
   * @return 加 scope 后的 key，形如 "iceberg.schema.&lt;signature&gt;"
   */
  private String scope(String key) {
    return key + '.' + signature;
  }

  /**
   * Iceberg RecordReader 实现：逐文件读取 CombinedScanTask 内的多个 FileScanTask， 把数据行输出为 Pig 可消费的记录。
   *
   * <p>设计意图：
   *
   * <ul>
   *   <li>单个 split 可能包含多个文件，因此 {@link #advance()} 在文件之间迭代， {@link #nextKeyValue()} 在行与文件之间分别迭代。
   *   <li>分区列值不写入数据文件，需从 {@link DataFile#partition()} 提取，通过 {@link PigParquetReader#buildReader}
   *       注入到结果 Tuple 中。
   * </ul>
   *
   * @param <T> 输出记录类型
   */
  public class IcebergRecordReader<T> extends RecordReader<Void, T> {
    private TaskAttemptContext context;

    private Iterator<FileScanTask> tasks;

    private CloseableIterable reader;
    private Iterator<T> recordIterator;
    private T currentRecord;

    /**
     * 初始化 RecordReader：取出 split 中的 {@link CombinedScanTask}，获取其文件迭代器， 然后调用 {@link #advance()}
     * 加载首个文件。
     *
     * @param split 输入分片
     * @param initContext TaskAttempt 上下文
     * @throws IOException 读取初始化失败
     */
    @Override
    public void initialize(InputSplit split, TaskAttemptContext initContext) throws IOException {
      this.context = initContext;

      CombinedScanTask task = ((IcebergSplit) split).task;
      this.tasks = task.files().iterator();

      advance();
    }

    /**
     * 推进到下一个 {@link FileScanTask}，并构造对应的 Parquet reader。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>若当前 reader 非空则关闭它。
     *   <li>若没有更多文件则返回 false。
     *   <li>取下一个 FileScanTask，从 Configuration 反序列化表 Schema 与投影字段， 计算出投影后 Schema。
     *   <li>构造 {@link HadoopInputFile}，并判断分区表是否有 identity 分区列。
     *   <li>对 Parquet 文件，区分"有/无分区列"两种情形：
     *       <ul>
     *         <li>有分区列：把分区列从 readSchema 中剔除（避免重复读）， 并构造 partitionValueMap（fieldId -&gt; 分区值）。
     *         <li>无分区列：直接按 projectedSchema 读取。
     *       </ul>
     *   <li>调用 {@link PigParquetReader#buildReader} 构造列读取器， 通过 {@link Parquet#read} 启动读取。
     * </ol>
     *
     * @return true 表示成功推进到下一文件；false 表示无更多文件
     * @throws IOException 文件读取失败
     */
    @SuppressWarnings("unchecked")
    private boolean advance() throws IOException {
      if (reader != null) {
        reader.close();
      }

      if (!tasks.hasNext()) {
        return false;
      }

      FileScanTask currentTask = tasks.next();

      Schema tableSchema =
          (Schema)
              ObjectSerializer.deserialize(context.getConfiguration().get(scope(ICEBERG_SCHEMA)));
      LOG.debug("[{}]: Task table schema: {}", signature, tableSchema);

      List<String> projectedFields =
          (List<String>)
              ObjectSerializer.deserialize(
                  context.getConfiguration().get(scope(ICEBERG_PROJECTED_FIELDS)));
      LOG.debug("[{}]: Task projected fields: {}", signature, projectedFields);

      Schema projectedSchema =
          projectedFields != null ? SchemaUtil.project(tableSchema, projectedFields) : tableSchema;

      PartitionSpec spec = currentTask.asFileScanTask().spec();
      DataFile file = currentTask.file();
      InputFile inputFile = HadoopInputFile.fromLocation(file.path(), context.getConfiguration());

      Set<Integer> idColumns = spec.identitySourceIds();

      // schema needed for the projection and filtering
      boolean hasJoinedPartitionColumns = !idColumns.isEmpty();

      switch (file.format()) {
        case PARQUET:
          Map<Integer, Object> partitionValueMap = Maps.newHashMap();

          if (hasJoinedPartitionColumns) {

            Schema readSchema = TypeUtil.selectNot(projectedSchema, idColumns);
            Schema projectedPartitionSchema = TypeUtil.select(projectedSchema, idColumns);

            Map<String, Integer> partitionSpecFieldIndexMap = Maps.newHashMap();
            for (int i = 0; i < spec.fields().size(); i++) {
              partitionSpecFieldIndexMap.put(spec.fields().get(i).name(), i);
            }

            for (Types.NestedField field : projectedPartitionSchema.columns()) {
              int partitionIndex = partitionSpecFieldIndexMap.get(field.name());

              Object partitionValue = file.partition().get(partitionIndex, Object.class);
              partitionValueMap.put(
                  field.fieldId(), convertPartitionValue(field.type(), partitionValue));
            }

            reader =
                Parquet.read(inputFile)
                    .project(readSchema)
                    .split(currentTask.start(), currentTask.length())
                    .filter(currentTask.residual())
                    .createReaderFunc(
                        fileSchema ->
                            PigParquetReader.buildReader(
                                fileSchema, projectedSchema, partitionValueMap))
                    .build();
          } else {
            reader =
                Parquet.read(inputFile)
                    .project(projectedSchema)
                    .split(currentTask.start(), currentTask.length())
                    .filter(currentTask.residual())
                    .createReaderFunc(
                        fileSchema ->
                            PigParquetReader.buildReader(
                                fileSchema, projectedSchema, partitionValueMap))
                    .build();
          }

          recordIterator = reader.iterator();

          break;
        default:
          throw new UnsupportedOperationException("Unsupported file format: " + file.format());
      }

      return true;
    }

    /**
     * 把分区值转换为 Pig 友好类型。Iceberg 的 binary 类型分区值是 {@link ByteBuffer}， 需转为 Pig 的 {@link
     * DataByteArray}；其它类型保持原值。
     *
     * @param type 字段类型
     * @param value 原始分区值
     * @return 转换后的分区值
     */
    private Object convertPartitionValue(Type type, Object value) {
      if (type.typeId() == Types.BinaryType.get().typeId()) {
        return new DataByteArray(ByteBuffers.toByteArray((ByteBuffer) value));
      }

      return value;
    }

    /**
     * 推进到下一条记录。若当前文件 reader 已耗尽，则继续 {@link #advance()} 到下一个文件。
     *
     * <p>逻辑：先尝试当前 recordIterator；若空则循环调用 {@link #advance()} 切换文件， 直到读到一条记录或所有文件耗尽。
     *
     * @return true 表示读到下一条记录；false 表示输入已耗尽
     * @throws IOException 读取失败
     */
    @Override
    public boolean nextKeyValue() throws IOException {
      if (recordIterator.hasNext()) {
        currentRecord = recordIterator.next();
        return true;
      }

      while (advance()) {
        if (recordIterator.hasNext()) {
          currentRecord = recordIterator.next();
          return true;
        }
      }

      return false;
    }

    /**
     * 返回当前记录的 key。Iceberg 表本身无 MR key 概念，故固定返回 null。
     *
     * @return null
     */
    @Override
    public Void getCurrentKey() {
      return null;
    }

    /**
     * 返回当前记录的 value。
     *
     * @return 当前记录值
     */
    @Override
    public T getCurrentValue() {
      return currentRecord;
    }

    /**
     * 返回读取进度。当前实现未追踪进度，固定返回 0。
     *
     * @return 0
     */
    @Override
    public float getProgress() {
      return 0;
    }

    /** 关闭 reader。实际资源在 {@link #advance()} 切换文件时已逐个关闭，此处为空实现。 */
    @Override
    public void close() {}
  }
}
