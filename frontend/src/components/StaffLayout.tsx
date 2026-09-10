import { NavLink, Outlet } from 'react-router-dom'
import { HeartHandshake, ClipboardList } from 'lucide-react'

export function StaffLayout() {
  return <>
    <nav className="staff-nav" aria-label="Staff workspace">
      <NavLink to="/staff" end><HeartHandshake size={17} aria-hidden="true" />Adoption review</NavLink>
      <NavLink to="/staff/care"><ClipboardList size={17} aria-hidden="true" />Care alerts</NavLink>
    </nav>
    <Outlet />
  </>
}
