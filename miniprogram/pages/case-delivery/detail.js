const app = getApp()
const { get } = require('../../utils/request')
const { formatDate, deliveryStatusLabel } = require('../../utils/format')

// 病案邮寄申请详情（PRD 316 行「申请详情 — 查看申请详情及物流状态」）。
//
// 「物流状态」在这套数据模型里只有两列可回：status（PENDING/SHIPPED/DELIVERED，V1:334）
// 与 tracking_no（快递单号，V1:335）。没有承运商对接，所以看不到轨迹——
// PRD 619 行那一格写的「物流查询」因此不是第四个端点，详情就是它的出口。
// 首版 trackingNo 必然为空（填它的是后台，卡片 720 行），这里显示「—」而不是把这一行藏掉，
// 因为患者要知道"还没有单号"这件事本身。
Page({
  data: {
    loading: false,
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.id = options.id ? Number(options.id) : null
    this.loadDetail()
  },

  async loadDetail() {
    if (!this.id) {
      wx.showToast({ title: '缺少申请编号', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const item = await get(`/user/case-deliveries/${this.id}`)
      if (!item) {
        return
      }
      this.setData({
        detail: {
          inpatientName: item.inpatientName || '—',
          inpatientNo: item.inpatientNo || '—',
          recipientName: item.recipientName || '—',
          address: item.address || '—',
          statusLabel: deliveryStatusLabel(item.status),
          trackingNo: item.trackingNo || '—',
          timeText: formatDate(item.createdAt),
        },
      })
    } catch (err) {
      // 5001 由 request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBackList() {
    wx.navigateBack({
      fail: () => wx.redirectTo({ url: '/pages/case-delivery/list' }),
    })
  },
})
