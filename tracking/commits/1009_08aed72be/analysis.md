# 提交 1009：Build: Configure options.release = 11 / remove com.palantir.baseline-release-compatibility plugin (#10849)

## 提交信息

- **序号**：1009 / 4088
- **哈希**：08aed72beeedeeffd80c87803e075593f91e9ba7
- **短哈希**：08aed72be
- **日期**：2024-08-02 15:14:40 +0200
- **作者**：Robert Stupp
- **提交说明**：Build: Configure options.release = 11 / remove com.palantir.baseline-release-compatibility plugin (#10849)
- **PR/Issue**：#10849

## 总体目的

本提交是配合"Drop support for Java 8"（提交 1005）的构建现代化改造。在 Java 8 时代，Iceberg 通过 `sourceCompatibility = '1.8'` / `targetCompatibility = '1.8'` 来声明编译产物兼容 Java 8 字节码。但这种方式有一个根本缺陷：它只保证生成的字节码版本号是 1.8，并不保证代码使用的 API 在 Java 8 中存在——编译器仍可能使用 JDK 11/17 标准库中的新 API，运行时在 Java 8 上会 NoSuchMethodError。Java 9 引入的 `--release` 编译选项（`options.release`）才是正确的多版本交叉编译机制，它会同时设置字节码版本并使用对应版本的 API signature 文件来校验。

本提交的目的有两个：

1. 把所有 `JavaCompile` 任务的 `options.release` 设为 11，正式声明 Iceberg 编译产物面向 Java 11，并由编译器强制校验 API 兼容性。同时移除 `sourceCompatibility`/`targetCompatibility = '1.8'`（与 `release` 选项冲突，且已过时）。
2. 移除 `com.palantir.baseline-release-compatibility` 这个 Palantir Baseline 插件。该插件的作用之一是检查 `sourceCompatibility` 编译选项，但它与新增的 `options.release = 11` 冲突（`release` 与 `sourceCompatibility`/`targetCompatibility` 不能同时使用）。同时把 `com.palantir.baseline-reproducibility` 插件提供的可复现归档行为用内联代码重新实现（因为该插件同样会带来不必要的副作用），保留原有的可复现构建语义。

## 如何达成设计目的

设计思路是用 Gradle 原生的 `options.release` 替代旧的 `sourceCompatibility`/`targetCompatibility`，并清理 Palantir Baseline 中与新方案冲突的插件：

1. 在 `build.gradle` 的 `subprojects` 块中，把原本分别配置 `compileJava` 与 `compileTestJava` 的 `options.encoding = "UTF-8"` 合并为 `tasks.withType(JavaCompile.class).configureEach { options.encoding = "UTF-8"; options.release = 11 }`，对所有 JavaCompile 任务统一设置 release=11。删除 `sourceCompatibility = '1.8'` / `targetCompatibility = '1.8'` 两行。
2. 在 `iceberg-nessie` 子项目中，删除原本单独把 `compileTestJava` 的 `sourceCompatibility`/`targetCompatibility` 设为 "11" 的配置（因为全局已统一为 release=11）。
3. 在 `baseline.gradle` 中：
   - 移除 `apply plugin: 'com.palantir.baseline-release-compatibility'`（与 `options.release` 冲突）。
   - 把 `apply plugin: 'com.palantir.baseline-reproducibility'` 替换为内联的 `tasks.withType(AbstractArchiveTask.class).configureEach { ... }` 配置，手动设置 `preserveFileTimestamps=false`、`reproducibleFileOrder=true`、`duplicatesStrategy=DuplicatesStrategy.WARN`，注释说明这是 "What 'com.palantir.baseline-reproducibility' used to do, except the check for the sourceCompatibility Java compile option"。

## 修改详情

### `build.gradle`

**修改目的**：统一对所有 JavaCompile 任务设置 `options.release = 11`，移除过时的 `sourceCompatibility`/`targetCompatibility = '1.8'`，并简化 Nessie 子项目的重复配置。

**工作逻辑**：
- 把
  ```groovy
  compileJava {
    options.encoding = "UTF-8"
  }
  compileTestJava {
    options.encoding = "UTF-8"
  }
  ```
  合并为
  ```groovy
  tasks.withType(JavaCompile.class).configureEach {
    options.encoding = "UTF-8"
    options.release = 11
  }
  ```
  这样所有 Java 编译任务（包括第三方插件引入的）都会以 release=11 编译，确保字节码版本与 API 使用都限制在 Java 11 范围内。
- 删除 `sourceCompatibility = '1.8'` 与 `targetCompatibility = '1.8'` 两行（与 `release` 冲突，且 Java 8 已不再支持）。
- 在 `iceberg-nessie` 子项目中删除 `compileTestJava { sourceCompatibility = "11"; targetCompatibility = "11" }` 块（全局 release=11 已覆盖）。

### `baseline.gradle`

**修改目的**：移除与 `options.release` 冲突的 `baseline-release-compatibility` 插件，并用内联代码替代 `baseline-reproducibility` 插件的可复现归档行为。

**工作逻辑**：
- 移除 `apply plugin: 'com.palantir.baseline-release-compatibility'`。该插件会校验 `sourceCompatibility`，而 `sourceCompatibility` 与 `options.release` 互斥，必须移除。
- 把 `apply plugin: 'com.palantir.baseline-reproducibility'` 替换为：
  ```groovy
  tasks.withType(AbstractArchiveTask.class).configureEach(t -> {
    t.setPreserveFileTimestamps(false);
    t.setReproducibleFileOrder(true);
    t.setDuplicatesStrategy(DuplicatesStrategy.WARN);
  });
  ```
  这三项设置确保 jar/zip 等归档产物不保留文件时间戳、文件顺序可复现、重复条目仅警告——即可复现构建（reproducible build）的核心要求。注释明确说明这等同于 `baseline-reproducibility` 插件曾提供的功能，但去掉了对 `sourceCompatibility` 的检查。

## 小结

- **成效**：把 Iceberg 的 Java 编译目标从 `sourceCompatibility/targetCompatibility = '1.8'` 切换为 `options.release = 11`，由编译器在 API 层面强制校验 Java 11 兼容性（而非仅字节码版本号）；移除了与 `release` 选项冲突的 `com.palantir.baseline-release-compatibility` 插件；用内联代码保留 `baseline-reproducibility` 的可复现归档语义。这是"Drop support for Java 8"后的必要构建现代化步骤。
- **影响范围**：仅构建脚本，2 个文件、+9/-14 行，无 Java 源代码变更。
- **回迁到 1.4.x 的注意事项**：**不建议回迁**。本提交与"Drop support for Java 8"强耦合——它把 `options.release` 设为 11 并移除了 Java 8 的 `sourceCompatibility`。1.4.x 若仍需支持 Java 8，回迁此提交会破坏 Java 8 兼容性。仅当 1.4.x 也决定放弃 Java 8 时才可考虑回迁，且需同时回迁 1005（Drop Java 8）等关联提交。单独回迁本提交会导致 1.4.x 构建脚本与 JDK 支持策略不一致。整体属于高风险回迁，建议保持 1.4.x 现有构建配置不动。
