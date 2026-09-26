import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MetricCard from '@/components/business/MetricCard'

describe('MetricCard', () => {
  it('深色卡上给出标签与数值', () => {
    const { container } = render(<MetricCard label="今日预约" value={128} />)
    expect(screen.getByText('今日预约')).toBeInTheDocument()
    expect(screen.getByText('128')).toBeInTheDocument()
    expect(container.firstElementChild?.className).toContain('bg-zinc-900')
  })

  it('delta 带方向时上色并画趋势图标', () => {
    const { container } = render(
      <MetricCard label="今日预约" value={128} delta="+12.4%" deltaTone="up" />,
    )
    expect(screen.getByText('+12.4%').className).toContain('text-emerald-400')
    expect(container.querySelector('svg')).not.toBeNull()
  })

  it('口径按"口径："前缀展示，省略时整段不渲染', () => {
    const withCaliber = render(
      <MetricCard label="今日预约" value={128} caliber="不含已取消" />,
    )
    expect(withCaliber.getByText('口径：不含已取消')).toBeInTheDocument()
    withCaliber.unmount()

    const bare = render(<MetricCard label="今日预约" value={128} />)
    expect(bare.queryByText(/^口径：/)).toBeNull()
    expect(bare.container.querySelector('svg')).toBeNull()
  })
})
