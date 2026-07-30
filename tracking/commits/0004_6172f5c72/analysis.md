# 提交 0004：Python: Add more Ruff rules (#8652)

## 提交信息

- **序号**：0004 / 4088
- **哈希**：6172f5c7207899a0983b28a09026644d568595ab
- **短哈希**：6172f5c72
- **日期**：2023-09-28 15:48:28 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Python: Add more Ruff rules (#8652)
- **PR/Issue**：#8652

## 总体目的

这个提交是 Iceberg Python 项目的 lint 规则扩展提交。Iceberg Python 使用 [ruff](https://github.com/astral-sh/ruff)（一个用 Rust 编写的高性能 Python linter，目标是替代 flake8/isort/pyupgrade 等多个工具）做代码静态检查，启用的规则集在 `pyproject.toml` 的 `[tool.ruff]` 段 `select` 字段中声明。本提交在原有规则集基础上新增两类规则：`B`（flake8-bugbear，捕捉常见陷阱）和 `C4`（flake8-comprehensions，建议使用更简洁的推导式写法），同时保留原有的 `E`/`W`/`F`/`I`/`UP`。

引入 flake8-bugbear (`B`) 的价值在于它能捕捉许多 Python 中容易引入 bug 的模式，例如：可变默认参数、`except:` 裸捕获、不正确的 `__init__` 调用、异常链丢失等。这些模式往往不会立即出错，但会在运行时或维护中暴露问题，启用 `B` 能在 lint 阶段提前发现。引入 flake8-comprehensions (`C4`) 的价值在于它能建议把 `list(x for x in y)` 简化为 `[x for x in y]`、把 `dict([(k,v) for ...])` 简化为 `{k:v for ...}` 等，提升代码可读性与一致性，有时还能带来轻微的性能改善。

提交同时把 `ignore` 列表扩展为 `["E501","E203","B024","B028"]`（原为 `["E501","E203"]`），其中 `E501`（行太长）和 `E203`（切片冒号前空白）是 black 格式化器与 pycodestyle 的传统冲突点，沿袭自 black 官方推荐；新增的 `B024`（abstract base class 中声明 abstract method 但没有 `@abstractmethod` 装饰器——本项目中可能是用别的机制实现抽象基类）和 `B028`（`warnings.warn` 没有指定 `stacklevel`）则是针对本仓库实际情况做的局部豁免，避免新规则一上线就产生大量误报噪音。这种"启用大类规则 + 豁免少量具体规则"的策略是引入新 lint 规则的常见渐进式做法，既享受新规则的收益，又不被边界情况拖累。

## 如何达成设计目的

整体设计思路是"扩展 select 列表 + 增量 ignore"。`select` 由字符串列表改为带行内注释的多行数组，每行注明规则前缀对应的工具来源（pycodestyle/Pyflakes/flake8-bugbear/flake8-comprehensions/isort/pyupgrade），提升可读性；同时在 `ignore` 数组中追加 `B024` 与 `B028` 两条具体豁免，使新规则启用后 CI 不会因已知边界情况失败。改动仅 9 行 +4 行 -，全在 `pyproject.toml`，不涉及任何业务代码。

## 修改详情

### `python/pyproject.toml`

**修改目的**：扩展 ruff 启用的规则集，并豁免两条新引入的 bugbear 规则。

**工作逻辑**：
- `select` 由 `["E", "F", "W", "I", "UP"]` 扩展为 `["E", "W", "F", "B", "C4", "I", "UP"]`，新增 `B`（flake8-bugbear，前缀覆盖 B 系列所有规则）与 `C4`（flake8-comprehensions）。同时把单行字符串列表改为多行带注释形式，每行注明规则来源，便于后续维护者理解每条规则的出处。
- `ignore` 由 `["E501","E203"]` 扩展为 `["E501","E203","B024","B028"]`：
  - `E501`（行过长）：与 black 共存的传统豁免，black 不强制硬行宽。
  - `E203`（切片冒号前空白 `x[1 : 2]`）：black 与 pycodestyle 在此有分歧，black 官方建议忽略。
  - `B024`（abstract base class 中含 abstract method 名但未用 `@abstractmethod`）：flske8-bugbear 会把"基类声明了看似抽象的方法但没标装饰器"判为可能误用。Iceberg Python 中可能存在用 ABCMeta 但通过其他机制（如 `abc.ABC` + raise NotImplementedError）实现抽象约定的场景，故豁免。
  - `B028`（`warnings.warn` 未指定 `stacklevel`）：默认 `stacklevel=1` 会把警告指向 `warn()` 调用处而非真正触发警告的用户代码，bugbear 建议显式指定。Iceberg Python 可能在多处 `warnings.warn` 未指定 stacklevel，全部修复成本较高，故先豁免以避免阻塞规则启用。

注释 `# Enable the pycodestyle (E) and Pyflakes (F) rules by default. Unlike Flake8, Ruff doesn't enable pycodestyle warnings (W) or McCabe complexity (C901) by default.` 被移除，因为新规则集已超出该注释描述的范围，注释内容不再准确。

## 小结

本提交为 Iceberg Python 的 ruff 配置新增 flake8-bugbear（`B`）与 flake8-comprehensions（`C4`）两类规则，并豁免两条已知会误报的具体规则，以渐进式方式提升代码静态检查的覆盖面，提前捕捉潜在 bug 与简化代码写法。
