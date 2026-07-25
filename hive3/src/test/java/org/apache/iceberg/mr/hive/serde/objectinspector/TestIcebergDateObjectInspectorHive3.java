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

import java.time.LocalDate;
import org.apache.hadoop.hive.common.type.Date;
import org.apache.hadoop.hive.serde2.io.DateWritableV2;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.DateObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 Hive3 的日期 ObjectInspector（{@link IcebergDateObjectInspectorHive3}）。
 *
 * <p>所属模块：iceberg-hive3。职责：验证 Iceberg 的日期类型（{@link LocalDate}）与 Hive3 的
 * {@link Date}/{@link DateWritableV2} 之间的双向转换正确性，包括类型分类、TypeInfo、
 * Java/Writable 类映射、null 处理、日期值转换与对象拷贝。
 *
 * <p>测试策略：构造已知 epochDays 的 {@link LocalDate}，断言转换后得到对应的 Hive {@link Date}；
 * 验证 copyObject 返回独立副本、preferWritable 返回 false。
 */
public class TestIcebergDateObjectInspectorHive3 {

  /**
   * 测试日期 ObjectInspector 的全部行为。
   *
   * <p>逻辑：
   * <ol>
   *   <li>断言 Category=PRIMITIVE、PrimitiveCategory=DATE；</li>
   *   <li>断言 TypeInfo/TypeName 正确；</li>
   *   <li>断言 Java 类={@link Date}、Writable 类={@link DateWritableV2}；</li>
   *   <li>断言 null 输入返回 null（copyObject/getPrimitiveJavaObject/getPrimitiveWritableObject）；</li>
   *   <li>构造 epochDays=5005 的 LocalDate，断言转换后得到对应的 Hive Date；</li>
   *   <li>断言 copyObject 返回值相等但非同一对象；</li>
   *   <li>断言 preferWritable=false。</li>
   * </ol>
   */
  @Test
  public void testIcebergDateObjectInspector() {
    DateObjectInspector oi = IcebergDateObjectInspectorHive3.get();

    Assert.assertEquals(ObjectInspector.Category.PRIMITIVE, oi.getCategory());
    Assert.assertEquals(PrimitiveObjectInspector.PrimitiveCategory.DATE, oi.getPrimitiveCategory());

    Assert.assertEquals(TypeInfoFactory.dateTypeInfo, oi.getTypeInfo());
    Assert.assertEquals(TypeInfoFactory.dateTypeInfo.getTypeName(), oi.getTypeName());

    Assert.assertEquals(Date.class, oi.getJavaPrimitiveClass());
    Assert.assertEquals(DateWritableV2.class, oi.getPrimitiveWritableClass());

    Assert.assertNull(oi.copyObject(null));
    Assert.assertNull(oi.getPrimitiveJavaObject(null));
    Assert.assertNull(oi.getPrimitiveWritableObject(null));

    int epochDays = 5005;
    LocalDate local = LocalDate.ofEpochDay(epochDays);
    Date date = Date.ofEpochDay(epochDays);

    Assert.assertEquals(date, oi.getPrimitiveJavaObject(local));
    Assert.assertEquals(new DateWritableV2(date), oi.getPrimitiveWritableObject(local));

    Date copy = (Date) oi.copyObject(date);

    Assert.assertEquals(date, copy);
    Assert.assertNotSame(date, copy);

    Assert.assertFalse(oi.preferWritable());
  }
}
