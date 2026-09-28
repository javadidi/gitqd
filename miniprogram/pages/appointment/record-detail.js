const app = getApp()
const { get, post } = require('../../utils/request')
const {
  formatMoney, formatDate, timeSlotLabel,
  appointmentStatusLabel, appointmentGroup, appointmentCancellable,
} = require('../../utils/format')

// 预约挂号详情（T13 卡片 476 行 / PRD 293 行「查看预约详细信息（支持退号操作）」）。
Page({
  data: {
    loading: false,
    id: null,
    detail: null,
    canCancel: false,
    canceling: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ id: options.id })
    this.loadDetail(options.id)
  },

  onShow() {
    // 退号后从别处回来也要重拉，理由同列表页
    if (this.data.id && this.data.detail) {
      this.loadDetail(this.data.id)
    }
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const item = await get(`/user/appointments/${id}`)
      this.setData({
        detail: {
          orderNo: item.orderNo || '—',
          patientName: item.patientName || '—',
          departmentName: item.departmentName || '—',
          doctorName: item.doctorName || '—',
          timeText: item.appointmentTime
            ? formatDate(item.appointmentTime, 'YYYY-MM-DD HH:mm') + '（' + timeSlotLabel(item.timeSlot) + '）'
            : '—',
          status: item.status,
          statusLabel: appointmentStatusLabel(item.status),
          group: appointmentGroup(item.status),
          feeText: formatMoney(item.feeFen),
        },
        canCancel: appointmentCancellable(item.status),
      })
    } catch (err) {
      // 后端 message 已由 request.js toast（2004 会走到这里）
    } finally {
      this.setData({ loading: false })
    }
  },

  onCancel() {
    if (!this.data.canCancel || this.data.canceling) return
    const paid = this.data.detail.status === 'CONFIRMED'
    // 二次确认文案必须区分"付过钱"和"没付钱"：卡片 479 行「退款需审核（二期做）」，
    // 已支付的单退号只是提交退款申请，钱不会立刻回来。写"将退还"就是撒谎。
    wx.showModal({
      title: '确认退号',
      content: paid
        ? '退号后本次预约取消，挂号费将提交退款审核（到账时间以医院审核为准）。确定退号？'
        : '退号后本次待支付预约将被取消。确定退号？',
      confirmText: '确认退号',
      cancelText: '再想想',
      success: (res) => {
        if (res.confirm) this.doCancel()
      },
    })
  },

  async doCancel() {
    this.setData({ canceling: true })
    try {
      const result = await post(`/user/appointments/${this.data.id}/cancel`, {})
      // 措辞跟着后端返回走，不在前端自行宣称"已退款"
      wx.showToast({
        title: result.refundRequired ? '已退号，退款申请已提交' : '已退号',
        icon: 'success',
      })
      this.loadDetail(this.data.id)
    } catch (err) {
      // 2006（已就诊/已取消）等消息由 request.js toast
    } finally {
      this.setData({ canceling: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
