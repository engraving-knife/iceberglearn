# 提交 0492：open-api: Use openapi-generator-gradle-plugin for validating specification (#9344)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0492 |
| 完整哈希 | 44eb00daaa654e4b286e8d99bbbf8fd6fd283a56 |
| 短哈希 | 44eb00daa |
| 日期 | 2024-02-08 03:35:16 -0800 |
| 作者 | Hongyue/Steve Zhang <steveiszhy@gmail.com> |
| 说明 | open-api: Use openapi-generator-gradle-plugin for validating specification (#9344) |
| PR | #9344 |
| 共同作者 | Steve Zhang <hongyue_zhang@apple.com> |

文件统计：4 个文件，54 行新增 / 26 行删除。
- build.gradle：17 行新增
- open-api/rest-catalog-open-api.py：11 行新增 / 1 行删除
- open-api/rest-catalog-open-api.yaml：50 行（含重排）
- settings.gradle：2 行新增

## 总体目的

本提交的核心目的是为 Iceberg 的 OpenAPI 规范引入自动化校验机制，使 REST Catalog 规范（以及 S3 Signer 规范）能够在每次构建 `check` 阶段被自动验证，从而避免规范文件出现结构性或语义性错误。在此之前，OpenAPI 规范文件（`rest-catalog-open-api.yaml`）的校验依赖人工或外部流程，缺乏持续集成的保障，容易在迭代中引入不符合 OpenAPI 规范语法的定义。

为了实现这一目标，作者引入了 `org.openapitools:openapi-generator-gradle-plugin:6.6.0`，利用其内置的 `ValidateTask` 对规范文件进行校验。这同时带来一个组织层面的变化：此前 `open-api` 目录并不是一个被 Gradle 管理的子项目，本次提交通过在 `settings.gradle` 中 `include 'open-api'` 将其正式纳入多项目构建体系（项目名 `iceberg-open-api`），使得校验任务能够挂载到该子项目的 `check` 生命周期上。

除引入校验机制外，本次提交还顺带修复了规范文件中导致校验失败的两处问题：一是 `rest-catalog-open-api.py`（由 OpenAPI 规范生成的 Pydantic 模型）中 `ViewRequirement` 的定义方式无法通过校验；二是 `rest-catalog-open-api.yaml` 中 `ViewRequirement` / `AssertViewUUID` 的缩进层级错误以及 `IcebergErrorResponse` 示例（example）的缩进错误，这些 YAML 缩进问题会导致校验器报错。换言之，本次提交是"引入校验器 + 修复既有不规范写法"的组合改动。

## 如何达成设计目的

实现路径分三步：第一步在 `settings.gradle` 中将 `open-api` 注册为 Gradle 子项目；第二步在根 `build.gradle` 的 `buildscript` 中声明 `openapi-generator-gradle-plugin` 插件依赖，并在 `:iceberg-open-api` 与 `:iceberg-aws` 两个子项目中各注册一个 `ValidateTask`，分别校验 REST Catalog 规范与 S3 Signer 规范，且都通过 `check.dependsOn(...)` 接入构建生命周期；第三步修改 `rest-catalog-open-api.py` 与 `rest-catalog-open-api.yaml`，把会导致校验失败的定义与缩进修正为符合规范的形式。

## 修改详情

### build.gradle

**修改目的**：引入 openapi-generator-gradle-plugin 并注册两个规范校验任务。

**工作逻辑**：

1. 在 `buildscript.dependencies` 中新增插件 classpath：
```groovy
classpath 'org.openapitools:openapi-generator-gradle-plugin:6.6.0'
```
这使整个构建脚本能够引用 `org.openapitools.generator.gradle.plugin.tasks.ValidateTask` 等插件提供的类型。

2. 在 `project(':iceberg-aws')` 块内新增 S3 Signer 规范校验任务：
```groovy
def s3SignerSpec = "$projectDir/src/main/resources/s3-signer-open-api.yaml"
tasks.register('validateS3SignerSpec', org.openapitools.generator.gradle.plugin.tasks.ValidateTask) {
    inputSpec.set(s3SignerSpec)
    recommend.set(true)
}
check.dependsOn('validateS3SignerSpec')
```
该校验任务指向 `iceberg-aws` 子项目内的 `s3-signer-open-api.yaml`，设置 `recommend.set(true)` 表示在校验时给出建议级别的检查，并将该任务挂在 `check` 上，使得每次执行 `check`（如 `./gradlew check` 或 CI）时都会自动校验 S3 Signer 规范。

3. 新增 `project(':iceberg-open-api')` 块，注册 REST Catalog 规范校验任务：
```groovy
project(':iceberg-open-api') {
  def restCatalogSpec = "$projectDir/rest-catalog-open-api.yaml"
  tasks.register('validateRESTCatalogSpec', org.openapitools.generator.gradle.plugin.tasks.ValidateTask) {
    inputSpec.set(restCatalogSpec)
    recommend.set(true)
  }
  check.dependsOn('validateRESTCatalogSpec')
}
```
该校验任务指向 `open-api` 目录下的 `rest-catalog-open-api.yaml`，同样设置 `recommend.set(true)` 并接入 `check`。注意 `$projectDir` 在 `:iceberg-open-api` 上下文中指向 `open-api/` 目录，因此 `rest-catalog-open-api.yaml` 路径解析正确。

### settings.gradle

**修改目的**：将 `open-api` 目录注册为 Gradle 子项目。

**工作逻辑**：在 `include` 列表中按字母位置加入 `open-api`，并在项目名映射区加入对应映射：
```groovy
include 'open-api'
...
project(':open-api').name = 'iceberg-open-api'
```
这使得根 `build.gradle` 中的 `project(':iceberg-open-api')` 块能够正确解析到 `open-api/` 目录。`iceberg-aws` 原本已是子项目，故无需在此新增。

### open-api/rest-catalog-open-api.py

**修改目的**：修复由 OpenAPI 规范生成的 Pydantic 模型中 `ViewRequirement` 的定义，使其能通过校验。

**工作逻辑**：原定义使用 `__root__: Any = Field(..., discriminator='type')` 的根模型（root model）写法：
```python
class ViewRequirement(BaseModel):
    __root__: Any = Field(..., discriminator='type')
```
这种写法在校验时存在问题。修改后拆分为基类与具体子类：
```python
class ViewRequirement(BaseModel):
    type: str


class AssertViewUUID(ViewRequirement):
    """
    The view UUID must match the requirement's `uuid`
    """

    type: Literal['assert-view-uuid']
    uuid: str
```
即 `ViewRequirement` 仅声明 `type: str` 作为基类字段，`AssertViewUUID` 继承它并固定 `type` 字面量为 `'assert-view-uuid'`，同时增加 `uuid: str` 字段。这与 YAML 规范中 `AssertViewUUID` 通过 `allOf` 引用 `ViewRequirement` 并限定 `type` 枚举的结构保持一致，使生成的 Python 模型可被正确校验与实例化。

### open-api/rest-catalog-open-api.yaml

**修改目的**：修正 YAML 中两处缩进/层级问题，使规范能通过 openapi-generator 的校验。

**工作逻辑**：

1. `ViewRequirement` 与 `AssertViewUUID` 的缩进修正。原文件中 `ViewRequirement` 的 `oneOf`（实际为 discriminator 配置）后，`type: object / required / properties` 以及 `AssertViewUUID` 块的缩进多了一级，导致它们错误地嵌套在 `ViewRequirement` 内部而非与 `ViewRequirement` 同级。修改后将这些块整体向左回退一级，使 `AssertViewUUID` 成为 `components.schemas` 下与 `ViewRequirement` 平级的 schema，`ViewRequirement` 自身的 `type/required/properties` 也回到正确层级：
```yaml
        propertyName: type
        mapping:
          assert-view-uuid: '#/components/schemas/AssertViewUUID'
      type: object
      required:
        - type
      properties:
        type:
          type: "string"

    AssertViewUUID:
      allOf:
        - $ref: "#/components/schemas/ViewRequirement"
      description: The view UUID must match the requirement's `uuid`
      required:
        - type
        - uuid
      properties:
        type:
          type: string
          enum: [ "assert-view-uuid" ]
        uuid:
          type: string
```
这一改动的实质是修正 schema 的归属关系——原先 `AssertViewUUID` 被错误地写成 `ViewRequirement` 的内嵌属性，修正后它成为独立的可被 `$ref` 引用的顶层 schema。

2. `IcebergErrorResponse` 响应示例（example）的缩进修正。原文件中 `example` 与 `$ref` 处于同一缩进，导致 example 被认为是 schema 的同级键而非 `application/json` 内容的 example。修改后 `example` 向右缩进一级，正确地归属于 `application/json` 的媒体类型描述之下：
```yaml
        application/json:
          schema:
            $ref: '#/components/schemas/IcebergErrorResponse'
          example: {
            "error": {
              "message": "The server does not support this operation",
              "type": "UnsupportedOperationException",
              "code": 406
            } }
```
这保证 example 不会被误解析为 schema 字段，而是作为该响应内容类型的示例。

## 小结

本次提交是 Iceberg 1.4.x 周期内一个面向工程基础设施的改动，价值在于把 OpenAPI 规范的校验自动化、常态化。通过引入 `openapi-generator-gradle-plugin:6.6.0` 并在 `:iceberg-open-api`、`:iceberg-aws` 两个子项目注册 `ValidateTask`、接入 `check` 生命周期，REST Catalog 规范与 S3 Signer 规范的合法性从此在每次构建中都会被验证。配合此机制，提交同步修正了 `rest-catalog-open-api.yaml` 中 `ViewRequirement`/`AssertViewUUID` 的层级缩进与 `IcebergErrorResponse` 的 example 缩进，以及 `rest-catalog-open-api.py` 中 `ViewRequirement` 的根模型写法，使规范文件从"能被人阅读但可能无法通过校验"变为"能通过自动校验"。该改动不改变运行时行为，但对规范文件的长期可维护性有实质贡献，回溯风险低。
