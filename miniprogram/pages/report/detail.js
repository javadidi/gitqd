const app = getApp()
const { get } = require('../../utils/request')
const {
  formatDate, reportTypeLabel, reportItemsText,
} = require('../../utils/format')

// 报告详情（T17 卡片 550 行「报告详情：查看报告详细内容」/ PRD §3.4.1 第 163 行）。
//
// 展示的六个字段就是 PRD 589 行数据字典那一行：
// 「报告 | 报告ID、就诊人ID、类型、检查项目、结果、时间」，另加列表带过来的报告编号。
//
// 这里刻意不解释「检查项目」的结构：后端是原样透传的（V1:207 那一列
// 只有"检查项目"四个字，形状没有规格出处），所以这一栏走 utils/format.js 的
// reportItemsText 兜底渲染。产品如果将来定了形状，改的是格式化函数与后端 DTO，
// 不是这个页面里的分支。
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
      const item = await get('/user/reports/' + id)
      const itemsText = reportItemsText(item.items)
      this.setData({
        detail: {
          reportNo: item.reportNo || '—',
          typeLabel: reportTypeLabel(item.type),
          patientName: item.patientName || '—',
          timeText: item.reportTime ? formatDate(item.reportTime, 'YYYY-MM-DD HH:mm:ss') : '—',
          itemsText,
          // 报告和结果都可能是空的（V1:208/209 两列都可空），空就是"还没出"，
          // 不能拿昨天的日期或空白页冒充一份已有结论的报告
          hasItems: !!itemsText,
          result: item.result || '',
        },
      })
    } catch (err) {
      // 5001（不是你的报告 / 体检报告不归本卡读）等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
