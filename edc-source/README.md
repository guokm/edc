# EDC 源码镜像说明

本目录用于在主项目下直接提供真实 Eclipse EDC 源码，便于阅读、比对和分阶段迁移。

## 来源目录

以下内容从 `upstream/connector` 同步而来：

- `core`
- `data-protocols`
- `extensions`
- `spi`
- `dist`

## 为什么需要这个目录

当前可运行服务（`edc-control-plane` 等）是项目内可运行骨架。
`edc-source` 用于提供真实 EDC 实现代码上下文，避免新会话只看到骨架结构。

## 重要说明

- `edc-source` 当前是“源码镜像目录”，默认不参与根 Maven 编译。
- 真正参与当前运行与验收的是以下模块：
  - `edc-control-plane`
  - `edc-data-plane`
  - `edc-identity-hub`
  - `edc-issuer-service`
  - `edc-federated-catalog`
  - `edc-operator-services`

## 重新同步命令

在项目根目录执行：

```bash
rsync -a --delete upstream/connector/core/ edc-source/core/
rsync -a --delete upstream/connector/data-protocols/ edc-source/data-protocols/
rsync -a --delete upstream/connector/extensions/ edc-source/extensions/
rsync -a --delete upstream/connector/spi/ edc-source/spi/
rsync -a --delete upstream/connector/dist/ edc-source/dist/
```
