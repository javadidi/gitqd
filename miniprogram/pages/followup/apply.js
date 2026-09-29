const app = getApp()
const { get } = require('../../utils/request')
const { relationLabel } = require('../../utils/format')

// 在线复诊申请（T20 卡片 601 行「选择就诊人/科室/医生」+ 602 行「在线复诊申请：填写复诊信息」；
// PRD §3.6 第 186–189 行把这三步分开列成三个页面名，§6.1 第 520 行同样列了
// 「选择就诊人、选择科室、科室详情」）。
//
// 三处刻意的设计：
// 1. 三步合并在这一页做完，不拆成三个页面 —— 与 T14 充值页内联「选择就诊人」
//    （PRD 94 行也把它单列成一步）同一条口径：合并的是页面，不是数据。
//    后端 PRD §9.1 第 615 行只给两个接口，页面拆几块都不改变只有两次请求这一事实。
// 2. 医生列表按 departmentId 现拉（GET /user/doctors?departmentId=，T10 建的只读端点），
//    换科室就清空已选医生 —— 这个联动也是后端「医生不属于该科室就 400」那道守卫
//    （FollowUpService）在 UI 侧的天然对应物：正常操作走不到那条分支。
// 3. 就诊人列表在 onShow 拉：从「添加就诊人」回来要立刻看到新人（T08 起的惯例）。
Page({
  data: {
    loadingPatients: false,
    loadingDepartments: false,
    loadingDoctors: false,
    patients: [],
    departments: [],
    doctors: [],
    patientId: null,
    patientName: '',
    departmentId: null,
    departmentName: '',
    departmentLocation: '',
    doctorId: null,
    doctorName: '',
    doctorSpecialty: '',
  },

  onShow() {
    if (!app.globalData.token) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadPatients()
    this.loadDepartments()
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

  async loadDepartments() {
    this.setData({ loadingDepartments: true })
    try {
      const rows = await get('/user/departments')
      this.setData({
        departments: (rows || []).map((item) => ({
          id: item.id,
          name: item.name,
          location: item.location,
        })),
      })
    } catch (err) {
      // 同上
    } finally {
      this.setData({ loadingDepartments: false })
    }
  },

  async loadDoctors(departmentId) {
    this.setData({ loadingDoctors: true })
    try {
      const rows = await get('/user/doctors', { departmentId })
      this.setData({
        doctors: (rows || []).map((item) => ({
          id: item.id,
          name: item.name,
          titleName: item.titleName,
          specialty: item.specialty,
        })),
      })
    } catch (err) {
      // 同上
    } finally {
      this.setData({ loadingDoctors: false })
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

  onPickDepartment(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.departments.find((d) => d.id === id)
    if (id === this.data.departmentId) return
    // 换科室必须把已选医生清掉：留着上一个人，提交的就是「消化内科 / 王建国（普外科）」
    // 这种自相矛盾的复诊单，后端会以 400 拒掉（FollowUpService 那条守卫）。
    this.setData({
      departmentId: id,
      departmentName: found ? found.name : '',
      departmentLocation: found ? (found.location || '') : '',
      doctorId: null,
      doctorName: '',
      doctors: [],
    })
    this.loadDoctors(id)
  },

  onPickDoctor(e) {
    const id = Number(e.currentTarget.dataset.id)
    const found = this.data.doctors.find((d) => d.id === id)
    this.setData({
      doctorId: id,
      doctorName: found ? found.name : '',
      // 带去下一页不是为了显示它，而是「选择疾病」的候选项要有出处：
      // 库里没有任何疾病字典表，唯一列了病名的地方就是医生的「擅长」（V1:89，seed.sql:60-64）。
      doctorSpecialty: found ? (found.specialty || '') : '',
    })
  },

  onNext() {
    if (!this.data.patientId) {
      wx.showToast({ title: '请选择就诊人', icon: 'none' })
      return
    }
    if (!this.data.departmentId) {
      wx.showToast({ title: '请选择复诊科室', icon: 'none' })
      return
    }
    if (!this.data.doctorId) {
      wx.showToast({ title: '请选择复诊医生', icon: 'none' })
      return
    }
    // 疾病信息在下一页填（PRD 190 行「选择疾病」是流程第五步），
    // 三个 id 走 query 带过去；名字只作展示用，提交时后端会自己按 id 解析。
    const query = [
      `patientId=${this.data.patientId}`,
      `departmentId=${this.data.departmentId}`,
      `doctorId=${this.data.doctorId}`,
      `patientName=${encodeURIComponent(this.data.patientName)}`,
      `departmentName=${encodeURIComponent(this.data.departmentName)}`,
      `doctorName=${encodeURIComponent(this.data.doctorName)}`,
      `doctorSpecialty=${encodeURIComponent(this.data.doctorSpecialty)}`,
    ].join('&')
    wx.navigateTo({ url: `/pages/followup/disease?${query}` })
  },
})
