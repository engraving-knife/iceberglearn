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

import java.util.UUID;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.io.Text;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestIcebergUUIDObjectInspector 的功能。
 *
 * <p>所属模块：iceberg-mr。职责：验证 TestIcebergUUIDObjectInspector 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestIcebergUUIDObjectInspector {

  /**
   * 测试场景：Iceberg UUID Object Inspector。
   *
   * <p>验证该方法在 Iceberg UUID Object Inspector 条件下的行为是否符合预期。
   */
  @Test
  public void testIcebergUUIDObjectInspector() {
    IcebergUUIDObjectInspector oi = IcebergUUIDObjectInspector.get();

    Assert.assertEquals(ObjectInspector.Category.PRIMITIVE, oi.getCategory());
    Assert.assertEquals(
        PrimitiveObjectInspector.PrimitiveCategory.STRING, oi.getPrimitiveCategory());

    Assert.assertEquals(TypeInfoFactory.stringTypeInfo, oi.getTypeInfo());
    Assert.assertEquals(TypeInfoFactory.stringTypeInfo.getTypeName(), oi.getTypeName());

    Assert.assertEquals(String.class, oi.getJavaPrimitiveClass());
    Assert.assertEquals(Text.class, oi.getPrimitiveWritableClass());

    Assert.assertNull(oi.copyObject(null));
    Assert.assertNull(oi.getPrimitiveJavaObject(null));
    Assert.assertNull(oi.getPrimitiveWritableObject(null));
    Assert.assertNull(oi.convert(null));

    UUID uuid = UUID.randomUUID();
    String uuidStr = uuid.toString();
    Text text = new Text(uuidStr);

    Assert.assertEquals(uuidStr, oi.getPrimitiveJavaObject(text));
    Assert.assertEquals(text, oi.getPrimitiveWritableObject(uuidStr));
    Assert.assertEquals(uuid, oi.convert(uuidStr));

    Text copy = (Text) oi.copyObject(text);

    Assert.assertEquals(text, copy);
    Assert.assertNotSame(text, copy);

    Assert.assertFalse(oi.preferWritable());
  }
}
