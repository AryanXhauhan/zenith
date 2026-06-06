import { useState } from 'react'
import Dashboard from './components/Dashboard'
import ZenithCheckout from './components/Checkout'
import { ShoppingBag } from 'lucide-react'

function App() {
  const [showCheckout, setShowCheckout] = useState(false);

  return (
    <div className="relative">
      {/* The Dashboard is the main "App" for the merchant */}
      <Dashboard />

      {/* Demo Floating Button to trigger Checkout (Simulating a customer action) */}
      <div className="fixed bottom-8 right-8 z-40 group">
        <div className="absolute -inset-2 bg-gradient-to-r from-purple-600 to-blue-600 rounded-full blur opacity-40 group-hover:opacity-75 transition duration-1000 group-hover:duration-200 animate-pulse"></div>
        <button 
          onClick={() => setShowCheckout(true)}
          className="relative flex items-center gap-2 bg-black text-white px-6 py-4 rounded-full font-bold shadow-2xl border border-white/10 hover:border-white/20 transition"
        >
          <ShoppingBag size={20} className="text-purple-400" />
          Test Checkout Flow
        </button>
      </div>

      {/* Checkout Modal */}
      {showCheckout && (
        <ZenithCheckout 
          amount={49.99} 
          currency="USD" 
          onClose={() => setShowCheckout(false)} 
        />
      )}
    </div>
  )
}

export default App
