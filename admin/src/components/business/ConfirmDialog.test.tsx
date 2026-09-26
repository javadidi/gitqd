import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ConfirmDialog from '@/components/business/ConfirmDialog'

describe('ConfirmDialog', () => {
  it('open=false 时不向文档里挂任何内容', () => {
    render(
      <ConfirmDialog open={false} onOpenChange={vi.fn()} title="确认退款" onConfirm={vi.fn()} />,
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('不要求原因时直接确认，回调收到 undefined', async () => {
    const onConfirm = vi.fn()
    render(<ConfirmDialog open onOpenChange={vi.fn()} title="确认退款" onConfirm={onConfirm} />)

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '确认' }))
    expect(onConfirm).toHaveBeenCalledWith(undefined)
  })

  it('requireReason 时原因为空则确认按钮禁用，填入后才放行且带上首尾去空的文本', async () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog
        open
        onOpenChange={vi.fn()}
        title="确认驳回退款"
        requireReason
        reasonLabel="驳回原因"
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByText('驳回原因')).toBeInTheDocument()
    const confirmBtn = screen.getByRole('button', { name: '确认' })
    expect(confirmBtn).toBeDisabled()

    await userEvent.type(screen.getByRole('textbox'), '  材料不齐  ')
    await waitFor(() => expect(confirmBtn).toBeEnabled())
    await userEvent.click(confirmBtn)
    expect(onConfirm).toHaveBeenCalledWith('材料不齐')
  })

  it('取消走 onOpenChange(false)，不触发确认', async () => {
    const onOpenChange = vi.fn()
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog
        open
        onOpenChange={onOpenChange}
        title="确认退款"
        onConfirm={onConfirm}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)
    expect(onConfirm).not.toHaveBeenCalled()
  })
})
