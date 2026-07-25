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
package org.apache.iceberg.dell.ecs;

import com.emc.object.s3.S3Client;
import org.apache.iceberg.dell.DellProperties;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.metrics.MetricsContext;

/**
 * 基于 ECS S3 的 {@link OutputFile} 实现，提供带存在性校验的写入能力。
 *
 * <p>所属模块：iceberg-dell（Dell EMC ECS 对象存储集成模块，ecs 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>{@link #create()} 在目标对象不存在时创建输出流，已存在则抛出 {@link AlreadyExistsException}。
 *   <li>{@link #createOrOverwrite()} 无条件创建输出流（覆盖既有对象）。
 *   <li>{@link #toInputFile()} 将自身转为可读的 {@link EcsInputFile}。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link BaseEcsFile} 复用存在性判断，使 {@code create()} 在写入前先检查， 避免误覆盖既有数据文件。
 *   <li>实际写入委托给 {@link EcsAppendOutputStream}，利用 ECS append API 实现追加写入。
 *   <li>工厂方法重载：与 {@link EcsInputFile} 对称，提供多组 {@code fromLocation} 重载。
 * </ul>
 *
 * <p>上下游关系：由 {@link EcsFileIO#newOutputFile(String)} 创建；写入流为 {@link EcsAppendOutputStream}；被
 * Iceberg 写入侧（如数据文件/元数据文件落盘）调用。
 */
class EcsOutputFile extends BaseEcsFile implements OutputFile {

  /**
   * 由 location 创建输出文件，使用空配置与空指标上下文。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @return 输出文件实例
   */
  public static EcsOutputFile fromLocation(String location, S3Client client) {
    return new EcsOutputFile(
        client, new EcsURI(location), new DellProperties(), MetricsContext.nullMetrics());
  }

  /**
   * 由 location 创建输出文件，使用指定 Dell 配置与空指标上下文。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @param dellProperties Dell 连接配置
   * @return 输出文件实例
   */
  public static EcsOutputFile fromLocation(
      String location, S3Client client, DellProperties dellProperties) {
    return new EcsOutputFile(
        client, new EcsURI(location), dellProperties, MetricsContext.nullMetrics());
  }

  /**
   * 由 location 创建输出文件，使用指定 Dell 配置与指标上下文（包级可见）。
   *
   * @param location ECS 对象 location
   * @param client S3 客户端
   * @param dellProperties Dell 连接配置
   * @param metrics 指标上下文
   * @return 输出文件实例
   */
  static EcsOutputFile fromLocation(
      String location, S3Client client, DellProperties dellProperties, MetricsContext metrics) {
    return new EcsOutputFile(client, new EcsURI(location), dellProperties, metrics);
  }

  EcsOutputFile(
      S3Client client, EcsURI uri, DellProperties dellProperties, MetricsContext metrics) {
    super(client, uri, dellProperties, metrics);
  }

  /**
   * 在目标对象不存在时创建输出流；若已存在则抛出 {@link AlreadyExistsException}。
   *
   * <p>逻辑：先 {@link #exists()} 判断，不存在则委托 {@link #createOrOverwrite()} 写入。
   *
   * @return 输出流
   * @throws AlreadyExistsException 当目标对象已存在时抛出
   */
  @Override
  public PositionOutputStream create() {
    if (!exists()) {
      return createOrOverwrite();
    } else {
      throw new AlreadyExistsException("ECS object already exists: %s", uri());
    }
  }

  /**
   * 无条件创建输出流（覆盖既有对象）。
   *
   * <p>逻辑：构造 {@link EcsAppendOutputStream}，首段使用 putObject 创建/覆盖对象， 后续段使用 appendObject 追加。
   *
   * @return 输出流
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    return EcsAppendOutputStream.create(client(), uri(), metrics());
  }

  /**
   * 将本输出文件转为输入文件，便于写入后立即读取。
   *
   * @return 复用同一 client/uri/配置的 {@link EcsInputFile}
   */
  @Override
  public InputFile toInputFile() {
    return new EcsInputFile(client(), uri(), dellProperties(), metrics());
  }
}
