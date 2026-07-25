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
package org.apache.iceberg.mr.hive.serde.objectinspector;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.iceberg.Schema;
import org.apache.iceberg.common.DynMethods;
import org.apache.iceberg.hive.HiveVersion;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：根据 Iceberg Schema 生成对应 Hive {@link ObjectInspector} 树的工厂/访问器。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）；是 Hive SerDe 与 Iceberg 类型 系统之间的映射中枢）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Iceberg schema，为每种类型生成对应的 Hive ObjectInspector：
 *       <ul>
 *         <li>基本类型：BOOLEAN/INT/LONG/FLOAT/DOUBLE/STRING 走 Hive 标准实现；
 *             BINARY/FIXED/UUID/DATE/TIME/TIMESTAMP/DECIMAL 走 Iceberg 自定义实现。
 *         <li>struct/list/map：递归生成嵌套 ObjectInspector。
 *       </ul>
 *   <li>处理 Hive 2 / Hive 3 在 Date/Timestamp ObjectInspector 上的 API 差异： 通过 {@link DynMethods}
 *       反射加载对应版本的实现类。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>继承 {@link TypeUtil.SchemaVisitor}，借助已有的 schema 遍历框架按结构同构地生成 ObjectInspector 树。
 *   <li>静态加载 Hive 版本相关的 DATE/TIMESTAMP/TIMESTAMPTZ inspector，避免运行期反复反射。
 *   <li>struct 字段名统一转小写，匹配 Hive 列名大小写不敏感的约定。
 * </ul>
 *
 * <p>上下游关系：上游被 {@link org.apache.iceberg.mr.hive.HiveIcebergSerDe} 在初始化时调用； 下游创建各
 * Iceberg*ObjectInspector 实例。
 */
public final class IcebergObjectInspector extends TypeUtil.SchemaVisitor<ObjectInspector> {

  // get the correct inspectors depending on whether we're working with Hive2 or Hive3 dependencies
  // we need to do this because there is a breaking API change in Date/TimestampObjectInspector
  // between Hive2 and Hive3
  private static final String DATE_INSPECTOR_CLASS =
      HiveVersion.min(HiveVersion.HIVE_3)
          ? "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergDateObjectInspectorHive3"
          : "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergDateObjectInspector";

  /** DATE 类型的 ObjectInspector 单例（按 Hive 版本反射加载）。 */
  public static final ObjectInspector DATE_INSPECTOR =
      DynMethods.builder("get").impl(DATE_INSPECTOR_CLASS).buildStatic().invoke();

  private static final String TIMESTAMP_INSPECTOR_CLASS =
      HiveVersion.min(HiveVersion.HIVE_3)
          ? "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergTimestampObjectInspectorHive3"
          : "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergTimestampObjectInspector";

  private static final String TIMESTAMPTZ_INSPECTOR_CLASS =
      HiveVersion.min(HiveVersion.HIVE_3)
          ? "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergTimestampWithZoneObjectInspectorHive3"
          : "org.apache.iceberg.mr.hive.serde.objectinspector.IcebergTimestampWithZoneObjectInspector";

  /** TIMESTAMP（不带时区）类型的 ObjectInspector 单例（按 Hive 版本反射加载）。 */
  public static final ObjectInspector TIMESTAMP_INSPECTOR =
      DynMethods.builder("get").impl(TIMESTAMP_INSPECTOR_CLASS).buildStatic().invoke();

  /** TIMESTAMP WITH ZONE 类型的 ObjectInspector 单例（按 Hive 版本反射加载）。 */
  public static final ObjectInspector TIMESTAMP_INSPECTOR_WITH_TZ =
      DynMethods.builder("get").impl(TIMESTAMPTZ_INSPECTOR_CLASS).buildStatic().invoke();

  /**
   * 根据 Iceberg schema 生成对应的 ObjectInspector 树。
   *
   * @param schema Iceberg schema，可为 null（返回空 struct inspector）
   * @return 顶层 ObjectInspector
   */
  public static ObjectInspector create(@Nullable Schema schema) {
    if (schema == null) {
      return IcebergRecordObjectInspector.empty();
    }

    return TypeUtil.visit(schema, new IcebergObjectInspector());
  }

