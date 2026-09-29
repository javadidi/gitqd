function formatMoney(fen) {
  if (fen == null || fen === '') return '—'
  const yuan = Number(fen) / 100
  return '¥' + yuan.toFixed(2)
}

function formatDate(dateStr, fmt) {
  if (!dateStr) return '—'
  fmt = fmt || 'YYYY-MM-DD'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  const second = String(d.getSeconds()).padStart(2, '0')
  return fmt
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second)
}

function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || '—'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

// 关系码 → 中文标签。码值来自后端 V1__init.sql 的 patient.relation 列注释，
// 后端只回码不回中文（和管理后台 StatusBadge 回状态码同理：改文案不用动后端）。
const RELATION_LABELS = {
  SELF: '本人',
  CHILD: '子女',
  PARENT: '父母',
  SPOUSE: '配偶',
  OTHER: '其他',
}

function relationLabel(relation) {
  return RELATION_LABELS[relation] || relation || '—'
}

// 时段码 → 中文标签。码值来自后端 V1__init.sql:105 的 schedule.time_slot 列注释
// 「MORNING/AFTERNOON/EVENING」，后端只回码不回中文（同 RELATION_LABELS 的取舍）。
const TIME_SLOT_LABELS = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上',
}

function timeSlotLabel(timeSlot) {
  return TIME_SLOT_LABELS[timeSlot] || timeSlot || '—'
}

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

// 排班日期只有 YYYY-MM-DD，没有"周几"，而患者挑号是按星期几看的，所以在前端补。
function weekdayLabel(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  return WEEKDAY_LABELS[d.getDay()]
}

// 预约状态码 → 中文标签。码值出处是后端 V1__init.sql:124 的列注释
// 「PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED」，后端只回码不回中文（同 TIME_SLOT_LABELS）。
const APPOINTMENT_STATUS_LABELS = {
  PENDING_PAYMENT: '待支付',
  CONFIRMED: '已确认',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
}

function appointmentStatusLabel(status) {
  return APPOINTMENT_STATUS_LABELS[status] || status || '—'
}

// 卡片 475 行把预约记录列表分成「待就诊 / 已完成 / 已取消」三组，但状态有四个值：
// PRD 从没定义"待就诊"等于哪个 status，所以归类放在这里、只归一次，两个页面共用。
// PENDING_PAYMENT 归进"待就诊"是因为它确实是一次还没发生的就诊；
// 行内仍显示精确标签"待支付"，不把"还没付钱"这个事实藏掉
// （超时自动取消是二期，卡片 458 行，所以待支付的单会长期停在这里）。
const APPOINTMENT_GROUPS = {
  pending: ['PENDING_PAYMENT', 'CONFIRMED'],
  completed: ['COMPLETED'],
  cancelled: ['CANCELLED'],
}

function appointmentGroup(status) {
  if (APPOINTMENT_GROUPS.completed.indexOf(status) >= 0) return 'completed'
  if (APPOINTMENT_GROUPS.cancelled.indexOf(status) >= 0) return 'cancelled'
  return 'pending'
}

// 能不能退号：卡片 479 行红线「已就诊不可退号」。
// 这只是按钮可见性，真正的兜底是后端 cancelIfActive 那条 SQL 的 WHERE。
function appointmentCancellable(status) {
  return status === 'PENDING_PAYMENT' || status === 'CONFIRMED'
}

// 充值状态码 → 中文标签。码值出处是后端 V1__init.sql:147 的列注释
// 「PENDING/SUCCESS/REFUNDED」。REFUNDED 在首版只会出现在种子数据里
//（真实退款属 T19/二期，卡片 498 行红线「不做退款」），标签仍先备着。
const RECHARGE_STATUS_LABELS = {
  PENDING: '待支付',
  SUCCESS: '充值成功',
  REFUNDED: '已退款',
}

function rechargeStatusLabel(status) {
  return RECHARGE_STATUS_LABELS[status] || status || '—'
}

// 支付方式码 → 中文标签。码值出处 V1__init.sql:146「WECHAT/ALIPAY/CASH/CARD」。
// 首版真实通道只有微信（卡片 494 行括号写死，后端也固定写 WECHAT），
// ALIPAY/CASH 只会出现在种子数据里。
// BALANCE 是 T15 缴费引入的第四个值：payment_record.pay_method（V1:162）的注释只有
// 「支付方式」四个字、没有封闭值域，而余额支付这个事实必须在财务表里留得下；
// 沿用单据上原有的 WECHAT 就等于记一笔假账，所以后端写死 BALANCE、前端在这里翻译。
const PAY_METHOD_LABELS = {
  WECHAT: '微信支付',
  ALIPAY: '支付宝',
  CASH: '现金',
  CARD: '刷卡',
  BALANCE: '就诊卡余额',
}

function payMethodLabel(method) {
  return PAY_METHOD_LABELS[method] || method || '—'
}

// 缴费状态码 → 中文标签，码值出处 V1__init.sql:163「PENDING/SUCCESS/REFUNDED」。
// 与充值的三个值同名但语义不同：这里 PENDING 是「待缴费」（医院推过来的账，等患者付），
// 充值那边 PENDING 是「待支付」（患者刚点充值，通道还没回来），所以两套标签分开写，
// 合成一套就会有一边说出另一边的假话。
const PAYMENT_STATUS_LABELS = {
  PENDING: '待缴费',
  SUCCESS: '已缴费',
  REFUNDED: '已退款',
}

