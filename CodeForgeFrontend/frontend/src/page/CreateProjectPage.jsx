import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import api from '../api'
import './CreateProjectPage.css'

const projectOptions = [
  { value: 'react', label: 'React', detail: 'Vite + React' },
  { value: 'angular', label: 'Angular', detail: 'Angular CLI' },
  { value: 'vue', label: 'Vue', detail: 'Vite + Vue' },
  { value: 'next', label: 'Next.js', detail: 'App Router' },
  { value: 'node', label: 'Node.js', detail: 'Express API' },
  { value: 'spring', label: 'Spring Boot', detail: 'Java + Maven' },
  { value: 'vite', label: 'Vite', detail: 'Vanilla starter' },
]

function CreateProjectPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const initialType = location.state?.projectType || 'react'
  const [appName, setAppName] = useState('')
  const [appType, setAppType] = useState(initialType)
  const [status, setStatus] = useState({ type: '', message: '' })
  const [isCreating, setIsCreating] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setIsCreating(true)
    setStatus({ type: '', message: '' })

    try {
      const response = await api.post('/api/projects', {
        name: appName.trim(),
        type: appType,
      })
      const projectId = response.data.id
      if (!projectId) throw new Error('The server did not return a project ID.')

      let project = response.data
      while (project.status === 'CREATING') {
        setStatus({ type: 'success', message: 'Project is being provisioned...' })
        await new Promise((resolve) => setTimeout(resolve, 2000))
        const statusResponse = await api.get(`/api/projects/${projectId}`)
        project = statusResponse.data
      }

      if (project.status === 'FAILED') {
        throw new Error(project.errorMessage || 'Project provisioning failed.')
      }
      if (project.status !== 'READY') {
        throw new Error(`Unexpected project status: ${project.status}`)
      }
      navigate(`/playground/${projectId}`)
    } catch (error) {
      const message = error.response?.data?.detail
        || error.response?.data?.message
        || error.message
        || 'Could not connect to the CodeForge server.'
      setStatus({ type: 'error', message })
    } finally {
      setIsCreating(false)
    }
  }

  return (
    <main className="create-page">
      <div className="auth-orbit auth-orbit-blue" />
      <div className="auth-orbit auth-orbit-orange" />
      <div className="create-grid" />
      <nav className="create-nav"><Link className="auth-brand" to="/"><span>⌘</span> CodeForge</Link><span className="create-nav-label">NEW PROJECT </span><Link className="create-back" to="/">← Back to home</Link></nav>
      <section className="create-layout">
        <div className="create-intro"><span className="auth-kicker">YOUR NEXT WORKSPACE</span><h1>Start with<br /><em>the right stack.</em></h1><p>Choose a technology, give your project a name, and let CodeForge prepare the workspace.</p><div className="create-note"><span>⌁</span><div><strong>Cloud-ready from the first command.</strong><small>Your project is generated inside a private workspace.</small></div></div></div>
        <section className="create-panel" aria-labelledby="create-title"><div className="create-panel-heading"><span className="auth-kicker">PROJECT CONFIGURATION</span><h2 id="create-title">Create a project</h2></div><form className="create-form" onSubmit={handleSubmit}><label htmlFor="app-name">Project name</label><input id="app-name" value={appName} onChange={(event) => setAppName(event.target.value)} placeholder="my-awesome-project" pattern="[A-Za-z][A-Za-z0-9-]{0,39}" title="Start with a letter; use letters, numbers, and hyphens (max 40 characters)" required /><label htmlFor="app-type">Technology</label><select id="app-type" value={appType} onChange={(event) => setAppType(event.target.value)}>{projectOptions.map((option) => <option value={option.value} key={option.value}>{option.label} — {option.detail}</option>)}</select><button className="auth-submit" type="submit" disabled={isCreating}>{isCreating ? 'Creating workspace...' : 'Create workspace'} <span>→</span></button>{status.message && <p className={`create-status ${status.type}`} role="status">{status.message}</p>}</form></section>
      </section>
    </main>
  )
}

export default CreateProjectPage
