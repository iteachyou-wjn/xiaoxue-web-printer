# JasperReport 报表打印

晓雪WEB打印控件集成 **JasperReport 7.0** 报表引擎，支持通过 JSON 传参填充 `.jasper` 模板，适用于**领料单、送货单、发票、报表**等复杂版式打印场景。

## 模板位置

编译后的 `.jasper` 模板文件放在安装目录下的 `reports/jr/` 文件夹中：

```
晓雪WEB打印控件/
├── 晓雪WEB打印控件.exe
└── reports/
    └── jr/
        ├── picking-slip.jasper    # 内置：生产领料单
        └── your-report.jasper     # 自定义模板
```

## 1. 获取模板列表

```js
const jr = await sdk.getJasperInstance();
const r = await jr.listTemplates();
// r.templates => [{ name: 'picking-slip', file: 'picking-slip.jasper', size: 34329 }]
r.templates.forEach(t => console.log(t.name, t.file));
```

## 2. 预览报表

```js
sdk.doPreview({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical' },
  content: {
    type: 'jasper',
    value: 'picking-slip',           // 模板名（不带 .jasper 后缀）
    params: {                         // 报表参数
      billNo: 'LL20261010001',
      billDate: '2026-10-10',
      warehouse: '原材料仓',
      qrContent: 'LL20261010001',   // 二维码内容
      items: [                       // 明细数据（List）
        { seq: 1, materialCode: 'M001', materialName: '螺丝M6', spec: 'M6x20', unit: '个', quantity: 100, remark: '' },
        { seq: 2, materialCode: 'M002', materialName: '螺母M6', spec: 'M6', unit: '个', quantity: 100, remark: '' }
      ]
    }
  }
}).then(r => console.log('预览已打开'));
```

## 3. 打印报表

参数与预览完全一致，调用 `doPrint()`：

```js
sdk.doPrint({
  printer: '默认打印机',
  style: { paper: 'A4', direction: 'vertical' },
  content: {
    type: 'jasper',
    value: 'picking-slip',
    params: {
      billNo: 'LL20261010001',
      billDate: '2026-10-10',
      items: [
        { seq: 1, materialCode: 'M001', materialName: '螺丝M6', spec: 'M6x20', unit: '个', quantity: 100 }
      ]
    }
  }
}).then(r => console.log('任务ID：', r.taskId));
```

## 参数说明

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `value` | string | 模板名（`reports/` 目录下的 `.jasper` 文件名，不带后缀） |
| `params` | object | 报表参数，键名与模板中定义的参数名一致 |
| `params.items` | array | 明细数据，对应模板中的 JRBeanCollectionDataSource |
| `params.qrContent` | string | 二维码内容，自动渲染为二维码图片 |

## 内置模板：生产领料单

系统内置了 `picking-slip`（生产领料单）模板，包含：

- 表头：单据编号、日期、仓库、制单人
- 明细表：序号、物料编码、物料名称、规格、单位、数量、备注、仓位
- 二维码：自动根据 `qrContent` 生成
- 表尾：领用人、发料人、审批人签字栏

## 自定义模板

1. 使用 Jaspersoft Studio 设计报表，导出为 `.jasper`（编译后格式）
2. 将 `.jasper` 文件放到安装目录的 `reports/jr/` 下
3. 前端通过 `getJasperTemplates()` 获取模板列表
4. 调用 `doPrint` / `doPreview` 时传入模板名和参数

::: warning 注意
- 模板文件必须是编译后的 `.jasper` 格式，不是 `.jrxml` 源码
- 中文字体使用内置的微软雅黑、黑体，无需额外安装
- 明细数据请传入数组，模板中通过 `$F{字段名}` 引用
:::
