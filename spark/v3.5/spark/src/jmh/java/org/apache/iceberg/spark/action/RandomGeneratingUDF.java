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
package org.apache.iceberg.spark.action;

import static org.apache.spark.sql.functions.udf;

import java.io.Serializable;
import java.util.Random;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.RandomUtil;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.types.DataTypes;

/**
 * 文件级说明：RandomGeneratingUDF 基准测试辅助类。
 *
 * <p>所属模块：iceberg-spark（v3.5）。职责：为基准测试提供 randomgeneratingUDF 相关工具与辅助逻辑。
 *
 * <p>设计要点：作为 JMH 基准测试的基础设施，被各 Benchmark 子类复用，提供数据准备、配置管理与结果物化等通用能力。
 */
class RandomGeneratingUDF implements Serializable {
  private final long uniqueValues;
  private Random rand = new Random();

  RandomGeneratingUDF(long uniqueValues) {
    this.uniqueValues = uniqueValues;
  }

  /** 辅助方法：random长整型UDF。 */
  UserDefinedFunction randomLongUDF() {
    return udf(() -> rand.nextLong() % (uniqueValues / 2), DataTypes.LongType)
        .asNondeterministic()
        .asNonNullable();
  }

  /** 辅助方法：random字符串。 */
  UserDefinedFunction randomString() {
    return udf(
            () -> (String) RandomUtil.generatePrimitive(Types.StringType.get(), rand),
            DataTypes.StringType)
        .asNondeterministic()
        .asNonNullable();
  }
}
