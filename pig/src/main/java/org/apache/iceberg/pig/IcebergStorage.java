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

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.Tables;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.hadoop.HadoopTables;
import org.apache.iceberg.pig.IcebergPigInputFormat.IcebergRecordReader;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.NaNUtil;
import org.apache.pig.Expression;
import org.apache.pig.Expression.BetweenExpression;
import org.apache.pig.Expression.BinaryExpression;
import org.apache.pig.Expression.Column;
import org.apache.pig.Expression.Const;
import org.apache.pig.Expression.InExpression;
import org.apache.pig.Expression.OpType;
import org.apache.pig.Expression.UnaryExpression;
import org.apache.pig.LoadFunc;
import org.apache.pig.LoadMetadata;
import org.apache.pig.LoadPredicatePushdown;
import org.apache.pig.LoadPushDown;
import org.apache.pig.ResourceSchema;
import org.apache.pig.ResourceStatistics;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.logicalLayer.FrontendException;
import org.apache.pig.impl.util.ObjectSerializer;
import org.apache.pig.impl.util.UDFContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Apache Pig 读取 Iceberg 表的 LoadFunc 入口实现。
 *
 * <p>所属模块：iceberg-pig（Pig 引擎集成模块；位于 Pig LoadFunc 抽象与 Iceberg 表之间， 是 Pig 脚本通过 LOAD 语句访问 Iceberg
 * 表的统一入口，向下创建 {@link IcebergPigInputFormat}）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Pig {@link LoadFunc}：负责定位表、构造 InputFormat、产出 Tuple。
 *   <li>实现 {@link LoadMetadata}：向 Pig 暴露 Iceberg 表 Schema 转换后的 ResourceSchema。
 *   <li>实现 {@link LoadPredicatePushdown}/{@link LoadPushDown}：接收 Pig 下推的过滤谓词与 投影字段，翻译为 Iceberg
 *       {@link org.apache.iceberg.expressions.Expression}， 并写入 UDFContext 供 Mapper 端读取。
 *   <li>跨阶段传递配置：在 frontend 阶段把 schema/投影/过滤表达式序列化进 UDFContext， 在 setLocation 阶段拷贝到 Configuration（带
 *       signature scope），供 backend InputFormat 读取。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>signature 隔离：Pig 在同一 Job 中可能多次调用同一 UDF（不同实例），通过 UDFContextSignature 把每个 IcebergStorage
 *       实例的状态（schema/投影/过滤/location）隔离，避免互相覆盖。
 *   <li>tables/locations 缓存为静态并发 Map：避免重复加载同一表，并支持 location -&gt; Table 反查。
 *   <li>iceberg Tables 实现可插拔：通过 {@code pig.iceberg.tables.impl} 配置自定义 Tables 实现， 默认 {@link
 *       HadoopTables}。
 *   <li>谓词翻译独立成 {@link #convert(Expression)} 与 {@link #convert(OpType, Column, Const)}，
 *       支持二叉/一元/区间/IN 表达式，并特殊处理 NaN 比较。
 * </ul>
 *
 * <p>上下游关系：被 Pig 运行时（LOAD 语句）调用；下游创建 {@link IcebergPigInputFormat}， 使用 {@link SchemaUtil} 做 Schema
 * 转换；通过 {@link Tables#load(String)} 加载 Iceberg 表。
 */
public class IcebergStorage extends LoadFunc
    implements LoadMetadata, LoadPredicatePushdown, LoadPushDown {
  private static final Logger LOG = LoggerFactory.getLogger(IcebergStorage.class);

  /** 配置项 key：自定义 Iceberg {@link Tables} 实现的全限定类名，默认 {@link HadoopTables}。 */
  public static final String PIG_ICEBERG_TABLES_IMPL = "pig.iceberg.tables.impl";

  private static Tables iceberg;
  /** location -&gt; Table 缓存，避免重复加载同一张表。 */
  private static Map<String, Table> tables = Maps.newConcurrentMap();
  /** signature -&gt; location 缓存，供 {@link #getInputFormat()} 反查 Table。 */
  private static Map<String, String> locations = Maps.newConcurrentMap();

  private String signature;

  private IcebergRecordReader reader;

  /**
   * 设置数据位置：记录 location，并把 frontend 阶段写入 UDFContext 的 schema/投影字段/过滤表达式拷贝到 Configuration（带 signature
   * scope）， 供 backend 的 {@link IcebergPigInputFormat} 读取。
   *
   * @param location Iceberg 表路径
   * @param job Pig Job 对象
   */
  @Override
  public void setLocation(String location, Job job) {
    LOG.info("[{}]: setLocation() -> {}", signature, location);

    locations.put(signature, location);

    Configuration conf = job.getConfiguration();

    copyUDFContextToScopedConfiguration(conf, IcebergPigInputFormat.ICEBERG_SCHEMA);
    copyUDFContextToScopedConfiguration(conf, IcebergPigInputFormat.ICEBERG_PROJECTED_FIELDS);
    copyUDFContextToScopedConfiguration(conf, IcebergPigInputFormat.ICEBERG_FILTER_EXPRESSION);
  }

  /**
   * 返回用于读取本表的 {@link InputFormat}。根据 signature 反查 location，再查 Table， 构造 {@link
   * IcebergPigInputFormat}。
   *
   * @return IcebergPigInputFormat 实例
   */
  @Override
  public InputFormat getInputFormat() {
    LOG.info("[{}]: getInputFormat()", signature);
    String location = locations.get(signature);

    return new IcebergPigInputFormat(tables.get(location), signature);
  }

  /**
   * 读取下一条记录并返回 Pig Tuple。底层委托给 IcebergRecordReader。
   *
   * @return 下一条 Tuple，输入耗尽时返回 null
   * @throws IOException 读取失败
   */
  @Override
  public Tuple getNext() throws IOException {
    if (!reader.nextKeyValue()) {
      return null;
    }

    return (Tuple) reader.getCurrentValue();
  }

  /**
   * 在 backend 阶段绑定 RecordReader，供后续 {@link #getNext()} 使用。
   *
   * @param newReader 由 Pig 提供的 RecordReader（实际为 IcebergRecordReader）
   * @param split Pig 分片
   */
  @Override
  public void prepareToRead(RecordReader newReader, PigSplit split) {
    LOG.info("[{}]: prepareToRead() -> {}", signature, split);

    this.reader = (IcebergRecordReader) newReader;
  }

  /**
   * 返回表 Schema 给 Pig frontend。加载表后取 Schema，序列化进 UDFContext 供 backend 使用， 并通过 {@link
   * SchemaUtil#convert(Schema)} 转换为 Pig {@link ResourceSchema}。
   *
   * @param location 表路径
   * @param job Pig Job
   * @return Pig ResourceSchema
   * @throws IOException 加载表或转换失败
   */
  @Override
  public ResourceSchema getSchema(String location, Job job) throws IOException {
    LOG.info("[{}]: getSchema() -> {}", signature, location);

    Schema schema = load(location, job).schema();
    storeInUDFContext(IcebergPigInputFormat.ICEBERG_SCHEMA, schema);

    return SchemaUtil.convert(schema);
  }

  /**
   * 返回表统计信息。当前未实现，固定返回 null。
   *
   * @param location 表路径
   * @param job Pig Job
   * @return null（未实现）
   */
  @Override
  public ResourceStatistics getStatistics(String location, Job job) {
    LOG.info("[{}]: getStatistics() -> : {}", signature, location);

    return null;
  }

  /**
   * 返回分区键列表。Iceberg 分区由表自身管理（不依赖 Pig 分区机制），故返回空数组。
   *
   * @param location 表路径
   * @param job Pig Job
   * @return 空数组
   */
  @Override
  public String[] getPartitionKeys(String location, Job job) {
    LOG.info("[{}]: getPartitionKeys()", signature);
    return new String[0];
  }

  /**
   * 设置分区过滤条件。当前未实现，仅记录日志。
   *
   * @param partitionFilter Pig 分区过滤表达式
   */
  @Override
  public void setPartitionFilter(Expression partitionFilter) {
    LOG.info("[{}]: setPartitionFilter() -> {}", signature, partitionFilter);
  }

  /**
   * 返回可参与谓词下推的字段列表。排除复杂类型（MAP/LIST/STRUCT），仅保留标量字段。
   *
   * <p>逻辑：遍历 schema.columns()，按 typeId 跳过复杂类型，其余加入结果。
   *
   * @param location 表路径
   * @param job Pig Job
   * @return 标量字段名列表
   * @throws IOException 加载表失败
   */
  @Override
  public List<String> getPredicateFields(String location, Job job) throws IOException {
    LOG.info("[{}]: getPredicateFields() -> {}", signature, location);
    Schema schema = load(location, job).schema();

    List<String> result = Lists.newArrayList();

    for (Types.NestedField nf : schema.columns()) {
      switch (nf.type().typeId()) {
        case MAP:
        case LIST:
        case STRUCT:
          continue;
        default:
          result.add(nf.name());
      }
    }

    return result;
  }

  /**
   * 声明本 LoadFunc 支持的 Pig 谓词操作类型，包括 AND/OR/EQ/NE/NOT/比较/BETWEEN/IN/NULL。
   *
   * @return 支持的 OpType 列表
   */
  @Override
  public ImmutableList<OpType> getSupportedExpressionTypes() {
    LOG.info("[{}]: getSupportedExpressionTypes()", signature);
    return ImmutableList.of(
        OpType.OP_AND,
        OpType.OP_OR,
        OpType.OP_EQ,
        OpType.OP_NE,
        OpType.OP_NOT,
        OpType.OP_GE,
        OpType.OP_GT,
        OpType.OP_LE,
        OpType.OP_LT,
        OpType.OP_BETWEEN,
        OpType.OP_IN,
        OpType.OP_NULL);
  }

  /**
   * 接收 Pig 下推的谓词，翻译为 Iceberg {@link org.apache.iceberg.expressions.Expression} 并写入 UDFContext，供
   * backend InputFormat 读取后下推到 TableScan。
   *
   * @param predicate Pig 谓词表达式
   * @throws IOException 翻译或序列化失败
   */
  @Override
  public void setPushdownPredicate(Expression predicate) throws IOException {
    LOG.info("[{}]: setPushdownPredicate()", signature);
    LOG.info("[{}]: Pig predicate expression: {}", signature, predicate);

    org.apache.iceberg.expressions.Expression icebergExpression = convert(predicate);

    LOG.info("[{}]: Iceberg predicate expression: {}", signature, icebergExpression);

    storeInUDFContext(IcebergPigInputFormat.ICEBERG_FILTER_EXPRESSION, icebergExpression);
  }

  /**
   * 递归把 Pig {@link Expression} 翻译为 Iceberg {@link org.apache.iceberg.expressions.Expression}。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>二叉表达式：分别处理 OP_AND（合取）、OP_OR（析取）、OP_BETWEEN（拆成 GE &amp;&amp; LE）、 OP_IN（多个 EQ 做 OR 归约）；其它如
   *       lhs=Column/rhs=Const 走 {@link #convert(OpType, Column, Const)}。
   *   <li>一元表达式：OP_NOT 走 not，OP_NULL 走 isNull。
   *   <li>左侧为 Const 右侧为 Column 视为非法排序，抛 FrontendException。
   * </ul>
   *
   * @param expression Pig 表达式
   * @return Iceberg 表达式
   * @throws IOException 不支持的表达式
   */
  private org.apache.iceberg.expressions.Expression convert(Expression expression)
      throws IOException {
    OpType op = expression.getOpType();

    if (expression instanceof BinaryExpression) {
      Expression lhs = ((BinaryExpression) expression).getLhs();
      Expression rhs = ((BinaryExpression) expression).getRhs();

      switch (op) {
        case OP_AND:
          return Expressions.and(convert(lhs), convert(rhs));
        case OP_OR:
          return Expressions.or(convert(lhs), convert(rhs));
        case OP_BETWEEN:
          BetweenExpression between = (BetweenExpression) rhs;
          return Expressions.and(
              convert(OpType.OP_GE, (Column) lhs, (Const) between.getLower()),
              convert(OpType.OP_LE, (Column) lhs, (Const) between.getUpper()));
        case OP_IN:
          return ((InExpression) rhs)
              .getValues().stream()
                  .map(value -> convert(OpType.OP_EQ, (Column) lhs, (Const) value))
                  .reduce(Expressions.alwaysFalse(), Expressions::or);
        default:
          if (lhs instanceof Column && rhs instanceof Const) {
            return convert(op, (Column) lhs, (Const) rhs);
          } else if (lhs instanceof Const && rhs instanceof Column) {
            throw new FrontendException("Invalid expression ordering " + expression);
          }
      }

    } else if (expression instanceof UnaryExpression) {
      Expression unary = ((UnaryExpression) expression).getExpression();

      switch (op) {
        case OP_NOT:
          return Expressions.not(convert(unary));
        case OP_NULL:
          return Expressions.isNull(((Column) unary).getName());
        default:
          throw new FrontendException("Unsupported unary operator" + op);
      }
    }

    throw new FrontendException("Failed to pushdown expression " + expression);
  }

  /**
   * 把单个 Column 与 Const 的二元比较翻译为 Iceberg 表达式。
   *
   * <p>设计要点：对 EQ/NE 特殊处理 NaN 值——若常量是 NaN，则翻译为 isNaN/notNaN 而非 equal/notEqual，避免直接相等比较的语义问题。
   *
   * @param op 比较操作类型
   * @param col 列引用
   * @param constant 常量
   * @return Iceberg 表达式
   */
  private org.apache.iceberg.expressions.Expression convert(OpType op, Column col, Const constant) {
    String name = col.getName();
    Object value = constant.getValue();

    switch (op) {
      case OP_GE:
        return Expressions.greaterThanOrEqual(name, value);
      case OP_GT:
        return Expressions.greaterThan(name, value);
      case OP_LE:
        return Expressions.lessThanOrEqual(name, value);
      case OP_LT:
        return Expressions.lessThan(name, value);
      case OP_EQ:
        return NaNUtil.isNaN(value) ? Expressions.isNaN(name) : Expressions.equal(name, value);
      case OP_NE:
        return NaNUtil.isNaN(value) ? Expressions.notNaN(name) : Expressions.notEqual(name, value);
    }

    throw new RuntimeException(
        String.format(
            "[%s]: Failed to pushdown expression: %s %s %s", signature, col, op, constant));
  }

  /**
   * 声明本 LoadFunc 支持的 Pig pushdown 特性，目前仅支持 PROJECTION（列裁剪）。
   *
   * @return 仅含 PROJECTION 的列表
   */
  @Override
  public List<OperatorSet> getFeatures() {
    return Collections.singletonList(OperatorSet.PROJECTION);
  }

  /**
   * 接收 Pig 下推的投影字段列表，提取字段别名后序列化进 UDFContext，供 backend 读取做列裁剪。
   *
   * @param requiredFieldList Pig 要求的字段列表
   * @return 接受投影的响应
   */
  @Override
  public RequiredFieldResponse pushProjection(RequiredFieldList requiredFieldList) {
    LOG.info("[{}]: pushProjection() -> {}", signature, requiredFieldList);

    try {
      List<String> projection =
          requiredFieldList.getFields().stream()
              .map(RequiredField::getAlias)
              .collect(Collectors.toList());

      storeInUDFContext(IcebergPigInputFormat.ICEBERG_PROJECTED_FIELDS, (Serializable) projection);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return new RequiredFieldResponse(true);
  }

  /**
   * 设置 UDF 上下文签名，用于在 UDFContext 中隔离本实例的配置。
   *
   * @param newSignature Pig 分配的签名
   */
  @Override
  public void setUDFContextSignature(String newSignature) {
    this.signature = newSignature;
  }

  /**
   * 把可序列化的值写入 UDFContext（按 signature 分组的 Properties）。
   *
   * @param key 配置 key
   * @param value 可序列化值
   * @throws IOException 序列化失败
   */
  private void storeInUDFContext(String key, Serializable value) throws IOException {
    Properties properties =
        UDFContext.getUDFContext().getUDFProperties(this.getClass(), new String[] {signature});

    properties.setProperty(key, ObjectSerializer.serialize(value));
  }

  /**
   * 把 UDFContext 中的某项配置拷贝到 Hadoop Configuration（key 加 signature 后缀）。
   *
   * @param conf Hadoop Configuration
   * @param key UDFContext 中的原始 key
   */
  private void copyUDFContextToScopedConfiguration(Configuration conf, String key) {
    String value =
        UDFContext.getUDFContext()
            .getUDFProperties(this.getClass(), new String[] {signature})
            .getProperty(key);

    if (value != null) {
      conf.set(key + '.' + signature, value);
    }
  }

  /**
   * 相对路径转绝对路径。Iceberg 表路径由调用方给出，此处直接返回原值不做转换。
   *
   * @param location 原始路径
   * @param curDir 当前目录
   * @return 原始路径
   * @throws IOException 本实现不抛出异常，仅保留接口签名兼容
   */
  @Override
  public String relativeToAbsolutePath(String location, Path curDir) throws IOException {
    return location;
  }

  /**
   * 加载 Iceberg 表。首次调用时按 {@code pig.iceberg.tables.impl} 初始化 Tables 实现， 之后按 location 从缓存取表，缓存未命中则通过
   * {@link Tables#load(String)} 加载并缓存。
   *
   * <p>逻辑：若 iceberg 为空则反射实例化 Tables 实现；若 tables 缓存未命中则 load 后 put 入缓存。
   *
   * @param location 表路径
   * @param job Pig Job
   * @return Iceberg Table
   * @throws IOException 加载失败抛 FrontendException
   */
  private Table load(String location, Job job) throws IOException {
    if (iceberg == null) {
      Class<?> tablesImpl =
          job.getConfiguration().getClass(PIG_ICEBERG_TABLES_IMPL, HadoopTables.class);
      LOG.info("Initializing iceberg tables implementation: {}", tablesImpl);
      iceberg = (Tables) ReflectionUtils.newInstance(tablesImpl, job.getConfiguration());
    }

    Table result = tables.get(location);

    if (result == null) {
      try {
        LOG.info("[{}]: Loading table for location: {}", signature, location);
        result = iceberg.load(location);
        tables.put(location, result);
      } catch (Exception e) {
        throw new FrontendException("Failed to instantiate tables implementation", e);
      }
    }

    return result;
  }
}
