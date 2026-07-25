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
package org.apache.iceberg.hadoop;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileAlreadyExistsException;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.encryption.NativeFileCryptoParameters;
import org.apache.iceberg.encryption.NativelyEncryptedFile;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;

/**
 * 文件级说明：基于 Hadoop {@link FileSystem} API 实现的 {@link OutputFile}。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 Hadoop {@link FileSystem} 与 {@link Path}，为 Iceberg 写入侧提供 {@link OutputFile}
 *       抽象（位置、创建/覆盖输出流）。
 *   <li>实现 {@link NativelyEncryptedFile}，承载原生加密参数。
 * </ul>
 *
 * <p>设计意图：与 {@link HadoopInputFile} 对称，提供多组静态工厂方法适配不同调用上下文 （已知路径字符串、已知 {@link Path}、已知 {@link
 * FileSystem} 等）。{@code create} 默认不覆盖， 文件已存在时抛 {@link AlreadyExistsException}，保证元数据/数据文件写入的幂等性约束。
 *
 * <p>上下游关系：由 {@link HadoopFileIO#newOutputFile} 创建；被 Parquet/ORC/Avro 写入器通过 {@link
 * #create()}/{@link #createOrOverwrite()} 获取输出流。
 */
public class HadoopOutputFile implements OutputFile, NativelyEncryptedFile {

  private final FileSystem fs;
  private final Path path;
  private final Configuration conf;
  private NativeFileCryptoParameters nativeEncryptionParameters;

  /**
   * 根据路径字符串与配置构造 {@link HadoopOutputFile}。
   *
   * @param location 文件路径字符串
   * @param conf Hadoop 配置
   * @return 新的 {@link OutputFile} 实例
   */
  public static OutputFile fromLocation(CharSequence location, Configuration conf) {
    Path path = new Path(location.toString());
    return fromPath(path, conf);
  }

  /**
   * 根据路径字符串与已解析的 {@link FileSystem} 构造 {@link HadoopOutputFile}。
   *
   * @param location 文件路径字符串
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link OutputFile} 实例
   */
  public static OutputFile fromLocation(CharSequence location, FileSystem fs) {
    Path path = new Path(location.toString());
    return fromPath(path, fs);
  }

  /**
   * 根据 {@link Path} 与配置构造 {@link HadoopOutputFile}。
   *
   * @param path Hadoop 路径
   * @param conf Hadoop 配置
   * @return 新的 {@link OutputFile} 实例
   */
  public static OutputFile fromPath(Path path, Configuration conf) {
    FileSystem fs = Util.getFs(path, conf);
    return fromPath(path, fs, conf);
  }

  /**
   * 根据 {@link Path} 与已解析的 {@link FileSystem} 构造 {@link HadoopOutputFile}。
   *
   * @param path Hadoop 路径
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link OutputFile} 实例
   */
  public static OutputFile fromPath(Path path, FileSystem fs) {
    return fromPath(path, fs, fs.getConf());
  }

  /**
   * 根据 {@link Path}、已解析的 {@link FileSystem} 与配置构造 {@link HadoopOutputFile}。
   *
   * @param path Hadoop 路径
   * @param fs 已解析的 Hadoop 文件系统
   * @param conf Hadoop 配置
   * @return 新的 {@link OutputFile} 实例
   */
  public static OutputFile fromPath(Path path, FileSystem fs, Configuration conf) {
    return new HadoopOutputFile(fs, path, conf);
  }

  private HadoopOutputFile(FileSystem fs, Path path, Configuration conf) {
    this.fs = fs;
    this.path = path;
    this.conf = conf;
  }

  /**
   * 创建文件输出流（不覆盖已存在文件）。
   *
   * <p>逻辑：调用 {@link FileSystem#create(Path, boolean)} 且 overwrite=false， 文件已存在时抛 {@link
   * AlreadyExistsException}，其他 IO 异常包装为 {@link RuntimeIOException}；最终通过 {@link HadoopStreams#wrap}
   * 包装为 {@link PositionOutputStream}。
   *
   * @return 可定位的输出流
   * @throws AlreadyExistsException 文件已存在时抛出
   */
  @Override
  public PositionOutputStream create() {
    try {
      return HadoopStreams.wrap(fs.create(path, false /* createOrOverwrite */));
    } catch (FileAlreadyExistsException e) {
      throw new AlreadyExistsException(e, "Path already exists: %s", path);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to create file: %s", path);
    }
  }

  /**
   * 创建文件输出流（覆盖已存在文件）。
   *
   * <p>逻辑：调用 {@link FileSystem#create(Path, boolean)} 且 overwrite=true， IO 异常包装为 {@link
   * RuntimeIOException}；最终通过 {@link HadoopStreams#wrap} 包装为 {@link PositionOutputStream}。
   *
   * @return 可定位的输出流
   */
  @Override
  public PositionOutputStream createOrOverwrite() {
    try {
      return HadoopStreams.wrap(fs.create(path, true /* createOrOverwrite */));
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to create file: %s", path);
    }
  }

  /** 获取文件对应的 Hadoop {@link Path}。 */
  public Path getPath() {
    return path;
  }

  /** 获取关联的 Hadoop {@link Configuration}。 */
  public Configuration getConf() {
    return conf;
  }

  /** 获取关联的 Hadoop {@link FileSystem}。 */
  public FileSystem getFileSystem() {
    return fs;
  }

  @Override
  public String location() {
    return path.toString();
  }

  /**
   * 把当前输出文件转换为对应的输入文件视图，便于写入后立即读取。
   *
   * @return 与同路径关联的 {@link HadoopInputFile}
   */
  @Override
  public InputFile toInputFile() {
    return HadoopInputFile.fromPath(path, fs, conf);
  }

  @Override
  public String toString() {
    return location();
  }

  /** 获取当前文件的原生加密参数。 */
  @Override
  public NativeFileCryptoParameters nativeCryptoParameters() {
    return nativeEncryptionParameters;
  }

  /**
   * 设置原生加密参数，供写入器在加密时使用。
   *
   * @param nativeCryptoParameters 原生文件加密参数
   */
  @Override
  public void setNativeCryptoParameters(NativeFileCryptoParameters nativeCryptoParameters) {
    this.nativeEncryptionParameters = nativeCryptoParameters;
  }
}
