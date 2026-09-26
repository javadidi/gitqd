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

module.exports = { formatMoney, formatDate, maskPhone }
