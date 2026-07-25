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
package org.apache.iceberg.mr.hive;

import java.util.List;
import java.util.Map;
import org.apache.hadoop.hive.serde2.objectinspector.ListObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.MapObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.mr.hive.serde.objectinspector.WriteObjectInspector;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.schema.SchemaWithPartnerVisitor;
import org.apache.iceberg.types.Type.PrimitiveType;
import org.apache.iceberg.types.Types.ListType;
import org.apache.iceberg.types.Types.MapType;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.iceberg.types.Types.StructType;

/**
 * 文件级说明：Hive 行对象 -> Iceberg Record 反序列化器。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，负责 Hive 写出路径 的数据转换，被 HiveIcebergRecordWriter
 * / HiveIcebergSerDe 使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 Hive 计算结果行（按 ObjectInspector 描述的结构）转换为 Iceberg {@link Record}。
 *   <li>基于 Schema 与“写端/源端”两套 ObjectInspector 构造字段级反序列化器树。
 *   <li>在需要类型转换时调用 {@link WriteObjectInspector#convert(Object)} 做修正。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 {@link SchemaWithPartnerVisitor} 按结构同构地遍历 Iceberg schema 与 Hive
 *       ObjectInspector，把“每个字段的取值逻辑”编译成 {@link FieldDeserializer} lambda， 避免每次反序列化都重新反射。
 *   <li>“双 inspector”模式：sourceInspector 用于从 Hive 行里取原始值，writerInspector 仅在需要类型适配时（实现 {@link
 *       WriteObjectInspector}）调用 convert。
 *   <li>{@link FixNameMappingObjectInspectorPair} 处理 Hive 查询结果列名与 Iceberg 列名 不一致问题：Hive
 *       结果按列顺序对齐，因此通过位置建立名字映射。
 *   <li>struct 反序列化复用 {@link GenericRecord#copy()} 作为模板，省去 NAME_MAP_CACHE 查找， 提升性能。
 * </ul>
 *
 * <p>上下游关系：上游为 HiveIcebergRecordWriter（写入 Iceberg 表前转换行）；下游依赖 iceberg-core 的 {@link
 * SchemaWithPartnerVisitor}、{@link GenericRecord} 以及 Hive serde2 的 ObjectInspector 体系。
 */
class Deserializer {
  private FieldDeserializer fieldDeserializer;

  /**
   * 反序列化器构造器，采用 Builder 模式收集所需输入。
   *
   * <p>需提供 Iceberg {@link Schema}、写端 {@link StructObjectInspector}、源端 {@link
   * StructObjectInspector}，三者共同决定字段映射与类型转换策略。
   */
  static class Builder {
    private Schema schema;
    private StructObjectInspector writerInspector;
    private StructObjectInspector sourceInspector;

    /** 指定目标 Iceberg 表 schema。 */
    Builder schema(Schema mainSchema) {
      this.schema = mainSchema;
      return this;
    }

    /** 指定写端 ObjectInspector（Iceberg 侧，仅在需要类型转换时使用）。 */
    Builder writerInspector(StructObjectInspector inspector) {
      this.writerInspector = inspector;
      return this;
    }

    /** 指定源端 ObjectInspector（Hive 侧，用于真正读取行数据）。 */
    Builder sourceInspector(StructObjectInspector inspector) {
      this.sourceInspector = inspector;
      return this;
    }

    /** 构造 {@link Deserializer} 实例。 */
    Deserializer build() {
      return new Deserializer(schema, new ObjectInspectorPair(writerInspector, sourceInspector));
    }
  }

  /**
   * 将 Hive 结果对象反序列化为 Iceberg {@link Record}。
   *
   * @param data Hive 行数据对象
   * @return 转换得到的 Iceberg Record
   */
  Record deserialize(Object data) {
    return (Record) fieldDeserializer.value(data);
  }

  private Deserializer(Schema schema, ObjectInspectorPair pair) {
    this.fieldDeserializer = DeserializerVisitor.visit(schema, pair);
  }

