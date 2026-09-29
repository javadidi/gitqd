import { api } from './client'

/**
 * T25 管理端预约管理的接口层：预约挂号、核酸、体检、排班四组。
 *
 * 路径全部带 `/api` 前缀（`server.servlet.context-path=/api`，与 Vite 的 `/api` 代理同源）。
 *
 * <p><b>为什么可选字段一律写成 `?:`（而不是 `| null`）</b>：后端
 * `default-property-inclusion: non_null` 会把 null 字段整个从 JSON 里去掉，
 * 键都不存在，读成 `row.refundNo` 是 `undefined`。类型如实反映这一点，
 * 页面里的空值分支才不用骗自己说"它可能是 null"。
 */

/** 后端 AdminAppointmentResponse：列表与详情共用一个类。 */
export interface RegistrationRow {
  id: number
  orderNo: string
  status: string
  patientId: number
  patientName: string
  cardNo?: string
  departmentName?: string
  doctorName?: string
  appointmentDate?: string
  timeSlot?: string
  feeFen: number
  createdAt: string
  /** 只有详情那趟会反查退款单；列表行里这三个键不存在。 */
  refundNo?: string
  refundFen?: number
  refundStatus?: string
}

export interface RegistrationFilters {
  dateFrom?: string
  dateTo?: string
  departmentId?: string
  doctorId?: string
  status?: string
}

/** GET /admin/appointments/filters 的下拉项。 */
export interface FilterOption {
  id: number
  name: string
  departmentId?: number
}

export interface FilterOptions {
  departments: FilterOption[]
  doctors: FilterOption[]
}

/** 后端 AdminNucleicResponse。 */
export interface NucleicRow {
  id: number
  orderNo: string
  patientId: number
  patientName?: string
  cardNo?: string
  appointmentDate?: string
  status: string
  createdAt: string
  /** 详情接口才会带这个键；首版后端恒不写 report 列，所以它一直是缺的。 */
  report?: string
}

/** 后端 AdminPhysicalResponse。 */
export interface PhysicalRow {
  id: number
  orderNo: string
  patientId: number
  patientName?: string
  cardNo?: string
  packageName?: string
  priceFen?: number
  appointmentDate?: string
  status: string
  createdAt: string
}

/** 后端 AdminPhysicalReportResponse。 */
export interface PhysicalReport {
  appointmentId: number
  reportId?: number
  reportNo?: string
  items?: unknown
  result?: string
  reportTime?: string
}

/** 后端 ScheduleAdminResponse。 */
export interface ScheduleRow {
  id: number
  doctorId: number
  doctorName?: string
  date: string
  timeSlot: string
  totalSlots: number
  remainingSlots: number
}

export interface ScheduleFilters {
  doctorId?: string
  dateFrom?: string
  dateTo?: string
}

/** 后端 AdminScheduleBatchResponse。 */
export interface BatchResult {
  createdCount: number
  createdIds?: number[]
  skippedCount: number
  /** 每一项是 `日期/时段`，跳过的原因：那个组合已经有活排班。 */
  skipped?: string[]
}

/** 后端 AdminScheduleSuspendResponse。 */
export interface SuspendResult {
  scheduleId?: number
  appointmentCount: number
  refundCount: number
  refundFen: number
}

export interface BatchPayload {
  doctorId: number
  dateFrom: string
  dateTo: string
  timeSlots: string[]
  totalSlots: number
}

export interface ReschedulePayload {
  date: string
  timeSlot: string
}

/**
 * 丢掉空串：空筛选项不该变成 `?status=`，后端虽然按选填处理，
 * 但地址栏里留一堆空参数违反附录 B 第 10 条的初衷（URL 要能代表当前视图）。
 */
function clean(params: Record<string, string | undefined>): Record<string, string> | undefined {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') out[key] = value
  }
  return Object.keys(out).length > 0 ? out : undefined
}

/** reason 只能走 query：DELETE 带 body 会被部分客户端丢掉（T11 已在后端注释里说明）。 */
function reasonQuery(reason?: string): string {
  return reason && reason.trim() !== '' ? `?reason=${encodeURIComponent(reason.trim())}` : ''
}

const BASE = '/api/admin'

export function listRegistrations(filters: RegistrationFilters): Promise<RegistrationRow[]> {
  return api.get<RegistrationRow[]>(`${BASE}/appointments`, clean(filters as Record<string, string>))
}

export function getRegistration(id: number | string): Promise<RegistrationRow> {
  return api.get<RegistrationRow>(`${BASE}/appointments/${id}`)
}

export function getFilterOptions(): Promise<FilterOptions> {
  return api.get<FilterOptions>(`${BASE}/appointments/filters`)
}

export function listNucleic(status?: string): Promise<NucleicRow[]> {
  return api.get<NucleicRow[]>(`${BASE}/nucleic-appointments`, clean({ status }))
}

export function getNucleic(id: number | string): Promise<NucleicRow> {
  return api.get<NucleicRow>(`${BASE}/nucleic-appointments/${id}`)
}

export function listPhysical(status?: string): Promise<PhysicalRow[]> {
  return api.get<PhysicalRow[]>(`${BASE}/physical-appointments`, clean({ status }))
}

export function getPhysical(id: number | string): Promise<PhysicalRow> {
  return api.get<PhysicalRow>(`${BASE}/physical-appointments/${id}`)
}

/** 报告详情；还没录入时 reportId/result 都是缺键，调用方按 undefined 分支处理。 */
export function getPhysicalReport(id: number | string): Promise<PhysicalReport> {
  return api.get<PhysicalReport>(`${BASE}/physical-appointments/${id}/report`)
}

export function recordPhysicalReport(id: number | string, result: string): Promise<PhysicalReport> {
  return api.post<PhysicalReport>(`${BASE}/physical-appointments/${id}/report`, { result })
}

export function listSchedules(filters: ScheduleFilters): Promise<ScheduleRow[]> {
  return api.get<ScheduleRow[]>(`${BASE}/schedules`, clean(filters as Record<string, string>))
}

export function createSchedule(payload: {
  doctorId: number
  date: string
  timeSlot: string
  totalSlots: number
}): Promise<ScheduleRow> {
  return api.post<ScheduleRow>(`${BASE}/schedules`, payload)
}

export function updateScheduleSlots(id: number, totalSlots: number): Promise<ScheduleRow> {
  return api.put<ScheduleRow>(`${BASE}/schedules/${id}`, { totalSlots })
}

export function cancelSchedule(id: number, reason?: string): Promise<void> {
  return api.delete<void>(`${BASE}/schedules/${id}${reasonQuery(reason)}`)
}

export function batchCreateSchedules(payload: BatchPayload): Promise<BatchResult> {
  return api.post<BatchResult>(`${BASE}/schedules/batch`, payload)
}

export function suspendSchedule(id: number, reason?: string): Promise<SuspendResult> {
  return api.post<SuspendResult>(`${BASE}/schedules/${id}/suspend${reasonQuery(reason)}`)
}

export function rescheduleSchedule(
  id: number,
  payload: ReschedulePayload,
  reason?: string,
): Promise<ScheduleRow> {
  return api.post<ScheduleRow>(
    `${BASE}/schedules/${id}/reschedule${reasonQuery(reason)}`,
    payload,
  )
}
