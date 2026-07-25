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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.encryption.NativeFileCryptoParameters;
import org.apache.iceberg.encryption.NativelyEncryptedFile;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.SeekableInputStream;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * 文件级说明：基于 Hadoop {@link FileSystem} API 实现的 {@link InputFile}。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 Hadoop {@link FileSystem} 与 {@link Path}，为 Iceberg 读取侧提供 {@link InputFile}
 *       抽象（位置、长度、可定位输入流）。
 *   <li>提供文件块位置（block locations）查询，支持数据本地性调度。
 *   <li>实现 {@link NativelyEncryptedFile}，承载原生加解密参数。
 * </ul>
 *
 * <p>设计意图：基于 Parquet 的 HadoopInputFile。{@code stat} 与 {@code length} 采用懒加载， 避免构造时立即发起远端
 * RPC；通过大量静态工厂方法（fromLocation/fromPath/fromStatus） 适配不同调用上下文（已知路径、已知 FileSystem、已知 FileStatus 等）。
 *
 * <p>上下游关系：由 {@link HadoopFileIO#newInputFile} 创建；被 Parquet/ORC/Avro 读取器通过 {@link #newStream()}
 * 获取输入流。
 */
public class HadoopInputFile implements InputFile, NativelyEncryptedFile {
  /** 表示无位置偏好的空数组，用于无法获取块位置的场景。 */
  public static final String[] NO_LOCATION_PREFERENCE = new String[0];

  private final String location;
  private final FileSystem fs;
  private final Path path;
  private final Configuration conf;
  private FileStatus stat = null;
  private Long length = null;
  private NativeFileCryptoParameters nativeDecryptionParameters;

  /**
   * 根据路径字符串与配置构造 {@link HadoopInputFile}。
   *
   * @param location 文件路径字符串
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromLocation(CharSequence location, Configuration conf) {
    FileSystem fs = Util.getFs(new Path(location.toString()), conf);
    return new HadoopInputFile(fs, location.toString(), conf);
  }

  /**
   * 根据路径字符串、已知长度与配置构造 {@link HadoopInputFile}。
   *
   * <p>逻辑：长度大于 0 时直接使用传入长度避免后续获取 FileStatus；否则按需懒加载。
   *
   * @param location 文件路径字符串
   * @param length 已知文件长度；小于等于 0 表示未知
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromLocation(
      CharSequence location, long length, Configuration conf) {
    FileSystem fs = Util.getFs(new Path(location.toString()), conf);
    if (length > 0) {
      return new HadoopInputFile(fs, location.toString(), length, conf);
    } else {
      return new HadoopInputFile(fs, location.toString(), conf);
    }
  }

  /**
   * 根据路径字符串与已解析的 {@link FileSystem} 构造 {@link HadoopInputFile}。
   *
   * @param location 文件路径字符串
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromLocation(CharSequence location, FileSystem fs) {
    return new HadoopInputFile(fs, location.toString(), fs.getConf());
  }

  /**
   * 根据路径字符串、已知长度与已解析的 {@link FileSystem} 构造 {@link HadoopInputFile}。
   *
   * @param location 文件路径字符串
   * @param length 已知文件长度
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromLocation(CharSequence location, long length, FileSystem fs) {
    return new HadoopInputFile(fs, location.toString(), length, fs.getConf());
  }

  /**
   * 根据 {@link Path} 与配置构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(Path path, Configuration conf) {
    FileSystem fs = Util.getFs(path, conf);
    return fromPath(path, fs, conf);
  }

  /**
   * 根据 {@link Path}、已知长度与配置构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param length 已知文件长度
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(Path path, long length, Configuration conf) {
    FileSystem fs = Util.getFs(path, conf);
    return fromPath(path, length, fs, conf);
  }

  /**
   * 根据 {@link Path} 与已解析的 {@link FileSystem} 构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(Path path, FileSystem fs) {
    return fromPath(path, fs, fs.getConf());
  }

  /**
   * 根据 {@link Path}、已知长度与已解析的 {@link FileSystem} 构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param length 已知文件长度
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(Path path, long length, FileSystem fs) {
    return fromPath(path, length, fs, fs.getConf());
  }

  /**
   * 根据 {@link Path}、已解析的 {@link FileSystem} 与配置构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param fs 已解析的 Hadoop 文件系统
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(Path path, FileSystem fs, Configuration conf) {
    return new HadoopInputFile(fs, path, conf);
  }

  /**
   * 根据 {@link Path}、已知长度、已解析的 {@link FileSystem} 与配置构造 {@link HadoopInputFile}。
   *
   * @param path Hadoop 路径
   * @param length 已知文件长度
   * @param fs 已解析的 Hadoop 文件系统
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromPath(
      Path path, long length, FileSystem fs, Configuration conf) {
    return new HadoopInputFile(fs, path, length, conf);
  }

  /**
   * 根据 {@link FileStatus} 与配置构造 {@link HadoopInputFile}，复用已知的文件状态。
   *
   * @param stat 已查询到的 Hadoop {@link FileStatus}
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromStatus(FileStatus stat, Configuration conf) {
    FileSystem fs = Util.getFs(stat.getPath(), conf);
    return fromStatus(stat, fs, conf);
  }

  /**
   * 根据 {@link FileStatus} 与已解析的 {@link FileSystem} 构造 {@link HadoopInputFile}。
   *
   * @param stat 已查询到的 Hadoop {@link FileStatus}
   * @param fs 已解析的 Hadoop 文件系统
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromStatus(FileStatus stat, FileSystem fs) {
    return fromStatus(stat, fs, fs.getConf());
  }

  /**
   * 根据 {@link FileStatus}、已解析的 {@link FileSystem} 与配置构造 {@link HadoopInputFile}。
   *
   * @param stat 已查询到的 Hadoop {@link FileStatus}
   * @param fs 已解析的 Hadoop 文件系统
   * @param conf Hadoop 配置
   * @return 新的 {@link HadoopInputFile} 实例
   */
  public static HadoopInputFile fromStatus(FileStatus stat, FileSystem fs, Configuration conf) {
    return new HadoopInputFile(fs, stat, conf);
  }

  private HadoopInputFile(FileSystem fs, String location, Configuration conf) {
    this.fs = fs;
    this.location = location;
    this.path = new Path(location);
    this.conf = conf;
  }

  private HadoopInputFile(FileSystem fs, String location, long length, Configuration conf) {
    Preconditions.checkArgument(length >= 0, "Invalid file length: %s", length);
    this.fs = fs;
    this.location = location;
    this.path = new Path(location);
    this.conf = conf;
    this.length = length;
  }

  private HadoopInputFile(FileSystem fs, Path path, Configuration conf) {
    this.fs = fs;
    this.path = path;
    this.location = path.toString();
    this.conf = conf;
  }

  private HadoopInputFile(FileSystem fs, Path path, long length, Configuration conf) {
    Preconditions.checkArgument(length >= 0, "Invalid file length: %s", length);
    this.fs = fs;
    this.path = path;
    this.location = path.toString();
    this.conf = conf;
    this.length = length;
  }

  private HadoopInputFile(FileSystem fs, FileStatus stat, Configuration conf) {
    this.fs = fs;
    this.path = stat.getPath();
    this.location = path.toString();
    this.stat = stat;
    this.conf = conf;
    this.length = stat.getLen();
  }

  /**
   * 懒加载获取文件的 {@link FileStatus}。
   *
   * <p>逻辑：若尚未查询过则调用 {@link FileSystem#getFileStatus(Path)} 获取； 文件不存在时抛 {@link NotFoundException}，其他
   * IO 异常包装为 {@link RuntimeIOException}。
   *
   * @return 当前文件的 {@link FileStatus}
   */
  private FileStatus lazyStat() {
    if (stat == null) {
      try {
        this.stat = fs.getFileStatus(path);
      } catch (FileNotFoundException e) {
        throw new NotFoundException(e, "File does not exist: %s", path);
      } catch (IOException e) {
        throw new RuntimeIOException(e, "Failed to get status for file: %s", path);
      }
    }
    return stat;
  }

  /**
   * 获取文件长度，按需懒加载。
   *
   * <p>逻辑：若构造时未传入长度，则通过 {@link #lazyStat()} 获取后取 {@code getLen()}。
   *
   * @return 文件长度（字节）
   */
  @Override
  public long getLength() {
    if (length == null) {
      this.length = lazyStat().getLen();
    }
    return length;
  }

  /**
   * 打开文件的可定位输入流。
   *
   * <p>逻辑：调用 {@link FileSystem#open(Path)} 并通过 {@link HadoopStreams#wrap} 包装为 {@link
   * SeekableInputStream}；文件不存在抛 {@link NotFoundException}，其他 IO 异常包装为 {@link RuntimeIOException}。
   *
   * @return 包装后的可定位输入流
   */
  @Override
  public SeekableInputStream newStream() {
    try {
      return HadoopStreams.wrap(fs.open(path));
    } catch (FileNotFoundException e) {
      throw new NotFoundException(e, "Failed to open input stream for file: %s", path);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to open input stream for file: %s", path);
    }
  }

  /** 获取关联的 Hadoop {@link Configuration}。 */
  public Configuration getConf() {
    return conf;
  }

  /** 获取关联的 Hadoop {@link FileSystem}。 */
  public FileSystem getFileSystem() {
    return fs;
  }

  /** 获取（按需懒加载）文件的 {@link FileStatus}。 */
  public FileStatus getStat() {
    return lazyStat();
  }

  /** 获取文件对应的 Hadoop {@link Path}。 */
  public Path getPath() {
    return path;
  }

  /**
   * 查询指定字节区间所在文件块的主机列表，用于数据本地性调度。
   *
   * <p>逻辑：调用 {@link FileSystem#getFileBlockLocations(Path, long, long)} 取出所有块， 汇总每个块的所有副本主机名后返回。IO
   * 异常包装为 {@link RuntimeIOException}。
   *
   * @param start 起始字节偏移
   * @param end 结束字节偏移（实际传入为 length，参见调用处）
   * @return 主机名数组
   */
  public String[] getBlockLocations(long start, long end) {
    List<String> hosts = Lists.newArrayList();
    try {
      for (BlockLocation bl : fs.getFileBlockLocations(path, start, end)) {
        Collections.addAll(hosts, bl.getHosts());
      }

      return hosts.toArray(NO_LOCATION_PREFERENCE);

    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to get block locations for path: %s", path);
    }
  }

  @Override
  public String location() {
    return location;
  }

  /**
   * 判断文件是否存在。
   *
   * <p>逻辑：调用 {@link #lazyStat()}，捕获 {@link NotFoundException} 视为不存在。
   *
   * @return true 表示文件存在
   */
  @Override
  public boolean exists() {
    try {
      return lazyStat() != null;
    } catch (NotFoundException e) {
      return false;
    }
  }

  /** 获取当前文件的原生解密参数。 */
  @Override
  public NativeFileCryptoParameters nativeCryptoParameters() {
    return nativeDecryptionParameters;
  }

  /**
   * 设置原生解密参数，供读取器在解密时使用。
   *
   * @param nativeCryptoParameters 原生文件解密参数
   */
  @Override
  public void setNativeCryptoParameters(NativeFileCryptoParameters nativeCryptoParameters) {
    this.nativeDecryptionParameters = nativeCryptoParameters;
  }

  @Override
  public String toString() {
    return path.toString();
  }
}
