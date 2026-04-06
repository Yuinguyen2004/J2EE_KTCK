import '../styles/auth.css'

export default function Register() {
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
          <h2>Join Our Team</h2>
          <p>Get started with your billiard club management account</p>
        </div>
      </div>

      <div className="auth-form-container">
        <div className="auth-form-wrapper">
          <div className="auth-header">
            <h1>Access Is Managed</h1>
            <p className="subtitle">
              Staff and admin accounts must be created in the server by an administrator.
            </p>
          </div>
          <div className="auth-switch">
            <p>Use an existing staff or admin email to sign in to this operations client.</p>
          </div>
        </div>
      </div>
    </div>
  )
}
