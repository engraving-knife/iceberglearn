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
package org.apache.iceberg.spark;

import java.util.function.Function;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

/**
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg Spark 集成的入口工具类，提供 bucket/truncate 等变换的 Spark UDF 注册静态方法。
 *
 * <p>设计意图：作为门面（Facade）聚合常用变换注册 API，简化上层调用。
 *
 * <p>上下游关系：被用户代码与 Spark 表函数注册调用；依赖 SparkTypeToType 与 SparkValueConverter。
 */
public class IcebergSpark {
  private IcebergSpark() {}
  /** 执行 registerBucketUDF 相关操作。 */
  public static void registerBucketUDF(
      SparkSession session, String funcName, DataType sourceType, int numBuckets) {
    SparkTypeToType typeConverter = new SparkTypeToType();
    Type sourceIcebergType = typeConverter.atomic(sourceType);
    Function<Object, Integer> bucket = Transforms.bucket(numBuckets).bind(sourceIcebergType);
    session
        .udf()
        .register(
            funcName,
            value -> bucket.apply(SparkValueConverter.convert(sourceIcebergType, value)),
            DataTypes.IntegerType);
  }
  /** 执行 registerTruncateUDF 相关操作。 */
  public static void registerTruncateUDF(
      SparkSession session, String funcName, DataType sourceType, int width) {
    SparkTypeToType typeConverter = new SparkTypeToType();
    Type sourceIcebergType = typeConverter.atomic(sourceType);
    Function<Object, Object> truncate = Transforms.truncate(width).bind(sourceIcebergType);
    session
        .udf()
        .register(
            funcName,
            value -> truncate.apply(SparkValueConverter.convert(sourceIcebergType, value)),
            sourceType);
  }
}
