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
package org.apache.hadoop.hive.ql.io.orc;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.ql.io.AcidInputFormat;
import org.apache.hadoop.hive.ql.io.AcidUtils;
import org.apache.hadoop.hive.ql.io.ColumnarSplit;
import org.apache.hadoop.hive.ql.io.LlapAwareSplit;
import org.apache.hadoop.hive.ql.io.SyntheticFileId;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableUtils;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.orc.OrcProto;
import org.apache.orc.impl.OrcTail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：从 Hive 3.x 源码复制而来的 ORC 文件分片表示类。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块）。
 *
 * <p>职责：在 ORC 与 Hive 3.x 及 shaded ORC 库之间存在兼容性问题时，
 * 提供与 Hive 内置一致的分片对象，使 Iceberg 向量化读取能够正确序列化
 * OrcTail、ACID 增量、根目录等附加信息。
 *
 * <p>设计意图：临时性兼容层，当 Hive 4 发布且 Iceberg 切换依赖后应被移除。
 *
 * <p>上下游关系：上游为 {@code HiveVectorizedReader} 的 ORC 分支，
 * 下游为 Hive 的 {@code VectorizedOrcInputFormat}。
 */
public class OrcSplit extends FileSplit implements ColumnarSplit, LlapAwareSplit {
  private static final Logger LOG = LoggerFactory.getLogger(OrcSplit.class);
  private OrcTail orcTail;
  private boolean hasFooter;
  /** 表示文件类型为 {@link AcidUtils.AcidBaseFileType#ORIGINAL_BASE}，即非 ACID 原始文件。 */
  private boolean isOriginal;

  private boolean hasBase;
  // partition root
  private Path rootDir;
  private final List<AcidInputFormat.DeltaMetaData> deltas = Lists.newArrayList();
  private long projColsUncompressedSize;
  private transient Object fileKey;
  private long fileLen;

  static final int HAS_SYNTHETIC_FILEID_FLAG = 16;
  static final int HAS_LONG_FILEID_FLAG = 8;
  static final int BASE_FLAG = 4;
  static final int ORIGINAL_FLAG = 2;
  static final int FOOTER_FLAG = 1;

  /**
   * 受保护的无参构造。
   *
   * <p>Hadoop 0.20/1.x 中 FileSplit 的无参构造为包私有，无法直接调用，
   * 这里传 null 给父构造仅用于创建对象后再通过 readFields 反序列化。
   */
  protected OrcSplit() {
    // The FileSplit() constructor in hadoop 0.20 and 1.x is package private so can't use it.
    // This constructor is used to create the object and then call readFields()
    // so just pass nulls to this super constructor.
    super(null, 0, 0, (String[]) null);
  }

  /**
   * 构造 OrcSplit 实例。
   *
   * <p>逻辑：先调用父类 FileSplit 完成基础字段序列化，再保存 OrcTail、ACID 标记、
   * 根目录、增量列表等 ORC 专用信息。fileLen 若小于等于 0 则置为 Long.MAX_VALUE，
   * 让 ORC reader 从文件系统自行获取长度。
   */
  public OrcSplit(
      Path path,
      Object fileId,
      long offset,
      long length,
      String[] hosts,
      OrcTail orcTail,
      boolean isOriginal,
      boolean hasBase,
      List<AcidInputFormat.DeltaMetaData> deltas,
      long projectedDataSize,
      long fileLen,
      Path rootDir) {
    super(path, offset, length, hosts);
    // For HDFS, we could avoid serializing file ID and just replace the path with inode-based
    // path. However, that breaks bunch of stuff because Hive later looks up things by split path.
    this.fileKey = fileId;
    this.orcTail = orcTail;
    hasFooter = this.orcTail != null;
    this.isOriginal = isOriginal;
    this.hasBase = hasBase;
    this.rootDir = rootDir;
    this.deltas.addAll(deltas);
    this.projColsUncompressedSize = projectedDataSize <= 0 ? length : projectedDataSize;
    // setting file length to Long.MAX_VALUE will let orc reader read file length from file system
    this.fileLen = fileLen <= 0 ? Long.MAX_VALUE : fileLen;
  }

