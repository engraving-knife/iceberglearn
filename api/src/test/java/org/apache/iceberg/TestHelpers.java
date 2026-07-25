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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ClosureSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundSetPredicate;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.ByteBuffers;
import org.assertj.core.api.Assertions;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * 文件级说明：测试 TestHelpers 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestHelpers 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestHelpers {

  /** 辅助方法：TestHelpers。 */
  private TestHelpers() {}

  /** Wait in a tight check loop until system clock is past {@code timestampMillis} */
  public static long waitUntilAfter(long timestampMillis) {
    long current = System.currentTimeMillis();
    while (current <= timestampMillis) {
      current = System.currentTimeMillis();
    }
    return current;
  }

  /** 辅助方法：assertAndUnwrap。 */
  public static <T> T assertAndUnwrap(Expression expr, Class<T> expected) {
    assertThat(expr).as("Expression should have expected type: " + expected).isInstanceOf(expected);
    return expected.cast(expr);
  }

  /** 辅助方法：assertAndUnwrap。 */
  @SuppressWarnings("unchecked")
  public static <T> BoundPredicate<T> assertAndUnwrap(Expression expr) {
    assertThat(expr)
        .as("Expression should be a bound predicate: " + expr)
        .isInstanceOf(BoundPredicate.class);
    return (BoundPredicate<T>) expr;
  }

  /** 辅助方法：assertAndUnwrapBoundSet。 */
  @SuppressWarnings("unchecked")
  public static <T> BoundSetPredicate<T> assertAndUnwrapBoundSet(Expression expr) {
    assertThat(expr)
        .as("Expression should be a bound set predicate: " + expr)
        .isInstanceOf(BoundSetPredicate.class);
    return (BoundSetPredicate<T>) expr;
  }

  /** 辅助方法：assertAndUnwrapUnbound。 */
  @SuppressWarnings("unchecked")
  public static <T> UnboundPredicate<T> assertAndUnwrapUnbound(Expression expr) {
    assertThat(expr)
        .as("Expression should be an unbound predicate: " + expr)
        .isInstanceOf(UnboundPredicate.class);
    return (UnboundPredicate<T>) expr;
  }

  /** 辅助方法：assertAllReferencesBound。 */
  public static void assertAllReferencesBound(String message, Expression expr) {
    ExpressionVisitors.visit(expr, new CheckReferencesBound(message));
  }

  /** 辅助方法：roundTripSerialize。 */
  @SuppressWarnings("unchecked")
  public static <T> T roundTripSerialize(T type) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(type);
    }

    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (T) in.readObject();
    }
  }

  /** 辅助方法：assertSameSchemaList。 */
  public static void assertSameSchemaList(List<Schema> list1, List<Schema> list2) {
    Assertions.assertThat(list1)
        .as("Should have same number of schemas in both lists")
        .hasSameSizeAs(list2);

    IntStream.range(0, list1.size())
        .forEach(
            index -> {
              Schema schema1 = list1.get(index);
              Schema schema2 = list2.get(index);
              assertThat(schema2.schemaId())
                  .as("Should have matching schema id")
                  .isEqualTo(schema1.schemaId());
              assertThat(schema2.asStruct())
                  .as("Should have matching schema struct")
                  .isEqualTo(schema1.asStruct());
            });
  }

  /** 辅助方法：assertSerializedMetadata。 */
  public static void assertSerializedMetadata(Table expected, Table actual) {
    assertThat(actual.name()).as("Name must match").isEqualTo(expected.name());
    assertThat(actual.location()).as("Location must match").isEqualTo(expected.location());
    assertThat(actual.properties()).as("Props must match").isEqualTo(expected.properties());
    assertThat(actual.schema().asStruct())
        .as("Schema must match")
        .isEqualTo(expected.schema().asStruct());
    assertThat(actual.spec()).as("Spec must match").isEqualTo(expected.spec());
    assertThat(actual.sortOrder()).as("Sort order must match").isEqualTo(expected.sortOrder());
  }

  /** 辅助方法：assertSerializedAndLoadedMetadata。 */
  public static void assertSerializedAndLoadedMetadata(Table expected, Table actual) {
    assertSerializedMetadata(expected, actual);
    assertThat(actual.specs()).as("Specs must match").isEqualTo(expected.specs());
    assertThat(actual.sortOrders()).as("Sort orders must match").isEqualTo(expected.sortOrders());
    assertThat(actual.currentSnapshot())
        .as("Current snapshot must match")
        .isEqualTo(expected.currentSnapshot());
    assertThat(actual.snapshots()).as("Snapshots must match").isEqualTo(expected.snapshots());
    assertThat(actual.history()).as("History must match").isEqualTo(expected.history());
  }

  /** 辅助方法：assertSameSchemaMap。 */
  public static void assertSameSchemaMap(Map<Integer, Schema> map1, Map<Integer, Schema> map2) {
    Assertions.assertThat(map1)
        .as("Should have same number of schemas in both maps")
        .hasSameSizeAs(map2);

    map1.forEach(
        (schemaId, schema1) -> {
          Schema schema2 = map2.get(schemaId);
          assertThat(schema2)
              .as(String.format("Schema ID %s does not exist in map: %s", schemaId, map2))
              .isNotNull();

          assertThat(schema2.schemaId())
              .as("Should have matching schema id")
              .isEqualTo(schema1.schemaId());
          assertThat(schema1.sameSchema(schema2))
              .as(
                  String.format(
                      "Should be the same schema. Schema 1: %s, schema 2: %s", schema1, schema2))
              .isTrue();
        });
  }

  /**
   * Deserializes a single {@link Object} from an array of bytes.
   *
   * <p>If the call site incorrectly types the return value, a {@link ClassCastException} is thrown
   * from the call site. Without Generics in this declaration, the call site must type cast and can
   * cause the same ClassCastException. Note that in both cases, the ClassCastException is in the
   * call site, not in this method.
   *
   * <p>This code is borrowed from `org.apache.commons:commons-lang3`
   *
   * @param <T> the object type to be deserialized
   * @param objectData the serialized object, must not be null
   * @return the deserialized object
   * @throws NullPointerException if {@code objectData} is {@code null}
   * @throws IOException (runtime) if the serialization fails
   */
  public static <T> T deserialize(final byte[] objectData)
      throws IOException, ClassNotFoundException {
    Preconditions.checkNotNull(objectData, "objectData");
    return deserialize(new ByteArrayInputStream(objectData));
  }

  /**
   * Deserializes an {@link Object} from the specified stream.
   *
   * <p>The stream will be closed once the object is written. This avoids the need for a finally
   * clause, and maybe also exception handling, in the application code.
   *
   * <p>The stream passed in is not buffered internally within this method. This is the
   * responsibility of your application if desired.
   *
   * <p>If the call site incorrectly types the return value, a {@link ClassCastException} is thrown
   * from the call site. Without Generics in this declaration, the call site must type cast and can
   * cause the same ClassCastException. Note that in both cases, the ClassCastException is in the
   * call site, not in this method.
   *
   * <p>This code is borrowed from `org.apache.commons:commons-lang3`
   *
   * @param <T> the object type to be deserialized
   * @param inputStream the serialized object input stream, must not be null
   * @return the deserialized object
   * @throws NullPointerException if {@code inputStream} is {@code null}
   * @throws IOException (runtime) if the serialization fails
   * @throws ClassNotFoundException if Class is not found
   */
  public static <T> T deserialize(final InputStream inputStream)
      throws IOException, ClassNotFoundException {
    Preconditions.checkNotNull(inputStream, "inputStream");
    try (ObjectInputStream in = new ObjectInputStream(inputStream)) {
      @SuppressWarnings("unchecked")
      final T obj = (T) in.readObject();
      return obj;
    }
  }
  /**
   * Serializes an {@link Object} to a byte array for storage/serialization.
   *
   * <p>This code is borrowed from `org.apache.commons:commons-lang3`
   *
   * @param obj the object to serialize to bytes
   * @return a byte[] with the converted Serializable
   * @throws IOException (runtime) if the serialization fails
   */
  public static byte[] serialize(final Serializable obj) throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
    serialize(obj, baos);
    return baos.toByteArray();
  }

  /**
   * Serializes an {@link Object} to the specified stream.
   *
   * <p>The stream will be closed once the object is written. This avoids the need for a finally
   * clause, and maybe also exception handling, in the application code.
   *
   * <p>The stream passed in is not buffered internally within this method. This is the
   * responsibility of your application if desired.
   *
   * <p>This code is borrowed from `org.apache.commons:commons-lang3`
   *
   * @param obj the object to serialize to bytes, may be null
   * @param outputStream the stream to write to, must not be null
   * @throws NullPointerException if {@code outputStream} is {@code null}
   * @throws IOException (runtime) if the serialization fails
   */
  public static void serialize(final Serializable obj, final OutputStream outputStream)
      throws IOException {
    Preconditions.checkNotNull(outputStream, "outputStream");
    try (ObjectOutputStream out = new ObjectOutputStream(outputStream)) {
      out.writeObject(obj);
    }
  }

  public static class KryoHelpers {
    /** 辅助方法：KryoHelpers。 */
    private KryoHelpers() {}

    /** 辅助方法：roundTripSerialize。 */
    @SuppressWarnings("unchecked")
    public static <T> T roundTripSerialize(T obj) throws IOException {
      Kryo kryo = new Kryo();

      // required for avoiding requirement of zero arg constructor
      kryo.setInstantiatorStrategy(
          new Kryo.DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));

      // required for serializing and deserializing $$Lambda$ Anonymous Classes
      kryo.register(SerializedLambda.class);
      kryo.register(ClosureSerializer.Closure.class, new ClosureSerializer());

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

  private static class CheckReferencesBound extends ExpressionVisitors.ExpressionVisitor<Void> {
    private final String message;

    CheckReferencesBound(String message) {
      this.message = message;
    }

    /** 辅助方法：predicate。 */
    @Override
    public <T> Void predicate(UnboundPredicate<T> pred) {
      fail(message + ": Found unbound predicate: " + pred);
      return null;
    }
  }

  /** Implements {@link StructLike#get} for passing data in tests. */
  public static class Row implements StructLike {
    /** 辅助方法：of。 */
    public static Row of(Object... values) {
      return new Row(values);
    }

    private final Object[] values;

    /** 辅助方法：Row。 */
    private Row(Object... values) {
      this.values = values;
    }

    /** 辅助方法：size。 */
    @Override
    public int size() {
      return values.length;
    }

    /** 辅助方法：get。 */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(int pos, Class<T> javaClass) {
      return javaClass.cast(values[pos]);
    }

    /** 辅助方法：set。 */
    @Override
    public <T> void set(int pos, T value) {
      values[pos] = value;
    }

    /** 辅助方法：equals。 */
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }

      Row that = (Row) other;

      return Arrays.equals(values, that.values);
    }

    /** 辅助方法：hashCode。 */
    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }
  }

  public static class TestFieldSummary implements ManifestFile.PartitionFieldSummary {
    private final boolean containsNull;
    private final Boolean containsNaN;
    private final ByteBuffer lowerBound;
    private final ByteBuffer upperBound;

    /** 辅助方法：TestFieldSummary。 */
    public TestFieldSummary(boolean containsNull, ByteBuffer lowerBound, ByteBuffer upperBound) {
      this(containsNull, null, lowerBound, upperBound);
    }

    /** 辅助方法：TestFieldSummary。 */
    public TestFieldSummary(
        boolean containsNull, Boolean containsNaN, ByteBuffer lowerBound, ByteBuffer upperBound) {
      this.containsNull = containsNull;
      this.containsNaN = containsNaN;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
    }

    /** 辅助方法：containsNull。 */
    @Override
    public boolean containsNull() {
      return containsNull;
    }

    /** 辅助方法：containsNaN。 */
    @Override
    public Boolean containsNaN() {
      return containsNaN;
    }

    /** 辅助方法：lowerBound。 */
    @Override
    public ByteBuffer lowerBound() {
      return lowerBound;
    }

    /** 辅助方法：upperBound。 */
    @Override
    public ByteBuffer upperBound() {
      return upperBound;
    }

    /** 辅助方法：copy。 */
    @Override
    public ManifestFile.PartitionFieldSummary copy() {
      return this;
    }
  }

  public static class TestManifestFile implements ManifestFile {
    private final String path;
    private final long length;
    private final int specId;
    private final ManifestContent content;
    private final Long snapshotId;
    private final Integer addedFiles;
    private final Long addedRows;
    private final Integer existingFiles;
    private final Long existingRows;
    private final Integer deletedFiles;
    private final Long deletedRows;
    private final List<PartitionFieldSummary> partitions;
    private final byte[] keyMetadata;

    /** 辅助方法：TestManifestFile。 */
    public TestManifestFile(
        String path,
        long length,
        int specId,
        Long snapshotId,
        Integer addedFiles,
        Integer existingFiles,
        Integer deletedFiles,
        List<PartitionFieldSummary> partitions,
        ByteBuffer keyMetadata) {
      this.path = path;
      this.length = length;
      this.specId = specId;
      this.content = ManifestContent.DATA;
      this.snapshotId = snapshotId;
      this.addedFiles = addedFiles;
      this.addedRows = null;
      this.existingFiles = existingFiles;
      this.existingRows = null;
      this.deletedFiles = deletedFiles;
      this.deletedRows = null;
      this.partitions = partitions;
      this.keyMetadata = ByteBuffers.toByteArray(keyMetadata);
    }

    /** 辅助方法：TestManifestFile。 */
    public TestManifestFile(
        String path,
        long length,
        int specId,
        ManifestContent content,
        Long snapshotId,
        Integer addedFiles,
        Long addedRows,
        Integer existingFiles,
        Long existingRows,
        Integer deletedFiles,
        Long deletedRows,
        List<PartitionFieldSummary> partitions,
        ByteBuffer keyMetadata) {
      this.path = path;
      this.length = length;
      this.specId = specId;
      this.content = content;
      this.snapshotId = snapshotId;
      this.addedFiles = addedFiles;
      this.addedRows = addedRows;
      this.existingFiles = existingFiles;
      this.existingRows = existingRows;
      this.deletedFiles = deletedFiles;
      this.deletedRows = deletedRows;
      this.partitions = partitions;
      this.keyMetadata = ByteBuffers.toByteArray(keyMetadata);
    }

    /** 辅助方法：path。 */
    @Override
    public String path() {
      return path;
    }

    /** 辅助方法：length。 */
    @Override
    public long length() {
      return length;
    }

    /** 辅助方法：partitionSpecId。 */
    @Override
    public int partitionSpecId() {
      return specId;
    }

    /** 辅助方法：content。 */
    @Override
    public ManifestContent content() {
      return content;
    }

    /** 辅助方法：sequenceNumber。 */
    @Override
    public long sequenceNumber() {
      return 0;
    }

    /** 辅助方法：minSequenceNumber。 */
    @Override
    public long minSequenceNumber() {
      return 0;
    }

    /** 辅助方法：snapshotId。 */
    @Override
    public Long snapshotId() {
      return snapshotId;
    }

    /** 辅助方法：addedFilesCount。 */
    @Override
    public Integer addedFilesCount() {
      return addedFiles;
    }

    /** 辅助方法：addedRowsCount。 */
    @Override
    public Long addedRowsCount() {
      return addedRows;
    }

    /** 辅助方法：existingFilesCount。 */
    @Override
    public Integer existingFilesCount() {
      return existingFiles;
    }

    /** 辅助方法：existingRowsCount。 */
    @Override
    public Long existingRowsCount() {
      return existingRows;
    }

    /** 辅助方法：deletedFilesCount。 */
    @Override
    public Integer deletedFilesCount() {
      return deletedFiles;
    }

    /** 辅助方法：deletedRowsCount。 */
    @Override
    public Long deletedRowsCount() {
      return deletedRows;
    }

    /** 辅助方法：partitions。 */
    @Override
    public List<PartitionFieldSummary> partitions() {
      return partitions;
    }

    /** 辅助方法：keyMetadata。 */
    @Override
    public ByteBuffer keyMetadata() {
      return keyMetadata == null ? null : ByteBuffer.wrap(keyMetadata);
    }

    /** 辅助方法：copy。 */
    @Override
    public ManifestFile copy() {
      return this;
    }
  }

  public static class TestDataFile implements DataFile {
    private final String path;
    private final StructLike partition;
    private final long recordCount;
    private final Map<Integer, Long> valueCounts;
    private final Map<Integer, Long> nullValueCounts;
    private final Map<Integer, Long> nanValueCounts;
    private final Map<Integer, ByteBuffer> lowerBounds;
    private final Map<Integer, ByteBuffer> upperBounds;

    /** 辅助方法：TestDataFile。 */
    public TestDataFile(String path, StructLike partition, long recordCount) {
      this(path, partition, recordCount, null, null, null, null, null);
    }

    /** 辅助方法：TestDataFile。 */
    public TestDataFile(
        String path,
        StructLike partition,
        long recordCount,
        Map<Integer, Long> valueCounts,
        Map<Integer, Long> nullValueCounts,
        Map<Integer, Long> nanValueCounts,
        Map<Integer, ByteBuffer> lowerBounds,
        Map<Integer, ByteBuffer> upperBounds) {
      this.path = path;
      this.partition = partition;
      this.recordCount = recordCount;
      this.valueCounts = valueCounts;
      this.nullValueCounts = nullValueCounts;
      this.nanValueCounts = nanValueCounts;
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    /** 辅助方法：pos。 */
    @Override
    public Long pos() {
      return null;
    }

    /** 辅助方法：specId。 */
    @Override
    public int specId() {
      return 0;
    }

    /** 辅助方法：path。 */
    @Override
    public CharSequence path() {
      return path;
    }

    /** 辅助方法：format。 */
    @Override
    public FileFormat format() {
      return FileFormat.fromFileName(path());
    }

    /** 辅助方法：partition。 */
    @Override
    public StructLike partition() {
      return partition;
    }

    /** 辅助方法：recordCount。 */
    @Override
    public long recordCount() {
      return recordCount;
    }

    /** 辅助方法：fileSizeInBytes。 */
    @Override
    public long fileSizeInBytes() {
      return 0;
    }

    /** 辅助方法：columnSizes。 */
    @Override
    public Map<Integer, Long> columnSizes() {
      return null;
    }

    /** 辅助方法：valueCounts。 */
    @Override
    public Map<Integer, Long> valueCounts() {
      return valueCounts;
    }

    /** 辅助方法：nullValueCounts。 */
    @Override
    public Map<Integer, Long> nullValueCounts() {
      return nullValueCounts;
    }

    /** 辅助方法：nanValueCounts。 */
    @Override
    public Map<Integer, Long> nanValueCounts() {
      return nanValueCounts;
    }

    /** 辅助方法：lowerBounds。 */
    @Override
    public Map<Integer, ByteBuffer> lowerBounds() {
      return lowerBounds;
    }

    /** 辅助方法：upperBounds。 */
    @Override
    public Map<Integer, ByteBuffer> upperBounds() {
      return upperBounds;
    }

    /** 辅助方法：keyMetadata。 */
    @Override
    public ByteBuffer keyMetadata() {
      return null;
    }

    /** 辅助方法：copy。 */
    @Override
    public DataFile copy() {
      return this;
    }

    /** 辅助方法：copyWithoutStats。 */
    @Override
    public DataFile copyWithoutStats() {
      return this;
    }

    /** 辅助方法：splitOffsets。 */
    @Override
    public List<Long> splitOffsets() {
      return null;
    }
  }
}
