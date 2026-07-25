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

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.apache.iceberg.relocated.com.google.common.primitives.Longs;

/**
 * 数组工具类，提供基础类型数组与集合之间的相互转换，以及对象数组到基础类型数组的拆箱转换。
 *
 * <p>所属模块：iceberg-core（Iceberg 核心实现层，位于 api 之下，提供表/元数据管理的具体实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在 {@code int[]}/{@code long[]} 与 {@code List<Integer>}/{@code List<Long>} 之间互转， 用于元数据字段（如字段
 *       ID 列表、快照 ID 列表）的序列化与内存表示切换。
 *   <li>把 {@code Boolean/Byte/Short/Integer/Long/Float/Double} 等包装类型数组拆箱为对应基础类型数组， 方法移植自
 *       commons-lang3，避免引入额外依赖。
 *   <li>提供数组追加元素、判断严格升序等通用数组操作。
 * </ul>
 *
 * <p>设计意图：Iceberg 在元数据 JSON 序列化时多使用 List 形式，而内存计算与文件格式写入时
 * 多使用基础类型数组以减少装箱开销和内存占用，因此需要高效的双向转换工具。{@code null} 输入统一 返回 {@code null}，保留语义透传，由调用方决定如何处理缺失值。
 *
 * <p>上下游关系：被 core 内部各类（如 Schema、Snapshot、元数据读写）使用；不依赖其他 Iceberg 业务模块， 仅依赖 relocated guava 的 {@link
 * Longs}。
 */
public class ArrayUtil {
  private ArrayUtil() {}

  /** 空的 boolean 数组常量，作为零长度输入的统一返回值以避免重复分配。 */
  public static final boolean[] EMPTY_BOOLEAN_ARRAY = new boolean[0];
  /** 空的 byte 数组常量。 */
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
  /** 空的 short 数组常量。 */
  public static final short[] EMPTY_SHORT_ARRAY = new short[0];
  /** 空的 int 数组常量。 */
  public static final int[] EMPTY_INT_ARRAY = new int[0];
  /** 空的 long 数组常量。 */
  public static final long[] EMPTY_LONG_ARRAY = new long[0];
  /** 空的 float 数组常量。 */
  public static final float[] EMPTY_FLOAT_ARRAY = new float[0];
  /** 空的 double 数组常量。 */
  public static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

  /**
   * 将 {@code int[]} 转换为 {@link List Integer} 列表。
   *
   * @param ints 基础类型 int 数组，可为 {@code null}
   * @return 装箱后的列表；输入为 {@code null} 时返回 {@code null}
   */
  public static List<Integer> toIntList(int[] ints) {
    if (ints != null) {
      return IntStream.of(ints).boxed().collect(Collectors.toList());
    } else {
      return null;
    }
  }

  /**
   * 将 {@link List Integer} 列表转换为 {@code int[]}。
   *
   * @param ints Integer 列表，可为 {@code null}
   * @return 拆箱后的基础类型数组；输入为 {@code null} 时返回 {@code null}
   */
  public static int[] toIntArray(List<Integer> ints) {
    if (ints != null) {
      return ints.stream().mapToInt(v -> v).toArray();
    } else {
      return null;
    }
  }

  /**
   * 将 {@code long[]} 转换为 {@link List Long} 列表。
   *
   * @param longs 基础类型 long 数组，可为 {@code null}
   * @return 装箱后的列表；输入为 {@code null} 时返回 {@code null}
   */
  public static List<Long> toLongList(long[] longs) {
    if (longs != null) {
      return LongStream.of(longs).boxed().collect(Collectors.toList());
    } else {
      return null;
    }
  }

  /**
   * 将 {@code long[]} 转换为不可修改的 {@link List Long} 列表。
   *
   * <p>设计要点：基于 guava {@link Longs#asList} 的视图实现，不发生拷贝，返回列表不可修改， 适合作为只读返回值暴露给调用方。
   *
   * @param longs 基础类型 long 数组，可为 {@code null}
   * @return 不可修改的列表视图；输入为 {@code null} 时返回 {@code null}
   */
  public static List<Long> toUnmodifiableLongList(long[] longs) {
    if (longs != null) {
      return Collections.unmodifiableList(Longs.asList(longs));
    } else {
      return null;
    }
  }

  /**
   * 将 {@link List Long} 列表转换为 {@code long[]}。
   *
   * @param longs Long 列表，可为 {@code null}
   * @return 拆箱后的基础类型数组；输入为 {@code null} 时返回 {@code null}
   */
  public static long[] toLongArray(List<Long> longs) {
    if (longs != null) {
      return longs.stream().mapToLong(v -> v).toArray();
    } else {
      return null;
    }
  }

