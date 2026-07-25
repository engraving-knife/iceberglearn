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
package org.apache.iceberg.mr.hive;

import java.io.IOException;
import java.util.Arrays;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.SerializationUtilities;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedInputFormatInterface;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedSupport;
import org.apache.hadoop.hive.ql.io.CombineHiveInputFormat;
import org.apache.hadoop.hive.ql.io.sarg.ConvertAstToSearchArg;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.plan.TableScanDesc;
import org.apache.hadoop.hive.serde2.ColumnProjectionUtils;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.hive.HiveVersion;
import org.apache.iceberg.mr.InputFormatConfig;
import org.apache.iceberg.mr.mapred.AbstractMapredIcebergRecordReader;
import org.apache.iceberg.mr.mapred.Container;
import org.apache.iceberg.mr.mapred.MapredIcebergInputFormat;
import org.apache.iceberg.mr.mapreduce.IcebergInputFormat;
import org.apache.iceberg.mr.mapreduce.IcebergSplit;
import org.apache.iceberg.mr.mapreduce.IcebergSplitContainer;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.SerializationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：Iceberg 的 Hive InputFormat 实现（mapred 老接口）。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类是 Hive 读取 Iceberg 表的入口， 位于 hive 子包，桥接 Hive 查询执行与 Iceberg
 * 读取引擎）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 Hive 老版 mapred InputFormat，供 Hive 在表扫描时调用。
 *   <li>把 Hive 下推的过滤条件（AST -> SearchArgument）转换为 Iceberg 表达式，并序列化到 JobConf 供下游 reader 使用。
 *   <li>支持 Hive 向量化执行（Hive 3+），动态加载向量化 reader 类。
 *   <li>声明 {@link CombineHiveInputFormat.AvoidSplitCombination}，避免 Hive 合并 split 破坏 Iceberg 自身的并行度。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link MapredIcebergInputFormat} 复用核心切分与读取逻辑，本类只负责 Hive 适配 （过滤转换、列裁剪、向量化路由）。
 *   <li>向量化 reader 类通过 {@link DynConstructors} 反射加载，避免对 Hive 3 专用类的编译期 依赖，从而兼容 Hive 2。
 *   <li>过滤转换失败时降级为“不带 Iceberg 过滤”，由 Hive 自身做残留过滤，保证查询仍能跑通。
 * </ul>
 *
 * <p>上下游关系：上游由 Hive 执行引擎在表扫描阶段调用；下游委托给 {@link IcebergInputFormat}（mapreduce 版）做真正的切分/读取，并调用 {@link
 * HiveIcebergFilterFactory} 做谓词转换。
 */
