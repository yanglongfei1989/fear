# 恐惧贪婪指数（Android）

原生 Kotlin + Compose + Glance 桌面组件，每小时自动刷新。

- App：最新值 + 半圆仪表盘 + 情绪标签 + 环比变化 + 历史曲线（3月/半年/1年/全部）+ 上证/沪深300 切换。
- 组件：数值 + 情绪 + 环比 + 更新时间，点击进 App。
- 数据：开源 A 股恐惧贪婪镜像主源（明文日更）+ Room 缓存；韭圈儿 funddb 官方接口做 Best-effort 直连预埋（签名补齐即切主源）。

文档：`docs/REVERSE_NOTES.md`（接口逆向全记录）、`docs/BUILD.md`（编译运行）。
