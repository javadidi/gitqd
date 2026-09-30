const app = getApp()
const { get } = require('../../utils/request')

// 病案配送须知（T23 卡片 661 行 / PRD 240 行「病案配送须知 — 展示病案邮寄规则和注意事项」）。
//
// T23 建这一页时 delivery_notice 表还不存在，所以当时把四条规则写在本地，
// 并在注释里指明"生产者属 T27"。T27 落了两张单行须知表，这四条正文都进了种子
// （原先"标题 + 说明"两段在同一行里用冒号/分号/逗号相连），于是这一页改成读
// GET /user/notices/delivery ——正文的主人是后台那张表，不是这个文件。
//
// 和预约须知同一条取舍：content 按 \n 拆行，不拆回 title/desc（V7 的列注释约定的就是"一行一条"）。
Page({
  data: {
    noticeTitle: '病案配送须知',
    lines: [],
    noticeLoading: true,
    noticeError: '',
  },

  onLoad() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadNotice()
  },

  async loadNotice() {
    try {
      const notice = await get('/user/notices/delivery')
      if (!notice) {
        this.setData({ lines: [], noticeLoading: false })
        return
      }
      this.setData({
        noticeTitle: notice.title || '病案配送须知',
        lines: String(notice.content || '')
          .split('\n')
          .map((line) => line.trim())
          .filter((line) => line !== ''),
        noticeLoading: false,
      })
    } catch (err) {
      // request.js 已统一 toast；读不到须知不该挡患者提交申请
      this.setData({ noticeLoading: false, noticeError: '须知加载失败，可稍后重试' })
    }
  },

  onNext() {
    wx.navigateTo({ url: '/pages/case-delivery/apply' })
  },

  onBackList() {
    wx.navigateBack({
      fail: () => wx.redirectTo({ url: '/pages/case-delivery/list' }),
    })
  },
})
