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
import java.util.Objects;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.table.data.RowData;

/**
 * 文件级说明：用于「数据统计或 RowData 记录」联合类型的序列化器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 sink/shuffle 子包）。
 *
 * <p>职责：把 {@link DataStatisticsOrRecord}（既可能是统计也可能是 RowData 记录） 序列化/反序列化到 Flink 数据视图，通过前置布尔标记区分二者。
 *
 * <p>设计意图：Flink shuffle 阶段既需要传输普通数据记录，也需要传输聚合统计， 用同一个序列化器统一处理可简化算子实现，并通过共享 buffer 提升性能。
 *
 * <p>上下游关系：上游为 Flink 算子中的 {@link DataStatisticsOrRecord} 实例， 下游为内部嵌套的 {@code statisticsSerializer}
 * 与 {@code recordSerializer}。
 */
@Internal
class DataStatisticsOrRecordSerializer<D extends DataStatistics<D, S>, S>
    extends TypeSerializer<DataStatisticsOrRecord<D, S>> {
  private final TypeSerializer<DataStatistics<D, S>> statisticsSerializer;
  private final TypeSerializer<RowData> recordSerializer;

  /** 构造联合序列化器，传入统计序列化器与记录序列化器。 */
  DataStatisticsOrRecordSerializer(
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer,
      TypeSerializer<RowData> recordSerializer) {
    this.statisticsSerializer = statisticsSerializer;
    this.recordSerializer = recordSerializer;
  }

  /** 联合类型为可变类型，返回 false。 */
  @Override
  public boolean isImmutableType() {
    return false;
  }

  /** 复制当前序列化器，若内部嵌套序列化器均不变则返回自身。 */
  @SuppressWarnings("ReferenceEquality")
  @Override
  public TypeSerializer<DataStatisticsOrRecord<D, S>> duplicate() {
    TypeSerializer<DataStatistics<D, S>> duplicateStatisticsSerializer =
        statisticsSerializer.duplicate();
    TypeSerializer<RowData> duplicateRowDataSerializer = recordSerializer.duplicate();
    if ((statisticsSerializer != duplicateStatisticsSerializer)
        || (recordSerializer != duplicateRowDataSerializer)) {
      return new DataStatisticsOrRecordSerializer<>(
          duplicateStatisticsSerializer, duplicateRowDataSerializer);
    } else {
      return this;
    }
  }

  /** 创建实例时默认构造一个空的 RowData 记录。 */
  @Override
  public DataStatisticsOrRecord<D, S> createInstance() {
    // 默认构造 RowData 实例
    return DataStatisticsOrRecord.fromRecord(recordSerializer.createInstance());
  }

  /**
   * 深拷贝联合对象，根据是否为记录分别走不同分支。
   *
   * <p>逻辑：若 from 是记录则拷贝 RowData；否则拷贝统计对象。
   */
  @Override
  public DataStatisticsOrRecord<D, S> copy(DataStatisticsOrRecord<D, S> from) {
    if (from.hasRecord()) {
      return DataStatisticsOrRecord.fromRecord(recordSerializer.copy(from.record()));
    } else {
      return DataStatisticsOrRecord.fromDataStatistics(
          statisticsSerializer.copy(from.dataStatistics()));
    }
  }

  /**
   * 带重用对象的深拷贝。
   *
   * <p>逻辑：根据是否为记录复用 reuse 中的内部对象，避免重复分配。
   */
  @Override
  public DataStatisticsOrRecord<D, S> copy(
      DataStatisticsOrRecord<D, S> from, DataStatisticsOrRecord<D, S> reuse) {
    DataStatisticsOrRecord<D, S> to;
    if (from.hasRecord()) {
      to = DataStatisticsOrRecord.reuseRecord(reuse, recordSerializer);
      RowData record = recordSerializer.copy(from.record(), to.record());
      to.record(record);
    } else {
      to = DataStatisticsOrRecord.reuseStatistics(reuse, statisticsSerializer);
      DataStatistics<D, S> statistics =
          statisticsSerializer.copy(from.dataStatistics(), to.dataStatistics());
      to.dataStatistics(statistics);
    }

    return to;
  }

  /** 返回 -1 表示变长。 */
  @Override
  public int getLength() {
    return -1;
  }

  /**
   * 把联合对象序列化到目标视图。
   *
   * <p>逻辑：先写布尔标记区分是记录还是统计，再委托相应内部序列化器写出内容。
   */
  @Override
  public void serialize(DataStatisticsOrRecord<D, S> statisticsOrRecord, DataOutputView target)
      throws IOException {
    if (statisticsOrRecord.hasRecord()) {
      target.writeBoolean(true);
      recordSerializer.serialize(statisticsOrRecord.record(), target);
    } else {
      target.writeBoolean(false);
      statisticsSerializer.serialize(statisticsOrRecord.dataStatistics(), target);
    }
  }

  /**
   * 从源视图反序列化。
   *
   * <p>逻辑：先读取布尔标记，再按标记读取记录或统计。
   */
  @Override
  public DataStatisticsOrRecord<D, S> deserialize(DataInputView source) throws IOException {
    boolean isRecord = source.readBoolean();
    if (isRecord) {
      return DataStatisticsOrRecord.fromRecord(recordSerializer.deserialize(source));
    } else {
      return DataStatisticsOrRecord.fromDataStatistics(statisticsSerializer.deserialize(source));
    }
  }

  /**
   * 带重用对象的反序列化。
   *
   * <p>逻辑：根据布尔标记复用 reuse 中的内部对象。
   */
  @Override
  public DataStatisticsOrRecord<D, S> deserialize(
      DataStatisticsOrRecord<D, S> reuse, DataInputView source) throws IOException {
    DataStatisticsOrRecord<D, S> to;
    boolean isRecord = source.readBoolean();
    if (isRecord) {
      to = DataStatisticsOrRecord.reuseRecord(reuse, recordSerializer);
      RowData record = recordSerializer.deserialize(to.record(), source);
      to.record(record);
    } else {
      to = DataStatisticsOrRecord.reuseStatistics(reuse, statisticsSerializer);
      DataStatistics<D, S> statistics =
          statisticsSerializer.deserialize(to.dataStatistics(), source);
      to.dataStatistics(statistics);
    }

    return to;
  }

  /**
   * 把源视图数据流式拷贝到目标视图。
   *
   * <p>逻辑：先拷贝布尔标记，再按标记委托相应内部序列化器。
   */
  @Override
  public void copy(DataInputView source, DataOutputView target) throws IOException {
    boolean hasRecord = source.readBoolean();
    target.writeBoolean(hasRecord);
    if (hasRecord) {
      recordSerializer.copy(source, target);
    } else {
      statisticsSerializer.copy(source, target);
    }
  }

  /** 比较两个序列化器是否相等，依据内部嵌套序列化器。 */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DataStatisticsOrRecordSerializer)) {
      return false;
    }

    @SuppressWarnings("unchecked")
    DataStatisticsOrRecordSerializer<D, S> other = (DataStatisticsOrRecordSerializer<D, S>) obj;
    return Objects.equals(statisticsSerializer, other.statisticsSerializer)
        && Objects.equals(recordSerializer, other.recordSerializer);
  }

  /** 返回内部嵌套序列化器的组合哈希值。 */
  @Override
  public int hashCode() {
    return Objects.hash(statisticsSerializer, recordSerializer);
  }

  /** 返回状态快照，用于状态恢复与版本兼容。 */
  @Override
  public TypeSerializerSnapshot<DataStatisticsOrRecord<D, S>> snapshotConfiguration() {
    return new DataStatisticsOrRecordSerializerSnapshot<>(this);
  }

  /**
   * 文件级说明：联合序列化器的状态快照类。
   *
   * <p>逻辑：把 statisticsSerializer 与 recordSerializer 作为嵌套序列化器 通过 {@link
   * CompositeTypeSerializerSnapshot} 进行版本化管理。
   */
  public static class DataStatisticsOrRecordSerializerSnapshot<D extends DataStatistics<D, S>, S>
      extends CompositeTypeSerializerSnapshot<
          DataStatisticsOrRecord<D, S>, DataStatisticsOrRecordSerializer<D, S>> {
    private static final int CURRENT_VERSION = 1;

    // 构造器必须 public，否则 Flink 状态恢复会报「类没有（隐式）public 无参构造器」。
    @SuppressWarnings("checkstyle:RedundantModifier")
    public DataStatisticsOrRecordSerializerSnapshot() {
      super(DataStatisticsOrRecordSerializer.class);
    }

    @SuppressWarnings("checkstyle:RedundantModifier")
    public DataStatisticsOrRecordSerializerSnapshot(
        DataStatisticsOrRecordSerializer<D, S> serializer) {
      super(serializer);
    }

    @SuppressWarnings("checkstyle:RedundantModifier")
    @Override
    protected int getCurrentOuterSnapshotVersion() {
      return CURRENT_VERSION;
    }

    /** 提取外层序列化器中的嵌套序列化器数组。 */
    @Override
    protected TypeSerializer<?>[] getNestedSerializers(
        DataStatisticsOrRecordSerializer<D, S> outerSerializer) {
      return new TypeSerializer<?>[] {
        outerSerializer.statisticsSerializer, outerSerializer.recordSerializer
      };
    }

    /** 用嵌套序列化器数组重新构造外层序列化器。 */
    @SuppressWarnings("unchecked")
    @Override
    protected DataStatisticsOrRecordSerializer<D, S> createOuterSerializerWithNestedSerializers(
        TypeSerializer<?>[] nestedSerializers) {
      TypeSerializer<DataStatistics<D, S>> statisticsSerializer =
          (TypeSerializer<DataStatistics<D, S>>) nestedSerializers[0];
      TypeSerializer<RowData> recordSerializer = (TypeSerializer<RowData>) nestedSerializers[1];
      return new DataStatisticsOrRecordSerializer<>(statisticsSerializer, recordSerializer);
    }
  }
}
