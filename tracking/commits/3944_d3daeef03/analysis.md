# 提交 3944：Build: Align Jackson versions to fix CVE (#16954)

## 提交信息

- **序号**：3944 / 4088
- **哈希**：d3daeef03146d817bbd763e9ea330293722ac88a
- **短哈希**：d3daeef03
- **日期**：2026-06-25 00:46:28 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Align Jackson versions to fix CVE (#16954)
- **PR/Issue**：#16954

## 总体目的

这次提交通过统一 Jackson 版本来修复已知的 CVE（安全漏洞）。Iceberg 项目使用 Jackson 作为 JSON 序列化库，但不同模块对 Jackson 版本的要求不同：Spark 4.0 需要 Jackson 2.15.2（Spark 的约束），Spark 4.1 需要 Jackson 2.21.x，而项目主版本使用 Jackson 2.22.0。

问题是 Azure SDK BOM 和 Google Cloud Libraries BOM 会引入较旧版本的 `jackson-databind`，这些旧版本存在已知 CVE。由于 bundle 模块（azure-bundle、gcp-bundle）使用 shaded jar（将所有依赖打包进一个 jar），如果不在构建时对齐 Jackson 版本，shaded jar 中会包含带 CVE 的旧版本 Jackson 类。

修复方案：
1. 为 Azure 和 GCP bundle 添加 `implementation platform(libs.jackson.bom)`，强制使用项目统一的 Jackson 2.22.0 版本。
2. 将 Spark 4.0 的 Jackson 强制版本从 2.15.2 提升到 2.18.8（修复 CVE 同时保持与 Spark 的兼容性）。
3. 将 Spark 4.1 的 Jackson 强制版本统一为 2.21.4。
4. 移除不再使用的 `jackson214` 版本别名。
5. 新增 `jackson218`（2.18.8）和 `jackson221`（2.21.4）版本别名。
6. 为 CVE 扫描工作流添加 trivyignore 文件，用于忽略暂时无法修复的已知 CVE。

## 如何达成设计目的

通过 Gradle 的 `platform` 依赖声明机制，在 bundle 模块中强制 Jackson BOM 对齐到项目版本；通过 `resolutionStrategy.force` 在 Spark 模块中覆盖传递性依赖的 Jackson 版本。同时为 CVE 扫描工作流添加 trivyignore 配置，允许对特定已知但暂时无法修复的 CVE 进行忽略管理。

## 修改详情

### `gradle/libs.versions.toml` (+5/-4 lines)

**修改目的**：调整 Jackson 版本别名。

**工作逻辑**：
- 移除 `jackson214 = { strictly = "2.14.2"}` 别名。
- 新增 `jackson218 = { strictly = "2.18.8"}` 和 `jackson221 = { strictly = "2.21.4"}` 别名。
- 移除 `jackson214-bom` 和 `jackson215-bom` 库声明（保留 `jackson215` 版本别名供其他用途）。

### `azure-bundle/build.gradle` (+3/-0 lines)

**修改目的**：强制 Azure bundle 使用项目 Jackson 版本。

**工作逻辑**：在依赖块中添加 `implementation platform(libs.jackson.bom)`，覆盖 Azure SDK BOM 引入的旧 Jackson 版本。

### `gcp-bundle/build.gradle` (+3/-0 lines)

**修改目的**：强制 GCP bundle 使用项目 Jackson 版本。

**工作逻辑**：同上，添加 `implementation platform(libs.jackson.bom)`。

### `spark/v4.0/build.gradle` (+3/-3 lines)

**修改目的**：升级 Spark 4.0 的 Jackson 强制版本。

**工作逻辑**：将 `force` 的 Jackson 版本从 `jackson215`（2.15.2）改为 `jackson218`（2.18.8），覆盖 jackson-module-scala、jackson-databind、jackson-core。

### `spark/v4.1/build.gradle` (+3/-3 lines)

**修改目的**：统一 Spark 4.1 的 Jackson 强制版本。

**工作逻辑**：将 `force` 的 Jackson 版本从 `jackson215`（2.15.2）改为 `jackson221`（2.21.4）。

### `.github/workflows/cve-scan.yml` (+5/-0 lines)

**修改目的**：为 CVE 扫描添加 trivyignore 支持。

**工作逻辑**：
- 在工作流触发路径中添加 `.github/trivyignore/**`，使 trivyignore 文件变更时触发扫描。
- 在 kafka-connect-runtime 和 spark-runtime-3.5 的 matrix 条目中添加 `trivyignores` 字段。
- 在 trivy action 调用中添加 `trivyignores: ${{ matrix.trivyignores }}` 参数。

### `.github/trivyignore/kafka-connect-runtime.trivyignore` (+29 lines, 新文件)

**修改目的**：记录 kafka-connect-runtime 中暂时无法修复的 CVE。

### `.github/trivyignore/spark-runtime-3.5.trivyignore` (+27 lines, 新文件)

**修改目的**：记录 spark-runtime-3.5 中暂时无法修复的 CVE。

### `azure-bundle/runtime-deps.txt`、`gcp-bundle/runtime-deps.txt`、`spark/v4.0/spark-runtime/runtime-deps.txt`、`spark/v4.1/spark-runtime/runtime-deps.txt`

**修改目的**：同步运行时依赖清单中 Jackson 版本变化。

## 总结

这次提交通过统一 Jackson 版本修复了多个 CVE 漏洞：为 Azure 和 GCP bundle 添加 Jackson BOM platform 声明覆盖旧版本，将 Spark 4.0/4.1 的 Jackson 强制版本提升到修复 CVE 的版本，并为 CVE 扫描工作流添加 trivyignore 机制管理暂时无法修复的漏洞。这是一次重要的安全修复，确保 shaded jar 分发物不包含带已知 CVE 的 Jackson 类。
