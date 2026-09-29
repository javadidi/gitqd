const app = getApp()
const { get } = require('../../utils/request')
const { formatMoney, relationLabel } = require('../../utils/format')

// 体检套餐列表（T22 卡片 637 行「选择体检人」+ 638 行「体检套餐列表：展示可预约的体检套餐」；
// PRD §3.8 第 210–211 行同两步，§6.1 第 522 行把它们列成两个页面名）。
//
// 与 T14/T20/T21 一样把「选择体检人」内联在这一页：PRD 把它单列成一步，
// 但它是流程的第一步而不是一个有独立数据的页面，拆出去只会多一次无内容的跳转。
//
// 首版这一页的套餐列表是空的，而且这是正确的：
// physical_package 在 seed 里零行，它的生产者是 T27 后台「体检套餐管理」
// （PRD 406–408 行，App.tsx:73 的占位路由已写明 card="T27"）。
// 所以这里不塞假套餐让页面好看，空态文案也只说"由后台维护"这一件事实。
Page({
  data: {
    loadingPatients: false,
    loadingPackages: false,
    patients: [],
    packages: [],
    patientId: null,
    patientName: '',
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadPatients()
    this.loadPackages()
  },

  async loadPatients() {
    this.setData({ loadingPatients: true })
    try {
      const rows = await get('/user/patients')
      const picked = (rows || []).map((item) => ({
        id: item.id,
        name: item.name,
        cardNo: item.cardNo,
        relationLabel: relationLabel(item.relation),
      }))
      const stillThere = picked.some((p) => p.id === this.data.patientId)
      this.setData({
        patients: picked,
        // 刚选的人如果被删了（T08 有删除入口），回退到不选中而不是指向一个不存在的 id
        patientId: stillThere ? this.data.patientId : null,
        patientName: stillThere ? this.data.patientName : '',
      })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loadingPatients: false })
    }
  },

  async loadPackages() {
    this.setData({ loadingPackages: true })
    try {
      const rows = await get('/user/physical-packages')
      this.setData({
        packages: (rows || []).map((item) => ({
          packageId: item.packageId,
          name: item.name || '—',
          priceText: formatMoney(item.priceFen),
          targetAudience: item.targetAudience || '',
        })),
      })
    } catch (err) {
      // 同上
    } finally {
      this.setData({ loadingPackages: false })
    }
  },

  onPickPatient(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.patients.find((p) => p.id === id)
    this.setData({ patientId: id, patientName: found ? found.name : '' })
  },

  onAddPatient() {
    wx.navigateTo({ url: '/pages/patient/edit' })
  },

  onOpenPackage(e) {
    if (!this.data.patientId) {
      wx.showToast({ title: '请先选择体检人', icon: 'none' })
      return
    }
    const id = e.currentTarget.dataset.id
    // 体检人名字带进详情页只是为了让患者一路看得见"这是给谁约的"，
    // 提交时后端仍按 patientId 自己判归属（见 PhysicalAppointmentService）。
    const query = [
      `packageId=${id}`,
      `patientId=${this.data.patientId}`,
      `patientName=${encodeURIComponent(this.data.patientName)}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/physical/package?${query}` })
  },

  onGoReports() {
    // 体检报告复用 T17 的两个端点与两个页面，只带一个 type=PHYSICAL 过去
    // （钩子是 T17 留在 ReportType.isQueryable 那一行的，本卡放开它，不另开一套报告页）。
    wx.navigateTo({ url: '/pages/report/list?type=PHYSICAL' })
  },

  onGoRecords() {
    wx.navigateTo({ url: '/pages/physical/list' })
  },
})
