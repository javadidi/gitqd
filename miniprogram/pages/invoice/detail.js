const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, formatDate, invoiceStatusLabel } = require('../../utils/format')

// 票据详情（T19 卡片 584 行「票据详情：查看电子发票详情」/ PRD §3.3.8 第 152 行）。
//
// PRD 152 行原话还有「及下载」两个字，本卡刻意不做：卡片 586 行红线写着
// 「不做真实开票（二期做）；首版仅模拟开票流程」，而首版既不产生文件也没有地方存文件
// （invoice 只有 V1:237-246 那七列，没有 URL/文件列）。
// 于是"下载"按钮点下去只能是一个假动作 —— 宁少勿假，不做，已记进 WORK_LOG 遗留项。
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
      const item = await get('/user/invoices/' + id)
      this.setData({
        detail: {
          invoiceNo: item.invoiceNo || '—',
          invoiceCode: item.invoiceCode || '—',
          amountText: formatMoney(item.amountFen),
          patientName: item.patientName || '—',
          paymentOrderNo: item.paymentOrderNo || '—',
          statusLabel: invoiceStatusLabel(item.status),
          tone: item.status === 'ISSUED' ? 'ok' : 'pending',
          timeText: item.issuedAt ? formatDate(item.issuedAt, 'YYYY-MM-DD HH:mm:ss') : '—',
          items: (item.items || []).map((row) => ({
            name: row.name || '—',
            amountText: formatMoney(row.amountFen),
          })),
        },
      })
    } catch (err) {
      // 5001（不是你的票 / 没这张票）等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBack() {
    wx.navigateBack()
  },
})
