import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { apiError } from '@/api/axiosInstance'
import { currentUserKey, useCurrentUser } from '@/hooks/useCurrentUser'
import { safeReturnPath } from '@/hooks/safeReturnPath'
import { ErrorState, LoadingState } from '@/components/QueryState'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const user = useCurrentUser()
  const location = useLocation()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const intended = safeReturnPath(location.state?.from)
  const login = useMutation({
    mutationFn: authApi.login,
    gcTime: 0,
    onSuccess: account => {
      setPassword('')
      cache.removeQueries({ predicate: query => query.queryKey[0] !== 'current-user' })
      cache.setQueryData(currentUserKey, account)
      navigate(intended ?? (account.role === 'STAFF' ? '/staff' : '/applications'), { replace: true })
    },
    onError: () => setPassword(''),
  })
  // Error state is rendered by the mutation; credentials are never persisted outside component memory.
  if (user.isPending) return <LoadingState />
  if (user.isError) return <ErrorState error={user.error} retry={() => void user.refetch()} />
  if (user.data) return <Navigate to={intended ?? (user.data.role === 'STAFF' ? '/staff' : '/applications')} replace />
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!login.isPending && email.trim() && password) login.mutate({ email: email.trim(), password })
  }
  return <section className="login-panel">
    <p className="eyebrow">PAWTRACK ACCOUNT</p><h1>Sign in</h1>
    <p>{intended ? (intended.startsWith('/staff') ? 'Sign in to continue to the staff workspace.' : 'Sign in to continue your adoption journey.') : 'Welcome back. Sign in with your PawTrack account.'}</p>
    <form onSubmit={submit}>
      <label>Email<input type="email" required autoComplete="username" maxLength={200} value={email} onChange={event => setEmail(event.target.value)} /></label>
      <label>Password<input type="password" required autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} /></label>
      {login.isError && <p className="form-error" role="alert">{apiError(login.error)}</p>}
      <button className="button primary full-width" disabled={login.isPending || !email.trim() || !password}>{login.isPending ? 'Signing in…' : 'Sign in'}</button>
    </form>
    <p className="muted">Local demo credentials are documented in the project README. Account registration is not available yet.</p>
    <Link className="text-button" to="/">Continue browsing cats →</Link>
  </section>
}
