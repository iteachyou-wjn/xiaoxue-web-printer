/**
 * xiaoxue-printer-sdk.js
 * 打印控件 WebSocket 客户端 SDK
 *
 * 封装与打印服务（ws://localhost:54321）的通信：
 * - 连接管理：连接、断开、自动重连
 * - 事件订阅：open / close / error / message / welcome / taskListUpdate
 * - 打印服务 API（Promise 化）：doPrint / doPreview / getPrinters / getPageSize / getTaskList / getTaskStatus / cancelTask / clearTasks
 *
 * 使用方式（浏览器）：
 *   <script src="xiaoxue-printer-sdk.js"></script>
 *   const sdk = new DreamerPrinterSDK({ url: 'ws://127.0.0.1:54321' });
 *   sdk.connect();
 *   sdk.on('open', () => console.log('已连接'));
 *   sdk.doPrint({ printer: 'HP', content: { type: 'text', value: '报表' } }).then(r => console.log(r));
 *
 * 获取可用打印机 / 纸张（需先创建 SDK 实例，未连接时自动连接）：
 *   const sdk = new DreamerPrinterSDK({ url: 'ws://localhost:54321' });
 *   sdk.getPrinters().then(r => console.log(r.printers));
 *   sdk.getPageSize('Microsoft Print to PDF').then(r => console.log(r.sizes));
 *
 * 提交并执行打印任务（需通过 SDK 实例调用；未连接时自动连接）：
 *   sdk.doPrint({ style: {...}, printer: '...', content: { type: 'text', value: '...' } })
 *     .then(r => console.log(r.taskId));
 *
 * 打开打印预览（需通过 SDK 实例调用；参数同 doPrint）：
 *   sdk.doPreview({ style: {...}, printer: '...', content: { type: 'text', value: '...' } })
 *     .then(r => console.log(r.message));
 *
 * 使用方式（Node.js）：
 *   const DreamerPrinterSDK = require('./xiaoxue-printer-sdk');
 */
