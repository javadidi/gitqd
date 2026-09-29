const app = getApp()
const { get } = require('../../utils/request')
const { formatDate } = require('../../utils/format')

// 医院介绍（T24 卡片 678 行 / J53「医院介绍 → 内容正确」/ PRD 252 行「展示医院简介、荣誉资质等」）。
//
// 这一页只有两栏：简介正文与荣誉资质。不显示地址、电话、床位数、建院年份、科室数——
// 规格一个字都没给过，而这里陈述的是一家医院的事实，编一条比留白坏得多
// （与 T21「产品代码永不写 report」、T22「须知不编医学条款」同一条纪律）。
//
// 首版后台还没人编辑过简介（生产者是 T27 卡片 744 行），所以 data 为 null、页面显示空态。
Page({
  data: {
    loading: false,
    profile: null,
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadProfile()
  },

  async loadProfile() {
    this.setData({ loading: true })
    try {
      const item = await get('/user/hospital-profile')
      if (!item) {
        this.setData({ profile: null })
        return
      }
      this.setData({
        profile: {
          title: item.title || '',
          intro: item.intro || '',
          honors: item.honors || '',
          timeText: formatDate(item.updatedAt),
        },
      })
    } catch (err) {
      // request.js 已统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },
})
