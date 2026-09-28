const app = getApp()
const { formatMoney, formatDate } = require('../../utils/format')

const STATUS_LABELS = {
  PENDING_PAYMENT: '待支付',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
}

// 预约信息（成功页，PRD 81 行「展示预约详情及二维码」）。
Page({
  data: {
    orderNo: '',
    statusLabel: '',
    isConfirmed: false,
    patientName: '',
    doctorName: '',
    departmentName: '',
    timeText: '—',
    feeText: '—',
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const feeFen = options.feeFen ? Number(options.feeFen) : null
    const rawTime = options.appointmentTime ? decodeURIComponent(options.appointmentTime) : ''
    this.setData({
      orderNo: decodeURIComponent(options.orderNo || ''),
      statusLabel: STATUS_LABELS[options.status] || options.status || '—',
      isConfirmed: options.status === 'CONFIRMED',
      patientName: decodeURIComponent(options.patientName || ''),
      doctorName: decodeURIComponent(options.doctorName || ''),
      departmentName: decodeURIComponent(options.departmentName || ''),
      timeText: rawTime ? formatDate(rawTime, 'YYYY-MM-DD HH:mm') : '—',
      feeText: formatMoney(feeFen),
    })
  },

  onBackHome() {
    // 成功页是流程终点，用 switchTab 回主 tab；navigateBack 会一路退回到科室列表。
    wx.switchTab({ url: '/pages/appointment/appointment' })
  },

  onGoMine() {
    wx.switchTab({ url: '/pages/mine/mine' })
  },
})