  /**
   * Schema 访问器实现：基于 {@link SchemaWithPartnerVisitor} 遍历 Iceberg schema，
   * 为每种类型（primitive/struct/list/map）生成对应的 {@link FieldDeserializer}。
   *
   * <p>逻辑：入口 {@link #visit(Schema, ObjectInspectorPair)} 先用 {@link
   * FixNameMappingObjectInspectorPair} 包装 inspector 对，修正列名映射后再访问。
   */
  private static class DeserializerVisitor
      extends SchemaWithPartnerVisitor<ObjectInspectorPair, FieldDeserializer> {

    /**
     * 入口方法：用名字映射修正包装后，启动 schema 访问。
     *
     * @param schema Iceberg schema
     * @param pair 原始 inspector 对
     * @return 顶层字段反序列化器
     */
    public static FieldDeserializer visit(Schema schema, ObjectInspectorPair pair) {
      return visit(
          schema,
          new FixNameMappingObjectInspectorPair(schema, pair),
          new DeserializerVisitor(),
          new PartnerObjectInspectorByNameAccessors());
    }

    @Override
    public FieldDeserializer schema(
        Schema schema, ObjectInspectorPair pair, FieldDeserializer deserializer) {
      return deserializer;
    }

    @Override
    public FieldDeserializer field(
        NestedField field, ObjectInspectorPair pair, FieldDeserializer deserializer) {
      return deserializer;
    }

    /**
     * 基本类型字段反序列化逻辑。
     *
     * <p>逻辑：先用 sourceInspector 取出 Java 基本值；若 writerInspector 实现了 {@link WriteObjectInspector}，则再调用
     * convert 做类型适配；null 直接返回 null。
     */
    @Override
    public FieldDeserializer primitive(PrimitiveType type, ObjectInspectorPair pair) {
      return o -> {
        if (o == null) {
          return null;
        }

        ObjectInspector writerFieldInspector = pair.writerInspector();
        ObjectInspector sourceFieldInspector = pair.sourceInspector();

        Object result = ((PrimitiveObjectInspector) sourceFieldInspector).getPrimitiveJavaObject(o);
        if (writerFieldInspector instanceof WriteObjectInspector) {
          // If we have a conversion method defined for the ObjectInspector then convert
          result = ((WriteObjectInspector) writerFieldInspector).convert(result);
        }

        return result;
      };
    }

    /**
     * struct 字段反序列化逻辑。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>用 {@link GenericRecord#create(StructType)} 生成模板；
     *   <li>对每个子字段调用对应 {@link FieldDeserializer} 取值并写入 {@link Record#set(int, Object)}；
     *   <li>使用 {@link GenericRecord#copy()} 而非每次 create，省去 NAME_MAP_CACHE 查找。
     * </ol>
     */
    @Override
    public FieldDeserializer struct(
        StructType type, ObjectInspectorPair pair, List<FieldDeserializer> deserializers) {
      Preconditions.checkNotNull(type, "Can not create reader for null type");
      GenericRecord template = GenericRecord.create(type);
      return o -> {
        if (o == null) {
          return null;
        }

        List<Object> data =
            ((StructObjectInspector) pair.sourceInspector()).getStructFieldsDataAsList(o);
        // GenericRecord.copy() is more performant then GenericRecord.create(StructType) since
        // NAME_MAP_CACHE access
        // is eliminated. Using copy here to gain performance.
        Record result = template.copy();

        for (int i = 0; i < deserializers.size(); i++) {
          Object fieldValue = data.get(i);
          if (fieldValue != null) {
            result.set(i, deserializers.get(i).value(fieldValue));
          } else {
            result.set(i, null);
          }
        }

        return result;
      };
    }

    /** list 字段反序列化逻辑：遍历源 list 元素，逐个用子反序列化器转换后收集到新 list。 */
    @Override
    public FieldDeserializer list(
        ListType listTypeInfo, ObjectInspectorPair pair, FieldDeserializer deserializer) {
      return o -> {
        if (o == null) {
          return null;
        }

        List<Object> result = Lists.newArrayList();
        ListObjectInspector listInspector = (ListObjectInspector) pair.sourceInspector();

        for (Object val : listInspector.getList(o)) {
          result.add(deserializer.value(val));
        }

        return result;
      };
    }

    /** map 字段反序列化逻辑：遍历源 map 条目，分别对 key/value 用子反序列化器转换。 */
    @Override
    public FieldDeserializer map(
        MapType mapType,
        ObjectInspectorPair pair,
        FieldDeserializer keyDeserializer,
        FieldDeserializer valueDeserializer) {
      return o -> {
        if (o == null) {
          return null;
        }

        Map<Object, Object> result = Maps.newHashMap();
        MapObjectInspector mapObjectInspector = (MapObjectInspector) pair.sourceInspector();

        for (Map.Entry<?, ?> entry : mapObjectInspector.getMap(o).entrySet()) {
          result.put(
              keyDeserializer.value(entry.getKey()), valueDeserializer.value(entry.getValue()));
        }
        return result;
      };
    }
  }