  /**
   * 将 {@code Boolean} 对象数组拆箱为 {@code boolean} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_BOOLEAN_ARRAY} 常量。
   *
   * @param array Boolean 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static boolean[] toPrimitive(final Boolean[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_BOOLEAN_ARRAY;
    }
    final boolean[] result = new boolean[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].booleanValue();
    }
    return result;
  }

  /**
   * 将 {@code Byte} 对象数组拆箱为 {@code byte} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_BYTE_ARRAY} 常量。
   *
   * @param array Byte 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static byte[] toPrimitive(final Byte[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_BYTE_ARRAY;
    }
    final byte[] result = new byte[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].byteValue();
    }
    return result;
  }

  /**
   * 将 {@code Short} 对象数组拆箱为 {@code short} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_SHORT_ARRAY} 常量。
   *
   * @param array Short 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static short[] toPrimitive(final Short[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_SHORT_ARRAY;
    }
    final short[] result = new short[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].shortValue();
    }
    return result;
  }

  /**
   * 将 {@code Integer} 对象数组拆箱为 {@code int} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_INT_ARRAY} 常量。
   *
   * @param array Integer 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static int[] toPrimitive(final Integer[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_INT_ARRAY;
    }
    final int[] result = new int[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].intValue();
    }
    return result;
  }

  /**
   * 将 {@code Long} 对象数组拆箱为 {@code long} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_LONG_ARRAY} 常量。
   *
   * @param array Long 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static long[] toPrimitive(final Long[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_LONG_ARRAY;
    }
    final long[] result = new long[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].longValue();
    }
    return result;
  }

  /**
   * 将 {@code Float} 对象数组拆箱为 {@code float} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_FLOAT_ARRAY} 常量。
   *
   * @param array Float 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static float[] toPrimitive(final Float[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_FLOAT_ARRAY;
    }
    final float[] result = new float[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].floatValue();
    }
    return result;
  }

  /**
   * 将 {@code Double} 对象数组拆箱为 {@code double} 基础类型数组。
   *
   * <p>移植自 {@code org.apache.commons:commons-lang3}。输入为 {@code null} 时返回 {@code null}； 输入长度为 0 时返回
   * {@link #EMPTY_DOUBLE_ARRAY} 常量。
   *
   * @param array Double 对象数组，可为 {@code null}
   * @return 拆箱后的基础类型数组，{@code null} 输入返回 {@code null}
   * @throws NullPointerException 若数组元素为 {@code null}
   */
  public static double[] toPrimitive(final Double[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_DOUBLE_ARRAY;
    }
    final double[] result = new double[array.length];
    for (int i = 0; i < array.length; i++) {
      result[i] = array[i].doubleValue();
    }
    return result;
  }

  /**
   * 拷贝输入数组并在末尾追加指定元素，返回新数组。
   *
   * <p>新数组包含原数组所有元素并在末尾位置加入给定元素，组件类型与输入数组一致。 若输入数组为 {@code null}，则返回长度为 1、组件类型与元素一致的新数组；当元素也为
   * {@code null} 时 抛出 {@link IllegalArgumentException}。移植自 {@code
   * org.apache.commons:commons-lang3}。
   *
   * <pre>
   * ArrayUtils.add(null, null)      = IllegalArgumentException
   * ArrayUtils.add(null, "a")       = ["a"]
   * ArrayUtils.add(["a"], null)     = ["a", null]
   * ArrayUtils.add(["a"], "b")      = ["a", "b"]
   * ArrayUtils.add(["a", "b"], "c") = ["a", "b", "c"]
   * </pre>
   *
   * @param <T> 数组组件类型
   * @param array 原 数组，可为 {@code null}
   * @param element 待追加的元素，可为 {@code null}
   * @return 包含原元素与新元素的新数组；返回类型与输入数组一致，输入为 {@code null} 时与元素类型一致
   * @throws IllegalArgumentException 当 array 与 element 同时为 {@code null}
   */
  public static <T> T[] add(final T[] array, final T element) {
    Class<?> type;
    if (array != null) {
      type = array.getClass().getComponentType();
    } else if (element != null) {
      type = element.getClass();
    } else {
      throw new IllegalArgumentException("Arguments cannot both be null");
    }
    @SuppressWarnings("unchecked") // type must be T
    final T[] newArray = (T[]) copyArrayGrow1(array, type);
    newArray[newArray.length - 1] = element;
    return newArray;
  }

  /**
   * 返回比原数组长度大 1 的拷贝，末尾位置保留默认值。移植自 commons-lang3。
   *
   * @param array 待拷贝的数组；非 null 时按其长度 +1 扩容
   * @param newArrayComponentType 当 array 为 null 时，按此组件类型创建长度为 1 的新数组
   * @return 长度比输入大 1 的新数组
   */
  private static Object copyArrayGrow1(final Object array, final Class<?> newArrayComponentType) {
    if (array != null) {
      final int arrayLength = Array.getLength(array);
      final Object newArray =
          Array.newInstance(array.getClass().getComponentType(), arrayLength + 1);
      System.arraycopy(array, 0, newArray, 0, arrayLength);
      return newArray;
    }
    return Array.newInstance(newArrayComponentType, 1);
  }

  /**
   * 判断 long 数组是否严格升序。
   *
   * @param array 待判断的 long 数组
   * @return 若数组中每个元素都严格大于前一个元素则返回 {@code true}；长度小于等于 1 时返回 {@code true}
   */
  public static boolean isStrictlyAscending(long[] array) {
    for (int index = 1; index < array.length; index++) {
      if (array[index] <= array[index - 1]) {
        return false;
      }
    }

    return true;
  }
}
