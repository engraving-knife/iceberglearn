# 提交 2493：Flink: Replace `assertThrows` with `assertThatThrownBy` (#13772)

## 提交信息

- **序号**：2493 / 4088
- **哈希**：86389ba52234b78aca6414a5ad232810b5c040c5
- **短哈希**：86389ba52
- **日期**：2025-08-13 09:50:59 +0200
- **作者**：slfan1989
- **提交说明**：Flink: Replace `assertThrows` with `assertThatThrownBy` (#13772)
- **PR/Issue**：#13772

## 总体目的

本提交是测试基础设施规范化的一部分，将 Flink 测试代码中的 JUnit 5 `assertThrows` 替换为 AssertJ 的 `assertThatThrownBy`。这符合 Iceberg 项目正在推进的 JUnit 5 迁移和 AssertJ 统一使用的方向（参见后续提交 2501 的测试指南更新）。

`assertThatThrownBy` 相比 `assertThrows` 有以下优势：提供流式断言 API，可以链式地验证异常类型、消息内容等；可读性更好；与项目其他模块的测试风格保持一致。此外，替换后的测试还增强了对异常消息内容的断言（`hasMessageContaining`），提升了测试的精确度。

同时，本提交在 checkstyle 配置中新增了一条规则，禁止静态导入 `org.junit.jupiter.api.Assertions` 的方法，从而在代码规范层面防止后续开发者再使用 JUnit 原生断言。

## 如何达成设计目的

1. **Checkstyle 规则**：在 `.baseline/checkstyle/checkstyle.xml` 中新增 `RegexpMultiline` 模块，匹配 `import static org.junit.jupiter.api.Assertions.\w+;` 的导入语句并给出提示，引导使用 AssertJ。
2. **测试代码改写**：将三处 `assertThrows(IllegalArgumentException.class, () -> ...)` 改写为 `assertThatThrownBy(() -> ...).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(...)`。
3. **多版本同步**：在 Flink v1.19、v1.20、v2.0 三个版本的 `TestRowDataConverter` 中做相同修改。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+4/-0 lines)

**修改目的**：添加 checkstyle 规则，禁止静态导入 JUnit Jupiter 的 `Assertions` 类方法。

**工作逻辑**：新增 `RegexpMultiline` 模块，正则匹配 `^\s*import\s+static\s+org\.junit\.jupiter\.api\.Assertions\.\w+;`，提示信息为 "Prefer using org.assertj.core.api.Assertions instead."

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestRowDataConverter.java` (+7/-5 lines)

**修改目的**：将 `assertThrows` 替换为 `assertThatThrownBy`，并增强异常消息断言。

**工作逻辑**：
- 导入从 `org.junit.jupiter.api.Assertions.assertThrows` 改为 `org.assertj.core.api.Assertions.assertThatThrownBy`。
- 两处测试方法中，将 `assertThrows(IllegalArgumentException.class, () -> convert(...))` 改为 `assertThatThrownBy(() -> convert(...)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is non-nullable but does not exist in source schema")`。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestRowDataConverter.java` (+7/-5 lines)

**修改目的**：与 v1.19 相同的替换，保持版本一致。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestRowDataConverter.java` (+7/-5 lines)

**修改目的**：与 v1.19 相同的替换，保持版本一致。

## 总结

本提交推进了项目测试框架向 AssertJ 统一迁移的进程。通过替换 `assertThrows` 为 `assertThatThrownBy` 并增强异常消息断言，提升了测试的表达力和精确度。同时新增的 checkstyle 规则从制度层面防止了后续代码回退到 JUnit 原生断言。三个 Flink 版本同步修改保证了分支一致性。
