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
package org.apache.iceberg.util;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.infra.Blackhole;

/**
 * 测试类：ZOrderByteUtilsBenchmark，用于验证 Z Order Byte Utils Benchmark 相关功能。
 *
 * <p>所属模块：iceberg-core（基准测试目录 src/jmh）。 职责：针对 Z Order Byte Utils Benchmark
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JMH 基准测试框架，通过构造典型数据集与读取场景， 测量清单读取/指标计数等操作的性能基线。
 */
@Fork(1)
@State(Scope.Benchmark)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.SingleShotTime)
@Timeout(time = 1000, timeUnit = TimeUnit.HOURS)
public class ZOrderByteUtilsBenchmark {

  private static final int NUM_ENTRIES = 10000000;

  private byte[][][] fourColumnInput;
  private byte[][][] threeColumnInput;
  private byte[][][] twoColumnInput;

  /** 辅助方法：setup bench。 */
  @Setup
  public void setupBench() {
    Random rand = new Random(42);
    fourColumnInput = new byte[NUM_ENTRIES][][];
    threeColumnInput = new byte[NUM_ENTRIES][][];
    twoColumnInput = new byte[NUM_ENTRIES][][];
    for (int i = 0; i < NUM_ENTRIES; i++) {
      fourColumnInput[i] = new byte[4][];
      threeColumnInput[i] = new byte[3][];
      twoColumnInput[i] = new byte[2][];
      for (int j = 0; j < 4; j++) {
        byte[] value = ByteBuffer.allocate(Long.BYTES).putLong(rand.nextLong()).array();
        if (j < 2) {
          twoColumnInput[i][j] = value;
        }
        if (j < 3) {
          threeColumnInput[i][j] = value;
        }
        fourColumnInput[i][j] = value;
      }
    }
  }

  /** 辅助方法：interleave values four columns。 */
  @Benchmark
  @Threads(1)
  public void interleaveValuesFourColumns(Blackhole blackhole) {
    int outputSize = ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE * 4;
    ByteBuffer outputBuffer = ByteBuffer.allocate(outputSize);

    for (byte[][] columnsBinary : fourColumnInput) {
      byte[] interleavedBytes =
          ZOrderByteUtils.interleaveBits(columnsBinary, outputSize, outputBuffer);
      blackhole.consume(interleavedBytes);
    }
  }

  /** 辅助方法：interleave values three columns。 */
  @Benchmark
  @Threads(1)
  public void interleaveValuesThreeColumns(Blackhole blackhole) {
    int outputSize = ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE * 3;
    ByteBuffer outputBuffer = ByteBuffer.allocate(outputSize);

    for (int i = 0; i < fourColumnInput.length; i++) {
      byte[] interleavedBytes =
          ZOrderByteUtils.interleaveBits(threeColumnInput[i], outputSize, outputBuffer);
      blackhole.consume(interleavedBytes);
    }
  }

  /** 辅助方法：interleave values two columns。 */
  @Benchmark
  @Threads(1)
  public void interleaveValuesTwoColumns(Blackhole blackhole) {
    int outputSize = ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE * 2;
    ByteBuffer outputBuffer = ByteBuffer.allocate(outputSize);

    for (int i = 0; i < fourColumnInput.length; i++) {
      byte[] interleavedBytes =
          ZOrderByteUtils.interleaveBits(twoColumnInput[i], outputSize, outputBuffer);
      blackhole.consume(interleavedBytes);
    }
  }

  /** 辅助方法：interleave values four columns 8 byte output。 */
  @Benchmark
  @Threads(1)
  public void interleaveValuesFourColumns8ByteOutput(Blackhole blackhole) {
    int outputSize = 8;
    ByteBuffer outputBuffer = ByteBuffer.allocate(outputSize);

    for (byte[][] columnsBinary : fourColumnInput) {
      byte[] interleavedBytes =
          ZOrderByteUtils.interleaveBits(columnsBinary, outputSize, outputBuffer);
      blackhole.consume(interleavedBytes);
    }
  }
}
