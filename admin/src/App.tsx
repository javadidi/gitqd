import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import RequireAuth from './components/RequireAuth'
import CaseDeliveryDetailPage from './pages/CaseDeliveryDetailPage'
import CaseDeliveryListPage from './pages/CaseDeliveryListPage'
import Dashboard from './pages/Dashboard'
import InpatientConsumePage from './pages/InpatientConsumePage'
import InpatientRechargeDetailPage from './pages/InpatientRechargeDetailPage'
import InpatientRechargeListPage from './pages/InpatientRechargeListPage'
import LoginPage from './pages/LoginPage'
import NucleicDetailPage from './pages/NucleicDetailPage'
import NucleicListPage from './pages/NucleicListPage'
import OutpatientRechargeDetailPage from './pages/OutpatientRechargeDetailPage'
import OutpatientRechargeListPage from './pages/OutpatientRechargeListPage'
import PaymentDetailPage from './pages/PaymentDetailPage'
import PaymentListPage from './pages/PaymentListPage'
import PlaceholderPage from './pages/PlaceholderPage'
import PhysicalDetailPage from './pages/PhysicalDetailPage'
import PhysicalListPage from './pages/PhysicalListPage'
import PhysicalReportPage from './pages/PhysicalReportPage'
import RefundDetailPage from './pages/RefundDetailPage'
import RefundListPage from './pages/RefundListPage'
import RegistrationDetailPage from './pages/RegistrationDetailPage'
import RegistrationListPage from './pages/RegistrationListPage'
import ScheduleManagePage from './pages/ScheduleManagePage'
import { AuthProvider } from './store/AuthProvider'

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <RequireAuth>
                <AppLayout />
              </RequireAuth>
            }
          >
            <Route path="/" element={<Dashboard />} />
            <Route path="/appointments/registration" element={<RegistrationListPage />} />
            <Route path="/appointments/registration/:id" element={<RegistrationDetailPage />} />
            <Route path="/appointments/nucleic-acid" element={<NucleicListPage />} />
            <Route path="/appointments/nucleic-acid/:id" element={<NucleicDetailPage />} />
            <Route path="/appointments/physical" element={<PhysicalListPage />} />
            <Route path="/appointments/physical/:id" element={<PhysicalDetailPage />} />
            <Route path="/appointments/physical/:id/report" element={<PhysicalReportPage />} />
            <Route path="/appointments/schedule" element={<ScheduleManagePage />} />
            <Route path="/finance/outpatient-consume" element={<PaymentListPage />} />
            <Route path="/finance/outpatient-consume/:id" element={<PaymentDetailPage />} />
            <Route path="/finance/outpatient-recharge" element={<OutpatientRechargeListPage />} />
            <Route path="/finance/outpatient-recharge/:id" element={<OutpatientRechargeDetailPage />} />
            <Route path="/finance/inpatient-recharge" element={<InpatientRechargeListPage />} />
            <Route path="/finance/inpatient-recharge/:id" element={<InpatientRechargeDetailPage />} />
            <Route path="/finance/inpatient-consume" element={<InpatientConsumePage />} />
            <Route path="/finance/medical-record-delivery" element={<CaseDeliveryListPage />} />
            <Route path="/finance/medical-record-delivery/:id" element={<CaseDeliveryDetailPage />} />
            <Route path="/finance/refund" element={<RefundListPage />} />
            <Route path="/finance/refund/:id" element={<RefundDetailPage />} />
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
    </AuthProvider>
  )
}

export default App