public class HiveIcebergInputFormat extends MapredIcebergInputFormat<Record>
    implements CombineHiveInputFormat.AvoidSplitCombination, VectorizedInputFormatInterface {

  private static final Logger LOG = LoggerFactory.getLogger(HiveIcebergInputFormat.class);
  private static final String HIVE_VECTORIZED_RECORDREADER_CLASS =
      "org.apache.iceberg.mr.hive.vector.HiveIcebergVectorizedRecordReader";
  private static final DynConstructors.Ctor<AbstractMapredIcebergRecordReader>
      HIVE_VECTORIZED_RECORDREADER_CTOR;

  static {
    if (HiveVersion.min(HiveVersion.HIVE_3)) {
      HIVE_VECTORIZED_RECORDREADER_CTOR =
          DynConstructors.builder(AbstractMapredIcebergRecordReader.class)
              .impl(
                  HIVE_VECTORIZED_RECORDREADER_CLASS,
                  IcebergInputFormat.class,
                  IcebergSplit.class,
                  JobConf.class,
                  Reporter.class)
              .build();
    } else {
      HIVE_VECTORIZED_RECORDREADER_CTOR = null;
    }
  }

  /**
   * 计算输入切分。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>从 JobConf 读取 Hive 过滤表达式，反序列化为 AST，转成 {@link SearchArgument}， 再用 {@link
   *       HiveIcebergFilterFactory} 生成 Iceberg 表达式并 base64 序列化写回 {@link
   *       InputFormatConfig#FILTER_EXPRESSION}；转换失败仅告警并跳过。
   *   <li>读取 Hive 列裁剪信息，写入 {@link InputFormatConfig#SELECTED_COLUMNS}。
   *   <li>调用父类 {@code super.getSplits} 得到 Iceberg 切分，再包装成 {@link HiveIcebergSplit}（携带表 location）。
   * </ol>
   *
   * @param job JobConf
   * @param numSplits 期望切分数（Iceberg 内部按文件切分，未必遵循此值）
   * @return 输入切分数组
   */
  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    // Convert Hive filter to Iceberg filter
    String hiveFilter = job.get(TableScanDesc.FILTER_EXPR_CONF_STR);
    if (hiveFilter != null) {
      ExprNodeGenericFuncDesc exprNodeDesc =
          SerializationUtilities.deserializeObject(hiveFilter, ExprNodeGenericFuncDesc.class);
      SearchArgument sarg = ConvertAstToSearchArg.create(job, exprNodeDesc);
      try {
        Expression filter = HiveIcebergFilterFactory.generateFilterExpression(sarg);
        job.set(InputFormatConfig.FILTER_EXPRESSION, SerializationUtil.serializeToBase64(filter));
      } catch (UnsupportedOperationException e) {
        LOG.warn(
            "Unable to create Iceberg filter, continuing without filter (will be applied by Hive later): ",
            e);
      }
    }

    String[] selectedColumns = ColumnProjectionUtils.getReadColumnNames(job);
    job.setStrings(InputFormatConfig.SELECTED_COLUMNS, selectedColumns);

    String location = job.get(InputFormatConfig.TABLE_LOCATION);
    return Arrays.stream(super.getSplits(job, numSplits))
        .map(split -> new HiveIcebergSplit((IcebergSplit) split, location))
        .toArray(InputSplit[]::new);
  }

  /**
   * 创建 RecordReader。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>同步列裁剪信息到 JobConf。
   *   <li>若开启向量化且 Hive 上下文提供 vectorized row batch ctx：要求 Hive 3+， 设置内存数据模型为 HIVE、跳过残留过滤，并通过反射构造向量化
   *       reader。
   *   <li>否则委托父类返回普通 reader。
   * </ol>
   *
   * @param split 输入切分
   * @param job JobConf
   * @param reporter 进度上报器
   * @return RecordReader 实例
   */
  @Override
  public RecordReader<Void, Container<Record>> getRecordReader(
      InputSplit split, JobConf job, Reporter reporter) throws IOException {
    String[] selectedColumns = ColumnProjectionUtils.getReadColumnNames(job);
    job.setStrings(InputFormatConfig.SELECTED_COLUMNS, selectedColumns);

    if (HiveConf.getBoolVar(job, HiveConf.ConfVars.HIVE_VECTORIZATION_ENABLED)
        && Utilities.getVectorizedRowBatchCtx(job) != null) {
      Preconditions.checkArgument(
          HiveVersion.min(HiveVersion.HIVE_3), "Vectorization only supported for Hive 3+");

      job.setEnum(InputFormatConfig.IN_MEMORY_DATA_MODEL, InputFormatConfig.InMemoryDataModel.HIVE);
      job.setBoolean(InputFormatConfig.SKIP_RESIDUAL_FILTERING, true);

      IcebergSplit icebergSplit = ((IcebergSplitContainer) split).icebergSplit();
      // bogus cast for favouring code reuse over syntax
      return (RecordReader)
          HIVE_VECTORIZED_RECORDREADER_CTOR.newInstance(
              new IcebergInputFormat<>(), icebergSplit, job, reporter);
    } else {
      return super.getRecordReader(split, job, reporter);
    }
  }

  /**
   * 告知 Hive 不要合并此 split。
   *
   * <p>设计要点：Iceberg 的切分已考虑文件大小与并行度，合并会破坏数据局部性与并行规划， 因此始终返回 true。
   *
   * @param path 输入路径
   * @param conf 配置
   * @return 恒为 true，表示跳过合并
   */
  @Override
  public boolean shouldSkipCombine(Path path, Configuration conf) {
    return true;
  }

  // Override annotation commented out, since this interface method has been introduced only in Hive
  // 3
  // @Override
  /**
   * 返回本 InputFormat 在向量化模式下支持的能力集合。
   *
   * <p>当前返回空数组，表示不声明任何特殊向量化支持（如 DECIMAL_64）。
   *
   * @return 支持的能力数组（空）
   */
  public VectorizedSupport.Support[] getSupportedFeatures() {
    return new VectorizedSupport.Support[0];
  }
}
