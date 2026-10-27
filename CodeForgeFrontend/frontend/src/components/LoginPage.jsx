import { useState } from 'react'
import { Link } from 'react-router-dom'

const LoginPage = () => {
  const [showPassword, setShowPassword] = useState(false)

  const handleSubmit = (event) => {
    event.preventDefault()
  }

  return (
    <main className="auth-page">
      <div className="auth-orbit auth-orbit-blue" />
      <div className="auth-orbit auth-orbit-orange" />
      <section className="auth-panel" aria-labelledby="login-title">
        <Link className="auth-brand" to="/"><span>⌘</span> CodeForge</Link>
        <div className="auth-heading"><span className="auth-kicker">WELCOME BACK </span><h1 id="login-title">Back to building.</h1><p>Sign in to return to your cloud workspace.</p></div>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="login-email">Email address</label>
          <input id="login-email" name="email" type="email" placeholder="you@company.com" autoComplete="email" className="text-[80px]" required />
          <div className="label-row"><label htmlFor="login-password">Password</label></div>
          <div className="password-field"><input id="login-password" name="password" type={showPassword ? 'text' : 'password'} placeholder="Enter your password" autoComplete="current-password" required /><button type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? 'Hide' : 'Show'}</button></div>
          <div className="flex justify-between"><span></span><Link to="/forgot-password" className="text-[#007bffe0] hover:text-[#0056b3] text-md">Forgot password?</Link></div>
          <button className="auth-submit" type="submit">Sign in <span>→</span></button>
        </form>
        <p className="auth-switch">New to CodeForge? <Link to="/register">Create an account</Link></p>
        <Link className="auth-home" to="/">← Back to home</Link>
      </section>
    </main>
  )
}

export default LoginPage