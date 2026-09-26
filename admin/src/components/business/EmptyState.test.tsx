import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import EmptyState from '@/components/business/EmptyState'

describe('EmptyState', () => {
  it('默认文案下仍渲染出可点击的下一步动作', async () => {
    const onClick = vi.fn()
    render(<EmptyState action={<button type="button" onClick={onClick}>新建就诊人</button>} />)

    expect(screen.getByText('暂无数据')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '新建就诊人' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('给出描述时一并显示', () => {
    render(
      <EmptyState
        title="还没有排班"
        description="近两周没有任何医生排班记录"
        action={<button type="button">去排班</button>}
      />,
    )
    expect(screen.getByText('还没有排班')).toBeInTheDocument()
    expect(screen.getByText('近两周没有任何医生排班记录')).toBeInTheDocument()
  })

  it('动作是必填属性，只写"暂无数据"编译不过', () => {
    // @ts-expect-error 反面清单：空态必带下一步动作，缺 action 应报类型错误
    render(<EmptyState title="暂无数据" />)
    expect(screen.getByText('暂无数据')).toBeInTheDocument()
  })
})
