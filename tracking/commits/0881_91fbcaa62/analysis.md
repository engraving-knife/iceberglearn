# 提交 0881：Build: Run CI checks on all supported JDKs (#10473)

## 提交信息

- **序号**：0881 / 4088
- **哈希**：91fbcaa62c25308aa815557dd2c0041f75530705
- **短哈希**：91fbcaa62
- **日期**：2024-06-28 09:04:43 +0200（Fri Jun 28 09:04:43 2024 +0200）
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Build: Run CI checks on all supported JDKs (#10473)
- **提交说明（含原因）**：This guarantees the build will succeed locally.
- **PR/Issue**：#10473

## 总体目的

Iceberg 项目声明支持 JDK 8、11、17 三个版本（这是 Iceberg 在 `build.gradle` 中通过 `java { toolchain { languageVersion = JavaLanguageVersion.of(8) } }` 等方式锁定的最低编译目标，同时保证在更高 JDK 上也能运行）。理论上，任何提交合并到 main 后，仓库都应该能在 JDK 8、11、17 三个版本上分别完成构建（`./gradlew build`）和 javadoc 生成（`./gradlew javadoc`）。

然而此前的 GitHub Actions CI 配置（`.github/workflows/java-ci.yml`）只在一个固定 JDK 版本（JDK 8）上运行 `build-checks` 和 `build-javadoc` 两个 job。这意味着：

- 如果某次改动引入了只在 JDK 11 或 JDK 17 上才会触发的编译错误或 javadoc 警告（例如使用了 JDK 9+ 才有的 API、或某个依赖在更高 JDK 上行为不同），CI 不会在合并前发现，问题会潜伏到下游用户在 JDK 11/17 上构建时才暴露。
- 提交说明"This guarantees the build will succeed locally"也说明：开发者本地可能使用 JDK 11 或 17，但 CI 只在 JDK 8 上验证，存在"本地能过但 CI 不过"或"CI 能过但本地不过"的不一致风险。

本提交的目的是把 `build-checks` 和 `build-javadoc` 这两个 CI job 改为在 JDK 8、11、17 三个版本上各运行一次（共 6 个矩阵任务），从而在合并前覆盖所有官方支持的 JDK，保证仓库在任何受支持的 JDK 上都能成功构建。

## 如何达成设计目的

整体思路是用 GitHub Actions 的 matrix strategy 在 JDK 维度上展开两个已有的 build job。具体做法是给 `build-checks` 和 `build-javadoc` 两个 job 各自添加一个 `strategy.matrix.jvm: [8, 11, 17]` 的矩阵，并把 `actions/setup-java` 步骤中的 `java-version` 从硬编码的 `8` 改为 `${{ matrix.jvm }}`。这样 GitHub Actions 会自动为每个 job × 每个 JDK 启动一个并行任务，三个 JDK 的构建结果都会反映到 PR 状态检查中。

这种做法的优点是改动量极小（每个 job 加几行 YAML），且能复用现有的构建脚本与步骤。缺点是 CI 总执行时间会增加（三个 JDK 并行，但占用更多 runner 资源），不过对开源项目而言这是可接受的代价。

## 修改详情

### `.github/workflows/java-ci.yml`

**修改目的**：让 `build-checks` 和 `build-javadoc` 两个 CI job 在 JDK 8、11、17 三个版本上各运行一次。

**工作逻辑**：对两个 job 做了相同的两处修改：

1. 在 job 配置块中加入 matrix strategy：
   ```yaml
   strategy:
     matrix:
       jvm: [8, 11, 17]
   ```
   这会让 GitHub Actions 把该 job 展开为三个并行的子任务，分别使用 `matrix.jvm = 8`、`11`、`17`。

2. 把 `actions/setup-java@v4` 步骤中的 `java-version` 从硬编码的 `8` 改为引用 matrix 变量：
   ```yaml
   - uses: actions/setup-java@v4
     with:
       distribution: zulu
       java-version: ${{ matrix.jvm }}
   ```

两个 job 的具体改动如下：

- `build-checks` job（runs-on: ubuntu-22.04，执行 `./gradlew -DallModules build -x test -x javadoc -x integrationTest`）：
  ```diff
   build-checks:
     runs-on: ubuntu-22.04
  +  strategy:
  +    matrix:
  +      jvm: [8, 11, 17]
     steps:
     - uses: actions/checkout@v4
     - uses: actions/setup-java@v4
       with:
         distribution: zulu
  -      java-version: 8
  +      java-version: ${{ matrix.jvm }}
     - run: ./gradlew -DallModules build -x test -x javadoc -x integrationTest
  ```

- `build-javadoc` job（runs-on: ubuntu-22.04，执行 `./gradlew -Pquick=true javadoc`）：
  ```diff
   build-javadoc:
     runs-on: ubuntu-22.04
  +  strategy:
  +    matrix:
  +      jvm: [8, 11, 17]
     steps:
     - uses: actions/checkout@v4
     - uses: actions/setup-java@v4
       with:
         distribution: zulu
  -      java-version: 8
  +      java-version: ${{ matrix.jvm }}
     - run: ./gradlew -Pquick=true javadoc
  ```

`-x test` 表示跳过测试（已有专门的 test job 负责测试），`-x javadoc` 表示跳过 javadoc 生成（避免重复），`-x integrationTest` 跳过集成测试。`-Pquick=true` 是 Iceberg 自己的 gradle 属性，用于在 javadoc job 中跳过一些耗时的非 javadoc 步骤。

## 小结

- **成效**：让 Iceberg 的 GitHub Actions CI 在 `build-checks` 和 `build-javadoc` 两个 job 上各运行 JDK 8、11、17 三个版本（共 6 个并行子任务），从而在合并前覆盖所有官方支持的 JDK，保证仓库在任何受支持的 JDK 上都能成功构建和生成 javadoc。提交说明明确指出动机是"This guarantees the build will succeed locally"。
- **影响范围**：仅 `.github/workflows/java-ci.yml` 一个文件、8 行新增、2 行修改（每个 job 各加 3 行 strategy 块，各改 1 行 java-version）。不涉及任何代码、测试或文档逻辑。
- **回迁到 1.4.x 的注意事项**：这是一个 CI 配置改动，**对 1.4.x 回迁是低风险且推荐的**。注意点：
  1. 该改动不影响仓库代码本身，只影响 GitHub Actions 的运行方式。回迁到 1.4.x 后会让 1.4.x 的 CI 也覆盖三个 JDK，有助于在维护阶段及早发现 JDK 兼容性问题。
  2. 需要确认 1.4.x 分支的 `java-ci.yml` 中 `build-checks` 和 `build-javadoc` 两个 job 的结构（runs-on、steps）与 main 一致；如果 1.4.x 的 CI 配置已有差异（例如 runs-on 不同、步骤不同），需要按 1.4.x 的实际结构添加 strategy 块和修改 java-version。
  3. matrix 展开会增加 CI 并行任务数（从 2 个变 6 个），如果 1.4.x 的 GitHub Actions 配额紧张（例如私有仓库），需要权衡。对开源仓库而言通常没有配额问题。
  4. 如果 1.4.x 的某些模块在 JDK 17 上确实有已知不兼容（例如某个依赖在 JDK 17 上报错），回迁此 CI 改动会让那些不兼容暴露出来——这本身是好事，但需要预先评估是否有"已知失败"需要在 CI 上 allow-fail 或排除。
  5. `[8, 11, 17]` 这三个版本是 Iceberg 当时声明的支持矩阵，如果 1.4.x 的支持矩阵不同（例如 1.4.x 不再支持 JDK 8），需要相应调整 matrix 列表。
