const app = getApp()

// 预约须知（PRD 78 行「展示挂号规则、退号规则、注意事项」）。
//
// 页面本身是规格要的（PRD 78 / §3.3.1 第 4 步、§6.1 第 511 行的页面清单），
// 但**内容在这里没有任何数据来源**：库里没有"须知"表（announcement 是公告表，
// type 只有 NOTICE/ACTIVITY），后台那个「预约须知管理」页标的是 T27（App.tsx:97）。
// 所以首版这份文案只写 PRD 84-87 那四条业务规则的原话，
// 一条也不自行补充——"提前 30 分钟到院""过期不予受理""爽约三次进黑名单"这类
// 看着像常识的条款，本仓库没有任何出处，写上去就是编造（附录 D「宁少勿假」）。
// T27 的 CMS 落地后，这一页应改为从接口取文案，本页只留渲染。
Page({
  data: {
    scheduleId: null,
    doctorId: null,
    doctorName: '',
    departmentName: '',
    date: '',
    timeSlot: '',
    slotLabel: '',
    feeFen: null,
    rules: [
      { title: '同一就诊人同一时段只能挂一个号', desc: '重复提交会被拒绝，不会多占号源' },
      { title: '预约需在规定的时间内完成支付', desc: '本页提交时会同步发起支付' },
      // 退号按 PRD 84 行写着，但入口要等 T13；这里如实说"暂未开放"，
      // 不能让患者照着一句不存在的指引去找按钮。
      { title: '退号', desc: '入口在「我的 - 预约挂号记录」，当前版本暂未开放' },
      { title: '挂号记录可在个人中心查看', desc: '「我的」页面内查看' },
    ],
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      scheduleId: options.scheduleId || null,
      doctorId: options.doctorId || null,
      doctorName: decodeURIComponent(options.doctorName || ''),
      departmentName: decodeURIComponent(options.departmentName || ''),
      date: options.date || '',
      timeSlot: options.timeSlot || '',
      slotLabel: decodeURIComponent(options.slotLabel || ''),
      feeFen: options.feeFen ? Number(options.feeFen) : null,
    })
  },

  onNext() {
    const query = [
      `scheduleId=${this.data.scheduleId}`,
      `doctorId=${this.data.doctorId}`,
      `doctorName=${encodeURIComponent(this.data.doctorName)}`,
      `departmentName=${encodeURIComponent(this.data.departmentName)}`,
      `date=${this.data.date}`,
      `timeSlot=${this.data.timeSlot}`,
      `slotLabel=${encodeURIComponent(this.data.slotLabel)}`,
      `feeFen=${this.data.feeFen == null ? '' : this.data.feeFen}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/appointment/patient?${query}` })
  },
})
