import { useEffect, useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import LandingPage from './page/LandingPage'
import LoginPage from './components/LoginPage'
import RegisterPage from './components/RegisterPage'
import ForgotPasswordPage from './components/ForgotPasswordPage'
import CreateProjectPage from './page/CreateProjectPage'
import PlaygroundPage from './page/PlaygroundPage'

function App() {
  const [introStep, setIntroStep] = useState(0)

  useEffect(() => {
    const firstStep = setTimeout(() => setIntroStep(1), 1000)
    const secondStep = setTimeout(() => setIntroStep(2), 2000)
    const finishIntro = setTimeout(() => setIntroStep(3), 3200)

    return () => {
      clearTimeout(firstStep)
      clearTimeout(secondStep)
      clearTimeout(finishIntro)
    }
  }, [])

  return (
    <BrowserRouter>
      {introStep < 3 ? (
        <div className="intro-screen">
          <h1 className="intro-word">
            {introStep === 0 ? 'Welcome' : introStep === 1 ? 'to' : 'CodeForge'}
          </h1>
          <div className="intro-line" />
        </div>
      ) : (
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/create" element={<CreateProjectPage />} />
          <Route path="/playground/:projectId?" element={<PlaygroundPage />} />
          <Route path="*" element={<LandingPage />} />
        </Routes>
      )}
    </BrowserRouter>
  )
}

export default App
