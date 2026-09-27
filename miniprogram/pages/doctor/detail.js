const app = getApp()
const { get } = require('../../utils/request')
const { timeSlotLabel, weekdayLabel } = require('../../utils/format')

Page({
  data: {
    loading: false,
    doctorId: null,
    name: '',
    departmentName: '',
    titleName: '',
    specialty: '',
    intro: '',
    avatar: '',
    schedules: [],
    // 后端 non_null 序列化会让 null 键整个消失，wxml 里没法区分"键不在"和"空数组"，
    // 所以列表统一在这里补空数组，模板只管渲染。
    hasSchedule: false,
  },

  onLoad(options) {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ doctorId: options.id })
    this.loadDetail(options.id)
  },

  async loadDetail(id) {
    this.setData({ loading: true })
    try {
      const detail = await get(`/user/doctors/${id}`)
      // 排班在医生详情里做只读展示（卡片 415 行）：日期 + 周几 + 时段中文 + 剩余/总号源。
      // 时段码翻译成中文、周几补算都放前端（后端只回码），见 utils/format.js。
      const schedules = (detail.schedules || []).map((item) => ({
        id: item.id,
        date: item.date,
        weekday: weekdayLabel(item.date),
        slotLabel: timeSlotLabel(item.timeSlot),
        totalSlots: item.totalSlots,
        remainingSlots: item.remainingSlots,
        // 约满的排班不隐藏：藏起来等于告诉患者"这位医生那天不出诊"，是误导。
        // 灰掉 + 明写"已约满"，患者能区分"没排班"和"排了但满了"。
        booked: item.remainingSlots === 0,
      }))
      this.setData({
        name: detail.name || '',
        departmentName: detail.departmentName || '',
        titleName: detail.titleName || '',
        specialty: detail.specialty || '',
        intro: detail.intro || '',
        avatar: detail.avatar || '',
        schedules,
        hasSchedule: schedules.length > 0,
      })
      wx.setNavigationBarTitle({ title: detail.name ? `${detail.name} 医生` : '医生详情' })
    } catch (err) {
      // 错误消息已由 utils/request.js 统一 toast
    } finally {
      this.setData({ loading: false })
    }
  },

  onBackToDepartment() {
    wx.navigateBack()
  },
})
