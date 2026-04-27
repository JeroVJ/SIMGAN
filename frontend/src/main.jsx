import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import App from './App'
import './index.css'
import './components/components.css'
import './styles/phase4.css'
import './styles/phase6.css'
import './styles/phase7.css'
<<<<<<< HEAD
import './styles/overhaul.css'
=======
>>>>>>> origin/procesamiento

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
      <Toaster position="top-right" />
    </BrowserRouter>
  </React.StrictMode>
)
