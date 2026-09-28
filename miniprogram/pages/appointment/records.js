const app = getApp()
const { get } = require('../../utils/request')
const {
  formatMoney, formatDate, timeSlotLabel,
  appointmentStatusLabel, appointmentGroup,
} = require('../../utils/format')

// 预约挂号记录列表（T13 卡片 475 行 / PRD 292 行）。
//
// 一次拉全量、分组在前端做：后端只认单个 status 参数（PRD §9.1 行 610 那一格的「预约列表」
// 没要求服务端筛选），而"待就诊"这一组本身就跨两个状态码
// （PENDING_PAYMENT + CONFIRMED），走服务端筛选反而要发两次请求再拼。
// 一次拉完 + 本地过滤，切 tab 不重发请求，也和 T08/T09 列表页"onShow 重拉保证不过期"一致。
Page({
  data: {
    loading: false,
    tab: 'pending',
    tabs: [
      { key: 'pending', label: '待就诊' },
      { key: 'completed', label: '已完成' },
      { key: 'cancelled', label: '已取消' },
      { key: 'all', label: '全部' },
    ],
    all: [],
    rows: [],
    counts: { pending: 0, completed: 0, cancelled: 0, all: 0 },
  },

  onShow() {
    // 用 onShow 不是 onLoad：从详情页退号回来必须看到新状态，
    // 否则列表会停在"已确认"上，患者以为退号没生效（T08/T09 同一条理由）。
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadRecords()
  },

  async loadRecords() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/appointments')
      const all = (list || []).map((item) => ({
        id: item.id,
        orderNo: item.orderNo,
        patientName: item.patientName || '—',
        departmentName: item.departmentName || '—',
        doctorName: item.doctorName || '—',
        timeText: item.appointmentTime
          ? formatDate(item.appointmentTime, 'YYYY-MM-DD') + ' ' + timeSlotLabel(item.timeSlot)
          : '—',
        status: item.status,
        statusLabel: appointmentStatusLabel(item.status),
        group: appointmentGroup(item.status),
        feeText: formatMoney(item.feeFen),
      }))
      this.setData({
        all,
        counts: {
          pending: all.filter((r) => r.group === 'pending').length,
          completed: all.filter((r) => r.group === 'completed').length,
          cancelled: all.filter((r) => r.group === 'cancelled').length,
          all: all.length,
        },
      })
      this.applyTab(this.data.tab)
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  applyTab(key) {
    const all = this.data.all
    this.setData({
      tab: key,
      rows: key === 'all' ? all : all.filter((r) => r.group === key),
    })
  },

  onTabTap(e) {
    this.applyTab(e.currentTarget.dataset.key)
  },

  onOpen(e) {
    wx.navigateTo({ url: `/pages/appointment/record-detail?id=${e.currentTarget.dataset.id}` })
  },

  onGoAppointment() {
    wx.switchTab({ url: '/pages/appointment/appointment' })
  },
})
