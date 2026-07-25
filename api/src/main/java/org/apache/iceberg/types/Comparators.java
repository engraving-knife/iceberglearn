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
package org.apache.iceberg.types;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.util.UnicodeUtil;

/**
 * 类型比较器工厂：为每种 Iceberg 类型提供对应的 {@link Comparator}。
 *
 * <p>所属模块：iceberg-api（被 core 的排序、表达式求值、数据比较等使用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为原始类型提供比较器（{@link #forType(Type.PrimitiveType)}）。
 *   <li>为 struct/list 类型提供递归比较器（{@link #forType(Types.StructType)}/{@link
 *       #forType(Types.ListType)}）。
 *   <li>提供 null 安全比较器（{@link #nullsFirst()}/{@link #nullsLast()}）。
 *   <li>提供无符号字节比较器（{@link #unsignedBytes()}/{@link #unsignedByteArrays()}）。
 *   <li>提供 Unicode 正确排序的 CharSequence 比较器（{@link #charSequences()}）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>无参数原始类型的比较器预缓存到 COMPARATORS 不可变映射，避免重复创建。
 *   <li>struct/list 比较器递归构建子字段比较器；可选字段用 nullsFirst 包装。
 *   <li>字符串比较器处理 UTF-16 代理对问题：4 字节 UTF-8 字符在 Java 中用两个 char 表示， 逐 char 比较会出错，故检测高代理区并视为大于任何 3
 *       字节以内字符。
 *   <li>二进制比较采用无符号逐字节比较，与 Iceberg 存储排序规范一致。
 * </ul>
 *
 * <p>上下游关系：被 core 的排序实现、表达式比较、各引擎的排序下推使用。
 */
public class Comparators {

  private Comparators() {}

  private static final ImmutableMap<Type.PrimitiveType, Comparator<?>> COMPARATORS =
      ImmutableMap.<Type.PrimitiveType, Comparator<?>>builder()
          .put(Types.BooleanType.get(), Comparator.naturalOrder())
          .put(Types.IntegerType.get(), Comparator.naturalOrder())
          .put(Types.LongType.get(), Comparator.naturalOrder())
          .put(Types.FloatType.get(), Comparator.naturalOrder())
          .put(Types.DoubleType.get(), Comparator.naturalOrder())
          .put(Types.DateType.get(), Comparator.naturalOrder())
          .put(Types.TimeType.get(), Comparator.naturalOrder())
          .put(Types.TimestampType.withZone(), Comparator.naturalOrder())
          .put(Types.TimestampType.withoutZone(), Comparator.naturalOrder())
          .put(Types.StringType.get(), Comparators.charSequences())
          .put(Types.UUIDType.get(), Comparator.naturalOrder())
          .put(Types.BinaryType.get(), Comparators.unsignedBytes())
          .buildOrThrow();

  /** 为 struct 类型构建递归比较器。 */
  public static Comparator<StructLike> forType(Types.StructType struct) {
    return new StructLikeComparator(struct);
  }

  /** 为 list 类型构建递归比较器。 */
  public static <T> Comparator<List<T>> forType(Types.ListType list) {
    return new ListComparator<>(list);
  }

  /**
   * 为原始类型返回比较器。
   *
   * <p>逻辑：从 COMPARATORS 缓存查找；FixedType 用无符号字节比较；DecimalType 用自然序。
   *
   * @param type 原始类型
   * @param <T> 比较值类型
   * @return 比较器
   * @throws UnsupportedOperationException 不支持的类型
   */
  @SuppressWarnings("unchecked")
  public static <T> Comparator<T> forType(Type.PrimitiveType type) {
    Comparator<?> cmp = COMPARATORS.get(type);
    if (cmp != null) {
      return (Comparator<T>) cmp;
    } else if (type instanceof Types.FixedType) {
      return (Comparator<T>) Comparators.unsignedBytes();
    } else if (type instanceof Types.DecimalType) {
      return (Comparator<T>) Comparator.naturalOrder();
    }

    throw new UnsupportedOperationException("Cannot determine comparator for type: " + type);
  }