  /** 根据一组字段构造 ObjectInspector。 */
  public static ObjectInspector create(Types.NestedField... fields) {
    return create(new Schema(fields));
  }

  /** 字段访问：直接返回子 inspector。 */
  @Override
  public ObjectInspector field(Types.NestedField field, ObjectInspector fieldObjectInspector) {
    return fieldObjectInspector;
  }

  /** list 访问：包装为 Hive 标准 list inspector。 */
  @Override
  public ObjectInspector list(Types.ListType listTypeInfo, ObjectInspector listObjectInspector) {
    return ObjectInspectorFactory.getStandardListObjectInspector(listObjectInspector);
  }

  /** map 访问：包装为 Hive 标准 map inspector。 */
  @Override
  public ObjectInspector map(
      Types.MapType mapType,
      ObjectInspector keyObjectInspector,
      ObjectInspector valueObjectInspector) {
    return ObjectInspectorFactory.getStandardMapObjectInspector(
        keyObjectInspector, valueObjectInspector);
  }

  /**
   * 基本类型访问：按 typeId 分支返回对应 ObjectInspector。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>BINARY/FIXED/UUID/DATE/TIME/TIMESTAMP/DECIMAL：返回 Iceberg 自定义 inspector。
   *   <li>BOOLEAN/DOUBLE/FLOAT/INT/LONG/STRING：返回 Hive 标准 Java ObjectInspector。
   *   <li>TIMESTAMP：按 shouldAdjustToUTC 选择带时区或不带时区 inspector。
   * </ul>
   *
   * @param primitiveType Iceberg 基本类型
   * @return 对应的 ObjectInspector
   */
  @Override
  public ObjectInspector primitive(Type.PrimitiveType primitiveType) {
    final PrimitiveTypeInfo primitiveTypeInfo;

    switch (primitiveType.typeId()) {
      case BINARY:
        return IcebergBinaryObjectInspector.get();
      case BOOLEAN:
        primitiveTypeInfo = TypeInfoFactory.booleanTypeInfo;
        break;
      case DATE:
        return DATE_INSPECTOR;
      case DECIMAL:
        Types.DecimalType type = (Types.DecimalType) primitiveType;
        return IcebergDecimalObjectInspector.get(type.precision(), type.scale());
      case DOUBLE:
        primitiveTypeInfo = TypeInfoFactory.doubleTypeInfo;
        break;
      case FIXED:
        return IcebergFixedObjectInspector.get();
      case FLOAT:
        primitiveTypeInfo = TypeInfoFactory.floatTypeInfo;
        break;
      case INTEGER:
        primitiveTypeInfo = TypeInfoFactory.intTypeInfo;
        break;
      case LONG:
        primitiveTypeInfo = TypeInfoFactory.longTypeInfo;
        break;
      case STRING:
        primitiveTypeInfo = TypeInfoFactory.stringTypeInfo;
        break;
      case UUID:
        return IcebergUUIDObjectInspector.get();
      case TIMESTAMP:
        boolean adjustToUTC = ((Types.TimestampType) primitiveType).shouldAdjustToUTC();
        return adjustToUTC ? TIMESTAMP_INSPECTOR_WITH_TZ : TIMESTAMP_INSPECTOR;
      case TIME:
        return IcebergTimeObjectInspector.get();
      default:
        throw new IllegalArgumentException(primitiveType.typeId() + " type is not supported");
    }

    return PrimitiveObjectInspectorFactory.getPrimitiveJavaObjectInspector(primitiveTypeInfo);
  }

  /** schema 访问：直接返回 struct inspector。 */
  @Override
  public ObjectInspector schema(Schema schema, ObjectInspector structObjectInspector) {
    return structObjectInspector;
  }

  /** struct 访问：构造 {@link IcebergRecordObjectInspector}。 */
  @Override
  public ObjectInspector struct(
      Types.StructType structType, List<ObjectInspector> fieldObjectInspectors) {
    return new IcebergRecordObjectInspector(structType, fieldObjectInspectors);
  }
}
