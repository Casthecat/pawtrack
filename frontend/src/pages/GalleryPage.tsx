import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowDown, Heart, Search, Sparkles } from 'lucide-react'
import { api } from '@/api/pawtrack'
import { CatCard } from '@/components/CatCard'
import { ErrorState, LoadingState } from '@/components/QueryState'

export function GalleryPage() {
  const query = useQuery({ queryKey: ['cats'], queryFn: api.cats })
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const cats = query.data || []
  const ready = cats.filter(c => ['NORMAL', 'ADOPTABLE'].includes(c.status))
  const shown = cats.filter(c => c.name.toLowerCase().includes(search.toLowerCase().trim()))
    .filter(c => filter === 'all' || (filter === 'ready' ? ['NORMAL', 'ADOPTABLE'].includes(c.status) : c.status === 'ADOPTED'))
  return <>
    <section className="hero">
      <div className="hero-copy"><p className="eyebrow"><span className="tiny-dot" /> SMALL PAWS. BIG POSSIBILITIES.</p>
        <h1>A new chapter.<br /><span>A friend for life.</span></h1>
        <p>Every cat has a story. Get to know the little personalities in our care, and be part of their next chapter.</p>
        <a href="#cats" className="button primary">Find your companion <ArrowDown size={17} /></a>
      </div>
      <div className="hero-aside"><Heart size={33} strokeWidth={1.4} /><p>A little patience.<br />A lot of love.</p><span>Good things start with a hello.</span><Sparkles className="hero-sparkle" size={36} strokeWidth={1} /></div>
    </section>
    <section id="cats" className="gallery-section">
      <div className="section-heading"><div><p className="eyebrow">THE PAWTRACK FAMILY</p><h2>Say hello to someone special.</h2></div>
        {query.isSuccess && <p className="count-label">{ready.length} ready to meet · {cats.length} in our family</p>}
      </div>
      <div className="gallery-toolbar"><div className="filter-group" aria-label="Filter cats">
        {[['all', 'All cats'], ['ready', 'Ready to meet'], ['adopted', 'Found a home']].map(([value, label]) => <button key={value} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}</button>)}
      </div><label className="search-field"><Search size={18} /><input aria-label="Search cats by name" placeholder="Search by name" value={search} onChange={e => setSearch(e.target.value)} /></label></div>
      {query.isPending ? <LoadingState /> : query.isError ? <ErrorState error={query.error} retry={() => void query.refetch()} /> :
        shown.length ? <div className="cat-grid">{shown.map(cat => <CatCard key={cat.id} cat={cat} />)}</div> :
          <div className="empty-state"><Heart size={28} /><h3>{cats.length ? 'No matching companions yet' : 'Our family starts here'}</h3><p>{cats.length ? 'Try another name or filter.' : 'Add a cat from Adoption review to get started.'}</p></div>}
    </section>
  </>
}
