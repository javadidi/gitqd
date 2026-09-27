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

module.exports = { formatMoney, formatDate, maskPhone, RELATION_LABELS, relationLabel }
