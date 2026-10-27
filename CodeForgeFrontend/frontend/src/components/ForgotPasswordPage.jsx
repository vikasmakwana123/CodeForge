import { useState } from 'react'
import { Link } from 'react-router-dom'
import './ForgotPasswordPage.css'

function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)

  const handleSubmit = (event) => {
    event.preventDefault()
    setSubmitted(true)
  }

  return (
    <main className="auth-page forgot-page">
      <div className="auth-orbit auth-orbit-blue" />
      <div className="auth-orbit auth-orbit-orange" />
      <section className="auth-panel" aria-labelledby="forgot-title">
        <Link className="auth-brand" to="/"><span>⌘</span> CodeForge</Link>
        <div className="auth-heading">
          <span className="auth-kicker">ACCOUNT RECOVERY </span>
          <h1 id="forgot-title">Reset your access.</h1>
          <p>Enter your email and we will send a secure link to get you back into your workspace.</p>
        </div>
        {submitted ? (
          <div className="forgot-success" role="status">
            <span className="forgot-success-mark">✓</span>
            <strong>Check your inbox.</strong>
            <p>If an account exists for that email, a reset link is on its way.</p>
            <Link className="auth-submit forgot-return" to="/login">Return to sign in <span>→</span></Link>
          </div>
        ) : (
          <form className="auth-form" onSubmit={handleSubmit}>
            <label htmlFor="forgot-email">Email address</label>
            <input id="forgot-email" name="email" type="email" placeholder="you@company.com" autoComplete="email" required />
            <button className="auth-submit" type="submit">Send reset link <span>→</span></button>
          </form>
        )}
        {!submitted && <p className="auth-switch">Remember your password? <Link to="/login">Sign in</Link></p>}
        <Link className="auth-home" to="/">← Back to home</Link>
      </section>
    </main>
  )
}

export default ForgotPasswordPage
