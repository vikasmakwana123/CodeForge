import { useState } from 'react'
import { Link } from 'react-router-dom'

const RegisterPage = () => {
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = (event) => {
    event.preventDefault()
  }

  return (
    <main className="auth-page">
      <div className="auth-orbit auth-orbit-blue" />
      <div className="auth-orbit auth-orbit-orange" />
      <section className="auth-panel" aria-labelledby="register-title">
        <Link className="auth-brand" to="/"><span>⌘</span> CodeForge</Link>
        <div className="auth-heading"><span className="auth-kicker">NEW WORKSPACE</span><h1>Start building.</h1><p>Create your account and get your cloud workspace ready.</p></div>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="register-name">Full name</label>
          <input id="register-name" name="name" type="text" placeholder="Alex Morgan" autoComplete="name" required />
          <label htmlFor="register-email">Email address</label>
          <input id="register-email" name="email" type="email" placeholder="you@company.com" autoComplete="email" required />
          <label htmlFor="register-password">Create password</label>
          <div className="password-field"><input id="register-password" name="password" type={showPassword ? 'text' : 'password'} placeholder="At least 8 characters" minLength="8" autoComplete="new-password" required /><button type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? 'Hide' : 'Show'}</button></div>
          <label className="terms-check"><input type="checkbox" required /> <span>I agree to the CodeForge terms and privacy policy.</span></label>
          <button className="auth-submit" type="submit">Create workspace <span>→</span></button>
        </form>
        <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
        <Link className="auth-home" to="/">← Back to home</Link>
      </section>
    </main>
  )
}

export default RegisterPage