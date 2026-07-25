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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.iceberg.Accessor;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.io.CloseableGroup;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.io.CloseableIterator;
import org.apache.iceberg.io.FilterIterator;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.Filter;
import org.apache.iceberg.util.SortedMerge;
import org.apache.iceberg.util.StructLikeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 删除处理工具集：提供在读取侧应用位置删除与相等删除的静态方法集合。
 *
 * <p>所属模块：iceberg-core，deletes 包内的核心工具类，串联删除文件与数据行扫描。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将相等删除文件物化为 {@link StructLikeSet}，并据此过滤数据行。
 *   <li>将位置删除文件（按数据文件路径过滤后）物化为 {@link PositionDeleteIndex}。
 *   <li>提供流式位置删除过滤（streamingFilter）与标记（streamingMarker），以归并有序的删除位置 与数据行位置，避免一次性物化全部删除位置。
 *   <li>提供按数据文件路径提取位置删除的有序迭代（deletePositions）。
 * </ul>
 *
 * <p>设计意图：删除应用是读取路径的关键环节。本类提供两类策略——全量物化（toPositionIndex/
 * toEqualitySet，适合删除量较小或需要随机查找）与流式归并（streamingFilter/streamingMarker，
 * 适合删除量较大且位置有序的场景，按行推进消费删除位置，内存占用低）。位置删除文件内记录的 是 (file_path, pos)，需要先按数据文件路径过滤再提取 pos。内部定义了多个私有迭代器类
 * 实现归并过滤与标记逻辑。
 *
 * <p>上下游关系：依赖 {@link PositionDeleteIndex}、{@link StructLikeSet}、{@link SortedMerge} 等； 被读取任务（如 core
 * 的扫描读取、各引擎的 Iceberg 读取器）调用以应用删除。
 */
public class Deletes {

  private static final Logger LOG = LoggerFactory.getLogger(Deletes.class);

  private static final Schema POSITION_DELETE_SCHEMA =
      new Schema(MetadataColumns.DELETE_FILE_PATH, MetadataColumns.DELETE_FILE_POS);

  private static final Accessor<StructLike> FILENAME_ACCESSOR =
      POSITION_DELETE_SCHEMA.accessorForField(MetadataColumns.DELETE_FILE_PATH.fieldId());
  private static final Accessor<StructLike> POSITION_ACCESSOR =
      POSITION_DELETE_SCHEMA.accessorForField(MetadataColumns.DELETE_FILE_POS.fieldId());

  private Deletes() {}

  /**
   * 使用相等删除集合过滤数据行，移除匹配删除键的行。
   *
   * <p>逻辑：若删除集合为空则直接返回原行集（短路优化）；否则构造 {@link EqualitySetDeleteFilter} 过滤掉删除键命中删除集合的行。
   *
   * @param rows 待过滤的数据行
   * @param rowToDeleteKey 从行提取相等删除键的函数
   * @param deleteSet 相等删除键集合
   * @param <T> 行类型
   * @return 过滤后的行集（已移除被删除行）
   */
  public static <T> CloseableIterable<T> filter(
      CloseableIterable<T> rows, Function<T, StructLike> rowToDeleteKey, StructLikeSet deleteSet) {
    if (deleteSet.isEmpty()) {
      return rows;
    }

    EqualitySetDeleteFilter<T> equalityFilter =
        new EqualitySetDeleteFilter<>(rowToDeleteKey, deleteSet);
    return equalityFilter.filter(rows);
  }

  /**
   * 遍历数据行，对被删除的行调用标记函数（不移除行）。
   *
   * <p>逻辑：对每行用 isDeleted 判定，命中则调用 deleteMarker 标记，始终返回原行。 用于在保留行的同时打上删除标记（如标记列）。
   *
   * @param rows 待处理的数据行
   * @param isDeleted 判定行是否被删除的谓词
   * @param deleteMarker 对被删除行执行的标记函数
   * @param <T> 行类型
   * @return 处理后的行集（行被标记但未移除）
   */
  public static <T> CloseableIterable<T> markDeleted(
      CloseableIterable<T> rows, Predicate<T> isDeleted, Consumer<T> deleteMarker) {
    return CloseableIterable.transform(
        rows,
        row -> {
          if (isDeleted.test(row)) {
            deleteMarker.accept(row);
          }

          return row;
        });
  }

