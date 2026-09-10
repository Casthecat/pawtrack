import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-dvh max-w-2xl flex-col items-start justify-center gap-3 p-6">
      <h1 className="text-2xl font-semibold">404</h1>
      <p className="text-sm text-muted-foreground">页面不存在。</p>
      <Link className="text-sm underline underline-offset-4" to="/">
        返回首页
      </Link>
    </main>
  )
}

