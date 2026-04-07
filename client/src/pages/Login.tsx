import { useEffect, useRef, useState } from 'react'
import { useAuth } from '../context/useAuth'
import { getErrorMessage } from '../services/error'
import '../styles/auth.css'

interface LoginProps {
  onSuccess?: () => void;
  onGoToRegister?: () => void;
}

export default function Login({ onSuccess = () => {}, onGoToRegister = () => {} }: LoginProps) {
  const { completeGoogleLogin, login, startGoogleLogin } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [apiError, setApiError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const oauthHandledRef = useRef(false)

  useEffect(() => {
    if (typeof window === 'undefined' || window.location.pathname !== '/oauth2/callback') {
      return
    }

    if (oauthHandledRef.current) {
      return
    }
    oauthHandledRef.current = true

    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const callbackError = params.get('error_description') || params.get('error')

    window.history.replaceState({}, document.title, '/login')

    if (callbackError) {
      setApiError(callbackError)
      return
    }

    if (!code) {
      setApiError('Google sign-in could not be completed.')
      return
    }

    setIsLoading(true)
    setApiError('')

    void completeGoogleLogin(code)
      .then(() => {
        onSuccess()
      })
      .catch((error: unknown) => {
        setApiError(getErrorMessage(error, 'Google sign-in failed. Please try again.'))
      })
      .finally(() => {
        setIsLoading(false)
      })
  }, [completeGoogleLogin, onSuccess])

  const validateForm = () => {
    const newErrors: { email?: string; password?: string } = {}
    
    if (!email) {
      newErrors.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Enter a valid email address'
    }
    
    if (!password) {
      newErrors.password = 'Password is required'
    } else if (password.length < 6) {
      newErrors.password = 'Password must be at least 6 characters'
    }
    
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!validateForm()) return
    
    setIsLoading(true)
    setApiError('')
    
    try {
      await login(email, password)
      onSuccess()
    } catch (error: unknown) {
      setApiError(getErrorMessage(error, 'Login failed. Please check your credentials.'))
    } finally {
      setIsLoading(false)
    }
  }

  const handleGoogleSignIn = () => {
    setApiError('')
    startGoogleLogin()
  }

  return (
    <div className="auth-container">
      {/* Left side - Image */}
      <div className="auth-image">
        <img 
          src="/billiard-hall.jpg" 
          alt="Premium billiard hall" 
          className="image-cover"
        />
        <div className="image-overlay"></div>
        <div className="image-content">
          <h2>Welcome Back</h2>
          <p>Book tables, browse the menu, and stay connected with the shop</p>
        </div>
      </div>

      {/* Right side - Form */}
      <div className="auth-form-container">
        <div className="auth-form-wrapper">
          <div className="auth-header">
            <h1>Sign In</h1>
            <p className="subtitle">
              Access your billiard account
            </p>
          </div>

          {apiError && (
            <div className="api-error" style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', marginBottom: '16px', fontSize: '14px' }}>
              {apiError}
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            {/* Email/Username Field */}
            <div className="form-group">
              <label htmlFor="email" className="form-label">
                Email
              </label>
              <input
                id="email"
                type="text"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value)
                  if (errors.email) setErrors({ ...errors, email: '' })
                }}
                placeholder="Enter your email address"
                className={`form-input ${errors.email ? 'input-error' : ''}`}
              />
              {errors.email && (
                <span className="error-message">{errors.email}</span>
              )}
            </div>

            {/* Password Field */}
            <div className="form-group">
              <label htmlFor="password" className="form-label">
                Password
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  if (errors.password) setErrors({ ...errors, password: '' })
                }}
                placeholder="Enter your password"
                className={`form-input ${errors.password ? 'input-error' : ''}`}
              />
              {errors.password && (
                <span className="error-message">{errors.password}</span>
              )}
            </div>

            <div className="form-footer">
              <a href="#forgot" className="forgot-link">
                Forgot password?
              </a>
            </div>

            {/* Submit Button */}
            <button 
              type="submit" 
              className="button button-primary"
              disabled={isLoading}
            >
              {isLoading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <div className="auth-divider">
            <span>or</span>
          </div>

          <button
            type="button"
            className="button button-secondary"
            onClick={handleGoogleSignIn}
            disabled={isLoading}
          >
            <span className="google-mark" aria-hidden="true">G</span>
            Continue with Google
          </button>

          <div className="auth-switch">
            <p>New here? <button type="button" className="auth-link-button" onClick={onGoToRegister}>Create a customer account</button></p>
            <p>Staff and admin users can still sign in here with their existing accounts.</p>
          </div>
        </div>
      </div>
    </div>
  )
}