  /**
   * 过滤掉被删除的行，同时对被删除行计数。
   *
   * <p>逻辑：构造 Filter，对每行用 isDeleted 判定，命中则计数器加 1 并丢弃该行，否则保留。
   *
   * @param rows 待过滤的数据行
   * @param isDeleted 判定行是否被删除的谓词
   * @param counter 删除计数器
   * @param <T> 行类型
   * @return 仅保留未删除行的行集
   */
  public static <T> CloseableIterable<T> filterDeleted(
      CloseableIterable<T> rows, Predicate<T> isDeleted, DeleteCounter counter) {
    Filter<T> remainingRowsFilter =
        new Filter<T>() {
          @Override
          protected boolean shouldKeep(T item) {
            boolean deleted = isDeleted.test(item);
            if (deleted) {
              counter.increment();
            }

            return !deleted;
          }
        };

    return remainingRowsFilter.filter(rows);
  }

  /**
   * 将相等删除文件物化为 {@link StructLikeSet}。
   *
   * <p>逻辑：以 eqType 创建 StructLikeSet，将所有删除记录加入集合，try-with-resources 关闭源。 集合化后可进行 O(1) 的删除键匹配。
   *
   * @param eqDeletes 相等删除记录的可关闭迭代源
   * @param eqType 相等删除键的结构类型
   * @return 包含所有删除键的集合
   */
  public static StructLikeSet toEqualitySet(
      CloseableIterable<StructLike> eqDeletes, Types.StructType eqType) {
    try (CloseableIterable<StructLike> deletes = eqDeletes) {
      StructLikeSet deleteSet = StructLikeSet.create(eqType);
      Iterables.addAll(deleteSet, deletes);
      return deleteSet;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close equality delete source", e);
    }
  }

  /**
   * 将多个位置删除文件（针对指定数据文件路径）物化为 {@link PositionDeleteIndex}。
   *
   * <p>逻辑：构造 {@link DataFileFilter} 过滤出目标数据文件的位置删除记录，提取 pos 列， 合并所有文件的位置后委托 {@link
   * #toPositionIndex(CloseableIterable)} 构建索引。
   *
   * @param dataLocation 目标数据文件路径
   * @param deleteFiles 位置删除文件的可关闭迭代源列表
   * @param <T> 位置删除记录类型（需为 StructLike）
   * @return 该数据文件的位置删除索引
   */
  public static <T extends StructLike> PositionDeleteIndex toPositionIndex(
      CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles) {
    DataFileFilter<T> locationFilter = new DataFileFilter<>(dataLocation);
    List<CloseableIterable<Long>> positions =
        Lists.transform(
            deleteFiles,
            deletes ->
                CloseableIterable.transform(
                    locationFilter.filter(deletes), row -> (Long) POSITION_ACCESSOR.get(row)));
    return toPositionIndex(CloseableIterable.concat(positions));
  }

  /**
   * 将位置序列物化为 {@link PositionDeleteIndex}。
   *
   * <p>逻辑：创建 {@link BitmapPositionDeleteIndex}，遍历所有位置调用 delete 加入索引， try-with-resources 关闭源。
   *
   * @param posDeletes 位置删除序列
   * @return 位置删除索引
   */
  public static PositionDeleteIndex toPositionIndex(CloseableIterable<Long> posDeletes) {
    try (CloseableIterable<Long> deletes = posDeletes) {
      PositionDeleteIndex positionDeleteIndex = new BitmapPositionDeleteIndex();
      deletes.forEach(positionDeleteIndex::delete);
      return positionDeleteIndex;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to close position delete source", e);
    }
  }

  /**
   * 流式过滤：以行位置归并有序删除位置，过滤掉被删除的行。
   *
   * <p>逻辑：委托 {@link #streamingFilter(CloseableIterable, Function, CloseableIterable,
   * DeleteCounter)}， 使用一个新的 DeleteCounter。
   *
   * @param rows 数据行
   * @param rowToPosition 从行提取位置的函数
   * @param posDeletes 有序的删除位置序列
   * @param <T> 行类型
   * @return 过滤后的行集
   */
  public static <T> CloseableIterable<T> streamingFilter(
      CloseableIterable<T> rows,
      Function<T, Long> rowToPosition,
      CloseableIterable<Long> posDeletes) {
    return streamingFilter(rows, rowToPosition, posDeletes, new DeleteCounter());
  }

