import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * 列表筛选条件读写 URL（附录 B 第 10 条）。
 *
 * <p><b>为什么地址栏才算数，而不是组件 state</b>：管理员要能把「10 月 3 日 · 心内科 · 待缴费」
 * 这个视图直接发给同事，刷新和后退也不能把筛选丢掉。`page` 那一项由
 * {@code DataTable} 自己维护，本 hook 只改筛选键，两者共用同一份 searchParams。
 *
 * <p><b>换筛选必然回第一页</b>：在第 4 页改筛选，新条件下可能只剩 2 页，
 * 停在第 4 页就是个空表格。所以每次 setFilters 都把 `page` 删掉（{@code DataTable}
 * 读不到 `page` 时按第 1 页处理）。
 */
export function useUrlFilters<K extends string>(keys: readonly K[]) {
  const [searchParams, setSearchParams] = useSearchParams()

  const filters = useMemo(() => {
    const out = {} as Record<K, string>
    for (const key of keys) out[key] = searchParams.get(key) ?? ''
    return out
  }, [searchParams, keys])

  const setFilters = useCallback(
    (patch: Partial<Record<K, string>>) => {
      const next = new URLSearchParams(searchParams)
      for (const key of keys) {
        const value = patch[key]
        if (value === undefined || value === '') next.delete(key)
        else next.set(key, value)
      }
      next.delete('page')
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams, keys],
  )

  const resetFilters = useCallback(() => {
    const next = new URLSearchParams(searchParams)
    for (const key of keys) next.delete(key)
    next.delete('page')
    setSearchParams(next, { replace: true })
  }, [searchParams, setSearchParams, keys])

  const hasAnyFilter = useMemo(() => Object.values(filters).some((v) => v !== ''), [filters])

  return { filters, setFilters, resetFilters, hasAnyFilter }
}
