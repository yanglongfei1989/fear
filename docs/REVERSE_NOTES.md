# funddb 恐惧贪婪接口逆向笔记（2026-09-21 全链路打通）

## 1. 结论一句话

- 页面 `https://funddb.cn/tool/fear` 是 SPA（韭圈儿），真数据走 `POST https://api.jiucaishuo.com/v2/kjtl/kjtlconnect`。
- **签名 + 解密已全部复刻**：真浏览器抓包 32/32 字段一致；解密出官方最新值 **36.16（2026-09-18）**，与页面截图一致。
- App 当前策略：**funddb 直连主源** -> 开源镜像降级 -> Room 缓存（来源标注在 UI）。

## 2. 已验证的请求（Python 复现通过）

```python
POST https://api.jiucaishuo.com/v2/kjtl/kjtlconnect
Headers: Referer=https://funddb.cn/tool/fear, Origin=https://funddb.cn, UA=浏览器
Body: {"gu_code":"000001.SH","type":"h5","version":"2.4.5","act_time":<ms>}
-> 200, "SYLnjmR3..."（11136 字符 base64，有首尾引号）
```

`gu_code`：上证 `000001.SH` / 沪深300 `000300.SH`。

明文对照（同一 host，军工产品，非恐惧贪婪）：
`POST /v2/kjtlother/kjtlconnect` 同 body -> `{"code":0,"data":{"xAxis":{"categories":[...]},"series":[{"name":"军工PH值",...},{"name":"中证国防指数...",...}]}}`，485 天（2024-09-20~2026-09-18）。

因子字典（明文，无需签名）：
`POST /v2/kjtl/getalltypes` -> 6 因子：50ETF波动率 / 陆股通累计买入净额 / 创新高个股占比 / 沪深300股指期货升贴水 / 股债回报差 / 两融交易额占比。

## 3. 前端解密链（`funddb.cn/static/js/app.*.js`，以 6c78383a 版为准）

- `kjtlconnect` 经统一 `fetch({url:"/v2/kjtl/kjtlconnect", is_jm:!0, ...})`，返回 string 时走 `A(e.data)` 解密。
- `A(t) = JSON.parse(AES.decrypt(t, H.e(), {iv: H.a(), CBC, Pkcs7}))`。
- `H.a()/H.e()` 由 5 个同构对象 P/z/q/E/H 的 `substr` 链逐级拼接，恒等于 `k.a/k.b` 全量：
  - `k.a` 终值 `bvroqevdjqibsdkq`（16B），`k.b` 终值 `eveqocftukbotqjcequcnkrqlw1oi`（29B）。
- **两个叠加补丁（缺一不可，第二个极易漏）**：
  1. `AES.decrypt` 覆写：`key=keyStr+"ll"`，`iv=ivStr+"ll"`；
  2. `Utf8.parse` 覆写：`parse(x)` 实际解析 `x+"1"`。
  - 叠加结果：`key = k.b + "ll1"`（**恰 32B，AES-256**），`iv = (k.a + "ll1").take(16)`。
- 服务端按 **32B 对齐填充**（填充字节值=填充长度，可 >16），标准 PKCS5 会误杀，
  App 端用 `AES/CBC/NoPadding` 解后 `extractJson()` 截断（等价 Python `json.raw_decode`）。
- 历史密钥（AKShare `cninfo.js` gen1/gen2）已废弃，仅备查。

## 4. 请求签名（已复刻，32/32 验证通过）

- `fetch` 先强制 `type="pc"`、`version="2.2.7"`（`p.a.version`）、`authtoken=""`；
- 恐惧页调用：`kjtlconnect({gu_code:"000001.SH"|"000300.SH", time:-1})`，`act_time=now`；
- `o = 按 key 排序拼接非空标量值 + SECRET("EWf45rlv#kfsr@k#gfksgkr")`，`u = md5(o)`；
- 32 字段映射（`b()` 实参顺序对应形参，见 `FundDbCrypto.buildSignedBody`），
  已用线上 `click/click` 与 `kjtlconnect` 真请求双重校验 32/32 一致。

## 5. 明文结构（解密成功后校验用）

`data.xAxis.categories=[date]`（`time=-1` 时约 241 天），
`data.series[0]` 为 `恐惧贪婪`，`data.series[1]` 为 `上证指数(点击隐藏)`（沪深300 口径同理）。
实测：2026-09-18 恐惧贪婪 **36.16**，上证 3911.87。

## 6. 排障备忘

- 因子明细 `getlist` 同密钥体系，可用同样方式解密（6 因子 id=1..6）。
- `kjtlother/*` 明文接口是军工 PH 值等其他产品，不是恐惧贪婪，别接错。
- 页面有反调试（检出 DevTools 会清空 body）+ 恐贪页有知情弹窗（`local_sto_fear_stauts`），
  自动化抓包需 `localStorage` 预置 + `JSON.parse` hook 外发（见本次排障过程）。
- 样本：`fear_direct.json`（构建机临时目录，解密后明文）。
