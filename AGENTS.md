# Codex Agent Instructions

本文件是本仓库对 Codex、代码 Agent 和自动化开发工具的工作约定。

## 项目范围

- 这是一个 Kotlin + Jetpack Compose Android 应用，项目入口位于 `android/`。
- 应用对接 IGNG 的服务器状态和工单服务。工单的字段、权限、可见性和状态规则以服务端接口为准，不要在客户端复制或修改业务规则。
- 默认服务地址写在公开构建配置中；不要把测试地址、内网地址、账号或凭据写入源码。

## 开始工作前

1. 先运行 `git status --short --branch`，阅读相关源码和现有改动。
2. 保留用户已有的未提交修改，不要使用 `git reset --hard`、`git checkout --` 或其他破坏性命令。
3. 修改前确认当前任务涉及的模块、接口契约和 Android 页面，不要为了完成小功能重构无关代码。
4. 手工编辑使用 `apply_patch`；新增抽象前先确认现有代码没有可复用的模式。

## 隐私与安全

以下内容永远不能进入 Git 提交或 GitHub：

- `local.properties`、`.env`、真实配置文件和本机绝对路径
- `android/keystore/`、`.jks`、`.keystore`、`.p12`、`.pfx` 和密码文件
- 账号密码、会话令牌、API key、SSH 私钥、Cookie、验证码和真实用户数据
- 带有隐私内容的日志、截图、调试转储和临时文件
- 未经用户明确要求的构建产物或本地 APK

完成修改后必须运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1
git diff --cached --check
```

隐私扫描失败时，先移除敏感内容，不要绕过 hook 或强制加入被忽略文件。若发现疑似泄露，必须在最终回复中明确说明。

## 构建与验证

- 普通 Android 代码修改至少运行 `cd android; .\gradlew.bat assembleDebug`。
- 影响 Release、Manifest、依赖或签名配置时，运行 `cd android; .\gradlew.bat assembleRelease`。
- Release APK 必须使用 `apksigner verify --verbose --print-certs` 验证，不能只用 `jarsigner` 或仅凭 Gradle 成功判断签名正确。
- 失败的测试、构建警告或未执行的验证必须如实报告，不要声称已验证。
- 若修改依赖服务端接口，优先检查对应 IGNG 站点契约和真实响应。

## 提交与推送

只有用户明确要求提交、推送或发布时才执行远程 Git 操作。执行时：

1. 只暂存本次任务相关文件。
2. 使用 Conventional Commits，例如 `feat: add ticket filters`、`fix: restore session`、`docs: update README`。
3. 提交前再次检查 `git diff --cached --name-only` 和隐私扫描结果。
4. 正常推送到 `main` 或用户指定的分支；禁止 `git push --force`，禁止改写远程历史。
5. 推送后核验远程分支、提交 hash 和工作区状态，并在最终回复中报告。

## Release 保护

- 已发布的 `v1.0.0` tag、Release 和 APK 下载地址必须保持不变。
- 后续发布只能使用新的递增版本号和 tag，例如 `v1.0.1`，不能移动、删除或覆盖旧 tag。
- 不要删除已有 Release 或重新上传同名资产；新 APK 使用新的文件名和版本号。
- 发布前必须完成 Release 构建、APK 签名验证、SHA-256 计算和隐私扫描。
- 仅修改 README、规则或普通代码时，不要重新构建或替换旧 Release 资产。

## 最终报告

完成任务时简要说明：改动文件、执行的验证、提交 hash、推送分支，以及是否创建了新的 Release。不要在回复、日志或提交信息中输出密钥、密码或完整会话令牌。
