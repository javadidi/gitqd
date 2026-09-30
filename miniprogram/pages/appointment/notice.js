const app = getApp()
const { get } = require('../../utils/request')

// 预约须知（PRD 78 行「展示挂号规则、退号规则、注意事项」；页面清单见 §6.1 第 511 行）。
//
// 文案的主人在 T27 换了：这四条原先硬编码在本文件里（旧注释自己写着"等 T27 的 CMS 落地后
// 本页改成读接口取文案"），现在它们是 appointment_notice 表里的那一行，由后台
// 「预约须知管理」（PRD 4.5.10）编辑，患者侧经 GET /user/notices/appointment 读同一行。
// 所以本文件不再自带任何条款——包括那句过时的「退号…当前版本暂未开放」：
// T13 早就开放了退号，库里那份是按 T13 更正过的，本地这份留着就是骗患者。
//
// content 是「一行一条」的纯文本（V7 给这一列的注释就是这么约定的），
// 这里只按 \n 拆行，不再拆回 title/desc 两段：按逗号再切一刀等于替规格编一条分隔规则，
// 而且"标题里本来就有逗号"的行会当场切错。
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
    noticeTitle: '预约须知',
    lines: [],
    noticeLoading: true,
    noticeError: '',
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
    this.loadNotice()
  },

  async loadNotice() {
    try {
      const notice = await get('/user/notices/appointment')
      if (!notice) {
        // 单行表可能一行都还没建过（只跑迁移、没跑 --seed 的库就是这样）。
        // 这时页面显示空态，不把旧的本地四条端回来兜底——那样后台的编辑页又是摆设。
        this.setData({ lines: [], noticeLoading: false })
        return
      }
      this.setData({
        noticeTitle: notice.title || '预约须知',
        lines: String(notice.content || '')
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line !== ''),
        noticeLoading: false,
      })
    } catch (err) {
      // request.js 已统一 toast。这里只把页面从"加载中"里放出来：
      // 须知读不到不该挡患者挂号，"已阅读，下一步"照旧可点。
      this.setData({ noticeLoading: false, noticeError: '须知加载失败，可稍后重试' })
    }
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
