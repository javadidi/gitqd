import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout'
import RequireAuth from './components/RequireAuth'
import CaseDeliveryDetailPage from './pages/CaseDeliveryDetailPage'
import CaseDeliveryListPage from './pages/CaseDeliveryListPage'
import Dashboard from './pages/Dashboard'
import DepartmentManagePage from './pages/DepartmentManagePage'
import DoctorManagePage from './pages/DoctorManagePage'
import FeedbackManagePage from './pages/FeedbackManagePage'
import GuideArticlePage from './pages/GuideArticlePage'
import HealthArticlePage from './pages/HealthArticlePage'
import HospitalNavigationPage from './pages/HospitalNavigationPage'
import HospitalProfilePage from './pages/HospitalProfilePage'
import InpatientConsumePage from './pages/InpatientConsumePage'
import InpatientRechargeDetailPage from './pages/InpatientRechargeDetailPage'
import InpatientRechargeListPage from './pages/InpatientRechargeListPage'
import LoginPage from './pages/LoginPage'
import NucleicDetailPage from './pages/NucleicDetailPage'
import NucleicListPage from './pages/NucleicListPage'
import NoticeManagePage from './pages/NoticeManagePage'
import OutpatientRechargeDetailPage from './pages/OutpatientRechargeDetailPage'
import OutpatientRechargeListPage from './pages/OutpatientRechargeListPage'
import PackageTypePage from './pages/PackageTypePage'
import PaymentDetailPage from './pages/PaymentDetailPage'
import PaymentListPage from './pages/PaymentListPage'
import PhysicalItemPage from './pages/PhysicalItemPage'
import PhysicalPackagePage from './pages/PhysicalPackagePage'
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
            {/* T27 医院管理：PRD 4.5.1–4.5.12 十二节，一节一条路由。
                /hospital/navigation 是有意的说明页（卡片 743 行要 CRUD，附录 A 784 行把院区
                数据模型判给二期），不是还没做。 */}
            <Route path="/hospital/doctors" element={<DoctorManagePage />} />
            <Route path="/hospital/departments" element={<DepartmentManagePage />} />
            <Route path="/hospital/physical-packages" element={<PhysicalPackagePage />} />
            <Route path="/hospital/physical-items" element={<PhysicalItemPage />} />
            <Route path="/hospital/package-types" element={<PackageTypePage />} />
            <Route path="/hospital/health-articles" element={<HealthArticlePage />} />
            <Route path="/hospital/guides" element={<GuideArticlePage />} />
            <Route path="/hospital/navigation" element={<HospitalNavigationPage />} />
            <Route path="/hospital/introduction" element={<HospitalProfilePage />} />
            <Route
              path="/hospital/appointment-notice"
              element={<NoticeManagePage kind="appointment" />}
            />
            <Route
              path="/hospital/delivery-notice"
              element={<NoticeManagePage kind="delivery" />}
            />
            <Route path="/hospital/feedback" element={<FeedbackManagePage />} />
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
