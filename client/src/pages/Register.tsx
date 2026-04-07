import { useState } from 'react'
import { useAuth } from '../context/useAuth'
import { getErrorMessage } from '../services/error'
import '../styles/auth.css'

interface RegisterProps {
  onSuccess?: () => void;
  onGoToLogin?: () => void;
}

export default function Register({ onSuccess = () => {}, onGoToLogin = () => {} }: RegisterProps) {
  const { register, startGoogleLogin } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const validateForm = () => {
    const nextErrors: Record<string, string> = {}

    if (!fullName.trim()) {
      nextErrors.fullName = 'Full name is required'
    }

    if (!email) {
      nextErrors.email = 'Email is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      nextErrors.email = 'Enter a valid email address'
    }

    if (!password) {
      nextErrors.password = 'Password is required'
    } else if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,72}$/.test(password)) {
      nextErrors.password = 'Use 8-72 chars with uppercase, lowercase, and a digit'
    }

    if (!confirmPassword) {
      nextErrors.confirmPassword = 'Please confirm your password'
    } else if (confirmPassword !== password) {
      nextErrors.confirmPassword = 'Passwords do not match'
    }

    if (phone.trim() && phone.trim().length > 20) {
      nextErrors.phone = 'Phone number must be 20 characters or fewer'
    }

    setErrors(nextErrors)
    return Object.keys(nextErrors).length === 0
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!validateForm()) {
      return
    }

    setIsLoading(true)
    setApiError('')

    try {
      await register({
        fullName,
        email,
        phone,
        password,
      })
      onSuccess()
    } catch (error: unknown) {
      setApiError(getErrorMessage(error, 'Registration failed. Please try again.'))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="auth-container">
      <div className="auth-image">
        <img 
          src="/billiard-hall.jpg" 
          alt="Premium billiard hall" 
          className="image-cover"
        />
        <div className="image-overlay"></div>
        <div className="image-content">
          <h2>Join The Club</h2>
          <p>Create a customer account to request reservations and chat with the shop</p>
        </div>
      </div>

      <div className="auth-form-container">
        <div className="auth-form-wrapper">
          <div className="auth-header">
            <h1>Create Account</h1>
            <p className="subtitle">
              Register as a customer in a few quick steps.
            </p>
          </div>

          {apiError && (
            <div className="api-error" style={{ color: '#ef4444', background: 'rgba(239,68,68,0.1)', padding: '12px', borderRadius: '8px', marginBottom: '16px', fontSize: '14px' }}>
              {apiError}
            </div>
          )}

          <form onSubmit={handleSubmit} className="auth-form">
            <div className="form-group">
              <label htmlFor="register-full-name" className="form-label">Full name</label>
              <input
                id="register-full-name"
                type="text"
                value={fullName}
                onChange={(event) => {
                  setFullName(event.target.value)
                  if (errors.fullName) setErrors({ ...errors, fullName: '' })
                }}
                placeholder="Enter your full name"
                className={`form-input ${errors.fullName ? 'input-error' : ''}`}
              />
              {errors.fullName && <span className="error-message">{errors.fullName}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="register-email" className="form-label">Email</label>
              <input
                id="register-email"
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value)
                  if (errors.email) setErrors({ ...errors, email: '' })
                }}
                placeholder="Enter your email address"
                className={`form-input ${errors.email ? 'input-error' : ''}`}
              />
              {errors.email && <span className="error-message">{errors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="register-phone" className="form-label">Phone</label>
              <input
                id="register-phone"
                type="tel"
                value={phone}
                onChange={(event) => {
                  setPhone(event.target.value)
                  if (errors.phone) setErrors({ ...errors, phone: '' })
                }}
                placeholder="Optional phone number"
                className={`form-input ${errors.phone ? 'input-error' : ''}`}
              />
              {errors.phone && <span className="error-message">{errors.phone}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="register-password" className="form-label">Password</label>
              <input
                id="register-password"
                type="password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value)
                  if (errors.password) setErrors({ ...errors, password: '' })
                }}
                placeholder="Create a strong password"
                className={`form-input ${errors.password ? 'input-error' : ''}`}
              />
              {errors.password && <span className="error-message">{errors.password}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="register-confirm-password" className="form-label">Confirm password</label>
              <input
                id="register-confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(event) => {
                  setConfirmPassword(event.target.value)
                  if (errors.confirmPassword) setErrors({ ...errors, confirmPassword: '' })
                }}
                placeholder="Re-enter your password"
                className={`form-input ${errors.confirmPassword ? 'input-error' : ''}`}
              />
              {errors.confirmPassword && <span className="error-message">{errors.confirmPassword}</span>}
            </div>

            <button
              type="submit"
              className="button button-primary"
              disabled={isLoading}
            >
              {isLoading ? 'Creating account...' : 'Create Account'}
            </button>
          </form>

          <div className="auth-divider">
            <span>or</span>
          </div>

          <button
            type="button"
            className="button button-secondary"
            onClick={startGoogleLogin}
            disabled={isLoading}
          >
            <span className="google-mark" aria-hidden="true">G</span>
            Continue with Google
          </button>
          <div className="auth-switch">
            <p>Already have an account? <button type="button" className="auth-link-button" onClick={onGoToLogin}>Sign in</button></p>
          </div>
        </div>
      </div>
    </div>
  )
}
