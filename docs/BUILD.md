# 编译与运行

## 环境要求（本机当前缺，需补）

- JDK 17（本机只有 JDK 8，AGP 8.x 必须 17+）
- Android SDK（API 35）+ Android Studio（推荐 Meerkat 及以上）
- Kotlin 2.0.21 / Gradle 8.11.1（按 `gradle-wrapper.properties` 自动下载）

## 步骤

1. 安装 JDK 17 并设置 `JAVA_HOME`；用 Android Studio 打开 `D:\WorkSpace\personal\Code\Android\fear`。
2. SDK Manager 安装 Android 35、Build-Tools；首次同步会自动拉依赖。
3. 直接 Run `app` 到真机/模拟器（widget 建议真机看效果）。
4. 加桌面组件：长按桌面 -> 微件 -> 恐惧贪婪指数。

## 关键行为验证

- `adb shell dumpsys jobscheduler | findstr fear`：应有 `fear-hourly-refresh`，约 1h 周期。
- 开飞行模式：App/组件显示本地缓存 + 来源标注，不崩。
- 手动刷新：App 右上角刷新按钮；组件点击跳转 App。
- 切标的：上证指数 / 沪深300（当前镜像为 A 股统一序列，直连激活后自动分流，见 `FearRepository` 注释）。

## 已知限制

- 数据日更（交易日），组件每小时轮询，日内多为同一条；Doze 下允许 ±10 分钟漂移。
- `updatePeriodMillis=0`，刷新完全由 WorkManager 驱动；省电策略激进的 ROM 需加白名单。
