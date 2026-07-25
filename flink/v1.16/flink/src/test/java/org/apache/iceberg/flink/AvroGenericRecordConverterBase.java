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
package org.apache.iceberg.flink;

import org.junit.Test;

/**
 * 文件级说明：测试 AvroGenericRecordConverterBase 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.16）。职责：验证 AvroGenericRecordConverterBase 在各类场景下的行为是否符合预期，
 * 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public abstract class AvroGenericRecordConverterBase {
  /**
   * 测试场景：Converter。
   *
   * <p>验证该方法在 Converter 条件下的行为是否符合预期。
   */
  protected abstract void testConverter(DataGenerator dataGenerator) throws Exception;

  /**
   * 测试场景：Primitive Types。
   *
   * <p>验证该方法在 Primitive Types 条件下的行为是否符合预期。
   */
  @Test
  public void testPrimitiveTypes() throws Exception {
    testConverter(new DataGenerators.Primitives());
  }

  /**
   * 测试场景：Struct Of Primitive。
   *
   * <p>验证该方法在 Struct Of Primitive 条件下的行为是否符合预期。
   */
  @Test
  public void testStructOfPrimitive() throws Exception {
    testConverter(new DataGenerators.StructOfPrimitive());
  }

  /**
   * 测试场景：Struct Of Array。
   *
   * <p>验证该方法在 Struct Of Array 条件下的行为是否符合预期。
   */
  @Test
  public void testStructOfArray() throws Exception {
    testConverter(new DataGenerators.StructOfArray());
  }

  /**
   * 测试场景：Struct Of Map。
   *
   * <p>验证该方法在 Struct Of Map 条件下的行为是否符合预期。
   */
  @Test
  public void testStructOfMap() throws Exception {
    testConverter(new DataGenerators.StructOfMap());
  }

  /**
   * 测试场景：Struct Of Struct。
   *
   * <p>验证该方法在 Struct Of Struct 条件下的行为是否符合预期。
   */
  @Test
  public void testStructOfStruct() throws Exception {
    testConverter(new DataGenerators.StructOfStruct());
  }

  /**
   * 测试场景：Array Of Primitive。
   *
   * <p>验证该方法在 Array Of Primitive 条件下的行为是否符合预期。
   */
  @Test
  public void testArrayOfPrimitive() throws Exception {
    testConverter(new DataGenerators.ArrayOfPrimitive());
  }

  /**
   * 测试场景：Array Of Array。
   *
   * <p>验证该方法在 Array Of Array 条件下的行为是否符合预期。
   */
  @Test
  public void testArrayOfArray() throws Exception {
    testConverter(new DataGenerators.ArrayOfArray());
  }

  /**
   * 测试场景：Array Of Map。
   *
   * <p>验证该方法在 Array Of Map 条件下的行为是否符合预期。
   */
  @Test
  public void testArrayOfMap() throws Exception {
    testConverter(new DataGenerators.ArrayOfMap());
  }

  /**
   * 测试场景：Array Of Struct。
   *
   * <p>验证该方法在 Array Of Struct 条件下的行为是否符合预期。
   */
  @Test
  public void testArrayOfStruct() throws Exception {
    testConverter(new DataGenerators.ArrayOfStruct());
  }

  /**
   * 测试场景：Map Of Primitives。
   *
   * <p>验证该方法在 Map Of Primitives 条件下的行为是否符合预期。
   */
  @Test
  public void testMapOfPrimitives() throws Exception {
    testConverter(new DataGenerators.MapOfPrimitives());
  }

  /**
   * 测试场景：Map Of Array。
   *
   * <p>验证该方法在 Map Of Array 条件下的行为是否符合预期。
   */
  @Test
  public void testMapOfArray() throws Exception {
    testConverter(new DataGenerators.MapOfArray());
  }

  /**
   * 测试场景：Map Of Map。
   *
   * <p>验证该方法在 Map Of Map 条件下的行为是否符合预期。
   */
  @Test
  public void testMapOfMap() throws Exception {
    testConverter(new DataGenerators.MapOfMap());
  }

  /**
   * 测试场景：Map Of Struct。
   *
   * <p>验证该方法在 Map Of Struct 条件下的行为是否符合预期。
   */
  @Test
  public void testMapOfStruct() throws Exception {
    testConverter(new DataGenerators.MapOfStruct());
  }
}
