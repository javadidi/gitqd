const app = getApp()

// 确认预约信息（T22 卡片 640 行「确认预约信息：确认体检时间、套餐、费用等」/
// PRD §3.8 第 213 行同一句）。
//
// 这一页只做两件事：收体检日期、把上一跳带过来的套餐与费用原样摆出来给人复核。
// 提交不在这页——PRD 把「体检须知」排在「确认预约信息」之后（214 行）、
// 「预约成功」再之后（215 行），所以真正的 POST 落在须知页的最后一个按钮上，
// 顺序与规格一致：确认 → 阅读须知 → 提交 → 成功。
//
// 费用是展示项，不是入参：它从上一跳的详情接口带来（源头是 physical_package.price_fen），
// 提交时一个金额字段都不发（见 PhysicalAppointmentCreateRequest 的推导）。
// 这里刻意不用 encodeURIComponent 之外的任何加工，也不在前端算钱——分/元换算只在 formatMoney。
Page({
  data: {
    packageId: null,
    patientId: null,
    patientName: '',
    packageName: '',
    priceText: '',
    appointmentDate: '',
    today: '',
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const now = new Date()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    this.setData({
      packageId: options.packageId,
      patientId: options.patientId,
      patientName: decodeURIComponent(options.patientName || ''),
      packageName: decodeURIComponent(options.packageName || ''),
      priceText: decodeURIComponent(options.priceText || ''),
      today: now.getFullYear() + '-' + month + '-' + day,
    })
  },

  onDateChange(e) {
    this.setData({ appointmentDate: e.detail.value })
  },

  onNext() {
    if (!this.data.appointmentDate) {
      wx.showToast({ title: '请选择体检日期', icon: 'none' })
      return
    }
    const query = [
      `packageId=${this.data.packageId}`,
      `patientId=${this.data.patientId}`,
      `patientName=${encodeURIComponent(this.data.patientName)}`,
      `packageName=${encodeURIComponent(this.data.packageName)}`,
      `priceText=${encodeURIComponent(this.data.priceText)}`,
      `appointmentDate=${this.data.appointmentDate}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/physical/notice?${query}` })
  },
})