  /** 按类型分派到原始/struct/list 比较器的内部方法。 */
  @SuppressWarnings("unchecked")
  private static <T> Comparator<T> internal(Type type) {
    if (type.isPrimitiveType()) {
      return forType(type.asPrimitiveType());
    } else if (type.isStructType()) {
      return (Comparator<T>) forType(type.asStructType());
    } else if (type.isListType()) {
      return (Comparator<T>) forType(type.asListType());
    }

    throw new UnsupportedOperationException("Cannot determine comparator for type: " + type);
  }

  private static class StructLikeComparator implements Comparator<StructLike> {
    private final Comparator<Object>[] comparators;
    private final Class<?>[] classes;

    private StructLikeComparator(Types.StructType struct) {
      this.comparators =
          struct.fields().stream()
              .map(
                  field ->
                      field.isOptional()
                          ? Comparators.nullsFirst().thenComparing(internal(field.type()))
                          : internal(field.type()))
              .toArray((IntFunction<Comparator<Object>[]>) Comparator[]::new);
      this.classes =
          struct.fields().stream()
              .map(field -> field.type().typeId().javaClass())
              .toArray(Class<?>[]::new);
    }

    @Override
    public int compare(StructLike o1, StructLike o2) {
      if (o1 == o2) {
        return 0;
      }

      for (int i = 0; i < comparators.length; i += 1) {
        Class<?> valueClass = classes[i];
        int cmp = comparators[i].compare(o1.get(i, valueClass), o2.get(i, valueClass));
        if (cmp != 0) {
          return cmp;
        }
      }

      return 0;
    }
  }

  private static class ListComparator<T> implements Comparator<List<T>> {
    private final Comparator<T> elementComparator;

    private ListComparator(Types.ListType list) {
      Comparator<T> elemComparator = internal(list.elementType());
      this.elementComparator =
          list.isElementOptional()
              ? Comparators.<T>nullsFirst().thenComparing(elemComparator)
              : elemComparator;
    }

    @Override
    public int compare(List<T> o1, List<T> o2) {
      if (o1 == o2) {
        return 0;
      }

      int length = Math.min(o1.size(), o2.size());
      for (int i = 0; i < length; i += 1) {
        int cmp = elementComparator.compare(o1.get(i), o2.get(i));
        if (cmp != 0) {
          return cmp;
        }
      }

      return Integer.compare(o1.size(), o2.size());
    }
  }

  /** 返回 ByteBuffer 的无符号字节比较器单例。 */
  public static Comparator<ByteBuffer> unsignedBytes() {
    return UnsignedByteBufComparator.INSTANCE;
  }

  /** 返回 byte 数组的无符号字节比较器单例。 */
  public static Comparator<byte[]> unsignedByteArrays() {
    return UnsignedByteArrayComparator.INSTANCE;
  }

  /** 返回 ByteBuffer 的有符号字节比较器（自然序）。 */
  public static Comparator<ByteBuffer> signedBytes() {
    return Comparator.naturalOrder();
  }

  /** 返回 null 排在最前的比较器单例。 */
  @SuppressWarnings("unchecked")
  public static <T> Comparator<T> nullsFirst() {
    return (Comparator<T>) NullsFirst.INSTANCE;
  }

  /** 返回 null 排在最后的比较器单例。 */
  @SuppressWarnings("unchecked")
  public static <T> Comparator<T> nullsLast() {
    return (Comparator<T>) NullsLast.INSTANCE;
  }

  /** 返回 Unicode 正确排序的 CharSequence 比较器单例。 */
  public static Comparator<CharSequence> charSequences() {
    return CharSeqComparator.INSTANCE;
  }

  private static class NullsFirst<T> implements Comparator<T> {
    private static final NullsFirst<?> INSTANCE = new NullsFirst<>();

    private NullsFirst() {}

    @Override
    public int compare(T o1, T o2) {
      if (o1 == o2) {
        return 0;
      }

      if (o1 != null) {
        if (o2 != null) {
          return 0;
        }
        return 1;
      }

      return -1;
    }

    @Override
    public Comparator<T> thenComparing(Comparator<? super T> other) {
      return new NullSafeChainedComparator<>(this, other);
    }
  }

  private static class NullsLast<T> implements Comparator<T> {
    private static final NullsLast<?> INSTANCE = new NullsLast<>();

    private NullsLast() {}

