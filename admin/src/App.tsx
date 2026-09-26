import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import Dashboard from './pages/Dashboard'
import PlaceholderPage from './pages/PlaceholderPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<PlaceholderPage title="登录" prd="4.1" card="T06" />} />
        <Route element={<AppLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route
            path="/appointments/registration"
            element={<PlaceholderPage title="预约挂号管理" prd="4.3.1" card="T25" />}
          />
          <Route
            path="/appointments/nucleic-acid"
            element={<PlaceholderPage title="预约核酸检测管理" prd="4.3.2" card="T25" />}
          />
          <Route
            path="/appointments/physical"
            element={<PlaceholderPage title="预约体检管理" prd="4.3.3" card="T25" />}
          />
          <Route
            path="/appointments/schedule"
            element={<PlaceholderPage title="医生排班管理" prd="4.3.4" card="T25" />}
          />
          <Route
            path="/finance/outpatient-consume"
            element={<PlaceholderPage title="门诊消费记录" prd="4.4.1" card="T26" />}
          />
          <Route
            path="/finance/outpatient-recharge"
            element={<PlaceholderPage title="门诊充值记录" prd="4.4.2" card="T26" />}
          />
          <Route
            path="/finance/inpatient-recharge"
            element={<PlaceholderPage title="住院充值记录" prd="4.4.3" card="T26" />}
          />
          <Route
            path="/finance/inpatient-consume"
            element={<PlaceholderPage title="住院消费记录" prd="4.4.4" card="T26" />}
          />
          <Route
            path="/finance/medical-record-delivery"
            element={<PlaceholderPage title="病案配送记录" prd="4.4.5" card="T26" />}
          />
          <Route
            path="/finance/refund"
            element={<PlaceholderPage title="退款记录" prd="4.4.6" card="T26" />}
          />
          <Route
            path="/hospital/doctors"
            element={<PlaceholderPage title="医生管理" prd="4.5.1" card="T27" />}
          />
          <Route
            path="/hospital/departments"
            element={<PlaceholderPage title="科室管理" prd="4.5.2" card="T27" />}
          />
          <Route
            path="/hospital/physical-packages"
            element={<PlaceholderPage title="体检套餐管理" prd="4.5.3" card="T27" />}
          />
          <Route
            path="/hospital/physical-items"
            element={<PlaceholderPage title="体检项目管理" prd="4.5.4" card="T27" />}
          />
          <Route
            path="/hospital/package-types"
            element={<PlaceholderPage title="套餐类型管理" prd="4.5.5" card="T27" />}
          />
          <Route
            path="/hospital/health-articles"
            element={<PlaceholderPage title="健康百科管理" prd="4.5.6" card="T27" />}
          />
          <Route
            path="/hospital/guides"
            element={<PlaceholderPage title="就诊指南管理" prd="4.5.7" card="T27" />}
          />
          <Route
            path="/hospital/navigation"
            element={<PlaceholderPage title="医院导航管理" prd="4.5.8" card="T27" />}
          />
          <Route
            path="/hospital/introduction"
            element={<PlaceholderPage title="医院简介管理" prd="4.5.9" card="T27" />}
          />
          <Route
            path="/hospital/appointment-notice"
            element={<PlaceholderPage title="预约须知管理" prd="4.5.10" card="T27" />}
          />
          <Route
            path="/hospital/delivery-notice"
            element={<PlaceholderPage title="病案配送须知管理" prd="4.5.11" card="T27" />}
          />
          <Route
            path="/hospital/feedback"
            element={<PlaceholderPage title="用户反馈管理" prd="4.5.12" card="T27" />}
          />
          <Route
            path="/system/admins"
            element={<PlaceholderPage title="管理员管理" prd="4.6.1" card="T28" />}
          />
          <Route
            path="/system/roles"
            element={<PlaceholderPage title="角色管理" prd="4.6.2" card="T28" />}
          />
          <Route
            path="/system/titles"
            element={<PlaceholderPage title="职称管理" prd="4.6.3" card="T28" />}
          />
          <Route
            path="/system/notices"
            element={<PlaceholderPage title="消息公告管理" prd="4.6.4" card="T28" />}
          />
          <Route
            path="/system/password"
            element={<PlaceholderPage title="修改密码" prd="4.6.5" card="T28" />}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
