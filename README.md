# IGNG MC Companion

IGNG MC Companion 是 IGNG 的 Android 客户端，提供 Minecraft 服务器状态监控和账户工单管理。

## 功能

- 查看服务器在线状态、TPS、MSPT、CPU、内存、在线人数和网络延迟
- 查看服务器性能历史趋势
- 使用 IGNG 账户登录
- 查看、创建和处理与自己相关的工单
- 支持多账户、主题切换和可选触感反馈

## 构建

需要 Android SDK、JDK 11 或更高版本，以及项目自带的 Gradle Wrapper。

```powershell
cd android
.\gradlew.bat assembleDebug
```

可通过 Gradle 属性覆盖服务地址：

```powershell
.\gradlew.bat assembleRelease -PMC_STATUS_BASE_URL=https://your-host -PIGNG_SSO_BASE_URL=https://your-sso-host
```

## 隐私与安全

项目不会提交 `local.properties`、构建产物、临时截图、日志、签名密钥或密码文件。提交前请运行：

```powershell
.\scripts\check-privacy.ps1
```

账户会话令牌仅保存在 Android Keystore 加密的数据存储中。请不要在 issue、日志或提交中公开账号凭据、会话令牌或签名材料。

## 许可证

本项目使用 MIT License，详见 [LICENSE](LICENSE)。

