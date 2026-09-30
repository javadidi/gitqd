import { api } from './client'

/**
 * T26 管理端费用管理的接口层：门诊消费、门诊充值、住院充值、病案配送、退款五组。
 *
 * <p>路径全部带 `/api` 前缀（`server.servlet.context-path=/api`，与 Vite 的 `/api` 代理同源）。
 *
 * <h2>类型里 `?:` 与 `| null` 是两件事，这份文件必须分清</h2>
 * 后端有两条让字段"看不见"的机制，前端要分别接住：
 * <ul>
 *   <li><b>{@code application.yml} 的 {@code default-property-inclusion: non_null}</b>：
 *       null 字段整个键都不出现。读出来是 {@code undefined}，所以类型写 {@code name?: string}。
 *       典型是列表行里没有 {@code items}、门诊充值行里没有 {@code inpatientName}。</li>
 *   <li><b>金额裁剪层（{@code MoneyMaskingSerializer}）</b>：护士角色下它把金额写成 JSON 的
 *       {@code null}——<b>键在，值是 null</b>。所以金额一律是 {@code number | null}，
 *       不能写成 {@code number}（会骗自己），也不能写成 {@code number?}
 *       （那会以为"缺键"，而实际是后端明明白白告诉了你有一个金额、只是不给你看）。
 *       {@code Money} 组件对两种情况都渲染成 {@code —}，这正是它接 {@code null | undefined} 的原因。</li>
 * </ul>
 *
 * <h2>五组列表都没有筛选参数</h2>
 * 与后端一致：PRD §4.4（369–390 行）通篇没写"筛选"，对照 §4.3.1 的 347 行是明写筛选的。
 * 这一条在后端有测试钉着（{@code j57_listsAcceptNoFilterParams…}），这里就不留参数入口。
 *
 * <h2>没有「住院消费记录」这一组</h2>
 * 卡片 719 行有这句话，但 V1 十七张表里没有一张装得下它（{@code payment_record} 只有
 * {@code patient_id NOT NULL}，没有住院人列）。后端零端点，前端也就无源可接——
 * 那一页走 {@link INPATIENT_CONSUME_UNAVAILABLE} 的说明文案，不放假数据。
 */

const BASE = '/api/admin'

/** 后端 AdminPaymentResponse：列表与详情共用一个类，详情才带 items。 */
export interface PaymentRow {
  id: number
  orderNo: string
  patientId: number
  patientName?: string
  cardNo?: string
  amountFen: number | null
  payMethod: string
  status: string
  tradeNo?: string
  /** 只有详情那趟带；列表行里这个键不存在。 */
  items?: PaymentItem[]
  createdAt: string
}

/**
 * 后端复用 T15 的 {@code OutpatientPaymentResponse.Item}（同一条解析路径，两处显示不漂移）。
 * 键名来自 {@code payment_record.items} 这个 JSON 列的实际形状，规格从没定义过它。
 */
export interface PaymentItem {
  name?: string
  amountFen: number | null
}

/** 后端 AdminRechargeResponse：门诊与住院两把列表共用一个类。 */
export interface RechargeRow {
  id: number
  orderNo: string
  patientId?: number
  patientName?: string
  cardNo?: string
  inpatientId?: number
  inpatientName?: string
  inpatientNo?: string
  amountFen: number | null
  payMethod: string
  status: string
  tradeNo?: string
  createdAt: string
  updatedAt: string
}

/** 后端 AdminCaseDeliveryResponse。主语是住院人，不是就诊人。 */
export interface CaseDeliveryRow {
  id: number
  inpatientId?: number
  inpatientName?: string
  inpatientNo?: string
  department?: string
  bedNo?: string
  recipientName?: string
  address?: string
  status: string
  /** 全系统无人写这一列，首版永远是缺键；见后端 DTO 的类注释。 */
  trackingNo?: string
  createdAt: string
  updatedAt: string
}

/** 后端 AdminRefundResponse。 */
export interface RefundRow {
  id: number
  orderNo: string
  relatedId?: number
  relatedType?: string
  /** 尽力解析：关联原单已被删/类型不认识时这个键不存在。 */
  relatedOrderNo?: string
  amountFen: number | null
  reason?: string
  status: string
  reviewerId?: number
  reviewerName?: string
  createdAt: string
  updatedAt: string
}

/**
 * 「住院消费记录」这一页要说什么。
 *
 * <p>它是一段说明，不是一个功能：卡片 719 行要它，V1 没有承载它的表，
 * 后端因此零端点。页面据此写清"缺的到底是哪一样东西"，而不是摆一张空表假装数据还没来。
 * 补齐它需要的三样（住院费用表、写入方、按住院人的归属）挂在跨卡 TODO。
 */
export const INPATIENT_CONSUME_UNAVAILABLE = {
  title: '住院消费记录暂无数据源',
  description:
    '这一页要展示的每笔住院花费，本系统的数据库里还没有一张表来装：'
    + '缴费流水表 payment_record 只挂就诊人（patient_id 非空、没有住院人列），'
    + '把住院的钱记到某个门诊就诊人头上去等于记假账。'
    + '它需要先补一张住院费用表和一个写入方（医院 HIS 对接属二期），本卡不编数据。',
}

export function listPayments(): Promise<PaymentRow[]> {
  return api.get<PaymentRow[]>(`${BASE}/payments`)
}

export function getPayment(id: number | string): Promise<PaymentRow> {
  return api.get<PaymentRow>(`${BASE}/payments/${id}`)
}

/** 门诊充值：后端只认 inpatient_id 为空的那批。 */
export function listOutpatientRecharges(): Promise<RechargeRow[]> {
  return api.get<RechargeRow[]>(`${BASE}/recharges`)
}

export function getOutpatientRecharge(id: number | string): Promise<RechargeRow> {
  return api.get<RechargeRow>(`${BASE}/recharges/${id}`)
}

/** 住院充值：同一张 recharge_record，只认 inpatient_id 非空的那批。 */
export function listInpatientRecharges(): Promise<RechargeRow[]> {
  return api.get<RechargeRow[]>(`${BASE}/inpatient-recharges`)
}

export function getInpatientRecharge(id: number | string): Promise<RechargeRow> {
  return api.get<RechargeRow>(`${BASE}/inpatient-recharges/${id}`)
}

export function listCaseDeliveries(): Promise<CaseDeliveryRow[]> {
  return api.get<CaseDeliveryRow[]>(`${BASE}/case-deliveries`)
}

export function getCaseDelivery(id: number | string): Promise<CaseDeliveryRow> {
  return api.get<CaseDeliveryRow>(`${BASE}/case-deliveries/${id}`)
}

export function listRefunds(): Promise<RefundRow[]> {
  return api.get<RefundRow[]>(`${BASE}/refunds`)
}

export function getRefund(id: number | string): Promise<RefundRow> {
  return api.get<RefundRow>(`${BASE}/refunds/${id}`)
}

/**
 * 审核通过。两把审核端点都不收 body：金额在挂单时就定死了，
 * 审核人只能从 token 取（后端有测试钉住"传了也不生效"）。
 * 返回的是审核后的详情形状，页面据此就地刷新，不必再发一次 GET。
 */
export function approveRefund(id: number | string): Promise<RefundRow> {
  return api.post<RefundRow>(`${BASE}/refunds/${id}/approve`)
}

export function rejectRefund(id: number | string): Promise<RefundRow> {
  return api.post<RefundRow>(`${BASE}/refunds/${id}/reject`)
}
