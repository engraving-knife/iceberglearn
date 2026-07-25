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

import static org.apache.iceberg.TableMetadata.newTableMetadata;

import java.io.File;
import java.util.Map;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.exceptions.CommitStateUnknownException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 测试类：TestTables，用于验证 Tables 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Tables 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestTables {

  /** 辅助方法：tables。 */
  private TestTables() {}

  /** 辅助方法：upgrade。 */
  private static TestTable upgrade(File temp, String name, int newFormatVersion) {
    TestTable table = load(temp, name);
    TableOperations ops = table.ops();
    TableMetadata base = ops.current();
    ops.commit(base, ops.current().upgradeToFormatVersion(newFormatVersion));
    return table;
  }

  /** 辅助方法：create。 */
  public static TestTable create(
      File temp, String name, Schema schema, PartitionSpec spec, int formatVersion) {
    return create(temp, name, schema, spec, SortOrder.unsorted(), formatVersion);
  }

  /** 辅助方法：create。 */
  public static TestTable create(
      File temp,
      String name,
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      int formatVersion) {
    TestTableOperations ops = new TestTableOperations(name, temp);
    if (ops.current() != null) {
      throw new AlreadyExistsException("Table %s already exists at location: %s", name, temp);
    }

    ops.commit(
        null,
        newTableMetadata(
            schema, spec, sortOrder, temp.toString(), ImmutableMap.of(), formatVersion));

    return new TestTable(ops, name);
  }

  /** 辅助方法：create。 */
  public static TestTable create(
      File temp,
      String name,
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      int formatVersion,
      MetricsReporter reporter) {
    TestTableOperations ops = new TestTableOperations(name, temp);
    if (ops.current() != null) {
      throw new AlreadyExistsException("Table %s already exists at location: %s", name, temp);
    }

    ops.commit(
        null,
        newTableMetadata(
            schema, spec, sortOrder, temp.toString(), ImmutableMap.of(), formatVersion));

    return new TestTable(ops, name, reporter);
  }

  /** 辅助方法：begin create。 */
  public static Transaction beginCreate(File temp, String name, Schema schema, PartitionSpec spec) {
    return beginCreate(temp, name, schema, spec, SortOrder.unsorted());
  }

  /** 辅助方法：begin create。 */
  public static Transaction beginCreate(
      File temp, String name, Schema schema, PartitionSpec spec, SortOrder sortOrder) {
    TableOperations ops = new TestTableOperations(name, temp);
    if (ops.current() != null) {
      throw new AlreadyExistsException("Table %s already exists at location: %s", name, temp);
    }

    TableMetadata metadata =
        newTableMetadata(schema, spec, sortOrder, temp.toString(), ImmutableMap.of(), 1);

    return Transactions.createTableTransaction(name, ops, metadata);
  }

  /** 辅助方法：begin replace。 */
  public static Transaction beginReplace(
      File temp, String name, Schema schema, PartitionSpec spec) {
    return beginReplace(
        temp,
        name,
        schema,
        spec,
        SortOrder.unsorted(),
        ImmutableMap.of(),
        new TestTableOperations(name, temp));
  }

  /** 辅助方法：begin replace。 */
  public static Transaction beginReplace(
      File temp,
      String name,
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      Map<String, String> properties) {
    return beginReplace(
        temp, name, schema, spec, sortOrder, properties, new TestTableOperations(name, temp));
  }

  /** 辅助方法：begin replace。 */
  public static Transaction beginReplace(
      File temp,
      String name,
      Schema schema,
      PartitionSpec spec,
      SortOrder sortOrder,
      Map<String, String> properties,
      TestTableOperations ops) {
    TableMetadata current = ops.current();
    TableMetadata metadata;
    if (current != null) {
      metadata = current.buildReplacement(schema, spec, sortOrder, current.location(), properties);
      return Transactions.replaceTableTransaction(name, ops, metadata);
    } else {
      metadata = newTableMetadata(schema, spec, sortOrder, temp.toString(), properties);
      return Transactions.createTableTransaction(name, ops, metadata);
    }
  }

  /** 辅助方法：load。 */
  public static TestTable load(File temp, String name) {
    TestTableOperations ops = new TestTableOperations(name, temp);
    return new TestTable(ops, name);
  }

  /** 辅助方法：table with commit succeed but state unknown。 */
  public static TestTable tableWithCommitSucceedButStateUnknown(File temp, String name) {
    TestTableOperations ops = opsWithCommitSucceedButStateUnknown(temp, name);
    return new TestTable(ops, name);
  }

  /** 辅助方法：ops with commit succeed but state unknown。 */
  public static TestTableOperations opsWithCommitSucceedButStateUnknown(File temp, String name) {
    return new TestTableOperations(name, temp) {
      /** 辅助方法：commit。 */
      @Override
      public void commit(TableMetadata base, TableMetadata updatedMetadata) {
        super.commit(base, updatedMetadata);
        throw new CommitStateUnknownException(new RuntimeException("datacenter on fire"));
      }
    };
  }

  public static class TestTable extends BaseTable {
    private final TestTableOperations ops;

    /** 辅助方法：table。 */
    private TestTable(TestTableOperations ops, String name) {
      super(ops, name);
      this.ops = ops;
    }

    /** 辅助方法：table。 */
    private TestTable(TestTableOperations ops, String name, MetricsReporter reporter) {
      super(ops, name, reporter);
      this.ops = ops;
    }

    TestTableOperations ops() {
      return ops;
    }
  }

  private static final Map<String, TableMetadata> METADATA = Maps.newHashMap();
  private static final Map<String, Integer> VERSIONS = Maps.newHashMap();

  /** 辅助方法：clear tables。 */
  public static void clearTables() {
    synchronized (METADATA) {
      METADATA.clear();
      VERSIONS.clear();
    }
  }

  static TableMetadata readMetadata(String tableName) {
    synchronized (METADATA) {
      return METADATA.get(tableName);
    }
  }

  static Integer metadataVersion(String tableName) {
    synchronized (METADATA) {
      return VERSIONS.get(tableName);
    }
  }

  public static class TestTableOperations implements TableOperations {

    private final String tableName;
    private final File metadata;
    private TableMetadata current = null;
    private long lastSnapshotId = 0;
    private int failCommits = 0;

    /** 辅助方法：table operations。 */
    public TestTableOperations(String tableName, File location) {
      this.tableName = tableName;
      this.metadata = new File(location, "metadata");
      metadata.mkdirs();
      refresh();
      if (current != null) {
        for (Snapshot snap : current.snapshots()) {
          this.lastSnapshotId = Math.max(lastSnapshotId, snap.snapshotId());
        }
      } else {
        this.lastSnapshotId = 0;
      }
    }

    void failCommits(int numFailures) {
      this.failCommits = numFailures;
    }

    /** 辅助方法：current。 */
    @Override
    public TableMetadata current() {
      return current;
    }

    /** 辅助方法：refresh。 */
    @Override
    public TableMetadata refresh() {
      synchronized (METADATA) {
        this.current = METADATA.get(tableName);
      }
      return current;
    }

    /** 辅助方法：commit。 */
    @Override
    public void commit(TableMetadata base, TableMetadata updatedMetadata) {
      if (base != current) {
        throw new CommitFailedException("Cannot commit changes based on stale metadata");
      }
      synchronized (METADATA) {
        refresh();
        if (base == current) {
          if (failCommits > 0) {
            this.failCommits -= 1;
            throw new CommitFailedException("Injected failure");
          }
          Integer version = VERSIONS.get(tableName);
          // remove changes from the committed metadata
          this.current = TableMetadata.buildFrom(updatedMetadata).discardChanges().build();
          VERSIONS.put(tableName, version == null ? 0 : version + 1);
          METADATA.put(tableName, current);
        } else {
          throw new CommitFailedException(
              "Commit failed: table was updated at %d", current.lastUpdatedMillis());
        }
      }
    }

    /** 辅助方法：io。 */
    @Override
    public FileIO io() {
      return new LocalFileIO();
    }

    /** 辅助方法：location provider。 */
    @Override
    public LocationProvider locationProvider() {
      Preconditions.checkNotNull(
          current, "Current metadata should not be null when locationProvider is called");
      return LocationProviders.locationsFor(current.location(), current.properties());
    }

    /** 辅助方法：metadata file location。 */
    @Override
    public String metadataFileLocation(String fileName) {
      return new File(metadata, fileName).getAbsolutePath();
    }

    /** 辅助方法：new snapshot id。 */
    @Override
    public long newSnapshotId() {
      long nextSnapshotId = lastSnapshotId + 1;
      this.lastSnapshotId = nextSnapshotId;
      return nextSnapshotId;
    }
  }

  static class LocalFileIO implements FileIO {

    /** 辅助方法：new input file。 */
    @Override
    public InputFile newInputFile(String path) {
      return Files.localInput(path);
    }

    /** 辅助方法：new output file。 */
    @Override
    public OutputFile newOutputFile(String path) {
      return Files.localOutput(path);
    }

    /** 辅助方法：delete file。 */
    @Override
    public void deleteFile(String path) {
      if (!new File(path).delete()) {
        throw new RuntimeIOException("Failed to delete file: " + path);
      }
    }
  }
}
