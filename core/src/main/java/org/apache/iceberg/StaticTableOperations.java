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

import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;

/**
 * 静态版 {@link TableOperations} 实现。
 *
 * <p>所属模块：iceberg-core，定位为只读、不可变版本的表元数据访问层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>基于给定的元数据文件路径，按需读取并缓存一份固定的 {@link TableMetadata}；
 *   <li>对外提供与可写 {@link TableOperations} 一致的接口，但所有写操作（commit、metadataFileLocation） 均抛出 {@link
 *       UnsupportedOperationException}；
 *   <li>对外暴露 {@link FileIO} 与 {@link LocationProvider}，便于读取底层文件。
 * </ul>
 *
 * <p>设计意图：用于在不持有可写表引用、也不需要刷新语义的场景下（如离线分析某个历史元数据快照、 工具类读取 metadata.json），以最小的代价获取一个稳定的
 * TableOperations 视图。{@code refresh()} 始终返回创建时绑定的同一份元数据，从而保证读取的快照"永不漂移"。{@code current()} 采用懒加载，
 * 避免在仅访问 io() / locationProvider() 时也强制读取元数据文件。
 *
 * <p>线程安全：{@code staticMetadata} 通过 null 检查进行懒加载，未做同步，因此不建议在多线程中 并发首次调用 {@link
 * #current()}；多线程读取已加载完成的元数据是安全的。
 *
 * <p>上下游关系：依赖 {@link TableMetadataParser} 反序列化元数据、{@link FileIO} 读取文件；
 * 通常被离线分析工具、元数据校验工具、测试辅助类等使用，不参与正常的表提交链路。
 */
public class StaticTableOperations implements TableOperations {
  private TableMetadata staticMetadata;
  private final String metadataFileLocation;
  private final FileIO io;
  private final LocationProvider locationProvider;

  /**
   * 构造一个静态表操作对象，不指定 {@link LocationProvider}。
   *
   * @param metadataFileLocation 元数据文件的路径
   * @param io 用于读取元数据文件的 {@link FileIO}
   */
  public StaticTableOperations(String metadataFileLocation, FileIO io) {
    this(metadataFileLocation, io, null);
  }

  /**
   * 构造一个静态表操作对象，可显式指定 {@link LocationProvider}。
   *
   * @param metadataFileLocation 元数据文件的路径
   * @param io 用于读取元数据文件的 {@link FileIO}
   * @param locationProvider 用于提供数据文件写入位置的位置提供者，可为 {@code null}
   */
  public StaticTableOperations(
      String metadataFileLocation, FileIO io, LocationProvider locationProvider) {
    this.io = io;
    this.metadataFileLocation = metadataFileLocation;
    this.locationProvider = locationProvider;
  }

  /**
   * 返回当前绑定的表元数据，首次调用时按需从元数据文件读取并缓存。
   *
   * <p>逻辑：当缓存的 {@code staticMetadata} 为 null 时，调用 {@link TableMetadataParser#read(FileIO, String)}
   * 从指定路径读取元数据；后续调用直接返回缓存对象， 保证整个生命周期内始终引用同一份 {@link TableMetadata}。
   *
   * @return 当前静态版本对应的表元数据
   */
  @Override
  public TableMetadata current() {
    if (staticMetadata == null) {
      staticMetadata = TableMetadataParser.read(io, metadataFileLocation);
    }
    return staticMetadata;
  }

  /**
   * 静态表的刷新操作。由于本实现绑定的是固定的元数据版本，刷新语义不适用， 因此始终返回 {@link #current()}，保证不会出现元数据版本漂移。
   *
   * @return 当前已加载的表元数据，等价于 {@link #current()}
   */
  @Override
  public TableMetadata refresh() {
    return current();
  }

  /**
   * 静态表不支持提交修改，调用将抛出 {@link UnsupportedOperationException}。
   *
   * @param base 提交所基于的旧元数据（本实现忽略）
   * @param metadata 欲提交的新元数据（本实现忽略）
   * @throws UnsupportedOperationException 静态表不可修改，始终抛出
   */
  @Override
  public void commit(TableMetadata base, TableMetadata metadata) {
    throw new UnsupportedOperationException("Cannot modify a static table");
  }

  /** 返回底层用于读写文件的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return this.io;
  }

  /**
   * 生成元数据文件路径，静态表不允许写入元数据，调用将抛出 {@link UnsupportedOperationException}。
   *
   * @param fileName 元数据文件名（本实现忽略）
   * @throws UnsupportedOperationException 静态表不可修改，始终抛出
   */
  @Override
  public String metadataFileLocation(String fileName) {
    throw new UnsupportedOperationException("Cannot modify a static table");
  }

  /** 返回构造时传入的 {@link LocationProvider}，可能为 {@code null}。 */
  @Override
  public LocationProvider locationProvider() {
    return locationProvider;
  }
}
