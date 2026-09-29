const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, reportItemsText } = require('../../utils/format')

// 套餐详情（T22 卡片 639 行「套餐详情：查看套餐详细内容」/ PRD §3.8 第 212 行
// 「套餐详情 — 查看体检套餐详细内容（检查项目、价格等）」）。
//
// items 用 T17 那份 reportItemsText 容错渲染：后端把 JSON 列原样透传、不解释形状
// （没有任何规格定义过它的键，见 PhysicalPackageDetailResponse 类注释），
// 所以前端也不假装认识它——数组逐项取 name，取不到就整项转字符串，
// 什么都没有就显示「—」，绝不编一份常见体检项目清单上去。
Page({
  data: {
    loading: false,
    packageId: null,
    patientId: null,
    patientName: '',
    detail: null,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({
      packageId: options.packageId,
      patientId: options.patientId,
      patientName: decodeURIComponent(options.patientName || ''),
    })
    this.loadDetail(options.packageId)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const item = await get('/user/physical-packages/' + id)
      this.setData({
        detail: {
          name: item.name || '—',
          priceText: formatMoney(item.priceFen),
          targetAudience: item.targetAudience || '—',
          itemsText: reportItemsText(item.items) || '—',
        },
      })
    } catch (err) {
      // 5001 等消息由 request.js toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onNext() {
    if (!this.data.detail) {
      wx.showToast({ title: '套餐信息还没读到，请稍候', icon: 'none' })
      return
    }
    const query = [
      `packageId=${this.data.packageId}`,
      `patientId=${this.data.patientId}`,
      `patientName=${encodeURIComponent(this.data.patientName)}`,
      `packageName=${encodeURIComponent(this.data.detail.name)}`,
      `priceText=${encodeURIComponent(this.data.detail.priceText)}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/physical/confirm?${query}` })
  },
})
