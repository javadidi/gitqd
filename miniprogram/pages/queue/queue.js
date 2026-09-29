const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, timeSlotLabel } = require('../../utils/format')

// 候诊查询（T16 卡片 526–539 行 / PRD §3.3.3 三个要点 + §6.1 513 行「候诊查询」一页）。
//
// 自动刷新的间隔取自 PRD 第 477 行「候诊叫号刷新频率 | ≤ 10秒」，取上限 10 秒：
// 卡片 530 行写的是「轮询或 WebSocket」二选一，而首版没有推送通道，
// WebSocket 要额外做连接管理与鉴权，规格只要求"十秒内更新"，所以轮询是够用且不发明东西的那一个。
const POLL_INTERVAL = 10000

Page({
  data: {
    loading: false,
    rows: [],
    lastSyncText: '',
    pollIntervalMs: POLL_INTERVAL,
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadQueues()
    this.startPolling()
  },

  onHide() {
    this.stopPolling()
  },

  onUnload() {
    this.stopPolling()
  },

  onPullDownRefresh() {
    this.loadQueues().then(() => wx.stopPullDownRefresh())
  },

  startPolling() {
    // 定时器挂在 this 上而不是模块级：模块级变量在所有页面实例间共享，
    // 从候诊页进出两次就会留下两个同时在跑的定时器，各自请求、各自 setData。
    this.stopPolling()
    this.pollTimer = setInterval(() => this.loadQueues(true), POLL_INTERVAL)
  },

  stopPolling() {
    if (this.pollTimer) {
      clearInterval(this.pollTimer)
      this.pollTimer = null
    }
  },

  async loadQueues(silent) {
    if (!silent) {
      this.setData({ loading: true })
    }
    try {
      const list = await get('/user/queues')
      this.setData({
        rows: (list || []).map((item) => ({
          appointmentId: item.appointmentId,
          patientName: item.patientName || '—',
          departmentName: item.departmentName || '—',
          doctorName: item.doctorName || '—',
          timeText: (item.appointmentTime
            ? formatDate(item.appointmentTime, 'MM-DD HH:mm') : '—')
            + '（' + timeSlotLabel(item.timeSlot) + '）',
          appointmentStatus: item.appointmentStatus,
          queueStatus: item.queueStatus || '',
          currentNumber: item.currentNumber,
          waitingCount: item.waitingCount,
        })),
        lastSyncText: formatDate(new Date(), 'HH:mm:ss'),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast；轮询失败保留上一次的数据不清空，
      // 患者不该因为一次网络抖动就看到"暂无候诊"
    } finally {
      this.setData({ loading: false })
    }
  },

  onRefresh() {
    this.loadQueues()
  },

  onGoAppointment() {
    wx.switchTab({ url: '/pages/appointment/appointment' })
  },

  onGoRecords() {
    wx.navigateTo({ url: '/pages/appointment/records' })
  },
})
