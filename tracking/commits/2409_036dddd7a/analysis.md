# 提交 2409：Build: Statically import Assumptions.assumeThat() (#13655)

## 提交信息

- **序号**：2409 / 4088
- **哈希**：036dddd7a390987fc3bd7061a7a668f13e16acda
- **短哈希**：036dddd7a
- **日期**：2025-07-24 16:16:55 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Build: Statically import Assumptions.assumeThat() (#13655)
- **PR/Issue**：#13655

## 总体目的

本提交是一个代码风格统一类的改动。Iceberg 项目已经在 checkstyle 中要求 `org.assertj.core.api.Assertions` 必须以静态导入方式使用（即直接使用 `assertThat(...)` 而非 `Assertions.assertThat(...)`），但同样来自 AssertJ 的 `Assumptions` 类却没有对应的规则约束。

测试代码中 `Assumptions.assumeThat(...)` 用于条件性跳过测试（当某些前置条件不满足时），其用法与 `Assertions.assertThat(...)` 类似。为了让代码风格保持一致，本提交在 checkstyle 配置中新增了对 `Assumptions` 的静态导入要求，并将所有测试文件中的 `Assumptions.assumeThat(...)` 调用改为静态导入后的 `assumeThat(...)`。

## 如何达成设计目的

设计思路很简单直接：

1. 在 checkstyle 配置文件中新增一个 `RegexpMultiline` 模块，匹配 `import org.assertj.core.api.Assumptions;` 这种非静态导入语句，并给出错误提示信息。
2. 逐一修改所有使用了 `Assumptions.assumeThat(...)` 的测试文件，将普通导入改为静态导入 `import static org.assertj.core.api.Assumptions.assumeThat;`，并将调用处的 `Assumptions.assumeThat(...)` 简化为 `assumeThat(...)`。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+4/-0 lines)

**修改目的**：新增 checkstyle 规则，禁止对 `org.assertj.core.api.Assumptions` 使用非静态导入。

**工作逻辑**：新增了一个 `RegexpMultiline` 模块，正则匹配 `^\s*import\s+\Qorg.assertj.core.api.Assumptions;\E`，即匹配普通形式的 `import org.assertj.core.api.Assumptions;` 语句。一旦匹配到，会报错提示 "org.assertj.core.api.Assumptions should only be used with static imports"。这与已有的对 `Assertions` 的规则完全对称。

### 多个测试文件 (+123/-154 lines，涉及12个文件)

**修改目的**：将所有测试文件中对 `Assumptions.assumeThat(...)` 的调用改为静态导入形式 `assumeThat(...)`，以符合新增的 checkstyle 规则。

**工作逻辑**：对每个文件执行两类改动：
- 将 `import org.assertj.core.api.Assumptions;` 替换为 `import static org.assertj.core.api.Assumptions.assumeThat;`
- 将所有 `Assumptions.assumeThat(...)` 调用替换为 `assumeThat(...)`

涉及的文件包括：
- `aliyun-oss/mock/.../TestLocalAliyunOSS.java`
- `aws/src/.../TestS3FileIOIntegration.java`
- `core/src/.../data/DataTestBase.java`
- `core/src/.../view/ViewCatalogTests.java`
- `flink/v1.20/.../TestRewriteDataFilesAction.java`
- `flink/v1.21/.../TestRewriteDataFilesAction.java`
- `flink/v1.22/.../TestRewriteDataFilesAction.java`
- `parquet/src/.../TestVariantReaders.java`
- `spark/v3.4/.../AvroDataTestBase.java`
- `spark/v3.5/.../AvroDataTestBase.java`
- `spark/v4.0/.../AvroDataTestBase.java`

其中 `ViewCatalogTests.java` 的改动量最大，因为有大量测试方法都使用了 `Assumptions.assumeThat(tableCatalog())` 来条件性跳过那些只对支持表的 catalog 才有效的测试。

## 总结

这是一个纯代码风格统一提交，不改变任何运行时逻辑。通过在 checkstyle 中新增规则并修改所有相关测试文件，使得 `Assumptions` 类与 `Assertions` 类保持一致的静态导入使用方式。这种改动提升了代码的可读性和一致性，同时防止未来开发者再次引入非静态导入的用法。
