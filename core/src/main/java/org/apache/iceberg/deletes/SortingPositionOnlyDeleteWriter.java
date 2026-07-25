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
package org.apache.iceberg.deletes;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.iceberg.io.DeleteWriteResult;
import org.apache.iceberg.io.FileWriter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Comparators;
import org.apache.iceberg.util.CharSequenceWrapper;
import org.roaringbitmap.longlong.PeekableLongIterator;
import org.roaringbitmap.longlong.Roaring64Bitmap;

/**
 * 排序型仅位置删除写入器：可处理无序且不携带行数据的位置删除。
 *
 * <p>所属模块：iceberg-core，deletes 包内删除文件写入的实现之一。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在内存中按数据文件路径维护各文件被删除行位置的 Roaring64 位图。
 *   <li>close 时按文件路径与行位置升序遍历所有位图，将排序后的位置删除写入底层 writer。
 * </ul>
 *
 * <p>设计意图：Iceberg 规范要求位置删除文件内的记录必须按文件路径与行位置排序。当上游无法 保证该顺序时，本写入器先在内存中以位图去重并暂存，再在关闭时统一排序输出，从而满足规范。
 * 由于仅存位置（不存行数据），内存开销可控。若上游已保证排序，应直接使用 {@link PositionDeleteWriter} 以避免额外的内存与排序开销。
 *
 * <p>上下游关系：包装一个底层 {@link FileWriter}（通常为 {@link PositionDeleteWriter}）， 由任务写入流程在删除记录无序时调用。
 *
 * @param <T> 被删除行数据的类型（本写入器不写入行数据，但仍保留泛型以适配接口）
 */
public class SortingPositionOnlyDeleteWriter<T>
    implements FileWriter<PositionDelete<T>, DeleteWriteResult> {

  private final FileWriter<PositionDelete<T>, DeleteWriteResult> writer;
  private final Map<CharSequenceWrapper, Roaring64Bitmap> positionsByPath;
  private final CharSequenceWrapper pathWrapper;
  private DeleteWriteResult result = null;

  /**
   * 构造排序型仅位置删除写入器。
   *
   * @param writer 底层位置删除写入器，负责实际落盘
   */
  public SortingPositionOnlyDeleteWriter(FileWriter<PositionDelete<T>, DeleteWriteResult> writer) {
    this.writer = writer;
    this.positionsByPath = Maps.newHashMap();
    this.pathWrapper = CharSequenceWrapper.wrap(null);
  }

  /**
   * 接收一条位置删除，将其位置加入对应数据文件的位图。
   *
   * <p>逻辑：取出记录的 path 与 position；用可复用的 pathWrapper 查询是否已有该文件的位图—— 若有则直接 add；若无则新建位图、add 并以新的 {@link
   * CharSequenceWrapper} 存入 map（避免 复用 wrapper 作为 key 导致后续被覆盖）。
   *
   * @param positionDelete 位置删除记录（仅使用 path 与 pos，忽略 row）
   */
  @Override
  public void write(PositionDelete<T> positionDelete) {
    CharSequence path = positionDelete.path();
    long position = positionDelete.pos();
    Roaring64Bitmap positions = positionsByPath.get(pathWrapper.set(path));
    if (positions != null) {
      positions.add(position);
    } else {
      positions = new Roaring64Bitmap();
      positions.add(position);
      positionsByPath.put(CharSequenceWrapper.wrap(path), positions);
    }
  }

  /** 返回底层 writer 的当前长度。 */
  @Override
  public long length() {
    return writer.length();
  }

  /** 返回写入结果，关闭前为 null。 */
  @Override
  public DeleteWriteResult result() {
    return result;
  }

  /**
   * 关闭写入器：若尚未产出结果，则执行排序写入。
   *
   * <p>设计要点：通过 result==null 保证幂等，避免重复写入。
   *
   * @throws IOException 写入或关闭底层 writer 时发生 IO 异常
   */
  @Override
  public void close() throws IOException {
    if (result == null) {
      this.result = writeDeletes();
    }
  }

  /**
   * 将内存中所有位图按路径与位置升序写入底层 writer。
   *
   * <p>逻辑：遍历排序后的路径列表，对每个路径的位图获取升序迭代器，逐个位置构造 {@link PositionDelete}（行数据置 null）并写入底层
   * writer；无论是否异常，最终在 finally 中关闭底层 writer 并返回其结果。
   *
   * @return 底层 writer 的写入结果
   * @throws IOException 写入或关闭时发生 IO 异常
   */
  private DeleteWriteResult writeDeletes() throws IOException {
    try {
      PositionDelete<T> positionDelete = PositionDelete.create();
      for (CharSequenceWrapper path : sortedPaths()) {
        // the iterator provides values in ascending sorted order
        PeekableLongIterator positions = positionsByPath.get(path).getLongIterator();
        while (positions.hasNext()) {
          long position = positions.next();
          writer.write(positionDelete.set(path.get(), position, null /* no row */));
        }
      }
    } finally {
      writer.close();
    }

    return writer.result();
  }

  /**
   * 返回按字符序列比较器升序排序后的路径列表。
   *
   * <p>逻辑：将 positionsByPath 的键集合转为列表，使用 {@link Comparators#charSequences()} 排序后返回。
   *
   * @return 排序后的路径包装列表
   */
  private List<CharSequenceWrapper> sortedPaths() {
    List<CharSequenceWrapper> paths = Lists.newArrayList(positionsByPath.keySet());
    paths.sort(Comparators.charSequences());
    return paths;
  }
}
