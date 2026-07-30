# 提交 4050：Build: Bump datamodel-code-generator from 0.67.0 to 0.68.1 (#17239)

## 提交信息

- **序号**：4050 / 4088
- **哈希**：37772e20475b35f1a29bc266fdd5216c8d271f7e
- **短哈希**：37772e204
- **日期**：2026-07-16 10:41:45 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.67.0 to 0.68.1 (#17239)
- **PR/Issue**：#17239

## 总体目的

这个提交将 `datamodel-code-generator` 从 0.67.0 升级到 0.68.1（semver minor 版本升级）。`datamodel-code-generator` 是一个 Python 工具，用于从 OpenAPI 规范自动生成 Pydantic 数据模型代码。Iceberg 在 `open-api/` 目录下使用它从 REST catalog 的 OpenAPI 规范生成 Python 模型类 `rest-catalog-open-api.py`。

由于代码生成器版本升级会改变生成代码的风格，本提交不仅更新了 `requirements.txt` 中的依赖版本，还同步重新生成了 `rest-catalog-open-api.py`，以反映新版本生成器的输出变化。具体来说，新版本生成器对 `Summary` 模型中 `__pydantic_extra__` 注解的处理方式发生了变化——从运行时动态赋值改为类体内静态声明。

## 如何达成设计目的

两步：(1) 在 `open-api/requirements.txt` 中将 `datamodel-code-generator` 版本固定从 `0.67.0` 改为 `0.68.1`；(2) 用新版本生成器重新生成 `open-api/rest-catalog-open-api.py`，使生成代码与新版本生成器输出保持一致，避免版本与生成产物不匹配。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 依赖版本。

**工作逻辑**：
```
datamodel-code-generator==0.68.1
```
将版本固定从 `0.67.0` 改为 `0.68.1`。

### `open-api/rest-catalog-open-api.py` (+4/-5 lines)

**修改目的**：用新版本生成器重新生成的代码，反映生成器输出风格变化。

**工作逻辑**：
`Summary` 模型（对应 Iceberg 快照的 summary 字段，使用 `extra='allow'` 允许任意额外属性）的 `__pydantic_extra__` 类型注解处理方式变化：

旧版本（运行时动态赋值）：
```python
class Summary(BaseModel):
    model_config = ConfigDict(
        extra='allow',
    )
    operation: Literal['append', 'replace', 'overwrite', 'delete']

Summary.__annotations__['__pydantic_extra__'] = Dict[str, str]
Summary.model_rebuild(force=True)
```

新版本（类体内静态声明）：
```python
class Summary(BaseModel):
    model_config = ConfigDict(
        extra='allow',
    )
    __annotations__ = {
        '__pydantic_extra__': Dict[str, str],
    }
    operation: Literal['append', 'replace', 'overwrite', 'delete']
```

新版本生成器直接在类体内通过 `__annotations__` 字典声明 `__pydantic_extra__` 的类型为 `Dict[str, str]`，无需在类定义后动态赋值并 `model_rebuild`。这种方式更简洁，也避免了运行时重建模型的额外开销。其余生成代码不变。

## 总结

这是依赖升级与生成产物同步更新的提交。`datamodel-code-generator` 升级到 0.68.1 后，生成器对 Pydantic `extra` 字段注解的处理从运行时动态赋值改为类体内静态声明，使生成的 `Summary` 模型代码更简洁。提交同步更新了依赖版本和重新生成的代码，保持二者一致。minor 版本升级风险较低。
