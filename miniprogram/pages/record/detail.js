const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 病历详情（T18 卡片 566 行「病历详情：查看病历详细内容（诊断、处方、医嘱等）」/
// PRD §3.5 第 177 行同一句）。
//
// 展示的六个字段全部可追到 PRD 590 行数据字典：
// 「病历 | 病历ID、就诊人ID、诊断、处方、医生、时间」，另加病历编号（列存在，理由同 T17）。
//
// 卡片与 PRD 177 行还点名了「医嘱」，但 PRD 590 行的数据字典与 V1:220-232 的建表语句
// 都没有这一列 —— 本卡的取舍是不加列、不显示、不编造（理由与
// 两路原文的冲突记在 MedicalRecordDetailResponse 的类注释和 WORK_LOG 里）。
// 所以这里刻意没有留一个空着的「医嘱」栏目：留一个永远为空的标题，
// 等于对患者假装这功能有了。
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
      const item = await get('/user/medical-records/' + id)
      this.setData({
        detail: {
          recordNo: item.recordNo || '—',
          patientName: item.patientName || '—',
          doctorName: item.doctorName || '—',
          timeText: item.recordTime ? formatDate(item.recordTime, 'YYYY-MM-DD HH:mm') : '—',
          // 两列都可空（V1:225/226），Jackson NON_NULL 会让键整个消失 ⇒ 必须 || '—'
          diagnosis: item.diagnosis || '',
          prescription: item.prescription || '',
        },
      })
    } catch (err) {
      // 5001（不是你的病历 / 没这条）等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
