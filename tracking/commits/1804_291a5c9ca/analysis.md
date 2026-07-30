# 提交 1804：Azure: Move docker-based tests to integrationTest (#12274)

## 提交信息

- **序号**：1804 / 4088
- **哈希**：291a5c9ca5f29bf2504f9162cb2dd760890bbeb2
- **短哈希**：291a5c9ca
- **日期**：2025-02-28 20:05:44 +0100
- **作者**：Anurag Mantripragada
- **提交说明**：Azure: Move docker-based tests to integrationTest (#12274)
- **PR/Issue**：#12274

## 总体目的

Iceberg 的 Azure 模块（`iceberg-azure`）中有一批依赖 Docker（通过 Azurite 容器模拟 ADLS Gen2）的测试，原先放在 `azure/src/test/java/` 下，与普通单元测试混在一起。这些测试需要 Docker 环境才能运行，且启动 Testcontainers 较慢，不适合在每次 `test` 任务中都执行，也不适合在没有 Docker 的环境中运行。

本提交将这批 Docker 依赖测试从 `src/test/java` 迁移到独立的 `src/integration/java` 源集，并配置 Gradle 的 `integrationTest` 任务来运行它们，使单元测试与集成测试分离。这样：
1. 普通 `test` 任务不再依赖 Docker，可在任何环境快速运行。
2. 集成测试通过 `integrationTest` 任务独立运行，可按需触发。
3. `check` 任务依赖 `integrationTest`，确保 CI 上仍会运行集成测试。

同时更新 checkstyle 和 error-prone 配置，使集成测试源集享受与测试代码相同的规则豁免。

## 如何达成设计目的

1. **文件迁移**：将 5 个 Azure 测试相关文件从 `azure/src/test/java/...` 重命名/移动到 `azure/src/integration/java/...`（git 识别为 rename，内容不变）。
2. **Gradle 配置**：在 `build.gradle` 的 `iceberg-azure` 项目中新增 `integration` sourceSet、`integrationImplementation`/`integrationRuntime` 配置（继承自 test 对应配置）、`integrationTest` Test 任务，并将 `check` 依赖 `integrationTest`。
3. **Checkstyle 配置**：在 `checkstyle-suppressions.xml` 中将抑制规则的正则从匹配 `test` 扩展为匹配 `test|integration`，使集成测试代码同样豁免 Javadoc、Visibility 等检查。
4. **Error-prone 配置**：在 `baseline.gradle` 中将 error-prone 排除路径从 `(test|generated-src|generated)` 扩展为 `(test|integration|generated-src|generated)`，避免在集成测试上运行耗时的 error-prone 检查。

## 修改详情

### `.baseline/checkstyle/checkstyle-suppressions.xml`（修改, +8 -8 lines）

**修改目的**：让 checkstyle 抑制规则覆盖集成测试源集。

**工作逻辑**：将 7 条测试类抑制规则和 1 条测试资源抑制规则的正则表达式中的 `[Tt]est` 或 `test` 部分扩展为 `([Tt]est|integration)` 或 `(test|integration)`，使位于 `src/integration/java` 和 `src/integration/resources` 下的代码同样被豁免对应检查。

### `azure/src/test/java/.../ADLSFileIOTest.java` → `azure/src/integration/java/.../ADLSFileIOTest.java`（重命名, 0 lines）

**修改目的**：将 ADLSFileIO 测试迁移到集成测试源集。

**工作逻辑**：文件内容不变，仅从 `src/test/java` 移动到 `src/integration/java`。该测试依赖 Azurite 容器。

### `azure/src/test/java/.../ADLSInputStreamTest.java` → `azure/src/integration/java/.../ADLSInputStreamTest.java`（重命名, 0 lines）

**修改目的**：将 ADLS 输入流测试迁移到集成测试源集。

**工作逻辑**：同上，内容不变，仅迁移路径。

### `azure/src/test/java/.../ADLSOutputStreamTest.java` → `azure/src/integration/java/.../ADLSOutputStreamTest.java`（重命名, 0 lines）

**修改目的**：将 ADLS 输出流测试迁移到集成测试源集。

**工作逻辑**：同上。

### `azure/src/test/java/.../AzuriteContainer.java` → `azure/src/integration/java/.../AzuriteContainer.java`（重命名, 0 lines）

**修改目的**：将 Azurite 容器辅助类迁移到集成测试源集。

**工作逻辑**：同上。该类是 Testcontainers 的 Azurite 容器封装，供上述测试共用。

### `azure/src/test/java/.../BaseAzuriteTest.java` → `azure/src/integration/java/.../BaseAzuriteTest.java`（重命名, 0 lines）

**修改目的**：将 Azurite 测试基类迁移到集成测试源集。

**工作逻辑**：同上。该类是所有 Azurite 测试的公共基类，负责启动和配置 Azurite 容器。

### `baseline.gradle`（修改, ±1 lines）

**修改目的**：让 error-prone 排除集成测试路径。

**工作逻辑**：将 error-prone 的 `XepExcludedPaths` 参数从 `.*/(test|generated-src|generated)/.*` 改为 `.*/(test|integration|generated-src|generated)/.*`，使 `src/integration` 路径下的代码不被 error-prone 检查（因为 error-prone 较慢，且测试代码无需其严格检查）。

### `build.gradle`（修改, +22 lines）

**修改目的**：为 `iceberg-azure` 项目配置集成测试 sourceSet 和任务。

**工作逻辑**：在 `project(':iceberg-azure')` 中新增：
- `sourceSets.integration`：定义 `src/integration/java` 和 `src/integration/resources`，编译和运行 classpath 包含 main 和 test 的输出（使集成测试可复用测试辅助类）。
- `configurations`：`integrationImplementation` 继承自 `testImplementation`，`integrationRuntime` 继承自 `testRuntimeOnly`，使集成测试可用与单元测试相同的依赖。
- `task integrationTest(type: Test)`：使用 JUnit Platform，指向 integration sourceSet 的输出和 classpath，传入 `extraJvmArgs`。
- `check.dependsOn integrationTest`：将集成测试纳入 `check` 任务，确保 CI 上会运行。

## 小结

- **成效**：将 Azure 模块依赖 Docker 的测试从单元测试中分离到独立的 `integrationTest` 任务，使普通 `test` 不再依赖 Docker、可快速运行，同时 CI 通过 `check` 仍会执行集成测试。
- **影响范围**：涉及 Azure 模块测试代码迁移（5 个文件重命名）、Gradle 构建配置（`build.gradle`、`baseline.gradle`）和 checkstyle 配置。不影响生产代码。
- **回迁到 1.4.x 的注意事项**：建议回迁，但需注意：
  - 1.4.x 分支的 Azure 模块需存在相同的测试文件和 Testcontainers 依赖，否则迁移无意义。
  - `build.gradle` 中 `iceberg-azure` 项目的配置需与 main 分支结构一致才能添加 sourceSet 配置。
  - `extraJvmArgs` 属性需在 1.4.x 的 gradle.properties 中定义。
  - 这是构建基础设施改进，回迁后能改善 1.4.x 的测试执行体验，无功能风险。
