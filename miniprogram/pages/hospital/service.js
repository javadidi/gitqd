// 医院服务入口（T24）。
//
// PRD 6.1 的 526 行把医院服务列成九页（医院介绍、选择院区、医院导航、地图导航、院内导航、
// 楼层索引、预约流程、文章详情、停诊通知），但没有任何一处规定"从哪儿进"：
// 首页八个快捷位是 T06 定下的（PRD 61 行只说「核心功能入口（预约挂号、门诊充值…等）」），
// 个人中心的分组里也没有医院服务。所以这一页只是一个四行链接的入口，
// 本身不承载任何内容——四行指向的页面全部是 PRD 点名的页面，
// 没有一行是替规格发明的功能。
Page({
  data: {
    entries: [
      { key: 'profile', label: '医院介绍', desc: '医院简介与荣誉资质', url: '/pages/hospital/profile' },
      { key: 'guides', label: '就医指南', desc: '预约挂号流程说明', url: '/pages/hospital/guides' },
      { key: 'articles', label: '健康百科', desc: '健康科普文章', url: '/pages/hospital/articles' },
      { key: 'notices', label: '停诊通知', desc: '医生停诊与调班公告', url: '/pages/hospital/notices' },
    ],
  },

  onOpen(e) {
    wx.navigateTo({ url: e.currentTarget.dataset.url })
  },
})
