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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.hadoop.hive.common.type.Timestamp;
import org.apache.hadoop.hive.serde2.io.TimestampWritableV2;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 Hive3 的无时区时间戳 ObjectInspector（{@link IcebergTimestampObjectInspectorHive3}）。
 *
 * <p>所属模块：iceberg-hive3。职责：验证 Iceberg 的无时区 Timestamp（{@link LocalDateTime}）
 * 与 Hive3 的 {@link Timestamp}/{@link TimestampWritableV2} 之间的双向转换正确性，
 * 包括类型分类、TypeInfo、Java/Writable 类映射、null 处理、时间戳值转换与对象拷贝。
 *
 * <p>测试策略：构造已知 epochMilli + 纳秒的 {@link LocalDateTime}，断言转换后得到对应的
 * Hive {@link Timestamp}；验证 convert 反向转换、copyObject 返回独立副本、preferWritable=false。
 */
public class TestIcebergTimestampObjectInspectorHive3 {

  /**
   * 测试无时区时间戳 ObjectInspector 的全部行为。
   *
   * <p>逻辑：
   * <ol>
   *   <li>断言 Category=PRIMITIVE、PrimitiveCategory=TIMESTAMP；</li>
   *   <li>断言 TypeInfo/TypeName 正确；</li>
   *   <li>断言 Java 类={@link Timestamp}、Writable 类={@link TimestampWritableV2}；</li>
   *   <li>断言 null 输入返回 null；</li>
   *   <li>构造 epochMilli=1601471970000 + 34000 纳秒的 LocalDateTime，断言转换得到对应 Hive Timestamp；</li>
   *   <li>断言 copyObject 返回值相等但非同一对象；</li>
   *   <li>断言 preferWritable=false；convert(Timestamp) 返回原始 LocalDateTime。</li>
   * </ol>
   */
  @Test
  public void testIcebergTimestampObjectInspector() {
    IcebergTimestampObjectInspectorHive3 oi = IcebergTimestampObjectInspectorHive3.get();

    Assert.assertEquals(ObjectInspector.Category.PRIMITIVE, oi.getCategory());
    Assert.assertEquals(
        PrimitiveObjectInspector.PrimitiveCategory.TIMESTAMP, oi.getPrimitiveCategory());

    Assert.assertEquals(TypeInfoFactory.timestampTypeInfo, oi.getTypeInfo());
    Assert.assertEquals(TypeInfoFactory.timestampTypeInfo.getTypeName(), oi.getTypeName());

    Assert.assertEquals(Timestamp.class, oi.getJavaPrimitiveClass());
    Assert.assertEquals(TimestampWritableV2.class, oi.getPrimitiveWritableClass());

    Assert.assertNull(oi.copyObject(null));
    Assert.assertNull(oi.getPrimitiveJavaObject(null));
    Assert.assertNull(oi.getPrimitiveWritableObject(null));
    Assert.assertNull(oi.convert(null));

    long epochMilli = 1601471970000L;
    LocalDateTime local =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.of("UTC"))
            .plusNanos(34000);
    Timestamp ts = Timestamp.ofEpochMilli(epochMilli);
    ts.setNanos(34000);

    Assert.assertEquals(ts, oi.getPrimitiveJavaObject(local));
    Assert.assertEquals(new TimestampWritableV2(ts), oi.getPrimitiveWritableObject(local));

    Timestamp copy = (Timestamp) oi.copyObject(ts);

    Assert.assertEquals(ts, copy);
    Assert.assertNotSame(ts, copy);

    Assert.assertFalse(oi.preferWritable());

    Assert.assertEquals(local, oi.convert(ts));
  }
}
