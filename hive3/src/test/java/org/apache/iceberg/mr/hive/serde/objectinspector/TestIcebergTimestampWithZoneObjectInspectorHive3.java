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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.apache.hadoop.hive.common.type.TimestampTZ;
import org.apache.hadoop.hive.serde2.io.TimestampLocalTZWritable;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 Hive3 的带时区时间戳 ObjectInspector（{@link IcebergTimestampWithZoneObjectInspectorHive3}）。
 *
 * <p>所属模块：iceberg-hive3。职责：验证 Iceberg 的带时区 Timestamp（{@link OffsetDateTime}）
 * 与 Hive3 的 {@link TimestampTZ}/{@link TimestampLocalTZWritable} 之间的双向转换正确性，
 * 包括类型分类、TypeInfo、Java/Writable 类映射、null 处理、时区转换与对象拷贝。
 *
 * <p>测试策略：构造不同时区偏移的 {@link OffsetDateTime}，断言转换后均归一化为 UTC 的
 * {@link TimestampTZ}；同时验证 copyObject 返回独立副本、preferWritable 返回 false。
 */
public class TestIcebergTimestampWithZoneObjectInspectorHive3 {

  /**
   * 测试带时区时间戳 ObjectInspector 的全部行为。
   *
   * <p>逻辑：
   * <ol>
   *   <li>断言 Category=PRIMITIVE、PrimitiveCategory=TIMESTAMPLOCALTZ；</li>
   *   <li>断言 TypeInfo/TypeName 正确；</li>
   *   <li>断言 Java 类={@link TimestampTZ}、Writable 类={@link TimestampLocalTZWritable}；</li>
   *   <li>断言 null 输入返回 null（copyObject/getPrimitiveJavaObject/getPrimitiveWritableObject/convert）；</li>
   *   <li>构造 UTC 时间 + 不同偏移（+4h、+11h）的 OffsetDateTime，断言转换后均为同一 UTC TimestampTZ；</li>
   *   <li>断言 copyObject 返回值相等但非同一对象（独立副本）；</li>
   *   <li>断言 preferWritable=false；convert(TimestampTZ) 返回 UTC OffsetDateTime。</li>
   * </ol>
   */
  @Test
  public void testIcebergTimestampLocalTZObjectInspector() {
    IcebergTimestampWithZoneObjectInspectorHive3 oi =
        IcebergTimestampWithZoneObjectInspectorHive3.get();

    Assert.assertEquals(ObjectInspector.Category.PRIMITIVE, oi.getCategory());
    Assert.assertEquals(
        PrimitiveObjectInspector.PrimitiveCategory.TIMESTAMPLOCALTZ, oi.getPrimitiveCategory());

    Assert.assertEquals(TypeInfoFactory.timestampLocalTZTypeInfo, oi.getTypeInfo());
    Assert.assertEquals(TypeInfoFactory.timestampLocalTZTypeInfo.getTypeName(), oi.getTypeName());

    Assert.assertEquals(TimestampTZ.class, oi.getJavaPrimitiveClass());
    Assert.assertEquals(TimestampLocalTZWritable.class, oi.getPrimitiveWritableClass());

    Assert.assertNull(oi.copyObject(null));
    Assert.assertNull(oi.getPrimitiveJavaObject(null));
    Assert.assertNull(oi.getPrimitiveWritableObject(null));
    Assert.assertNull(oi.convert(null));

    LocalDateTime dateTimeAtUTC = LocalDateTime.of(2020, 12, 10, 15, 55, 20, 30000);
    OffsetDateTime offsetDateTime =
        OffsetDateTime.of(dateTimeAtUTC.plusHours(4), ZoneOffset.ofHours(4));
    TimestampTZ ts = new TimestampTZ(dateTimeAtUTC.atZone(ZoneId.of("UTC")));

    Assert.assertEquals(ts, oi.getPrimitiveJavaObject(offsetDateTime));
    Assert.assertEquals(
        new TimestampLocalTZWritable(ts), oi.getPrimitiveWritableObject(offsetDateTime));

    // try with another offset as well
    offsetDateTime = OffsetDateTime.of(dateTimeAtUTC.plusHours(11), ZoneOffset.ofHours(11));
    Assert.assertEquals(ts, oi.getPrimitiveJavaObject(offsetDateTime));
    Assert.assertEquals(
        new TimestampLocalTZWritable(ts), oi.getPrimitiveWritableObject(offsetDateTime));

    TimestampTZ copy = (TimestampTZ) oi.copyObject(ts);

    Assert.assertEquals(ts, copy);
    Assert.assertNotSame(ts, copy);

    Assert.assertFalse(oi.preferWritable());

    Assert.assertEquals(OffsetDateTime.of(dateTimeAtUTC, ZoneOffset.UTC), oi.convert(ts));
  }
}
