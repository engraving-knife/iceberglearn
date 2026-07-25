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

import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopFileIO;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.metrics.MetricsReport;
import org.apache.iceberg.metrics.MetricsReporter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestCatalogUtil，用于验证 Catalog Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Catalog Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestCatalogUtil {

  /**
   * 测试场景：load custom catalog。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    String name = "custom";
    Catalog catalog =
        CatalogUtil.loadCatalog(TestCatalog.class.getName(), name, options, hadoopConf);
    Assertions.assertThat(catalog).isInstanceOf(TestCatalog.class);
    Assertions.assertThat(((TestCatalog) catalog).catalogName).isEqualTo(name);
    Assertions.assertThat(((TestCatalog) catalog).catalogProperties).isEqualTo(options);
  }

  /**
   * 测试场景：load custom catalog with hadoop config。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog_withHadoopConfig() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    hadoopConf.set("key", "val");
    String name = "custom";
    Catalog catalog =
        CatalogUtil.loadCatalog(TestCatalogConfigurable.class.getName(), name, options, hadoopConf);
    Assertions.assertThat(catalog).isInstanceOf(TestCatalogConfigurable.class);
    Assertions.assertThat(((TestCatalogConfigurable) catalog).catalogName).isEqualTo(name);
    Assertions.assertThat(((TestCatalogConfigurable) catalog).catalogProperties).isEqualTo(options);
    Assertions.assertThat(((TestCatalogConfigurable) catalog).configuration).isEqualTo(hadoopConf);
  }

  /**
   * 测试场景：load custom catalog no arg constructor not found。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog_NoArgConstructorNotFound() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    String name = "custom";
    Assertions.assertThatThrownBy(
            () ->
                CatalogUtil.loadCatalog(
                    TestCatalogBadConstructor.class.getName(), name, options, hadoopConf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize Catalog implementation")
        .hasMessageContaining(
            "NoSuchMethodException: org.apache.iceberg.TestCatalogUtil$TestCatalogBadConstructor.<init>()");
  }

  /**
   * 测试场景：load custom catalog not implement catalog。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog_NotImplementCatalog() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    String name = "custom";

    Assertions.assertThatThrownBy(
            () ->
                CatalogUtil.loadCatalog(
                    TestCatalogNoInterface.class.getName(), name, options, hadoopConf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize Catalog")
        .hasMessageContaining("does not implement Catalog");
  }

  /**
   * 测试场景：load custom catalog constructor error catalog。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog_ConstructorErrorCatalog() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    String name = "custom";

    String impl = TestCatalogErrorConstructor.class.getName();
    Assertions.assertThatThrownBy(() -> CatalogUtil.loadCatalog(impl, name, options, hadoopConf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize Catalog implementation")
        .hasMessageContaining("NoClassDefFoundError: Error while initializing class");
  }

  /**
   * 测试场景：load custom catalog bad catalog name catalog。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomCatalog_BadCatalogNameCatalog() {
    Map<String, String> options = Maps.newHashMap();
    options.put("key", "val");
    Configuration hadoopConf = new Configuration();
    String name = "custom";
    String impl = "CatalogDoesNotExist";
    Assertions.assertThatThrownBy(() -> CatalogUtil.loadCatalog(impl, name, options, hadoopConf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize Catalog implementation")
        .hasMessageContaining("java.lang.ClassNotFoundException: CatalogDoesNotExist");
  }

  /**
   * 测试场景：load custom file i no arg。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomFileIO_noArg() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put("key", "val");
    FileIO fileIO = CatalogUtil.loadFileIO(TestFileIONoArg.class.getName(), properties, null);
    Assertions.assertThat(fileIO).isInstanceOf(TestFileIONoArg.class);
    Assertions.assertThat(((TestFileIONoArg) fileIO).map).isEqualTo(properties);
  }

  /**
   * 测试场景：load custom file i hadoop config constructor。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomFileIO_hadoopConfigConstructor() {
    Configuration configuration = new Configuration();
    configuration.set("key", "val");
    FileIO fileIO =
        CatalogUtil.loadFileIO(HadoopFileIO.class.getName(), Maps.newHashMap(), configuration);
    Assertions.assertThat(fileIO).isInstanceOf(HadoopFileIO.class);
    Assertions.assertThat(((HadoopFileIO) fileIO).conf().get("key")).isEqualTo("val");
  }

  /**
   * 测试场景：load custom file i configurable。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomFileIO_configurable() {
    Configuration configuration = new Configuration();
    configuration.set("key", "val");
    FileIO fileIO =
        CatalogUtil.loadFileIO(
            TestFileIOConfigurable.class.getName(), Maps.newHashMap(), configuration);
    Assertions.assertThat(fileIO).isInstanceOf(TestFileIOConfigurable.class);
    Assertions.assertThat(((TestFileIOConfigurable) fileIO).configuration).isEqualTo(configuration);
  }

  /**
   * 测试场景：load custom file i bad arg。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomFileIO_badArg() {
    Assertions.assertThatThrownBy(
            () -> CatalogUtil.loadFileIO(TestFileIOBadArg.class.getName(), Maps.newHashMap(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize FileIO, missing no-arg constructor");
  }

  /**
   * 测试场景：load custom file i bad class。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomFileIO_badClass() {
    Assertions.assertThatThrownBy(
            () ->
                CatalogUtil.loadFileIO(TestFileIONotImpl.class.getName(), Maps.newHashMap(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Cannot initialize FileIO")
        .hasMessageContaining("does not implement FileIO");
  }

  /**
   * 测试场景：build custom catalog with type set。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void buildCustomCatalog_withTypeSet() {
    Map<String, String> options = Maps.newHashMap();
    options.put(CatalogProperties.CATALOG_IMPL, "CustomCatalog");
    options.put(CatalogUtil.ICEBERG_CATALOG_TYPE, "hive");
    Configuration hadoopConf = new Configuration();
    String name = "custom";
    Assertions.assertThatThrownBy(() -> CatalogUtil.buildIcebergCatalog(name, options, hadoopConf))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Cannot create catalog custom, both type and catalog-impl are set: type=hive, catalog-impl=CustomCatalog");
  }

  /**
   * 测试场景：load custom metrics reporter no arg。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomMetricsReporter_noArg() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put("key", "val");
    properties.put(
        CatalogProperties.METRICS_REPORTER_IMPL, TestMetricsReporterDefault.class.getName());

    MetricsReporter metricsReporter = CatalogUtil.loadMetricsReporter(properties);
    Assertions.assertThat(metricsReporter).isInstanceOf(TestMetricsReporterDefault.class);
  }

  /**
   * 测试场景：load custom metrics reporter bad arg。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomMetricsReporter_badArg() {
    Assertions.assertThatThrownBy(
            () ->
                CatalogUtil.loadMetricsReporter(
                    ImmutableMap.of(
                        CatalogProperties.METRICS_REPORTER_IMPL,
                        TestMetricsReporterBadArg.class.getName())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing no-arg constructor");
  }

  /**
   * 测试场景：load custom metrics reporter bad class。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void loadCustomMetricsReporter_badClass() {
    Assertions.assertThatThrownBy(
            () ->
                CatalogUtil.loadMetricsReporter(
                    ImmutableMap.of(
                        CatalogProperties.METRICS_REPORTER_IMPL,
                        TestFileIONotImpl.class.getName())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not implement MetricsReporter");
  }

  public static class TestCatalog extends BaseMetastoreCatalog {

    private String catalogName;
    private Map<String, String> catalogProperties;

    /** 辅助方法：catalog。 */
    public TestCatalog() {}

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(String name, Map<String, String> properties) {
      this.catalogName = name;
      this.catalogProperties = properties;
    }

    /** 辅助方法：new table ops。 */
    @Override
    protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：default warehouse location。 */
    @Override
    protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：list tables。 */
    @Override
    public List<TableIdentifier> listTables(Namespace namespace) {
      return null;
    }

    /** 辅助方法：drop table。 */
    @Override
    public boolean dropTable(TableIdentifier identifier, boolean purge) {
      return false;
    }

    /** 辅助方法：rename table。 */
    @Override
    public void renameTable(TableIdentifier from, TableIdentifier to) {}
  }

  public static class TestCatalogConfigurable extends BaseMetastoreCatalog implements Configurable {

    private String catalogName;
    private Map<String, String> catalogProperties;
    private Configuration configuration;

    /** 辅助方法：catalog configurable。 */
    public TestCatalogConfigurable() {}

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(String name, Map<String, String> properties) {
      this.catalogName = name;
      this.catalogProperties = properties;
    }

    /** 辅助方法：set conf。 */
    @Override
    public void setConf(Configuration conf) {
      this.configuration = conf;
    }

    /** 辅助方法：get conf。 */
    @Override
    public Configuration getConf() {
      return configuration;
    }

    /** 辅助方法：new table ops。 */
    @Override
    protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：default warehouse location。 */
    @Override
    protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：list tables。 */
    @Override
    public List<TableIdentifier> listTables(Namespace namespace) {
      return null;
    }

    /** 辅助方法：drop table。 */
    @Override
    public boolean dropTable(TableIdentifier identifier, boolean purge) {
      return false;
    }

    /** 辅助方法：rename table。 */
    @Override
    public void renameTable(TableIdentifier from, TableIdentifier to) {}
  }

  public static class TestCatalogBadConstructor extends BaseMetastoreCatalog {

    /** 辅助方法：catalog bad constructor。 */
    public TestCatalogBadConstructor(String arg) {}

    /** 辅助方法：new table ops。 */
    @Override
    protected TableOperations newTableOps(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：default warehouse location。 */
    @Override
    protected String defaultWarehouseLocation(TableIdentifier tableIdentifier) {
      return null;
    }

    /** 辅助方法：list tables。 */
    @Override
    public List<TableIdentifier> listTables(Namespace namespace) {
      return null;
    }

    /** 辅助方法：drop table。 */
    @Override
    public boolean dropTable(TableIdentifier identifier, boolean purge) {
      return false;
    }

    /** 辅助方法：rename table。 */
    @Override
    public void renameTable(TableIdentifier from, TableIdentifier to) {}

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(String name, Map<String, String> properties) {}
  }

  public static class TestCatalogNoInterface {
    /** 辅助方法：catalog no interface。 */
    public TestCatalogNoInterface() {}
  }

  public static class TestFileIOConfigurable implements FileIO, Configurable {

    private Configuration configuration;

    /** 辅助方法：file io configurable。 */
    public TestFileIOConfigurable() {}

    /** 辅助方法：set conf。 */
    @Override
    public void setConf(Configuration conf) {
      this.configuration = conf;
    }

    /** 辅助方法：get conf。 */
    @Override
    public Configuration getConf() {
      return configuration;
    }

    /** 辅助方法：new input file。 */
    @Override
    public InputFile newInputFile(String path) {
      return null;
    }

    /** 辅助方法：new output file。 */
    @Override
    public OutputFile newOutputFile(String path) {
      return null;
    }

    /** 辅助方法：delete file。 */
    @Override
    public void deleteFile(String path) {}

    /** 辅助方法：get configuration。 */
    public Configuration getConfiguration() {
      return configuration;
    }
  }

  public static class TestFileIONoArg implements FileIO {

    private Map<String, String> map;

    /** 辅助方法：file io no arg。 */
    public TestFileIONoArg() {}

    /** 辅助方法：new input file。 */
    @Override
    public InputFile newInputFile(String path) {
      return null;
    }

    /** 辅助方法：new output file。 */
    @Override
    public OutputFile newOutputFile(String path) {
      return null;
    }

    /** 辅助方法：delete file。 */
    @Override
    public void deleteFile(String path) {}

    /** 辅助方法：get map。 */
    public Map<String, String> getMap() {
      return map;
    }

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(Map<String, String> properties) {
      map = properties;
    }
  }

  public static class TestFileIOBadArg implements FileIO {

    private final String arg;

    /** 辅助方法：file io bad arg。 */
    public TestFileIOBadArg(String arg) {
      this.arg = arg;
    }

    /** 辅助方法：new input file。 */
    @Override
    public InputFile newInputFile(String path) {
      return null;
    }

    /** 辅助方法：new output file。 */
    @Override
    public OutputFile newOutputFile(String path) {
      return null;
    }

    /** 辅助方法：delete file。 */
    @Override
    public void deleteFile(String path) {}

    /** 辅助方法：get arg。 */
    public String getArg() {
      return arg;
    }
  }

  public static class TestFileIONotImpl {
    /** 辅助方法：file io not impl。 */
    public TestFileIONotImpl() {}
  }

  public static class TestMetricsReporterBadArg implements MetricsReporter {
    private final String arg;

    /** 辅助方法：metrics reporter bad arg。 */
    public TestMetricsReporterBadArg(String arg) {
      this.arg = arg;
    }

    /** 辅助方法：report。 */
    @Override
    public void report(MetricsReport report) {}
  }

  public static class TestMetricsReporterDefault implements MetricsReporter {

    /** 辅助方法：report。 */
    @Override
    public void report(MetricsReport report) {}
  }
}