  /**
   * 流式过滤：以行位置归并有序删除位置，过滤掉被删除的行并对删除计数。
   *
   * <p>设计意图：删除位置与数据行位置均按升序推进，通过单次归并即可完成过滤， 无需将所有删除位置物化为索引，适合删除量较大的流式读取场景。
   *
   * @param rows 数据行
   * @param rowToPosition 从行提取位置的函数
   * @param posDeletes 有序的删除位置序列
   * @param counter 删除计数器
   * @param <T> 行类型
   * @return 过滤后的行集
   */
  public static <T> CloseableIterable<T> streamingFilter(
      CloseableIterable<T> rows,
      Function<T, Long> rowToPosition,
      CloseableIterable<Long> posDeletes,
      DeleteCounter counter) {
    return new PositionStreamDeleteFilter<>(rows, rowToPosition, posDeletes, counter);
  }

  /**
   * 流式标记：以行位置归并有序删除位置，对被删除行调用标记函数（不移除行）。
   *
   * @param rows 数据行
   * @param rowToPosition 从行提取位置的函数
   * @param posDeletes 有序的删除位置序列
   * @param markDeleted 对被删除行执行的标记函数
   * @param <T> 行类型
   * @return 处理后的行集（行被标记但未移除）
   */
  public static <T> CloseableIterable<T> streamingMarker(
      CloseableIterable<T> rows,
      Function<T, Long> rowToPosition,
      CloseableIterable<Long> posDeletes,
      Consumer<T> markDeleted) {
    return new PositionStreamDeleteMarker<>(rows, rowToPosition, posDeletes, markDeleted);
  }

  /**
   * 返回单个位置删除文件中针对指定数据文件的、有序的删除位置序列。
   *
   * <p>逻辑：委托 {@link #deletePositions(CharSequence, List)}，传入单元素列表。
   *
   * @param dataLocation 目标数据文件路径
   * @param deleteFile 位置删除文件
   * @return 有序的删除位置序列
   */
  public static CloseableIterable<Long> deletePositions(
      CharSequence dataLocation, CloseableIterable<StructLike> deleteFile) {
    return deletePositions(dataLocation, ImmutableList.of(deleteFile));
  }

  /**
   * 返回多个位置删除文件中针对指定数据文件的、全局有序的删除位置序列。
   *
   * <p>逻辑：构造 {@link DataFileFilter} 过滤出目标数据文件的记录并提取 pos 列， 再用 {@link SortedMerge}
   * 对各文件的位置流做归并排序，产出全局有序序列。
   *
   * @param dataLocation 目标数据文件路径
   * @param deleteFiles 位置删除文件列表
   * @param <T> 位置删除记录类型
   * @return 全局有序的删除位置序列
   */
  public static <T extends StructLike> CloseableIterable<Long> deletePositions(
      CharSequence dataLocation, List<CloseableIterable<T>> deleteFiles) {
    DataFileFilter<T> locationFilter = new DataFileFilter<>(dataLocation);
    List<CloseableIterable<Long>> positions =
        Lists.transform(
            deleteFiles,
            deletes ->
                CloseableIterable.transform(
                    locationFilter.filter(deletes), row -> (Long) POSITION_ACCESSOR.get(row)));

    return new SortedMerge<>(Long::compare, positions);
  }

  /**
   * 相等删除集合过滤器：基于 {@link StructLikeSet} 判断行是否被相等删除命中。
   *
   * <p>设计意图：将删除键集合化后进行 O(1) 匹配，配合 {@link Filter} 实现行级过滤。
   */
  private static class EqualitySetDeleteFilter<T> extends Filter<T> {
    private final StructLikeSet deletes;
    private final Function<T, StructLike> extractEqStruct;

    protected EqualitySetDeleteFilter(Function<T, StructLike> extractEq, StructLikeSet deletes) {
      this.extractEqStruct = extractEq;
      this.deletes = deletes;
    }

