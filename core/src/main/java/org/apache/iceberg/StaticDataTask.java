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
package org.apache.iceberg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.util.StructProjection;

/**
 * 静态数据任务：把内存中的行集合包装为 {@link DataTask}，供元数据表扫描返回。
 *
 * <p>所属模块：iceberg-core（元数据表扫描任务层）。
 *
 * <p>职责：把一组静态数据（如 refs、properties 等元数据）包装成可扫描的任务， 通过 {@link StructProjection} 做列投影，使元数据表能像普通表一样被查询。
 *
 * <p>设计意图：元数据表的数据并非来自数据文件，而是来自表元数据本身（如 refs map）。 本类把这些内存数据封装为 DataTask 接口的实现，复用扫描框架的投影/过滤机制。
 *
 * <p>上下游关系：被 {@link RefsTable} 等元数据表使用；内部用 {@link Row} 表示静态行。
 */
class StaticDataTask implements DataTask {

  /**
   * 工厂方法：把值集合通过转换函数映射为行，构造静态数据任务。
   *
   * @param metadata 元数据文件（用于构造 DataFile 元信息）
   * @param tableSchema 表 schema
   * @param projectedSchema 投影 schema
   * @param values 原始值集合
   * @param transform 值到行的转换函数
   * @param <T> 原始值类型
   * @return 静态数据任务
   */
  static <T> DataTask of(
      InputFile metadata,
      Schema tableSchema,
      Schema projectedSchema,
      Iterable<T> values,
      Function<T, Row> transform) {
    return new StaticDataTask(
        metadata,
        tableSchema,
        projectedSchema,
        Lists.newArrayList(Iterables.transform(values, transform::apply)).toArray(new Row[0]));
  }

  private final DataFile metadataFile;
  private final StructLike[] rows;
  private final Schema tableSchema;
  private final Schema projectedSchema;

  private StaticDataTask(
      InputFile metadata, Schema tableSchema, Schema projectedSchema, StructLike[] rows) {
    this.tableSchema = tableSchema;
    this.projectedSchema = projectedSchema;
    this.metadataFile =
        DataFiles.builder(PartitionSpec.unpartitioned())
            .withInputFile(metadata)
            .withRecordCount(rows.length)
            .withFormat(FileFormat.METADATA)
            .build();
    this.rows = rows;
  }

  /** 返回空删除文件列表（静态任务无删除文件）。 */
  @Override
  public List<DeleteFile> deletes() {
    return ImmutableList.of();
  }

  /**
   * 返回投影后的行集合。
   *
   * <p>逻辑：用 {@link StructProjection} 把 tableSchema 投影到 projectedSchema， 对每行做包装后返回。
   *
   * @return 投影后的行集合
   */
  @Override
  public CloseableIterable<StructLike> rows() {
    StructProjection projection = StructProjection.create(tableSchema, projectedSchema);
    Iterable<StructLike> projectedRows = Iterables.transform(Arrays.asList(rows), projection::wrap);
    return CloseableIterable.withNoopClose(projectedRows);
  }

  /** 返回元数据文件（作为 DataFile）。 */
  @Override
  public DataFile file() {
    return metadataFile;
  }

  /** 返回非分区规格（静态任务不分区）。 */
  @Override
  public PartitionSpec spec() {
    return PartitionSpec.unpartitioned();
  }

  /** 返回起始偏移 0。 */
  @Override
  public long start() {
    return 0;
  }

  /** 返回元数据文件大小。 */
  @Override
  public long length() {
    return metadataFile.fileSizeInBytes();
  }

  /** 返回 alwaysTrue 残留表达式（静态任务无残留过滤）。 */
  @Override
  public Expression residual() {
    return Expressions.alwaysTrue();
  }

  /** 不支持切分，返回自身。 */
  @Override
  public Iterable<FileScanTask> split(long splitSize) {
    return ImmutableList.of(this);
  }

  /**
   * 静态行实现：基于 Object 数组的 {@link StructLike}，用于传递静态数据。
   *
   * <p>设计意图：简单的值数组包装，支持 get 但不支持 set（静态数据只读）。
   */
  static class Row implements StructLike, Serializable {
    /**
     * 工厂方法：用可变参数构造一行。
     *
     * @param values 行中的各列值
     * @return 新的 {@link Row}
     */
    public static Row of(Object... values) {
      return new Row(values);
    }

    private final Object[] values;

    private Row(Object... values) {
      this.values = values;
    }

    @Override
    public int size() {
      return values.length;
    }

    /**
     * 按位置获取列值并转换为指定类型。
     *
     * @param pos 列位置
     * @param javaClass 期望的 Java 类型
     * @param <T> 类型参数
     * @return 列值
     */
    @Override
    public <T> T get(int pos, Class<T> javaClass) {
      return javaClass.cast(values[pos]);
    }

    /** 不支持 set 操作（静态行只读）。 */
    @Override
    public <T> void set(int pos, T value) {
      throw new UnsupportedOperationException("Setting values is not supported");
    }
  }
}
