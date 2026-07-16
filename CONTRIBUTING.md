# Contributing

## 提交代码前

首次使用本仓库时运行：

```powershell
.\scripts\install-git-hooks.ps1
```

之后每次提交前请：

1. 只暂存本次开发相关的文件：`git add <files>`。
2. 运行 `powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1`。
3. 运行 `git diff --cached --check` 检查空白字符和冲突标记。
4. 运行 `cd android; .\gradlew.bat assembleDebug`。
5. 确认暂存区没有账号、密码、令牌、私有地址、本地路径或真实用户数据。
6. 使用 Conventional Commits 格式，例如 `feat: add server status timeline` 或 `fix: restore ticket session`。

pre-commit hook 会自动执行隐私扫描和 `git diff --cached --check`。检查失败时不要绕过 hook；先移除敏感内容。

## 推送规则

- 日常开发使用 `feat/*`、`fix/*` 或 `docs/*` 分支，完成检查后再推送。
- 不要使用 `git push --force` 覆盖 `main`。
- 推送前确认 `git status` 和 `git diff --cached`，避免把其他本地工作一起提交。
- Release 使用新的版本号和 tag，例如 `v1.0.1`；不要移动、删除或覆盖已经发布的 tag。
- 不要删除既有 Release 或重新上传同名资产。这样可以保证历史版本下载链接持续有效。
