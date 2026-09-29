const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, reportTypeLabel, REPORT_TYPE_LABELS } = require('../../utils/format')

// 报告列表（T17 卡片 549 行「报告列表：展示报告列表」/ PRD §3.4.1 第 162 行）。
//
// 类型从 URL 进来（?type=LAB|IMAGING），不是页面内部状态：附录 B 第 10 条
// 「列表筛选/搜索/分页是否进 URL」。进了 URL 才有两个实际好处——
// 从详情返回不丢筛选、这一页可以被直接打开或转发。
// 上一跳（选择报告类型页）负责保证它是两个合法值之一；手改 URL 传错值时
// 后端回 400，request.js toast 之后列表为空，走的就是下面这个空态，不会白屏。
//
// 只在 onLoad 拉一次、从详情返回不重拉：报告是医院推过来的数据，患者自己不会在这条链上
// 写东西（与 T08 就诊人列表"刚添加完必须立刻看到"的情形相反），所以没有重拉的必要。
Page({
  data: {
    loading: false,
    type: '',
    typeLabel: '',
    rows: [],
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    const type = options.type || ''
    this.setData({ type, typeLabel: reportTypeLabel(type) })
    // 导航栏标题跟着类型走（T10 科室详情页同一个做法）：患者从详情退回来时，
    // 抬头就能确认自己还在「检验报告」这一层，而不是混着看。
    wx.setNavigationBarTitle({ title: REPORT_TYPE_LABELS[type] || '报告查询' })
    this.loadReports()
  },

  async loadReports() {
    this.setData({ loading: true })
    try {
      const list = await get('/user/reports?type=' + this.data.type)
      this.setData({
        rows: (list || []).map((item) => ({
          reportId: item.reportId,
          reportNo: item.reportNo || '—',
          typeLabel: reportTypeLabel(item.type),
          patientName: item.patientName || '—',
          // report_time 可空（V1:209），没出报告时间就显示 —，不显示今天的日期冒充
          timeText: item.reportTime ? formatDate(item.reportTime, 'YYYY-MM-DD HH:mm') : '—',
        })),
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast；这里不清空也不重写 rows，
      // 免得一次网络抖动把已经看到的列表抹掉
    } finally {
      this.setData({ loading: false })
    }
  },

  onOpen(e) {
    wx.navigateTo({ url: '/pages/report/detail?id=' + e.currentTarget.dataset.id })
  },

  onBackToType() {
    wx.navigateBack()
  },

  onGoHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
