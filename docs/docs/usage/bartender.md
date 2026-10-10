# BarTender 模板打印

晓雪WEB打印控件深度集成 **BarTender** 标签软件（bartender-printer-sdk 2.1.1），支持读取 `.btw` 模板参数、生成模板预览图像，以及直接打印 BarTender 模板。适用于**条码、标签、证卡**等专业打印场景。

## 适用前提

- 本机已安装 **BarTender** 软件；
- 服务端可正常访问 BarTender（通过 `available` 判断）。

## 1. 获取 BarTender 实例与模板列表

```js
const bt = await sdk.getBartenderInstance();
console.log(bt.available);      // BarTender 是否可用
console.log(bt.version);        // 版本号
console.log(bt.fullVersion);    // 完整版本信息

// 列出 reports/bt/ 下所有模板
const r = await bt.listTemplates();
// r.templates => [{ name: 'mylabel', file: 'mylabel.btw', size: 12345 }]
```

### 实例属性

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `available` | boolean | BarTender 是否可用 |
| `version` | string | 版本号 |
| `fullVersion` | string | 完整版本信息 |

### 刷新实例信息

```js
await bt.refresh();   // 重新获取实例信息（版本号等）
```

## 2. 获取模板参数

读取 `reports/bt/` 目录下 `.btw` 模板中的字段（参数名 + 控件类型），用于动态填充：

```js
const r = await bt.getTemplateParams('mylabel.btw');
// r.params => [{ name: 'barcode1', type: 'Barcode' }, { name: 'name', type: 'Text' }]
r.params.forEach(p => console.log(p.name, p.type));
```

`type` 为控件类型（ObjectType 粒度）：`Barcode` / `Text` / `Picture` / `RichText` / `RFID` 等。条码不细分（QRCode / Code128 均返回 `Barcode`）。

## 3. 获取模板图像（预览）

### 无参数模板图像

```js
const img = await bt.getTemplateImage('mylabel.btw', 300);
// img.image => base64 字符串
```

### 带参数模板图像

```js
const img2 = await bt.getTemplateImageWithParams(
  'mylabel.btw',
  { 字段1: '值1', 字段2: '值2' },   // 模板字段值
  300                                // 渲染分辨率 dpi，默认 300
);
```

返回的 `image` 为 base64 字符串，可直接用于页面预览：

```js
imgEl.src = 'data:image/png;base64,' + img.image;
```

## 4. 打印 BarTender 模板

通过 `doPrint()`，将 `content.type` 设为 `bartender`，`value` 为模板路径（放在安装目录 `reports/bt/` 下），`params` 传模板字段值：

```js
sdk.doPrint({
  printer: '默认打印机',
  style: { paper: 'A4' },   // 或标签纸
  content: {
    type: 'bartender',
    value: 'mylabel.btw',               // 模板名（reports/bt/ 目录下）
    params: { 字段1: '值1', 字段2: '值2' }  // 模板参数（可选）
  }
}).then(r => console.log('任务ID：', r.taskId));
```

## 完整示例（Vue 3）

```js
// 获取 BarTender 实例（含版本号）
const bt = await sdk.getBartenderInstance()
console.log(bt.available, bt.version, bt.fullVersion)

// 获取模板参数（含控件类型：Barcode/Text/Picture 等）
const p = await bt.getTemplateParams('a.btw')
// p.params => [{ name: 'barcode1', type: 'Barcode' }]

// 获取模板图像（返回 base64）
const img = await bt.getTemplateImage('a.btw')
// img.image => base64 字符串，可直接 new Image() 使用

// 获取带参数模板图像
const img2 = await bt.getTemplateImageWithParams('a.btw', { 字段1: '值1' })
```

## 注意事项

- 模板文件放在安装目录 `reports/bt/` 下，传入文件名即可（如 `mylabel.btw`）；
- 打印前建议先通过 `getTemplateImageWithParams` 预览，确认字段填充正确后再打印；
- 若 `available` 为 `false`，请确认本机已正确安装并启动 BarTender。
