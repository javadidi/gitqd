const { queueStatusLabel, queueStatusTone, queueStatusIcon } = require('../../utils/format')

// 候诊排队进度条（任务卡 §2.2 第 114 行「<QueueProgress> | 候诊排队进度条 | T16」、
// 卡片 531 行「<QueueProgress> 组件：排队进度条」）。
//
// 这是小程序侧的第一个自定义组件（此前 miniprogram/ 下一个组件都没有，页面全是整页 wxml）。
// 抽成组件不是因为复用——目前只有候诊页用它——而是卡片把它列为本卡的交付物，
// 且进度口径需要一个唯一出处：写在页面里，下一个要用进度的页就会各算各的。
//
// <b>进度公式没有任何规格出处，这是我自己定的口径</b>：PRD 105-108 行只给了
// 「当前排队人数」「叫号进度」两个词，从没定义进度怎么算。这里取
//   进度 = 当前叫号 / (当前叫号 + 前方等待人数)
// 理由是它只用到 queue_status 已有的两个数（V1:191-192），且两个方向都单调：
// 叫号前进则变大、前面人变多则变小。DONE 直接给满，因为"已经完成"不需要比例。
// 产品如果要改口径（例如按号源总数算），只需要改这一个文件。
Component({
  properties: {
    status: { type: String, value: '' },
    currentNumber: { type: null, value: null },
    waitingCount: { type: null, value: null },
  },

  data: {
    hasQueue: false,
    label: '—',
    tone: 'waiting',
    icon: '⏳',
    percent: 0,
    numberText: '—',
    waitingText: '—',
  },

  observers: {
    'status, currentNumber, waitingCount': function (status, currentNumber, waitingCount) {
      if (!status) {
        this.setData({
          hasQueue: false, label: '暂未进入叫号队列', tone: 'waiting', icon: '⏳',
          percent: 0, numberText: '—', waitingText: '—',
        })
        return
      }
      const current = Number(currentNumber) || 0
      const waiting = Number(waitingCount) || 0
      const total = current + waiting
      const percent = status === 'DONE'
        ? 100
        : (total > 0 ? Math.min(100, Math.round((current / total) * 100)) : 0)
      this.setData({
        hasQueue: true,
        label: queueStatusLabel(status),
        tone: queueStatusTone(status),
        icon: queueStatusIcon(status),
        percent,
        numberText: String(current),
        waitingText: String(waiting),
      })
    },
  },
})
