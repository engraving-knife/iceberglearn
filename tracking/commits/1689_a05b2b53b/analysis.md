# 提交 1689 a05b2b53b 分析

## 提交信息
- 哈希：a05b2b53b792a6215732aa8b1118adfba0cf3317
- 日期：2025-02-06 16:24:08 +0300
- 作者：Maria
- 消息：Hive: Use correct classloader to load SQL script (#12140)

## 总体目的

本提交修复 `TestHiveMetastore.setupMetastoreDB()` 在初始化 Hive 元数据库时使用错误类加载器加载 SQL 脚本的问题。该方法负责在测试启动嵌入式 Hive metastore 之前，用 `hive-schema-3.1.0.derby.sql` 脚本初始化 Derby 数据库 schema。

原代码使用 `ClassLoader.getSystemClassLoader()` 来加载 SQL 脚本资源。系统类加载器在大多数普通 JVM 运行中能从 classpath 找到资源，但在某些测试环境下会失败：当测试由自定义类加载器（例如 Gradle 的测试隔离类加载器、某些 IDE 的类加载器层次、或 OSGi/容器环境）加载时，`hive-schema-3.1.0.derby.sql` 这个资源可能只存在于当前线程的上下文类加载器或 `TestHiveMetastore` 类自身的类加载器中，而系统类加载器看不到，导致 `getResourceAsStream` 返回 null。原代码没有对 null 做检查，`InputStreamReader(inputStream)` 会在后续抛出 `NullPointerException`，错误信息晦涩，难以定位是资源找不到。

修复改为用 `TestHiveMetastore.class.getClassLoader()` 加载资源（即加载该测试类的类加载器，一定能看到与该类同模块的资源），并对结果做 `Preconditions.checkNotNull` 校验，失败时给出明确消息 "Invalid input stream: null"。同时用 try-with-resources 正确关闭 Connection、InputStream、Reader，避免资源泄漏。

## 如何达成设计目的

1. 把 `ClassLoader.getSystemClassLoader()` 换成 `TestHiveMetastore.class.getClassLoader()`。这是 Java 资源加载的最佳实践：用定义当前类的类加载器加载与该类同包/同模块的资源，能保证资源可见性，兼容各种自定义类加载器场景。
2. 用 `Preconditions.checkNotNull(inputStream, "Invalid input stream: null")` 包装，把潜在的 NPE 转成有明确信息的失败，便于诊断。
3. 把 `Connection`、`InputStream`、`Reader` 都放进 try-with-resources，保证异常路径下也能关闭，修复原代码 Connection 未关闭的隐患。

### 修改详情

#### hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveMetastore.java
新增 import `org.apache.iceberg.relocated.com.google.common.base.Preconditions`。

`setupMetastoreDB(String dbURL)` 方法重写：
- 旧实现：直接 `DriverManager.getConnection(dbURL)` 拿到 Connection（未关闭），用系统类加载器加载资源，无 null 检查。
- 新实现：
  ```
  try (Connection connection = DriverManager.getConnection(dbURL)) {
    ScriptRunner scriptRunner = new ScriptRunner(connection, true, true);
    try (InputStream inputStream =
            TestHiveMetastore.class
                .getClassLoader()
                .getResourceAsStream("hive-schema-3.1.0.derby.sql");
        Reader reader =
            new InputStreamReader(
                Preconditions.checkNotNull(inputStream, "Invalid input stream: null"))) {
      scriptRunner.runScript(reader);
    }
  }
  ```
  Connection、InputStream、Reader 均在 try-with-resources 中管理。

## 小结

成效：修复了某些测试环境下 Hive metastore 测试因类加载器问题加载 SQL 脚本失败的问题，并改善资源管理和错误诊断。影响范围仅限 `hive-metastore` 模块的测试工具类 `TestHiveMetastore`，不影响生产代码。

回迁到 1.4.x 的注意事项：这是一个测试基础设施修复，依赖少，可干净 cherry-pick 到 1.4.x（前提是 1.4.x 的 `TestHiveMetastore.setupMetastoreDB` 结构与 main 类似）。需确认 1.4.x 中 `Preconditions`（relocated Guava）在 `hive-metastore` 测试中可用——通常 Iceberg 各模块都依赖 relocated Guava，应无问题。如果 1.4.x 的 Hive 测试在 CI 中偶发出现资源加载失败，回迁本提交可直接解决。优先级中等（仅影响测试）。