(function (global) {
  'use strict';

  const DEFAULT_URL = 'ws://localhost:54321';

  class DreamerPrinterSDK {
    /**
     * @param {Object} options
     * @param {string}  [options.url='ws://localhost:54321']  WebSocket 服务地址
     * @param {boolean} [options.autoReconnect=true]          是否自动重连
     * @param {number}  [options.reconnectDelay=3000]         重连间隔（毫秒）
     * @param {number}  [options.maxReconnectAttempts=Infinity] 最大重连次数
     * @param {number}  [options.connectionTimeout=5000]      连接超时（毫秒）
     */
    constructor(options = {}) {
      this.url = options.url || DEFAULT_URL;
      this.autoReconnect = options.autoReconnect !== false;
      this.reconnectDelay = options.reconnectDelay || 3000;
      this.maxReconnectAttempts = options.maxReconnectAttempts == null ? Infinity : options.maxReconnectAttempts;
      this.connectionTimeout = options.connectionTimeout || 5000;

      this.ws = null;
      this.connected = false;
      this.connecting = false;
      this._reconnectAttempts = 0;
      this._manuallyClosed = false;

      // 广播事件处理器
      this._handlers = {
        open: [],
        close: [],
        error: [],
        message: [],
        welcome: [],
        taskListUpdate: [],
      };

      // 等待响应的请求队列（服务端协议未带 requestId，按响应 type 与请求发出顺序匹配）
      this._pending = [];
    }

    // ================= 事件订阅 =================

    /**
     * 订阅事件
     * @param {string} event open|close|error|message|welcome|taskListUpdate
     * @param {Function} callback
     */
    on(event, callback) {
      if (!this._handlers[event]) {
        this._handlers[event] = [];
      }
      this._handlers[event].push(callback);
      return this;
    }

    /**
     * 取消订阅
     */
    off(event, callback) {
      const list = this._handlers[event];
      if (list) {
        const idx = list.indexOf(callback);
        if (idx >= 0) list.splice(idx, 1);
      }
      return this;
    }

    _emit(event, ...args) {
      (this._handlers[event] || []).forEach((cb) => {
        try {
          cb(...args);
        } catch (e) {
          console.error('[xiaoxue-printer-sdk] 事件处理异常:', e);
        }
      });
    }

    // ================= 连接管理 =================

    /**
     * 连接服务
     * @returns {DreamerPrinterSDK}
     */
    connect() {
      if (this.ws && (this.connecting || this.connected)) {
        return this;
      }
      this._manuallyClosed = false;
      this._connect();
      return this;
    }

    _connect() {
      this.connecting = true;
      const ws = new WebSocket(this.url);
      this.ws = ws;

      // 连接超时兜底
      const timeout = setTimeout(() => {
        if (ws.readyState !== WebSocket.OPEN) {
          try {
            ws.close();
          } catch (e) { /* 忽略 */ }
        }
      }, this.connectionTimeout);

      ws.onopen = () => {
        clearTimeout(timeout);
        this.connecting = false;
        this.connected = true;
        this._reconnectAttempts = 0;
        this._emit('open');
      };

      ws.onmessage = (evt) => {
        this._handleMessage(evt.data);
      };

      ws.onerror = (err) => {
        clearTimeout(timeout);
        this._emit('error', err);
      };

      ws.onclose = () => {
        clearTimeout(timeout);
        this.connecting = false;
        this.connected = false;
        this.ws = null;
        // 使所有挂起的请求失败
        this._rejectAllPending(new Error('连接已关闭'));
        this._emit('close');
        this._scheduleReconnect();
      };
    }

    _scheduleReconnect() {
      if (this._manuallyClosed || !this.autoReconnect) return;
      if (this._reconnectAttempts >= this.maxReconnectAttempts) return;
      this._reconnectAttempts++;
      setTimeout(() => {
        if (!this.connected && !this._manuallyClosed) {
          this._connect();
        }
      }, this.reconnectDelay);
    }

    /**
     * 断开连接（停止自动重连）
     */
    disconnect() {
      this._manuallyClosed = true;
      if (this.ws) {
        try {
          this.ws.close();
        } catch (e) { /* 忽略 */ }
      }
      this.connected = false;
      this.connecting = false;
      this.ws = null;
    }

    // ================= 消息处理 =================

    _handleMessage(data) {
      let msg;
      try {
        msg = typeof data === 'string' ? JSON.parse(data) : data;
      } catch (e) {
        this._emit('message', { raw: data });
        return;
      }

      this._emit('message', msg);
      const type = msg && msg.type;

      // 服务端主动推送
      if (type === 'welcome') this._emit('welcome', msg);
      if (type === 'task_list_update') this._emit('taskListUpdate', msg.tasks || []);

      // 匹配挂起的请求响应
      this._resolvePending(type, msg);
    }

    _resolvePending(type, msg) {
      for (let i = 0; i < this._pending.length; i++) {
        const p = this._pending[i];
        if (p.expectType === type) {
          this._pending.splice(i, 1);
          clearTimeout(p.timer);
          if (type === 'error') {
            p.reject(new Error((msg && msg.message) || '服务器错误'));
          } else if (msg && msg.success === false) {
            p.reject(new Error((msg && msg.message) || '操作失败'));
          } else {
            p.resolve(msg);
          }
          return;
        }
      }
    }

    _rejectAllPending(err) {
      this._pending.forEach((p) => {
        clearTimeout(p.timer);
        p.reject(err);
      });
      this._pending = [];
    }

    /**
     * 发送请求并等待对应类型的响应
     * @private
     */
    _request(message, expectType, timeoutMs = 10000) {
      return new Promise((resolve, reject) => {
        if (!this.connected || !this.ws) {
          reject(new Error('未连接到打印服务，请先 connect()'));
          return;
        }
        const timer = setTimeout(() => {
          const idx = this._pending.findIndex((p) => p.resolve === resolve);
          if (idx >= 0) this._pending.splice(idx, 1);
          reject(new Error('请求超时（' + expectType + '）'));
        }, timeoutMs);
        this._pending.push({ expectType, resolve, reject, timer });
        this.ws.send(JSON.stringify(message));
      });
    }

    /**
     * 确保已连接；未连接时自动建立连接
     * @private
     * @param {number} [timeout=8000] 连接超时（毫秒）
     * @returns {Promise<void>}
     */
    _ensureConnected(timeout = 8000) {
      if (this.connected && this.ws) {
        return Promise.resolve();
      }
      return new Promise((resolve, reject) => {
        let done = false;
        let timer = null;
        const cleanup = () => { if (timer) clearTimeout(timer); };
        const onOpen = () => {
          if (done) return;
          done = true;
          this.off('open', onOpen);
          this.off('error', onError);
          cleanup();
          resolve();
        };
        const onError = (e) => {
          if (done) return;
          done = true;
          this.off('open', onOpen);
          this.off('error', onError);
          cleanup();
          reject(e || new Error('连接打印服务失败'));
        };
        timer = setTimeout(() => {
          if (done) return;
          done = true;
          this.off('open', onOpen);
          this.off('error', onError);
          reject(new Error('连接超时'));
        }, timeout);
        this.on('open', onOpen);
        this.on('error', onError);
        this.connect();
      });
    }

    // ================= 打印服务 API =================

    /**
     * 查询任务列表
     * @returns {Promise<Object>} task_list { tasks: [...] }
     */
    getTaskList() {
      return this._request({ type: 'list' }, 'task_list');
    }

    /**
     * 查询单个任务状态
     * @param {string} taskId
     * @returns {Promise<Object>} task_status { task?, found, message? }
     */
    getTaskStatus(taskId) {
      return this._request({ type: 'status', taskId }, 'task_status');
    }

    /**
     * 取消任务
     * @param {string} taskId
     * @returns {Promise<Object>} cancel_result { success, message }
     */
    cancelTask(taskId) {
      return this._request({ type: 'cancel', taskId }, 'cancel_result');
    }

    /**
     * 清除已完成/失败的任务
     * @returns {Promise<Object>} clear_result { success, message }
     */
    clearTasks() {
      return this._request({ type: 'clear' }, 'clear_result');
    }

    /**
     * 获取可用打印机列表（需通过 SDK 实例调用；未连接时自动连接）
     * @returns {Promise<Object>} printers_result { printers: [String] }
     */
    getPrinters() {
      return this._ensureConnected().then(() =>
        this._request({ type: 'printers' }, 'printers_result'));
    }

    /**
     * 获取指定打印机可用的纸张类型（需通过 SDK 实例调用；未连接时自动连接）
     * @param {string} printerName 打印机名称
     * @returns {Promise<Object>} pageSize_result { printerName, sizes: [String] }
     */
    getPageSize(printerName) {
      return this._ensureConnected().then(() =>
        this._request({ type: 'pageSize', printerName: printerName || '' }, 'pageSize_result'));
    }

    /**
     * 提交并执行打印任务（需通过 SDK 实例调用；未连接时自动连接）
     *
     * @param {Object} json 打印请求
     * @param {Object} [json.style] 打印样式
     * @param {Object} [json.style.margin] 页边距（cm）{ top, right, bottom, left }
     * @param {number} [json.style.zoom=1] 缩放比例
     * @param {string} [json.style.direction='vertical'] 方向：horizontal(横向) | vertical(纵向)
     * @param {string} [json.style.paperHeader] 页头
     * @param {string} [json.style.paperFooter] 页脚
     * @param {string} [json.style.fontFamily='宋体'] 字体
     * @param {number} [json.style.fontSize=12] 字号
     * @param {string} [json.style.paper='A4'] 纸张
     * @param {string} [json.printer='默认打印机'] 打印机名称
     * @param {Object} [json.content] 打印内容
     * @param {string} [json.content.type='text'] text | html
     * @param {string} [json.content.value] 打印内容
     * @returns {Promise<Object>} doPrint_result { success, taskId, message }
     */
    doPrint(json = {}) {
      return this._ensureConnected().then(() =>
        this._request(Object.assign({}, json, { type: 'doPrint' }), 'doPrint_result'));
    }

    /**
     * 打开打印预览（需通过 SDK 实例调用；未连接时自动连接；不打印，仅服务端弹窗展示）
     * 参数与 doPrint 相同。
     *
     * @param {Object} json 预览请求（结构同 doPrint）
     * @returns {Promise<Object>} doPreview_result { success, message }
     */
    doPreview(json = {}) {
      return this._ensureConnected().then(() =>
        this._request(Object.assign({}, json, { type: 'doPreview' }), 'doPreview_result'));
    }

    // ================= BarTender =================

    /**
     * 获取 BarTender 实例（需通过 SDK 实例调用；未连接时自动连接）。
     * 返回的实例对象包含 BarTender 软件版本号等信息，并提供模板参数 / 图像获取能力。
     *
     * @returns {Promise<Bartender>} 包含 version / fullVersion / available 等信息的 Bartender 实例
     */
    getBartenderInstance() {
      return this._ensureConnected().then(() =>
        this._request({ type: 'bartenderInstance' }, 'bartenderInstance_result'))
        .then((info) => new Bartender(this, info));
    }
  }

  /**
   * BarTender 实例对象。
   * 通过 {@link DreamerPrinterSDK#getBartenderInstance()} 获取。
   *
   * 实例属性（来自服务端 BarTender 实例信息）：
   *  - available   : boolean   BarTender 是否可用
   *  - version     : string    版本号
   *  - fullVersion : string    完整版本信息
   */
  class Bartender {
    /**
     * @private
     * @param {DreamerPrinterSDK} sdk SDK 实例
     * @param {Object} info BarTender 实例信息
     */
    constructor(sdk, info = {}) {
      this._sdk = sdk;
      this.info = info || {};
      this.available = this.info.available === true;
      this.version = this.info.version || null;
      this.fullVersion = this.info.fullVersion || null;
    }

    /**
     * 刷新 BarTender 实例信息（版本号等）。
     * @returns {Promise<Object>} bartenderInstance_result { success, available, version, fullVersion, message }
     */
    refresh() {
      return this._sdk._ensureConnected().then(() =>
        this._sdk._request({ type: 'bartenderInstance' }, 'bartenderInstance_result'))
        .then((info) => {
          this.info = info || {};
          this.available = this.info.available === true;
          this.version = this.info.version || null;
          this.fullVersion = this.info.fullVersion || null;
          return info;
        });
    }

    /**
     * 获取模板参数（参数名 + 控件类型）。
     * @param {string} templatePath BarTender 模板路径（.btw）
     * @returns {Promise<Object>} bartenderTemplateParams_result
     *   { success, templatePath, params: [{ name, type }] }
     *   type 为控件类型（ObjectType 粒度）：Barcode / Text / Picture / RichText / RFID 等，
     *   条码不细分（QRCode/Code128 均返回 Barcode）。
     */
    getTemplateParams(templatePath) {
      return this._sdk._ensureConnected().then(() =>
        this._sdk._request({ type: 'bartenderTemplateParams', templatePath }, 'bartenderTemplateParams_result'));
    }

    /**
     * 获取模板图像（无参数），返回 base64 图片。
     * @param {string} templatePath BarTender 模板路径（.btw）
     * @param {number} [dpi=300] 渲染分辨率
     * @returns {Promise<Object>} bartenderTemplateImage_result { success, templatePath, image: <base64> }
     */
    getTemplateImage(templatePath, dpi = 300) {
      return this._sdk._ensureConnected().then(() =>
        this._sdk._request(
          { type: 'bartenderTemplateImage', templatePath, dpi },
          'bartenderTemplateImage_result'));
    }

    /**
     * 获取带参数的模板预览图像（base64）。
     * @param {string} templatePath BarTender 模板路径（.btw）
     * @param {Object} [params={}] 模板字段值
     * @param {number} [dpi=300] 渲染分辨率
     * @returns {Promise<Object>} bartenderTemplateImageWithParams_result { success, templatePath, image: <base64> }
     */
    getTemplateImageWithParams(templatePath, params = {}, dpi = 300) {
      return this._sdk._ensureConnected().then(() =>
        this._sdk._request(
          { type: 'bartenderTemplateImageWithParams', templatePath, params, dpi },
          'bartenderTemplateImageWithParams_result'));
    }
  }

  // 导出（浏览器挂到全局 / Node 用 module.exports）
  global.DreamerPrinterSDK = DreamerPrinterSDK;
  global.Bartender = Bartender;
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = DreamerPrinterSDK;
  }
})(typeof window !== 'undefined' ? window : globalThis);
