import { ReactNode } from 'react'

export default function PageHeader({ title, description, action }: { title: string, description: string, action?: ReactNode }) {
  return (
    <header className="mb-8 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
      <div><h1 className="text-2xl font-bold tracking-[-.025em] text-slate-950 sm:text-[28px]">{title}</h1><p className="mt-1.5 text-sm leading-6 text-slate-500">{description}</p></div>
      {action}
    </header>
  )
}
