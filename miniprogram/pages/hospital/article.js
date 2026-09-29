const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 内容详情页（PRD 266 行「文章详情」+ 262 行「预约流程」那一页）。
//
// 一页服务两种内容是刻意的：两者的形状完全一样（标题 + 正文 + 一个时间），
// 而数据来源靠 URL 上的 kind 分开——不带 kind 走健康文章接口，带 kind=guide 走指南接口。
// 附录 B「列表筛选/搜索/分页是否进 URL」的同一条精神：能刷新、能分享。
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
    this.kind = options.kind === 'guide' ? 'guide' : 'health'
    this.loadDetail()
  },

  async loadDetail() {
    if (!this.id) {
      wx.showToast({ title: '缺少文章编号', icon: 'none' })
      return
    }
    const path = this.kind === 'guide'
      ? `/user/guides/${this.id}`
      : `/user/health-articles/${this.id}`
    this.setData({ loading: true })
    try {
      const item = await get(path)
      if (!item) {
        return
      }
      this.setData({
        detail: {
          title: item.title || '',
          content: item.content || '',
          timeText: formatDate(item.publishTime || item.updatedAt),
          hasTime: Boolean(item.publishTime || item.updatedAt),
        },
      })
    } catch (err) {
      // 5001（没这篇 / 已下架）由 request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },
})
