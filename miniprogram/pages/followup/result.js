const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, followUpStatusLabel, followUpStatusTone } = require('../../utils/format')

// 复诊申请成功（PRD §3.6 第 191 行「复诊申请成功 — 申请成功提示页」，§6.1 第 520 行同列这一页）。
//
// 与 T19 的开票成功页同一做法：不接上一页 POST 返回的那份数据，而是拿 followUpId 重新 GET 一次。
// 创建接口确实把整条复诊回出来了，但"成功页停一会儿再看"时应当显示与库里一致的当前值 ——
// 这一点对本卡比对 T19 更重要：状态以后会被院内侧推进，成功页若冻在提交那一刻的快照上，
// 患者看到的「待处理」可能就是假的。
//
// 这一页同时是「复诊详情」的唯一入口：PRD §9.1 第 615 行只给两个接口（创建、详情），
// 没有列表，所以离开这条流程就没有第二条路回到详情。缺口记在 FollowUpController 类注释
// 与 WORK_LOG 的遗留 TODO，不在这里偷偷加一个"我的复诊"列表页。
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
          // 配色跟着状态码走，不跟着"首版只会写 PENDING"这件事走：
          // 状态一旦有生产者推进到 COMPLETED，页面不会把「已完成」渲染成待处理的橙色。
          statusTone: followUpStatusTone(item.status),
          timeText: item.createdAt ? formatDate(item.createdAt, 'YYYY-MM-DD HH:mm') : '—',
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onGoDetail() {
    wx.redirectTo({ url: '/pages/followup/detail?id=' + this.data.id })
  },

  onBackHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
