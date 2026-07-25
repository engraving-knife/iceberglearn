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
package org.apache.iceberg.nessie;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.BaseMetastoreTableOperations;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.NamespaceNotEmptyException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.io.FileIO;
import org.projectnessie.client.http.HttpClientException;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.error.NessieReferenceConflictException;
import org.projectnessie.error.ReferenceConflicts;
import org.projectnessie.model.Conflict;
import org.projectnessie.model.Conflict.ConflictType;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Nessie 的 Iceberg TableOperations 实现。
 *
 * <p>所属模块：iceberg-nessie。职责：把"元数据文件存 FS、指针存 Nessie"的模式落地—— {@code doRefresh} 从 Nessie 读取 {@link
 * IcebergTable} 内容条目获取 metadata location 并加载元数据； {@code doCommit} 写入新元数据文件后，通过 Nessie 的 CAS（compare
 * metadata pointer）原子更新条目， 失败时按 {@code CommitFailedException}/{@code CommitStateUnknownException}
 * 语义上报。
 *
 * <p>设计意图：继承 {@link BaseMetastoreTableOperations} 复用元数据文件读写与 {@code checkCommitStatus} 状态检查骨架；把
 * Nessie 的冲突异常（{@code NessieConflictException}/{@code NessieReferenceConflictException}） 适配为
 * Iceberg 的提交异常体系；通过 {@code NESSIE_COMMIT_ID_PROPERTY} 记录加载来源 commit ID， 便于分支切换时判断是否需要重新加载。
 */
public class NessieTableOperations extends BaseMetastoreTableOperations {

  private static final Logger LOG = LoggerFactory.getLogger(NessieTableOperations.class);

  /** {@link TableMetadata} 属性名，记录加载该元数据时的 Nessie commit ID。 */
  public static final String NESSIE_COMMIT_ID_PROPERTY = "nessie.commit.id";

  /** 属性名，设置后关闭 Nessie GC 相关告警。 */
  public static final String NESSIE_GC_NO_WARNING_PROPERTY = "nessie.gc.no-warning";

  private final NessieIcebergClient client;
  private final ContentKey key;
  private IcebergTable table;
  private final FileIO fileIO;
  private final Map<String, String> catalogOptions;

  /** 根据表标识符（{@link ContentKey}）构造 Nessie 表操作对象。 */
  NessieTableOperations(
      ContentKey key,
      NessieIcebergClient client,
      FileIO fileIO,
      Map<String, String> catalogOptions) {
    this.key = key;
    this.client = client;
    this.fileIO = fileIO;
    this.catalogOptions = catalogOptions;
  }

  /** 返回表名（{@link ContentKey} 的字符串形式）。 */
  @Override
  protected String tableName() {
    return key.toString();
  }

