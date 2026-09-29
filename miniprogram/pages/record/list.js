const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 病历列表（T18 卡片 565 行「病历列表：展示历史病历」/ PRD §3.5 第 176 行）。
//
// 这一页没有任何筛选参数：medical_record 没有分类列（V1:220-232 六列里没有类型），
// PRD 614 行的接口概览「病历列表、病历详情」也没给参数。与 T17 恰好相反 ——
// 那边 ?type= 是卡片 548 行明确要求的，这边硬造一个"按医生筛"就是发明需求。
//
// 只在 onLoad 拉一次、从详情返回不重拉：与 T17 列表同理（病历由院内出具，
// 患者自己不会在这条链上写东西，没有"刚添加完必须立刻看到"的诉求）。
Page({
  data: {
    loading: false,
    rows: [],
  },

  onLoad() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadRecords()
  },

  async loadRecords() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/medical-records')
      this.setData({
        rows: (list || []).map((item) => ({
          recordId: item.recordId,
          recordNo: item.recordNo || '—',
          patientName: item.patientName || '—',
          // 医生行被软删时后端给 null（不拿 id 冒充名字），这里兜成 —
          doctorName: item.doctorName || '—',
          // record_time 是 NOT NULL（V1:227），所以这一项总有值；仍留兜底是因为它来自接口
          timeText: item.recordTime ? formatDate(item.recordTime, 'YYYY-MM-DD') : '—',
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast；不清空 rows，避免网络抖动抹掉已看到的列表
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: '/pages/record/detail?id=' + e.currentTarget.dataset.id })
  },

  onGoHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
