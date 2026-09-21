# funddb 恐惧贪婪接口逆向笔记（2026-09-21 实测）

## 1. 结论一句话

- 页面 `https://funddb.cn/tool/fear` 是 SPA（韭圈儿，`funddb.cn`），真数据走 `POST https://api.jiucaishuo.com/v2/kjtl/kjtlconnect`。
- 该接口 `is_jm=1`（加密）+ 请求签名双重保护；**未签名请求能拿到 200 + 11KB 加密 blob，但三代密钥都解不开**。
- 同 host 下 `/v2/kjtlother/kjtlconnect` 是明文但属于另一产品（军工 PH 值），不是恐惧贪婪。
- App 当前策略：镜像主源 + Room 缓存 + 直连 Best-effort 预埋（`FundDbCrypto` / `tryDirect`）。

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
- `H.a()/H.e()` 由 5 个同构对象 P/z/q/E/H 的 `substr` 链逐级拼接，数学上恒等于 `k.a/k.b` 全量：
  - `k.a` 初值 `nengnongchulainb` -> 运行时覆写 `bvroqevdjqibsdkq`（16B）
  - `k.b` 初值 `bieyanjiulexixishuibatoufamei` -> 运行时覆写 `eveqocftukbotqjcequcnkrqlw1oi`（29B）
- monkey-patch：`AES.decrypt` 被覆写为 `key=keyStr+"ll"`, `iv=ivStr+"ll"` 后再调原生。
- 历史密钥（AKShare `cninfo.js`）：gen1 `h5.jiucaishuo.com*`；gen2 `bieyanjiulexixishuibatoufameill1/nengnongchulainbl1`。
- **三代密钥对未签名 blob 全部 Malformed UTF-8 / Padding 错误**，说明响应密钥与请求签名绑定或签名失败返回不可解包。

## 4. 请求签名（待补，激活直连的关键）

`fetch` 前对 `t.data` 追加约 30 个混淆字段（`tirgkjfs/abiokytke/u54rg5d/...`），由 `md5(sorted(params)+secret)` 按固定下标截取；
secret 来自 `p.a.fklreialk`。锚点：`app.*.js` 中 `case 0:for(r in t.data.type,t.data.version,a=+new Date,t.data.act_time=a...` 段。

## 5. 激活直连的标准动作

1. Playwright 打开 `https://funddb.cn/tool/fear`，拦截 `kjtlconnect` 的请求体（即合法签名样本）+ 解密后 `A()` 返回的明文结构；
2. 把签名函数（含 `fklreialk`）移植为 Kotlin（md5 为主，无非对称）；
3. 填入 `FundDbCrypto.buildSignedBody`，跑通后 `tryDirect` 会自动落库并标记 `FUNDDB_DIRECT`。

## 6. 明文结构（解密成功后校验用）

与 `kjtlother` 同形：`data.xAxis.categories=[date]`，`data.series[0].data=[fear]`，`data.series[1].data=[大盘点位]`。

样本原文已存临时目录（构建机）：`enc.txt`（未签名 blob）、`kjtl_000001.SH.json`（军工明文对照）。
