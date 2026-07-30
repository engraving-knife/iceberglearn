# 提交 1160：Build: switch to slf4j-simple 2.x for test implementation dependency because avro 1.12.0 brings in slf4j-api dependency to 2.x (#11001)

## 提交信息

- **序号**：1160 / 4088
- **哈希**：06ed235f9748465a8d85f8772f052bfe64b2f955
- **短哈希**：06ed235f9
- **日期**：2024-09-17（Tue Sep 17 09:13:30 2024 -0700，commit date 显示 -0500 时区）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Build: switch to slf4j-simple 2.x for test implementation dependency because avro 1.12.0 brings in slf4j-api dependency to 2.x (#11001)
- **PR/Issue**：#11001

## 总体目的

Iceberg 在 `gradle/libs.versions.toml` 中统一定义 slf4j 版本，用于 `slf4j-api`（API 接口）与 `slf4j-simple`（测试用的简单日志实现）。此前版本是 `1.7.36`。同一文件中 `avro = "1.12.0"`，而 Avro 1.12.0 的依赖树中传递引入了 `slf4j-api 2.x`。

slf4j 1.x 与 2.x 的绑定机制不同：
- slf4j 1.x 通过反射查找实现 jar 中的 `StaticLoggerBinder` 类。
- slf4j 2.x 改用 Java 标准 `ServiceLoader` 机制查找 `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`。

当 classpath 上同时存在 `slf4j-api 2.x` 与 `slf4j-simple 1.7.x` 时，slf4j-api 2.x 找不到符合新 SPI 协议的 provider，会输出 `"Failed to load class StaticLoggerBinder"` 之类的告警，或日志实现无法正确加载，导致测试日志丢失或运行时报错。

本提交把 `slf4j` 版本从 `1.7.36` 升到 `2.0.16`，让 `slf4j-api` 与 `slf4j-simple` 同步进入 2.x，与 Avro 1.12.0 引入的 `slf4j-api 2.x` 对齐，消除版本不匹配。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `slf4j` 这一行：`slf4j = "1.7.36"` → `slf4j = "2.0.16"`。

由于 `libs.versions.toml` 中 `slf4j-api` 与 `slf4j-simple` 两个库都通过 `version.ref = "slf4j"` 引用同一版本变量，改一处即可同时更新 API 与实现版本，保证两者一致。

无代码改动，纯构建配置。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 slf4j 版本对齐到 2.x，与 Avro 1.12.0 引入的 slf4j-api 2.x 兼容。

**工作逻辑**：
- 单行修改：`slf4j = "1.7.36"` → `slf4j = "2.0.16"`。
- 该版本变量被 `[libraries]` 节中的两条引用：
  - `slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }`
  - `slf4j-simple = { module = "org.slf4j:slf4j-simple", version.ref = "slf4j" }`
- 两者同时升到 2.0.16，保证 API 与实现的 SPI 协议一致。slf4j 2.0.16 是 2.0 系列的稳定版本，与 slf4j-api 2.x 完全兼容。

## 小结

- **成效**：解决 Avro 1.12.0 传递引入 slf4j-api 2.x 后与 slf4j-simple 1.7.x 不匹配的问题，测试日志能正确加载与输出，避免 "Failed to load class StaticLoggerBinder" 告警或测试失败。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，改 1 行版本号。`slf4j-api` 与 `slf4j-simple` 主要用于测试 scope（slf4j-simple 是 test implementation dependency），生产代码中 slf4j-api 是常规依赖但 1.x → 2.x 在 API 层面向后兼容（`Logger` / `LoggerFactory` 等核心 API 不变），因此对生产运行时无影响。
- **回迁到 1.4.x 的注意事项**：
  1. 这是一个构建依赖版本升级，回迁到 1.4.x 是否必要取决于 1.4.x 是否也引入了 Avro 1.12.0。
  2. 若 1.4.x 的 `avro` 版本仍是 1.11.x 或更早（未传递引入 slf4j-api 2.x），则不需要回迁本提交；强行回迁反而可能引入其他依赖兼容性问题（slf4j 2.x 与某些旧版依赖的绑定机制不兼容）。
  3. 若 1.4.x 已经把 avro 升到 1.12.0 或更高（传递引入 slf4j-api 2.x），则必须回迁本提交，否则测试会因 slf4j 版本不匹配而出现日志丢失或运行时错误。
  4. slf4j 1.x → 2.x 是源头库的主版本升级，虽然核心 API 兼容，但绑定机制变化较大。回迁时需检查 1.4.x 是否有其他依赖（如 Logback、log4j-slf4j-impl 等）仍依赖 slf4j 1.x 的 `StaticLoggerBinder` 模式，若有则可能需要一并升级。
  5. 该改动不影响发布产物（slf4j-simple 是 test scope），不影响运行时行为，无数据兼容性风险。
