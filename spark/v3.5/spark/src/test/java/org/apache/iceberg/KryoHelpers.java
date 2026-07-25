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
package org.apache.iceberg;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.apache.spark.SparkConf;
import org.apache.spark.serializer.KryoSerializer;

/**
 * 文件级说明：测试 KryoHelpers 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Kryo辅助 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class KryoHelpers {

  /** Kryo辅助。 */
  private KryoHelpers() {}

  /** roundtrip序列化。 */
  @SuppressWarnings("unchecked")
  public static <T> T roundTripSerialize(T obj) throws IOException {
    Kryo kryo = new KryoSerializer(new SparkConf()).newKryo();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    try (Output out = new Output(new ObjectOutputStream(bytes))) {
      kryo.writeClassAndObject(out, obj);
    }

    try (Input in =
        new Input(new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))) {
      return (T) kryo.readClassAndObject(in);
    }
  }
}
