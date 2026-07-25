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
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.ContentScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.ScanTask;
import org.apache.iceberg.ScanTaskGroup;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.ResolvingFileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：hadoop 包内的通用工具类，封装文件系统访问、数据本地性判断与路径转换等实用方法。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>从 {@link Path} 获取对应的 {@link FileSystem}，并把 {@link IOException} 包装为 {@link
 *       RuntimeIOException}。
 *   <li>根据扫描任务查询文件块所在主机（block locations），用于数据本地性调度。
 *   <li>判断给定 FileIO/location 是否可能存在块位置信息（仅 HDFS 等本地性白名单文件系统为真）。
 *   <li>定义 version-hint 文件名常量；提供 URI 到字符串的解码工具方法。
 * </ul>
 *
 * <p>设计意图：把 hadoop 包内多处复用的逻辑集中到工具类，避免散落重复代码； 把受检异常统一转为运行期异常，简化 Iceberg 内部调用链；以白名单方式限制本地性优化，
 * 防止对象存储（如 S3）等无块位置概念的文件系统触发无谓查询。
 *
 * <p>上下游关系：被 {@link HadoopCatalog}、{@link HadoopFileIO}、{@link HadoopInputFile}、 {@link
 * HadoopOutputFile}、{@link HadoopTableOperations} 等同包类调用。
 */
public class Util {

  /** version-hint 文件名常量，用于记录表当前最新 metadata 版本号。 */
  public static final String VERSION_HINT_FILENAME = "version-hint.text";

  /** 支持块位置（数据本地性）的文件系统 scheme 白名单，目前仅包含 HDFS。 */
  private static final Set<String> LOCALITY_WHITELIST_FS = ImmutableSet.of("hdfs");

  private static final Logger LOG = LoggerFactory.getLogger(Util.class);

  private Util() {}

  /**
   * 根据路径与配置获取对应的 {@link FileSystem} 实例。
   *
   * <p>逻辑：委托 {@link Path#getFileSystem(Configuration)} 解析文件系统， 失败时把 {@link IOException} 包装为 {@link
   * RuntimeIOException} 抛出。
   *
   * @param path 需要解析的 Hadoop 路径
   * @param conf Hadoop 配置
   * @return 与该路径对应的 {@link FileSystem}
   * @throws RuntimeIOException 获取文件系统失败时抛出
   */
  public static FileSystem getFs(Path path, Configuration conf) {
    try {
      return path.getFileSystem(conf);
    } catch (IOException e) {
      throw new RuntimeIOException(e, "Failed to get file system for path: %s", path);
    }
  }

  /**
   * 查询组合扫描任务中所有文件所在主机集合，用于数据本地性调度。
   *
   * <p>逻辑：遍历任务中的每个 {@link FileScanTask}，根据其路径获取 {@link FileSystem}， 再调用 {@link
   * FileSystem#getFileBlockLocations(Path, long, long)} 取出每个块的所有副本主机名，
   * 汇总去重后返回。单个文件查询失败仅告警并跳过，不影响整体。
   *
   * @param task 组合扫描任务
   * @param conf Hadoop 配置
   * @return 主机名数组（已去重）
   */
  public static String[] blockLocations(CombinedScanTask task, Configuration conf) {
    Set<String> locationSets = Sets.newHashSet();
    for (FileScanTask f : task.files()) {
      Path path = new Path(f.file().path().toString());
      try {
        FileSystem fs = path.getFileSystem(conf);
        for (BlockLocation b : fs.getFileBlockLocations(path, f.start(), f.length())) {
          locationSets.addAll(Arrays.asList(b.getHosts()));
        }
      } catch (IOException ioe) {
        LOG.warn("Failed to get block locations for path {}", path, ioe);
      }
    }

    return locationSets.toArray(new String[0]);
  }

