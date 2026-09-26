import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import PatientCell from '@/components/business/PatientCell'

describe('PatientCell', () => {
  it('头像+姓名+就诊卡号三件套齐全', () => {
    render(<PatientCell name="张伟" cardNo="1000000001" avatarUrl="/img/zhangwei.png" />)

    expect(screen.getByText('张伟')).toBeInTheDocument()
    expect(screen.getByText('就诊卡号 1000000001')).toBeInTheDocument()
    const img = screen.getByRole('img', { name: '张伟' })
    expect(img).toHaveAttribute('src', '/img/zhangwei.png')
  })

  it('无头像时用姓名首字兜底', () => {
    render(<PatientCell name="李娜" cardNo="1000000002" />)

    expect(screen.queryByRole('img')).toBeNull()
    expect(screen.getByText('李')).toBeInTheDocument()
  })

  it('卡号缺失显示破折号而不是空白', () => {
    render(<PatientCell name="王强" cardNo={null} />)
    expect(screen.getByText('就诊卡号 —')).toBeInTheDocument()
  })
})