  /**
   * Partner 访问器实现：按字段名/容器元素从 inspector 对中取出子 inspector 对。
   *
   * <p>用于在 schema 访问过程中递归下钻到子字段对应的 inspector。
   */
  private static class PartnerObjectInspectorByNameAccessors
      implements SchemaWithPartnerVisitor.PartnerAccessors<ObjectInspectorPair> {

    /** 按字段名取出 struct 子字段的 writer/source inspector 对。 */
    @Override
    public ObjectInspectorPair fieldPartner(ObjectInspectorPair pair, int fieldId, String name) {
      String sourceName = pair.sourceName(name);
      return new ObjectInspectorPair(
          ((StructObjectInspector) pair.writerInspector())
              .getStructFieldRef(name)
              .getFieldObjectInspector(),
          ((StructObjectInspector) pair.sourceInspector())
              .getStructFieldRef(sourceName)
              .getFieldObjectInspector());
    }

    /** 取出 map key 的 inspector 对。 */
    @Override
    public ObjectInspectorPair mapKeyPartner(ObjectInspectorPair pair) {
      return new ObjectInspectorPair(
          ((MapObjectInspector) pair.writerInspector()).getMapKeyObjectInspector(),
          ((MapObjectInspector) pair.sourceInspector()).getMapKeyObjectInspector());
    }

    /** 取出 map value 的 inspector 对。 */
    @Override
    public ObjectInspectorPair mapValuePartner(ObjectInspectorPair pair) {
      return new ObjectInspectorPair(
          ((MapObjectInspector) pair.writerInspector()).getMapValueObjectInspector(),
          ((MapObjectInspector) pair.sourceInspector()).getMapValueObjectInspector());
    }

    /** 取出 list 元素的 inspector 对。 */
    @Override
    public ObjectInspectorPair listElementPartner(ObjectInspectorPair pair) {
      return new ObjectInspectorPair(
          ((ListObjectInspector) pair.writerInspector()).getListElementObjectInspector(),
          ((ListObjectInspector) pair.sourceInspector()).getListElementObjectInspector());
    }
  }

  /** 字段反序列化器函数式接口：给定输入对象返回转换后的值。 */
  private interface FieldDeserializer {
    Object value(Object object);
  }

  /**
   * 修正列名映射的 inspector 对包装。
   *
   * <p>Hive 查询结果的列名可能与目标 Iceberg 列名不一致，但列顺序是对齐的。本包装器在构造时 按 schema 列顺序与 source inspector
   * 字段顺序建立“Iceberg 列名 -> Hive 列名”映射， 让后续按名访问的代码无需感知这一差异。
   */
  private static class FixNameMappingObjectInspectorPair extends ObjectInspectorPair {
    private final Map<String, String> sourceNameMap;

    FixNameMappingObjectInspectorPair(Schema schema, ObjectInspectorPair pair) {
      super(pair.writerInspector(), pair.sourceInspector());

      this.sourceNameMap = Maps.newHashMapWithExpectedSize(schema.columns().size());

      List<? extends StructField> fields =
          ((StructObjectInspector) sourceInspector()).getAllStructFieldRefs();
      for (int i = 0; i < schema.columns().size(); ++i) {
        sourceNameMap.put(schema.columns().get(i).name(), fields.get(i).getFieldName());
      }
    }

    /** 把 Iceberg 列名翻译回 Hive 源端列名。 */
    @Override
    String sourceName(String originalName) {
      return sourceNameMap.get(originalName);
    }
  }

  /**
   * inspector 对：同时持有写端（Iceberg 侧）与源端（Hive 侧）的 ObjectInspector。
   *
   * <p>设计意图：源端 inspector 用于真正读取 Hive 原始值；写端 inspector 仅在需要类型转换 时（实现 {@link
   * WriteObjectInspector}）调用其 convert 方法。默认 sourceName 直接返回原名， 由 {@link
   * FixNameMappingObjectInspectorPair} 子类覆盖。
   */
  private static class ObjectInspectorPair {
    private ObjectInspector writerInspector;
    private ObjectInspector sourceInspector;

    ObjectInspectorPair(ObjectInspector writerInspector, ObjectInspector sourceInspector) {
      this.writerInspector = writerInspector;
      this.sourceInspector = sourceInspector;
    }

    /** 返回写端 inspector。 */
    ObjectInspector writerInspector() {
      return writerInspector;
    }

    /** 返回源端 inspector。 */
    ObjectInspector sourceInspector() {
      return sourceInspector;
    }

    /** 返回源端字段名（默认原名，可被子类覆盖做名字映射）。 */
    String sourceName(String originalName) {
      return originalName;
    }
  }
}
