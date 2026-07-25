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
package org.apache.iceberg.flink.sink.shuffle;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.base.LongSerializer;
import org.apache.flink.api.common.typeutils.base.MapSerializer;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：基于 Map 形式的 {@link MapDataStatistics} 序列化器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink/shuffle 子包）。
 *
 * <p>职责：把 {@code DataStatistics<MapDataStatistics, Map<RowData, Long>>} 形式的统计信息 序列化/反序列化到 Flink
 * 的数据视图，供算子状态与协调器间传输使用。
 *
 * <p>设计意图：复用 Flink {@link MapSerializer} 完成键值序列化， 仅在外层包装统计类型，便于状态快照与版本兼容。
 *
 * <p>上下游关系：上游为 Flink 的状态与算子事件机制， 下游为 {@link MapSerializer}（按 RowData→Long 序列化键值对）。
 */
@Internal
class MapDataStatisticsSerializer
    extends TypeSerializer<DataStatistics<MapDataStatistics, Map<RowData, Long>>> {
  private final MapSerializer<RowData, Long> mapSerializer;

  /** 通过 RowData 键序列化器构造 MapDataStatisticsSerializer。 */
  static TypeSerializer<DataStatistics<MapDataStatistics, Map<RowData, Long>>> fromKeySerializer(
      TypeSerializer<RowData> keySerializer) {
    return new MapDataStatisticsSerializer(
        new MapSerializer<>(keySerializer, LongSerializer.INSTANCE));
  }

  /** 构造序列化器，传入具体的 MapSerializer。 */
  MapDataStatisticsSerializer(MapSerializer<RowData, Long> mapSerializer) {
    this.mapSerializer = mapSerializer;
  }

  /** Map 形式统计为可变类型，返回 false。 */
  @Override
  public boolean isImmutableType() {
    return false;
  }

  /** 复制当前序列化器，若内部 MapSerializer 不变则返回自身。 */
  @SuppressWarnings("ReferenceEquality")
  @Override
  public TypeSerializer<DataStatistics<MapDataStatistics, Map<RowData, Long>>> duplicate() {
    MapSerializer<RowData, Long> duplicateMapSerializer =
        (MapSerializer<RowData, Long>) mapSerializer.duplicate();
    return (duplicateMapSerializer == mapSerializer)
        ? this
        : new MapDataStatisticsSerializer(duplicateMapSerializer);
  }

  /** 创建一个空的 MapDataStatistics 实例。 */
  @Override
  public DataStatistics<MapDataStatistics, Map<RowData, Long>> createInstance() {
    return new MapDataStatistics();
  }

  /**
   * 深拷贝 MapDataStatistics。
   *
   * <p>逻辑：遍历源 Map，使用键序列化器对每个 RowData 键进行拷贝， 值为不可变 Long，可直接复用。
   */
  @Override
  public DataStatistics<MapDataStatistics, Map<RowData, Long>> copy(DataStatistics obj) {
    Preconditions.checkArgument(
        obj instanceof MapDataStatistics, "Invalid data statistics type: " + obj.getClass());
    MapDataStatistics from = (MapDataStatistics) obj;
    TypeSerializer<RowData> keySerializer = mapSerializer.getKeySerializer();
    Map<RowData, Long> newMap = Maps.newHashMapWithExpectedSize(from.statistics().size());
    for (Map.Entry<RowData, Long> entry : from.statistics().entrySet()) {
      RowData newKey = keySerializer.copy(entry.getKey());
      // 值是 Long，无需拷贝
      newMap.put(newKey, entry.getValue());
    }

    return new MapDataStatistics(newMap);
  }

  /** 复制统计对象，忽略 reuse 参数（重用收益不大）。 */
  @Override
  public DataStatistics<MapDataStatistics, Map<RowData, Long>> copy(
      DataStatistics from, DataStatistics reuse) {
    // 重用收益不大
    return copy(from);
  }

  /** 返回 -1 表示变长。 */
  @Override
  public int getLength() {
    return -1;
  }

  /**
   * 把 MapDataStatistics 序列化到目标视图。
   *
   * <p>逻辑：校验类型后委托给内部 MapSerializer 序列化底层 Map。
   */
  @Override
  public void serialize(DataStatistics obj, DataOutputView target) throws IOException {
    Preconditions.checkArgument(
        obj instanceof MapDataStatistics, "Invalid data statistics type: " + obj.getClass());
    MapDataStatistics mapStatistics = (MapDataStatistics) obj;
    mapSerializer.serialize(mapStatistics.statistics(), target);
  }

  /** 从源视图反序列化为新的 MapDataStatistics。 */
  @Override
  public DataStatistics<MapDataStatistics, Map<RowData, Long>> deserialize(DataInputView source)
      throws IOException {
    return new MapDataStatistics(mapSerializer.deserialize(source));
  }

  /** 反序列化忽略 reuse（重用收益不大）。 */
  @Override
  public DataStatistics<MapDataStatistics, Map<RowData, Long>> deserialize(
      DataStatistics reuse, DataInputView source) throws IOException {
    // 重用收益不大
    return deserialize(source);
  }

  /** 把源视图数据流式拷贝到目标视图。 */
  @Override
  public void copy(DataInputView source, DataOutputView target) throws IOException {
    mapSerializer.copy(source, target);
  }

  /** 比较两个序列化器是否相等，依据内部 MapSerializer。 */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MapDataStatisticsSerializer)) {
      return false;
    }

    MapDataStatisticsSerializer other = (MapDataStatisticsSerializer) obj;
    return Objects.equals(mapSerializer, other.mapSerializer);
  }

  /** 返回内部 MapSerializer 的哈希值。 */
  @Override
  public int hashCode() {
    return mapSerializer.hashCode();
  }

  /** 返回序列化器的状态快照，用于状态恢复与版本兼容。 */
  @Override
  public TypeSerializerSnapshot<DataStatistics<MapDataStatistics, Map<RowData, Long>>>
      snapshotConfiguration() {
    return new MapDataStatisticsSerializerSnapshot(this);
  }

  /**
   * 文件级说明：MapDataStatisticsSerializer 的快照类，用于 Flink 状态恢复。
   *
   * <p>逻辑：通过 {@link CompositeTypeSerializerSnapshot} 把内部 MapSerializer 作为嵌套序列化器 进行版本化管理，保证状态兼容。
   */
  public static class MapDataStatisticsSerializerSnapshot
      extends CompositeTypeSerializerSnapshot<
          DataStatistics<MapDataStatistics, Map<RowData, Long>>, MapDataStatisticsSerializer> {
    private static final int CURRENT_VERSION = 1;

    // 构造器必须 public，否则 Flink 状态恢复会报「类没有（隐式）public 无参构造器」。
    @SuppressWarnings("checkstyle:RedundantModifier")
    public MapDataStatisticsSerializerSnapshot() {
      super(MapDataStatisticsSerializer.class);
    }

    @SuppressWarnings("checkstyle:RedundantModifier")
    public MapDataStatisticsSerializerSnapshot(MapDataStatisticsSerializer serializer) {
      super(serializer);
    }

    /** 返回当前快照版本号。 */
    @Override
    protected int getCurrentOuterSnapshotVersion() {
      return CURRENT_VERSION;
    }

    /** 提取外层序列化器中的嵌套序列化器数组。 */
    @Override
    protected TypeSerializer<?>[] getNestedSerializers(
        MapDataStatisticsSerializer outerSerializer) {
      return new TypeSerializer<?>[] {outerSerializer.mapSerializer};
    }

    /** 用嵌套序列化器数组重新构造外层序列化器。 */
    @Override
    protected MapDataStatisticsSerializer createOuterSerializerWithNestedSerializers(
        TypeSerializer<?>[] nestedSerializers) {
      @SuppressWarnings("unchecked")
      MapSerializer<RowData, Long> mapSerializer =
          (MapSerializer<RowData, Long>) nestedSerializers[0];
      return new MapDataStatisticsSerializer(mapSerializer);
    }
  }
}
