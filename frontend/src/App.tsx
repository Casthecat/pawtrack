import { useEffect } from 'react'
import { NavLink, Route, Routes, Link, useLocation } from 'react-router-dom'
import { PawPrint, ArrowUpRight } from 'lucide-react'
import { GalleryPage } from '@/pages/GalleryPage'
import { CatDetailPage } from '@/pages/CatDetailPage'
import { ReviewPage } from '@/pages/ReviewPage'
import { CarePage } from '@/pages/CarePage'
import { StaffLayout } from '@/components/StaffLayout'
import { ApplicationPage } from '@/pages/ApplicationPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

export default function App() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }) }, [pathname])
  return <div className="app">
    <a className="skip-link" href="#main">Skip to content</a>
    <div className="demo-banner">Portfolio demo · Use sample contact details. Staff access is open for this local preview.</div>
    <header className="site-header">
      <Link to="/" className="brand"><span className="brand-mark"><PawPrint size={23} /></span>PawTrack<span className="brand-dot">.</span></Link>
      <nav aria-label="Main navigation"><NavLink to="/" end>Meet the cats</NavLink><NavLink to="/staff">Staff workspace <ArrowUpRight size={15} /></NavLink></nav>
    </header>
    <main id="main" className="page-shell">
      <Routes>
        <Route path="/" element={<GalleryPage />} />
        <Route path="/cats/:id" element={<CatDetailPage />} />
        <Route path="/applications/:id" element={<ApplicationPage />} />
        <Route path="/staff" element={<StaffLayout />}>
          <Route index element={<ReviewPage />} />
          <Route path="care" element={<CarePage />} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </main>
    <footer><Link to="/" className="footer-brand"><PawPrint size={17} />PawTrack</Link><span>Better care. More happy beginnings.</span><span>Shelter care & adoption</span></footer>
  </div>
}
