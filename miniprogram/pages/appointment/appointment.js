Page({
  data: {
    departments: [],
    selectedDept: null,
    doctors: [],
  },

  onLoad() {
    this.loadDepartments()
  },

  async loadDepartments() {
    // T10+ will implement real API calls
  },

  onDeptTap(e) {
    const dept = e.currentTarget.dataset.dept
    this.setData({ selectedDept: dept })
  },
})
