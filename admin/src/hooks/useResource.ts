import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '@/api/client'

interface ResourceState<T> {
  data: T | null
  error: string | null
  loading: boolean
  reload: () => void
}

/**
 * 一个依赖数组驱动一次取数：列表筛选变了就重取，详情 id 变了也重取。
 *
 * <p><b>为什么自带 cancelled 标志</b>：筛选连点两下会并发两个请求，晚到的那个
 * 若直接 setState，页面就停在旧条件下——这是"数据看起来对但和地址栏不一致"的那类 bug。
 *
 * <p><b>错误文案取后端原话</b>：{@code GlobalExceptionHandler} 把 BizException 翻译成
 * HTTP 200 + body.code + 人话 message，{@code ApiError.message} 就是那句人话
 * （例如"该班还有 2 条未取消的预约，请先停诊"）。前端不重写一套错误字典。
 */
export function useResource<T>(loader: () => Promise<T>, deps: unknown[]): ResourceState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [nonce, setNonce] = useState(0)

  const reload = useCallback(() => setNonce((value) => value + 1), [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    loader()
      .then((value) => {
        if (!cancelled) setData(value)
      })
      .catch((cause: unknown) => {
        if (cancelled) return
        // ApiError 的 message 就是后端的人话（业务码 ≠200 时由 client 抛出）；
        // 走到 else 说明是网络层失败，后端根本没答话，这时才给通用兜底。
        setError(cause instanceof ApiError ? cause.message : '加载失败，请确认后端服务在运行')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
    // deps 由调用方按页面自己的筛选键给出，loader 每次渲染都是新闭包——
    // 这两条都不是遗漏，是本 hook 的取数契约，所以整行豁免。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce])

  return { data, error, loading, reload }
}
