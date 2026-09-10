import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Heart, ShieldCheck, Thermometer } from 'lucide-react'
import { api } from '@/api/pawtrack'
import { apiError } from '@/api/axiosInstance'
import { CatPortrait } from '@/components/CatCard'
import { CatStatusBadges } from '@/components/CatStatusBadges'
import { LoadingState, ErrorState } from '@/components/QueryState'

export function CatDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const cache = useQueryClient()
  const query = useQuery({ queryKey: ['cat', id], queryFn: () => api.cat(id) })
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [notes, setNotes] = useState('')
  const apply = useMutation({
    mutationFn: api.apply,
    onSuccess: async application => {
      await cache.invalidateQueries({ queryKey: ['applications'] })
      navigate(`/applications/${application.id}`, { state: { submitted: true } })
    },
    onError: () => { void cache.invalidateQueries({ queryKey: ['cat', id] }) },
  })
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} retry={() => void query.refetch()} />
  const cat = query.data
  const available = cat.adoptionStatus === 'AVAILABLE' && cat.healthStatus === 'NORMAL' && !cat.hasActiveAlert
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim() || !email.trim()) return
    apply.mutate({ catId: cat.id, adopterName: name.trim(), adopterEmail: email.trim(), notes: notes.trim() })
  }
  return <>
    <Link className="back-link" to="/"><ArrowLeft size={16} />Back to the cats</Link>
    <div className="detail-layout">
      <div><CatPortrait key={cat.imageUrl} cat={cat} /><div className="care-summary"><ShieldCheck size={22} /><div><strong>Thoughtful care, every day.</strong><p>Our team reviews each application with the cat's wellbeing in mind.</p></div></div></div>
      <div className="detail-copy"><p className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</p><h1>Meet {cat.name}.</h1><CatStatusBadges cat={cat} />
        <p className="detail-intro">A familiar face, a quiet companion, a new member of the family. Tell us a little about yourself to start an adoption conversation.</p>
        <div className="health-strip"><Thermometer size={18} /><span>Latest recorded temperature</span><strong>{cat.temperatureC == null ? 'Not recorded' : `${cat.temperatureC.toFixed(2)} °C`}</strong></div>
        {cat.hasActiveAlert && <p className="notice">{cat.adoptionStatus === 'ADOPTED' ? 'Our team is reviewing a care alert. This cat has already found a home.' : 'Our team is reviewing a care alert. Adoption is paused until the review is complete.'}</p>}
        {available ? <form className="application-form" onSubmit={submit}>
          <h2><Heart size={20} />Make the first introduction.</h2>
          <p className="muted">Submitting an application starts a review with the shelter team.</p>
          <div className="form-row"><label>Your name<input required maxLength={120} autoComplete="name" value={name} onChange={e => setName(e.target.value)} placeholder="Alex Morgan" /></label>
          <label>Email address<input required type="email" maxLength={200} autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="alex@example.com" /></label></div>
          <label>A little about your home <span className="optional">(optional)</span><textarea maxLength={2000} rows={4} value={notes} onChange={e => setNotes(e.target.value)} placeholder="Tell us about your home, other pets, and what you're looking for in a companion." /></label>
          <button className="button primary full-width" disabled={apply.isPending || !name.trim() || !email.trim()}>{apply.isPending ? 'Sending your application…' : 'Send adoption application'}<Heart size={17} /></button>
          <small>Local portfolio demo — please use sample contact details.</small>
        </form> : <div className="notice"><strong>{cat.adoptionStatus === 'ADOPTED' ? 'A home has been found.' : 'Taking a little time for care.'}</strong><p>{cat.adoptionStatus === 'ADOPTED' ? 'Explore the other cats waiting for their next chapter.' : 'Applications will reopen when the shelter team confirms this cat is ready.'}</p><Link to="/">Meet other cats →</Link></div>}
        {apply.isError && <p className="form-error" role="alert">{apiError(apply.error)}</p>}
      </div>
    </div>
  </>
}