  /**
   * 从 Nessie 刷新表元数据。
   *
   * <p>逻辑：先 refresh Nessie 引用；再按 key 查询 Nessie 内容条目，若条目不存在且当前已有元数据则抛 {@link
   * NoSuchTableException}；若条目存在则解包为 {@link IcebergTable} 获取 metadata location； 最后通过 {@code
   * refreshFromMetadataLocation} 加载并补充 Nessie 特有属性。
   */
  @Override
  protected void doRefresh() {
    try {
      client.refresh();
    } catch (NessieNotFoundException e) {
      throw new RuntimeException(
          String.format(
              "Failed to refresh as ref '%s' " + "is no longer valid.", client.getRef().getName()),
          e);
    }
    String metadataLocation = null;
    Reference reference = client.getRef().getReference();
    try {
      Content content = client.getApi().getContent().key(key).reference(reference).get().get(key);
      LOG.debug("Content '{}' at '{}': {}", key, reference, content);
      if (content == null) {
        if (currentMetadataLocation() != null) {
          throw new NoSuchTableException("No such table '%s' in '%s'", key, reference);
        }
      } else {
        this.table =
            content
                .unwrap(IcebergTable.class)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            String.format(
                                "Cannot refresh iceberg table: "
                                    + "Nessie points to a non-Iceberg object for path: %s.",
                                key)));
        metadataLocation = table.getMetadataLocation();
      }
    } catch (NessieNotFoundException ex) {
      if (currentMetadataLocation() != null) {
        throw new NoSuchTableException(ex, "No such table '%s'", key);
      }
    }
    refreshFromMetadataLocation(
        metadataLocation,
        null,
        2,
        location ->
            NessieUtil.updateTableMetadataWithNessieSpecificProperties(
                TableMetadataParser.read(fileIO, location),
                location,
                table,
                key.toString(),
                reference));
  }

  /**
   * 提交表元数据到 Nessie。
   *
   * <p>逻辑：先写入新元数据文件；调用 {@code client.commitTable} 通过 Nessie CAS 原子更新条目。 冲突时尝试转为专用异常或抛 {@link
   * CommitFailedException}；网络异常抛 {@link CommitStateUnknownException}；引用不存在抛
   * RuntimeException。失败时删除已写的新元数据文件。
   */
  @Override
  protected void doCommit(TableMetadata base, TableMetadata metadata) {
    boolean newTable = base == null;
    String newMetadataLocation = writeNewMetadataIfRequired(newTable, metadata);

    String refName = client.refName();
    boolean failure = false;
    try {
      client.commitTable(base, metadata, newMetadataLocation, table, key);
    } catch (NessieConflictException ex) {
      failure = true;
      if (ex instanceof NessieReferenceConflictException) {
        // Throws a specialized exception, if possible
        maybeThrowSpecializedException((NessieReferenceConflictException) ex);
      }
      throw new CommitFailedException(
          ex,
          "Cannot commit: Reference hash is out of date. "
              + "Update the reference '%s' and try again",
          refName);
    } catch (HttpClientException ex) {
      // Intentionally catch all nessie-client-exceptions here and not just the "timeout" variant
      // to catch all kinds of network errors (e.g. connection reset). Network code implementation
      // details and all kinds of network devices can induce unexpected behavior. So better be
      // safe than sorry.
      throw new CommitStateUnknownException(ex);
    } catch (NessieNotFoundException ex) {
      failure = true;
      throw new RuntimeException(
          String.format("Cannot commit: Reference '%s' no longer exists", refName), ex);
    } finally {
      if (failure) {
        io().deleteFile(newMetadataLocation);
      }
    }
  }

  /**
   * 将 Nessie 引用冲突异常映射为 Iceberg 专用异常。
   *
   * <p>逻辑：仅当服务端返回单一冲突时，按冲突类型（命名空间缺失/非空、key 不存在/已存在） 抛出对应的 Iceberg 异常；否则不处理由调用方走通用冲突路径。
   */
  private static void maybeThrowSpecializedException(NessieReferenceConflictException ex) {
    // Check if the server returned 'ReferenceConflicts' information
    ReferenceConflicts referenceConflicts = ex.getErrorDetails();
    if (referenceConflicts == null) {
      return;
    }

    // Can only narrow down to a single exception, if there is only one conflict.
    List<Conflict> conflicts = referenceConflicts.conflicts();
    if (conflicts.size() != 1) {
      return;
    }

    Conflict conflict = conflicts.get(0);
    ConflictType conflictType = conflict.conflictType();
    if (conflictType != null) {
      switch (conflictType) {
        case NAMESPACE_ABSENT:
          throw new NoSuchNamespaceException(ex, "Namespace does not exist: %s", conflict.key());
        case NAMESPACE_NOT_EMPTY:
          throw new NamespaceNotEmptyException(ex, "Namespace not empty: %s", conflict.key());
        case KEY_DOES_NOT_EXIST:
          throw new NoSuchTableException(ex, "Table or view does not exist: %s", conflict.key());
        case KEY_EXISTS:
          throw new AlreadyExistsException(ex, "Table or view already exists: %s", conflict.key());
        default:
          // Explicit fall-through
          break;
      }
    }
  }

  /** 返回用于读写表文件的 {@link FileIO}。 */
  @Override
  public FileIO io() {
    return fileIO;
  }
}
