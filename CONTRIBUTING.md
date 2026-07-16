# Contributing

提交代码前请：

1. 运行 `powershell -ExecutionPolicy Bypass -File .\scripts\check-privacy.ps1`。
2. 运行 `cd android; .\gradlew.bat assembleDebug`。
3. 确认 `git diff --cached` 中没有账号、密码、令牌、私有地址或本地路径。
4. 使用清晰的提交信息，例如 `feat: add server status timeline` 或 `fix: restore ticket session`。

不要提交 `local.properties`、`android/keystore/`、`.jks`、`.keystore`、`keystore.properties`、构建目录、日志或临时截图。

