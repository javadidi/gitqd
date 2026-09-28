const app = getApp()
const { get } = require('../../utils/request')
const { timeSlotLabel, weekdayLabel, formatMoney } = require('../../utils/format')

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
        timeSlot: item.timeSlot,
        totalSlots: item.totalSlots,
        remainingSlots: item.remainingSlots,
        feeFen: item.feeFen,
        // 挂号费由后端按职称算好下发（唯一出处 AppointmentFeeService），前端只做元/分换算。
        feeText: formatMoney(item.feeFen),
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

  /**
   * 挂这个号的入口（T12 加）。T10 那版这里是只读的，卡片 417 行红线写着「不做预约（T12）」；
   * 现在预约归本卡，所以按钮出现在这里，并且**只有有余号的行可点**——
   * 约满的行依然显示（不隐藏，理由见上面的注释），但按下去只会得到一个 2003，
   * 所以前端直接不给它绑点击，省一次注定失败的请求。
   */
  onBook(e) {
    const item = e.currentTarget.dataset.schedule
    const doctor = this.data
    const query = [
      `scheduleId=${item.id}`,
      `doctorId=${doctor.doctorId}`,
      `doctorName=${encodeURIComponent(doctor.name)}`,
      `departmentName=${encodeURIComponent(doctor.departmentName)}`,
      `date=${item.date}`,
      `timeSlot=${item.timeSlot}`,
      `slotLabel=${encodeURIComponent(item.slotLabel)}`,
      `feeFen=${item.feeFen == null ? '' : item.feeFen}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/appointment/notice?${query}` })
  },
})