    /** 保留条件：行的删除键不在删除集合中。 */
    @Override
    protected boolean shouldKeep(T row) {
      return !deletes.contains(extractEqStruct.apply(row));
    }
  }

  /**
   * 流式位置删除归并迭代基类：以行位置推进消费有序的删除位置。
   *
   * <p>设计意图：数据行与删除位置均升序，通过维护 nextDeletePos 并按行推进删除迭代器， 实现单次归并判定，避免物化全部删除位置。子类通过 {@link
   * #applyDelete} 决定是过滤还是标记。
   */
  private abstract static class PositionStreamDeleteIterable<T> extends CloseableGroup
      implements CloseableIterable<T> {
    private final CloseableIterable<T> rows;
    private final CloseableIterator<Long> deletePosIterator;
    private final Function<T, Long> rowToPosition;
    private long nextDeletePos;

    PositionStreamDeleteIterable(
        CloseableIterable<T> rows,
        Function<T, Long> rowToPosition,
        CloseableIterable<Long> deletePositions) {
      this.rows = rows;
      this.rowToPosition = rowToPosition;
      this.deletePosIterator = deletePositions.iterator();
    }

    /**
     * 返回应用删除处理后的行迭代器。
     *
     * <p>逻辑：若存在删除位置则预取首个 nextDeletePos 并对行迭代器套用 applyDelete； 否则直接返回行迭代器。将两个迭代器注册到 CloseableGroup
     * 以统一关闭。
     */
    @Override
    public CloseableIterator<T> iterator() {
      CloseableIterator<T> iter;
      if (deletePosIterator.hasNext()) {
        nextDeletePos = deletePosIterator.next();
        iter = applyDelete(rows.iterator(), deletePosIterator);
      } else {
        iter = rows.iterator();
      }

      addCloseable(iter);
      addCloseable(deletePosIterator);

      return iter;
    }

    /**
     * 判断当前行是否被删除。
     *
     * <p>逻辑：取当前行位置 currentPos；若小于 nextDeletePos 则肯定未删除；否则消费删除迭代器 直到 nextDeletePos 越过
     * currentPos，期间若任一删除位置等于 currentPos 则判定为已删除。
     *
     * @param row 当前行
     * @return 若该行位置命中删除位置返回 true
     */
    boolean isDeleted(T row) {
      long currentPos = rowToPosition.apply(row);
      if (currentPos < nextDeletePos) {
        return false;
      }

      // consume delete positions until the next is past the current position
      boolean isDeleted = currentPos == nextDeletePos;
      while (deletePosIterator.hasNext() && nextDeletePos <= currentPos) {
        this.nextDeletePos = deletePosIterator.next();
        if (!isDeleted && currentPos == nextDeletePos) {
          // if any delete position matches the current position
          isDeleted = true;
        }
      }

      return isDeleted;
    }

    /**
     * 对行迭代器套用删除处理（过滤或标记），由子类实现。
     *
     * @param items 行迭代器
     * @param deletePositions 删除位置迭代器
     * @return 处理后的行迭代器
     */
    protected abstract CloseableIterator<T> applyDelete(
        CloseableIterator<T> items, CloseableIterator<Long> deletePositions);
  }

  /** 流式位置删除过滤器：过滤掉被删除的行并计数。 */
  private static class PositionStreamDeleteFilter<T> extends PositionStreamDeleteIterable<T> {
    private final DeleteCounter counter;

    PositionStreamDeleteFilter(
        CloseableIterable<T> rows,
        Function<T, Long> rowToPosition,
        CloseableIterable<Long> deletePositions,
        DeleteCounter counter) {
      super(rows, rowToPosition, deletePositions);
      this.counter = counter;
    }

    /**
     * 返回过滤迭代器：丢弃被删除行并对删除计数。
     *
     * <p>逻辑：基于 {@link FilterIterator}，shouldKeep 中调用 isDeleted，命中则计数并丢弃； close 时关闭删除位置迭代器与底层迭代器。
     */
    @Override
    protected CloseableIterator<T> applyDelete(
        CloseableIterator<T> items, CloseableIterator<Long> deletePositions) {
      return new FilterIterator<T>(items) {
        @Override
        protected boolean shouldKeep(T item) {
          boolean deleted = isDeleted(item);
          if (deleted) {
            counter.increment();
          }

          return !deleted;
        }

        @Override
        public void close() {
          try {
            deletePositions.close();
          } catch (IOException e) {
            LOG.warn("Error closing delete file", e);
          }
          super.close();
        }
      };
    }
  }

