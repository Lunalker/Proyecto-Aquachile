import { Fragment } from 'react';

const rutas = {
  panel: <><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></>,
  usuarios: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></>,
  usuario: <><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></>,
  clipboard: <><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4.5h6M9 11h6M9 15h6"/><path d="M9 2h6v4H9z"/></>,
  mas: <path d="M12 5v14M5 12h14"/>,
  lupa: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
  salir: <><path d="M10 17l5-5-5-5M15 12H3"/><path d="M12 3h6a3 3 0 0 1 3 3v12a3 3 0 0 1-3 3h-6"/></>,
  menu: <path d="M4 6h16M4 12h16M4 18h16"/>,
  cerrar: <path d="M18 6 6 18M6 6l12 12"/>,
  flecha: <path d="M5 12h14m-7-7 7 7-7 7"/>,
  chevron: <path d="m9 18 6-6-6-6"/>,
  calendario: <><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/></>,
  check: <path d="m5 12 4 4L19 6"/>,
  reloj: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
  grafico: <><path d="M4 20V10M10 20V5M16 20v-8M22 20V3"/><path d="M2 20h21"/></>,
  editar: <><path d="m16 4 4 4-12 12H4v-4z"/><path d="m14 6 4 4"/></>,
  documento: <><path d="M7 3h7l5 5v13H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2"/><path d="M14 3v6h5M9 14h6M9 18h6"/></>,
  aviso: <><circle cx="12" cy="12" r="9"/><path d="M12 7v6M12 17h.01"/></>,
  filtro: <><path d="M3 5h18M6 12h12M9 19h6"/></>,
  escudo: <><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11z"/><path d="m9 11 2 2 4-4"/></>,
  olas: <><path d="M2 15c3-3 5-3 8 0s5 3 8 0 4-3 4-3"/><path d="M2 20c3-3 5-3 8 0s5 3 8 0 4-3 4-3"/><path d="M2 10c3-3 5-3 8 0s5 3 8 0 4-3 4-3"/></>,
};

export default function Icono({ nombre, size = 20, className = '' }) {
  return <svg aria-hidden="true" className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><Fragment>{rutas[nombre] ?? rutas.documento}</Fragment></svg>;
}