  /**
   * 序列化分片内容，包括父类基础字段与 ORC 附加 payload。
   *
   * @param out 数据输出目标
   * @throws IOException 写入失败时抛出
   */
  @Override
  public void write(DataOutput out) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    // serialize path, offset, length using FileSplit
    super.write(dos);
    int required = bos.size();

    // write addition payload required for orc
    writeAdditionalPayload(dos);
    int additional = bos.size() - required;

    out.write(bos.toByteArray());
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Writing additional {} bytes to OrcSplit as payload. Required {} bytes.",
          additional,
          required);
    }
  }

  /**
   * 写入 ORC 专用附加 payload：标记位、ACID 增量、OrcTail、文件 ID、文件长度、根目录。
   *
   * @param out 数据输出流
   * @throws IOException 写入失败时抛出
   */
  private void writeAdditionalPayload(final DataOutputStream out) throws IOException {
    boolean isFileIdLong = fileKey instanceof Long;
    boolean isFileIdWritable = fileKey instanceof Writable;
    int flags =
        (hasBase ? BASE_FLAG : 0)
            | (isOriginal ? ORIGINAL_FLAG : 0)
            | (hasFooter ? FOOTER_FLAG : 0)
            | (isFileIdLong ? HAS_LONG_FILEID_FLAG : 0)
            | (isFileIdWritable ? HAS_SYNTHETIC_FILEID_FLAG : 0);
    out.writeByte(flags);
    out.writeInt(deltas.size());
    for (AcidInputFormat.DeltaMetaData delta : deltas) {
      delta.write(out);
    }
    if (hasFooter) {
      OrcProto.FileTail fileTail = orcTail.getMinimalFileTail();
      byte[] tailBuffer = fileTail.toByteArray();
      int tailLen = tailBuffer.length;
      WritableUtils.writeVInt(out, tailLen);
      out.write(tailBuffer);
    }
    if (isFileIdLong) {
      out.writeLong(((Long) fileKey).longValue());
    } else if (isFileIdWritable) {
      ((Writable) fileKey).write(out);
    }
    out.writeLong(fileLen);
    out.writeUTF(rootDir.toString());
  }

  /**
   * 反序列化分片内容，与 {@link #write} 对应。
   *
   * @param in 数据输入源
   * @throws IOException 读取失败或字段不合法时抛出
   */
  @Override
  public void readFields(DataInput in) throws IOException {
    // deserialize path, offset, length using FileSplit
    super.readFields(in);

    byte flags = in.readByte();
    hasFooter = (FOOTER_FLAG & flags) != 0;
    isOriginal = (ORIGINAL_FLAG & flags) != 0;
    hasBase = (BASE_FLAG & flags) != 0;
    boolean hasLongFileId = (HAS_LONG_FILEID_FLAG & flags) != 0;
    boolean hasWritableFileId = (HAS_SYNTHETIC_FILEID_FLAG & flags) != 0;
    if (hasLongFileId && hasWritableFileId) {
      throw new IOException("Invalid split - both file ID types present");
    }

    deltas.clear();
    int numDeltas = in.readInt();
    for (int i = 0; i < numDeltas; i++) {
      AcidInputFormat.DeltaMetaData dmd = new AcidInputFormat.DeltaMetaData();
      dmd.readFields(in);
      deltas.add(dmd);
    }
    if (hasFooter) {
      int tailLen = WritableUtils.readVInt(in);
      byte[] tailBuffer = new byte[tailLen];
      in.readFully(tailBuffer);
      OrcProto.FileTail fileTail = OrcProto.FileTail.parseFrom(tailBuffer);
      orcTail = new OrcTail(fileTail, null);
    }
    if (hasLongFileId) {
      fileKey = in.readLong();
    } else if (hasWritableFileId) {
      SyntheticFileId fileId = new SyntheticFileId();
      fileId.readFields(in);
      this.fileKey = fileId;
    }
    fileLen = in.readLong();
    rootDir = new Path(in.readUTF());
  }

  /** 返回 ORC 文件尾元数据，可能为 null。 */
  public OrcTail getOrcTail() {
    return orcTail;
  }

  /** 是否携带 OrcTail 元数据。 */
  public boolean hasFooter() {
    return hasFooter;
  }

  /**
   * 返回文件是否为非 ACID 原始文件。
   *
   * <p>说明：这类文件 schema 不含 ACID 元数据列，可能因 "load data" 命令位于
   * delta_x_y/ 或 base_x 目录，也可能因表从非 ACID 转换为 ACID 表而位于分区或表根目录，
   * 甚至是 union 子查询写入的临时目录。
   *
   * @return 若文件 schema 不含 ACID 元数据列则返回 true
   */
  public boolean isOriginal() {
    return isOriginal;
  }

  /** 是否存在 base 文件。 */
  public boolean hasBase() {
    return hasBase;
  }

  /** 返回分区根目录。 */
  public Path getRootDir() {
    return rootDir;
  }

  /** 返回 ACID 增量列表。 */
  public List<AcidInputFormat.DeltaMetaData> getDeltas() {
    return deltas;
  }

  /** 返回文件总长度。 */
  public long getFileLength() {
    return fileLen;
  }

  /**
   * 判断是否为 ACID 分片。
   *
   * <p>注意：返回 true 一定是 ACID；返回 false 不能确定，可能为 ACID 或非 ACID。
   *
   * @return 若存在 base 或 deltas 则返回 true
   */
  public boolean isAcid() {
    return hasBase || deltas.size() > 0;
  }

  /** 返回投影列未压缩大小。 */
  public long getProjectedColumnsUncompressedSize() {
    return projColsUncompressedSize;
  }

  /** 返回文件标识。 */
  public Object getFileKey() {
    return fileKey;
  }

  /** 返回列式投影大小，等于投影列未压缩大小。 */
  @Override
  public long getColumnarProjectionSize() {
    return projColsUncompressedSize;
  }

  /**
   * 判断当前分片是否可使用 LLAP IO 缓存读取。
   *
   * <p>逻辑：根据是否为原始文件、是否 ACID 全表扫描、是否有 delta、是否向量化、
   * 是否启用 LLAP ACID 等条件综合判断，仅在确定可走 LLAP 路径时返回 true。
   *
   * @param conf 任务配置
   * @return 是否可使用 LLAP IO
   */
  @Override
  public boolean canUseLlapIo(Configuration conf) {
    final boolean hasDelta = deltas != null && !deltas.isEmpty();
    final boolean isAcidRead = AcidUtils.isFullAcidScan(conf);
    final boolean isVectorized = HiveConf.getBoolVar(conf, ConfVars.HIVE_VECTORIZATION_ENABLED);
    Boolean isSplitUpdate = null;
    if (isAcidRead) {
      final AcidUtils.AcidOperationalProperties acidOperationalProperties =
          AcidUtils.getAcidOperationalProperties(conf);
      isSplitUpdate = acidOperationalProperties.isSplitUpdate();
    }

    if (isOriginal) {
      if (!isAcidRead && !hasDelta) {
        // Original scan only
        return true;
      }
    } else {
      boolean isAcidEnabled = HiveConf.getBoolVar(conf, ConfVars.LLAP_IO_ACID_ENABLED);
      if (isAcidEnabled && isAcidRead && hasBase && isVectorized) {
        if (hasDelta) {
          if (isSplitUpdate) { // Base with delete deltas
            return true;
          }
        } else {
          // Base scan only
          return true;
        }
      }
    }
    return false;
  }

  /** 返回可读的分片摘要字符串。 */
  @Override
  public String toString() {
    return "OrcSplit ["
        + getPath()
        + ", start="
        + getStart()
        + ", length="
        + getLength()
        + ", isOriginal="
        + isOriginal
        + ", fileLength="
        + fileLen
        + ", hasFooter="
        + hasFooter
        + ", hasBase="
        + hasBase
        + ", deltas="
        + (deltas == null ? 0 : deltas.size())
        + "]";
  }
}