  /**
   * 查询扫描任务组中所有内容扫描任务的块位置主机集合。
   *
   * <p>逻辑：遍历任务组，仅处理 {@link ContentScanTask} 类型的子任务， 委托 {@link #blockLocations(FileIO,
   * ContentScanTask)} 获取主机列表后汇总去重。
   *
   * @param io 文件 IO，用于解析路径对应的输入文件
   * @param taskGroup 扫描任务组
   * @return 主机名数组；若无可获取的块位置则返回 {@link HadoopInputFile#NO_LOCATION_PREFERENCE}
   */
  public static String[] blockLocations(FileIO io, ScanTaskGroup<?> taskGroup) {
    Set<String> locations = Sets.newHashSet();

    for (ScanTask task : taskGroup.tasks()) {
      if (task instanceof ContentScanTask) {
        Collections.addAll(locations, blockLocations(io, (ContentScanTask<?>) task));
      }
    }

    return locations.toArray(HadoopInputFile.NO_LOCATION_PREFERENCE);
  }

  /**
   * 判断给定 FileIO 与 location 是否可能存在块位置信息（数据本地性）。
   *
   * <p>逻辑：仅当 IO 实际使用 HadoopFileIO（或 {@link ResolvingFileIO} 解析出的 IO 是 HadoopFileIO 子类）且对应 {@link
   * FileSystem} 的 scheme 在白名单（hdfs）中时返回 true， 否则返回 false。用于在对象存储等场景下跳过本地性优化。
   *
   * @param io 当前使用的 FileIO
   * @param location 文件路径
   * @return true 表示可能存在块位置信息
   */
  public static boolean mayHaveBlockLocations(FileIO io, String location) {
    if (usesHadoopFileIO(io, location)) {
      InputFile inputFile = io.newInputFile(location);
      if (inputFile instanceof HadoopInputFile) {
        String scheme = ((HadoopInputFile) inputFile).getFileSystem().getScheme();
        return LOCALITY_WHITELIST_FS.contains(scheme);

      } else {
        return false;
      }
    }

    return false;
  }

  /**
   * 查询单个内容扫描任务对应文件的块位置主机列表。
   *
   * <p>逻辑：若 IO 使用 HadoopFileIO，则把文件转为 {@link HadoopInputFile} 并取其块位置； 否则返回 {@link
   * HadoopInputFile#NO_LOCATION_PREFERENCE}。
   *
   * @param io 文件 IO
   * @param task 内容扫描任务
   * @return 主机名数组
   */
  private static String[] blockLocations(FileIO io, ContentScanTask<?> task) {
    String location = task.file().path().toString();
    if (usesHadoopFileIO(io, location)) {
      InputFile inputFile = io.newInputFile(location);
      if (inputFile instanceof HadoopInputFile) {
        return ((HadoopInputFile) inputFile).getBlockLocations(task.start(), task.length());

      } else {
        return HadoopInputFile.NO_LOCATION_PREFERENCE;
      }
    } else {
      return HadoopInputFile.NO_LOCATION_PREFERENCE;
    }
  }

  /**
   * 判断给定 FileIO 在处理指定 location 时是否实际使用 {@link HadoopFileIO}。
   *
   * <p>逻辑：若 IO 本身就是 {@link HadoopFileIO} 直接返回 true；若是 {@link ResolvingFileIO} 则按 location 解析其底层 IO
   * 类是否为 HadoopFileIO 子类； 其他情况返回 false。
   *
   * @param io 文件 IO
   * @param location 文件路径
   * @return true 表示底层会使用 HadoopFileIO
   */
  private static boolean usesHadoopFileIO(FileIO io, String location) {
    if (io instanceof HadoopFileIO) {
      return true;

    } else if (io instanceof ResolvingFileIO) {
      ResolvingFileIO resolvingFileIO = (ResolvingFileIO) io;
      return HadoopFileIO.class.isAssignableFrom(resolvingFileIO.ioClass(location));

    } else {
      return false;
    }
  }

  /**
   * 将 {@link URI} 转换为字符串路径，并对其进行 URL 解码。
   *
   * <p>逻辑：{@link URI#toString()} 不会解码转义字符（如 %25 不会还原为 %）， 此处借助 Hadoop {@link Path} 构造时执行的解码行为，通过
   * {@link Path#toString()} 得到已解码的路径字符串。该方法源自 Apache Spark。
   *
   * @param uri 待转换的 URI
   * @return 解码后的路径字符串
   */
  public static String uriToString(URI uri) {
    return new Path(uri).toString();
  }
}
