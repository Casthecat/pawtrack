import { Cat as CatIcon, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useState } from 'react'
import type { Cat } from '@/api/types'
import { imageSource } from '@/api/axiosInstance'
import { StatusBadge } from './StatusBadge'

export function CatPortrait({ cat }: { cat: Cat }) {
  const [failed, setFailed] = useState(false)
  const src = imageSource(cat.imageUrl)
  return <div className={`cat-portrait tone-${cat.id % 4}`}>
    {src && !failed
      ? <img alt={cat.name} src={src} onError={() => setFailed(true)} />
      : <><div className="portrait-ring" /><CatIcon strokeWidth={1} aria-hidden="true" /><span className="portrait-label">A little friend. A new beginning.</span></>}
  </div>
}

export function CatCard({ cat }: { cat: Cat }) {
  return <Link to={`/cats/${cat.id}`} className="cat-card" aria-label={`Meet ${cat.name}`}>
    <CatPortrait cat={cat} />
    <div className="cat-card-body"><StatusBadge status={cat.status} />
      <div className="cat-card-title"><h3>{cat.name}</h3><ArrowUpRight size={22} /></div>
      <p>{cat.status === 'ADOPTED' ? 'A happy new chapter has started.' : 'Get to know your potential companion.'}</p>
    </div>
  </Link>
}
