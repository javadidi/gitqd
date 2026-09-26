import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import PageHeader from '@/components/business/PageHeader'

describe('PageHeader', () => {
  it('标题渲染为页面唯一的 h1', () => {
    render(<PageHeader title="退款记录" />)
    expect(screen.getByRole('heading', { level: 1, name: '退款记录' })).toBeInTheDocument()
  })

  it('描述与操作区都给出时一并渲染', () => {
    render(
      <PageHeader
        title="退款记录"
        description="对应 PRD 4.14 / 待 T14"
        actions={<button type="button">导出</button>}
      />,
    )
    expect(screen.getByText('对应 PRD 4.14 / 待 T14')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '导出' })).toBeInTheDocument()
  })

  it('省略描述与操作区时不留空节点', () => {
    const { container } = render(<PageHeader title="退款记录" />)
    expect(container.querySelector('p')).toBeNull()
    expect(container.querySelector('button')).toBeNull()
  })
})
