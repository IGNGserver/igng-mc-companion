# IGNGmc Android

Jetpack Compose + Kotlin + Material 3 实现的 IGNG MC 服务器状态客户端。

## 已实现

- 顶部数字总览
- 按站点顺序展示全部服务器
- 每台服务器显示在线状态、TPS、MSPT、内存、CPU、在线人数
- 点击进入服务器详情
- 详情页展示 5 条折线图：TPS、MSPT、内存、CPU、在线人数
- 顶部时间粒度切换：`24h`、`3d`、`7d`、`30d`

## 接口约定

默认对接 `https://mc.igng.net`，使用：

- `/api/status/list`
- `/api/status/nodes`
- `/api/status/overview`
- `/api/status/timeline`
- `/api/status/server/{id}`

为满足移动端首页卡片，网页端 `apps/mc/app/api/status/timeline/route.js` 已补充返回：

- `avg_mspt`
- `cpu_usage`
- `memory_usage_mb`

## 构建

```powershell
cd android
.\gradlew.bat assembleDebug
```

## 自定义接口地址

可通过 Gradle 属性覆盖：

```powershell
.\gradlew.bat assembleDebug -PMC_STATUS_BASE_URL=https://your-host
```
