const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, followUpStatusLabel, followUpStatusTone } = require('../../utils/format')

// 复诊详情（T20 卡片 604 行「复诊详情：查看复诊详情及配药信息」/ PRD §3.6 第 192 行同一句）。
//
// 页面上<b>没有「配药信息」这一栏</b>，与 T18 病历详情页刻意不留「医嘱：」空标题同一条口径：
// 卡片 606 行红线写着「不做真实开药（二期做）；首版仅模拟流程」，
// follow_up 表（V1:312-323）也没有任何一列能装药名/剂量/处方，
// 留一个空栏目就等于假装有这个功能。替代它的是一行实话（wxml 里 .fvd-note 那段）。
// 完整四路证据记在 FollowUpDetailResponse 的类注释。
//
// 这一页唯一的入口是上一页（申请成功页），因为 PRD §9.1 第 615 行只给了两个接口，
// 没有列表接口 —— 患者离开这条流程后回不来，这条缺口记在 FollowUpController 与 WORK_LOG。
Page({
  data: {
    loading: false,
    id: null,
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ id: options.id })
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const item = await get('/user/follow-ups/' + id)
      this.setData({
        detail: {
          patientName: item.patientName || '—',
          departmentName: item.departmentName || '—',
          doctorName: item.doctorName || '—',
          disease: item.disease || '—',
          statusLabel: followUpStatusLabel(item.status),
          statusTone: followUpStatusTone(item.status),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm:ss') : '—',
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