  /** 流式位置删除标记器：对被删除的行调用标记函数（不移除行）。 */
  private static class PositionStreamDeleteMarker<T> extends PositionStreamDeleteIterable<T> {
    private final Consumer<T> markDeleted;

    PositionStreamDeleteMarker(
        CloseableIterable<T> rows,
        Function<T, Long> rowToPosition,
        CloseableIterable<Long> deletePositions,
        Consumer<T> markDeleted) {
      super(rows, rowToPosition, deletePositions);
      this.markDeleted = markDeleted;
    }

    /**
     * 返回标记迭代器：对被删除行调用标记函数，但保留所有行。
     *
     * <p>逻辑：自定义 CloseableIterator，next 时取行并用 isDeleted 判定，命中则调用 markDeleted； close
     * 时分别关闭删除位置与数据行迭代器，IO 异常仅记录警告不影响关闭流程。
     */
    @Override
    protected CloseableIterator<T> applyDelete(
        CloseableIterator<T> items, CloseableIterator<Long> deletePositions) {

      return new CloseableIterator<T>() {
        @Override
        public void close() {
          try {
            deletePositions.close();
          } catch (IOException e) {
            LOG.warn("Error closing delete file", e);
          }
          try {
            items.close();
          } catch (IOException e) {
            LOG.warn("Error closing data file", e);
          }
        }

        @Override
        public boolean hasNext() {
          return items.hasNext();
        }

        @Override
        public T next() {
          T row = items.next();
          if (isDeleted(row)) {
            markDeleted.accept(row);
          }
          return row;
        }
      };
    }
  }

  /**
   * 数据文件路径过滤器：从位置删除记录中筛出针对指定数据文件的记录。
   *
   * <p>设计意图：位置删除文件可能包含多个数据文件的删除记录，读取某数据文件时需先按 path 过滤。 {@link #charSeqEquals} 针对 CharSequence
   * 做了反向比较优化——文件路径前缀通常相同（如
   * "s3:/bucket/db/table/data/partition/00000-0-[uuid]-00001.parquet"），差异多出现在尾部 uuid，
   * 故从尾部向前比较可更快命中差异。
   */
  private static class DataFileFilter<T extends StructLike> extends Filter<T> {
    private final CharSequence dataLocation;

    DataFileFilter(CharSequence dataLocation) {
      this.dataLocation = dataLocation;
    }

    /** 保留条件：位置删除记录的 file_path 与目标数据文件路径相等。 */
    @Override
    protected boolean shouldKeep(T posDelete) {
      return charSeqEquals(dataLocation, (CharSequence) FILENAME_ACCESSOR.get(posDelete));
    }

    /**
     * 比较两个 CharSequence 是否内容相等。
     *
     * <p>逻辑：先做引用相等与长度检查短路；若同为 String 则用 hashCode 快速排除； 最后从尾部向前逐字符比较（路径前缀通常相同，差异多在尾部，反向比较更快）。
     *
     * @param s1 第一个字符序列
     * @param s2 第二个字符序列
     * @return 内容相等返回 true
     */
    private boolean charSeqEquals(CharSequence s1, CharSequence s2) {
      if (s1 == s2) {
        return true;
      }

      int count = s1.length();
      if (count != s2.length()) {
        return false;
      }

      if (s1 instanceof String && s2 instanceof String && s1.hashCode() != s2.hashCode()) {
        return false;
      }

      // File paths inside a delete file normally have more identical chars at the beginning. For
      // example, a typical
      // path is like "s3:/bucket/db/table/data/partition/00000-0-[uuid]-00001.parquet".
      // The uuid is where the difference starts. So it's faster to find the first diff backward.
      for (int i = count - 1; i >= 0; i--) {
        if (s1.charAt(i) != s2.charAt(i)) {
          return false;
        }
      }
      return true;
    }
  }
}