function paymentStatusLabel(status) {
  return PAYMENT_STATUS_LABELS[status] || status || '—'
}

// 候诊队列状态码 → 中文标签与色调。码值出处 V1__init.sql:193「WAITING/CALLING/SERVING/DONE」。
// 色调沿用任务卡 §2.1 的设计令牌：候诊中=amber-500（93 行「提醒 | amber-500 | 候诊中/待缴费」）、
// 进行中=sky-600（95 行）、已完成=zinc-400（96 行）。99 行还要求
// 「状态颜色+图标+文字三重编码，禁止纯色圆点」，所以组件里色块、图标、文字三样都给了。
const QUEUE_STATUS_LABELS = {
  WAITING: '排队中',
  CALLING: '叫到你了',
  SERVING: '就诊中',
  DONE: '已就诊',
}

const QUEUE_STATUS_TONES = {
  WAITING: 'waiting',
  CALLING: 'active',
  SERVING: 'active',
  DONE: 'done',
}

const QUEUE_STATUS_ICONS = {
  WAITING: '⏳',
  CALLING: '📣',
  SERVING: '🩺',
  DONE: '✓',
}

function queueStatusLabel(status) {
  return QUEUE_STATUS_LABELS[status] || status || '—'
}

function queueStatusTone(status) {
  return QUEUE_STATUS_TONES[status] || 'waiting'
}

function queueStatusIcon(status) {
  return QUEUE_STATUS_ICONS[status] || '⏳'
}

// 报告类型码 → 中文标签。码值出处 V1__init.sql:206「LAB/IMAGING/PHYSICAL」，
// 中文名出处 PRD 161 行「选择检查报告类型（检验报告/检查报告等）」。
// PHYSICAL（体检报告）在这里也给了标签，但 T17 的页面与接口都拿不到它 ——
// 体检报告查询属 PRD §3.4.2、承接卡是 T22；这里备好只是为了 T22 不必再造一份映射。
const REPORT_TYPE_LABELS = {
  LAB: '检验报告',
  IMAGING: '检查报告',
  PHYSICAL: '体检报告',
}

function reportTypeLabel(type) {
  return REPORT_TYPE_LABELS[type] || type || '—'
}

// T17 卡片 548 行「选择报告类型」这一页要列的两类。
// 只有两个，且规格（PRD 161 行）把它们写死成词而不是数据，所以这里是一个常量而不是接口。
const REPORT_QUERY_TYPES = [
  { type: 'LAB', label: '检验报告', icon: '🧪', desc: '抽血、尿检等化验结果' },
  { type: 'IMAGING', label: '检查报告', icon: '🩻', desc: '超声、CT、X 光等影像结论' },
]

/**
 * 检查项目（report.items）的兜底渲染。
 *
 * <p><strong>这一份兜底是必需的，因为这一列的形状没有任何规格出处</strong>：
 * V1:207 只给了「检查项目」四个字，seed 零行可抄，后端也刻意原样透传不解释
 * （理由见 ReportDetailResponse 的类注释）。所以前端不能假设它是数组还是对象、
 * 里面有没有 name 键 —— 一旦假设了，真实生产者（HIS）给出别的形状时这一栏就白屏。
 *
 * <p>规则只做"怎么都能显示出来"，不做"这是什么意思"：
 * 字符串数组直接顿号连接；对象优先取 name（这是中文项目名最常见的键），
 * 取不到就把这个对象自己序列化出来；非数组一律转成字符串。
 * 两条 REPORT 页面都用它，避免出现第二种兜底写法。
 */
function reportItemsText(items) {
  if (items === null || items === undefined || items === '') {
    return ''
  }
  if (!Array.isArray(items)) {
    return typeof items === 'object' ? JSON.stringify(items) : String(items)
  }
  return items.map((row) => {
    if (row === null || row === undefined) {
      return '—'
    }
    if (typeof row === 'object') {
      return row.name !== undefined ? String(row.name) : JSON.stringify(row)
    }
    return String(row)
  }).join('、')
}

module.exports = {
  formatMoney,
  formatDate,
  maskPhone,
  RELATION_LABELS,
  relationLabel,
  TIME_SLOT_LABELS,
  timeSlotLabel,
  weekdayLabel,
  APPOINTMENT_STATUS_LABELS,
  appointmentStatusLabel,
  APPOINTMENT_GROUPS,
  appointmentGroup,
  appointmentCancellable,
  RECHARGE_STATUS_LABELS,
  rechargeStatusLabel,
  PAY_METHOD_LABELS,
  payMethodLabel,
  PAYMENT_STATUS_LABELS,
  paymentStatusLabel,
  QUEUE_STATUS_LABELS,
  queueStatusLabel,
  QUEUE_STATUS_TONES,
  queueStatusTone,
  QUEUE_STATUS_ICONS,
  queueStatusIcon,
  REPORT_TYPE_LABELS,
  reportTypeLabel,
  REPORT_QUERY_TYPES,
  reportItemsText,
}
