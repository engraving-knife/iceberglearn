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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.apache.hadoop.hive.serde2.io.TimestampWritable;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestIcebergTimestampObjectInspector 的功能。
 *
 * <p>所属模块：iceberg-mr。职责：验证 TestIcebergTimestampObjectInspector 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestIcebergTimestampObjectInspector {

  /**
   * 测试场景：Iceberg Timestamp Object Inspector。
   *
   * <p>验证该方法在 Iceberg Timestamp Object Inspector 条件下的行为是否符合预期。
   */
  @Test
  public void testIcebergTimestampObjectInspector() {
    IcebergTimestampObjectInspector oi = IcebergTimestampObjectInspector.get();

    Assert.assertEquals(ObjectInspector.Category.PRIMITIVE, oi.getCategory());
    Assert.assertEquals(
        PrimitiveObjectInspector.PrimitiveCategory.TIMESTAMP, oi.getPrimitiveCategory());

    Assert.assertEquals(TypeInfoFactory.timestampTypeInfo, oi.getTypeInfo());
    Assert.assertEquals(TypeInfoFactory.timestampTypeInfo.getTypeName(), oi.getTypeName());

    Assert.assertEquals(Timestamp.class, oi.getJavaPrimitiveClass());
    Assert.assertEquals(TimestampWritable.class, oi.getPrimitiveWritableClass());

    Assert.assertNull(oi.copyObject(null));
    Assert.assertNull(oi.getPrimitiveJavaObject(null));
    Assert.assertNull(oi.getPrimitiveWritableObject(null));
    Assert.assertNull(oi.convert(null));

    LocalDateTime local = LocalDateTime.of(2020, 1, 1, 12, 55, 30, 5560000);
    Timestamp ts = Timestamp.valueOf(local);

    Assert.assertEquals(ts, oi.getPrimitiveJavaObject(local));
    Assert.assertEquals(new TimestampWritable(ts), oi.getPrimitiveWritableObject(local));

    Timestamp copy = (Timestamp) oi.copyObject(ts);

    Assert.assertEquals(ts, copy);
    Assert.assertNotSame(ts, copy);

    Assert.assertFalse(oi.preferWritable());

    Assert.assertEquals(local, oi.convert(ts));
  }
}
