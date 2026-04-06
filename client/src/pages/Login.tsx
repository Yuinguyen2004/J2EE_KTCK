import { useState } from 'react'
import { useAuth } from '../context/useAuth'
import { getErrorMessage } from '../services/error'
import '../styles/auth.css'

interface LoginProps {
  onSuccess?: () => void;
}

export default function Login({ onSuccess = () => {} }: LoginProps) {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [apiError, setApiError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

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
          <p>Manage your billiard club operations seamlessly</p>
        </div>
      </div>

      {/* Right side - Form */}
      <div className="auth-form-container">
        <div className="auth-form-wrapper">
          <div className="auth-header">
            <h1>Sign In</h1>
            <p className="subtitle">
              Access your staff dashboard
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

          <div className="auth-switch">
            <p>Staff and admin accounts are provisioned by your administrator.</p>
          </div>
        </div>
      </div>
    </div>
  )
}
