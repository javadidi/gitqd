import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import StatusBadge from '@/components/business/StatusBadge'

function badgeOf(status: string): HTMLElement {
  const el = document.querySelector<HTMLElement>(`[data-status="${status}"]`)
  if (!el) throw new Error(`未渲染出 status=${status} 的徽章`)
  return el
}

describe('StatusBadge', () => {
  it('已知状态同时给出颜色、图标与文字', () => {
    const { container } = render(<StatusBadge status="SUCCESS" />)

    expect(screen.getByText('成功')).toBeInTheDocument()
    expect(badgeOf('SUCCESS')).toHaveAttribute('data-tone', 'success')
    expect(container.querySelector('svg')).not.toBeNull()
  })

  it.each([
    ['PENDING_PAYMENT', 'warning', '待缴费'],
    ['CONFIRMED', 'success', '已确认'],
    ['CANCELLED', 'danger', '已取消'],
    ['SERVING', 'info', '就诊中'],
    ['COMPLETED', 'neutral', '已完成'],
  ])('%s → %s 色板，文案「%s」', (status, tone, label) => {
    render(<StatusBadge status={status} />)
    const badge = badgeOf(status)
    expect(badge).toHaveAttribute('data-tone', tone)
    expect(badge).toHaveTextContent(label)
  })

  it('未登记的状态退回中性色板并原样显示状态值', () => {
    render(<StatusBadge status="SOMETHING_NEW" />)
    const badge = badgeOf('SOMETHING_NEW')
    expect(badge).toHaveAttribute('data-tone', 'neutral')
    expect(badge).toHaveTextContent('SOMETHING_NEW')
  })

  it('label 可覆盖登记文案，但色板仍按状态取', () => {
    render(<StatusBadge status="PENDING" label="待审核" />)
    const badge = badgeOf('PENDING')
    expect(badge).toHaveTextContent('待审核')
    expect(badge).toHaveAttribute('data-tone', 'warning')
  })
})
