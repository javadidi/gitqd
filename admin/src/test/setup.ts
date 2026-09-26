import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// vitest 的 globals 关闭了，@testing-library/react 的自动 cleanup 也不会注册，
// 前一个用例的 DOM 会留到下一个用例里，导致 getByText 查出多个元素。
afterEach(() => {
  cleanup()
})
