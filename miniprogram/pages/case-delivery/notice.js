const app = getApp()

// 病案配送须知（T23 卡片 661 行 / PRD 240 行「病案配送须知 — 展示病案邮寄规则和注意事项」）。
//
// 四条文案每一条都指着一条已经存在的事实（表列、卡片红线或后台归属），
// 不写"病案邮寄需本人持身份证办理"这类看着像常识的医学/行政条款——
// 全仓没有任何出处说过这件事，而 PRD 438 行「病案配送须知管理」（属 T27）
// 才是这些规则正文的生产者，首版那张表还没有内容可展示（与 T22 体检须知同一条纪律）。
Page({
  data: {
    items: [
      {
        title: '首版为申请流程演示，不含真实寄递',
        desc: '提交后不会产生快递，也不收取任何费用',
      },
      {
        title: '提交后本单状态为「待处理」',
        desc: '寄出与签收由医院后台更新，患者侧不能自行推进',
      },
      {
        title: '快递单号由医院填写，可在申请详情查看',
        desc: '本系统不对接承运商，看不到物流轨迹',
      },
      {
        title: '本版本不上传证件照片',
        desc: '申请只登记收件人与收件地址',
      },
    ],
  },

  onLoad() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
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