    @Override
    public int compare(T o1, T o2) {
      if (o1 == o2) {
        return 0;
      }

      if (o1 != null) {
        if (o2 != null) {
          return 0;
        }
        return -1;
      }

      return 1;
    }

    @Override
    public Comparator<T> thenComparing(Comparator<? super T> other) {
      return new NullSafeChainedComparator<>(this, other);
    }
  }

  private static class NullSafeChainedComparator<T> implements Comparator<T> {
    private final Comparator<T> first;
    private final Comparator<? super T> second;

    NullSafeChainedComparator(Comparator<T> first, Comparator<? super T> second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public int compare(T o1, T o2) {
      if (o1 == o2) {
        return 0;
      }

      int cmp = first.compare(o1, o2);
      if (cmp == 0 && o1 != null) {
        return second.compare(o1, o2);
      }
      return cmp;
    }
  }

  private static class UnsignedByteBufComparator implements Comparator<ByteBuffer> {
    private static final UnsignedByteBufComparator INSTANCE = new UnsignedByteBufComparator();

    private UnsignedByteBufComparator() {}

    @Override
    public int compare(ByteBuffer buf1, ByteBuffer buf2) {
      if (buf1 == buf2) {
        return 0;
      }

      int len = Math.min(buf1.remaining(), buf2.remaining());

      // find the first difference and return
      int b1pos = buf1.position();
      int b2pos = buf2.position();
      for (int i = 0; i < len; i += 1) {
        // Conversion to int is what Byte.toUnsignedInt would do
        int cmp =
            Integer.compare(((int) buf1.get(b1pos + i)) & 0xff, ((int) buf2.get(b2pos + i)) & 0xff);
        if (cmp != 0) {
          return cmp;
        }
      }

      // if there are no differences, then the shorter seq is smaller
      return Integer.compare(buf1.remaining(), buf2.remaining());
    }
  }

  private static class UnsignedByteArrayComparator implements Comparator<byte[]> {
    private static final UnsignedByteArrayComparator INSTANCE = new UnsignedByteArrayComparator();

    private UnsignedByteArrayComparator() {}

    @Override
    public int compare(byte[] array1, byte[] array2) {
      if (array1 == array2) {
        return 0;
      }

      int len = Math.min(array1.length, array2.length);

      // find the first difference and return
      for (int i = 0; i < len; i += 1) {
        // Conversion to int is what Byte.toUnsignedInt would do
        int cmp = Integer.compare(((int) array1[i]) & 0xff, ((int) array2[i]) & 0xff);
        if (cmp != 0) {
          return cmp;
        }
      }

      // if there are no differences, then the shorter seq is smaller
      return Integer.compare(array1.length, array2.length);
    }
  }

  private static class CharSeqComparator implements Comparator<CharSequence> {
    private static final CharSeqComparator INSTANCE = new CharSeqComparator();

    private CharSeqComparator() {}

    /**
     * 字符串比较逻辑说明。
     *
     * <p>Java char 只支持 3 字节以内的 UTF-8 字符。4 字节 UTF-8 字符用两个 Java char （UTF-16 代理对）表示。逐 char 比较在比较 4
     * 字节字符与普通 char 时会出错。 通过检测高代理区，把 4 字节字符视为字典序大于任何 3 字节以内字符。
     */
    @Override
    public int compare(CharSequence s1, CharSequence s2) {
      if (s1 == s2) {
        return 0;
      }

      int len = Math.min(s1.length(), s2.length());

      // find the first difference and return
      for (int i = 0; i < len; i += 1) {
        char c1 = s1.charAt(i);
        char c2 = s2.charAt(i);
        boolean isC1HighSurrogate = UnicodeUtil.isCharHighSurrogate(c1);
        boolean isC2HighSurrogate = UnicodeUtil.isCharHighSurrogate(c2);
        if (isC1HighSurrogate && !isC2HighSurrogate) {
          return 1;
        }
        if (!isC1HighSurrogate && isC2HighSurrogate) {
          return -1;
        }
        int cmp = Character.compare(c1, c2);
        if (cmp != 0) {
          return cmp;
        }
      }

      // if there are no differences, then the shorter seq is first
      return Integer.compare(s1.length(), s2.length());
    }
  }
}
