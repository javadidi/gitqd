const app = getApp()
const { get } = require('../../utils/request')

Page({
  data: {
    isLoggedIn: false,
    loading: false,
    keyword: '',
    departments: [],
    searched: false,
  },

  // 用 onShow 而不是 onLoad：这是 tabBar 页，登录完切回来要能看到科室
  onShow() {
    const isLoggedIn = !!app.globalData.token
    this.setData({ isLoggedIn })
    if (isLoggedIn) {
      this.loadDepartments()
    }
  },

  async loadDepartments() {
    this.setData({ loading: true })
    try {
      const keyword = this.data.keyword.trim()
      // keyword 为空串时不传该参数：后端 trimToNull 后等同于不搜，但显式不传更贴近"没有筛选条件"
      const list = await get('/user/departments', keyword ? { keyword } : undefined)
      this.setData({ departments: list || [], searched: true })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast，页面不重复提示
      this.setData({ searched: true })
    } finally {
      this.setData({ loading: false })
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  // 搜索走 input 的 confirm（键盘"搜索"键），不做逐字符请求：
  // 每敲一个字打一次后端在弱网下会把列表刷得抖动，也没有任何规格要求实时联想。
  onSearch() {
    if (!this.data.isLoggedIn) return
    this.loadDepartments()
  },

  onClearKeyword() {
    this.setData({ keyword: '' })
    if (this.data.isLoggedIn) {
      this.loadDepartments()
    }
  },

  onDeptTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/department/detail?id=${id}` })
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/login' })
  },
})
