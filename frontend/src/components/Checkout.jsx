import React, { useState } from 'react';
import { 
  ShieldCheck, 
  Lock, 
  X, 
  Loader2,
  CheckCircle2,
  ArrowRight
} from 'lucide-react';
import axios from 'axios';
import { motion, AnimatePresence } from 'framer-motion';

const API_BASE = 'http://localhost:8080/api/v1';

const ZenithCheckout = ({ amount: initialAmount, currency = "USD", on处理 = () => {}, onClose = () => {} }) => {
  const [step, setStep] = useState('select'); // select, pay, processing, success
  const [method, setMethod] = useState(null); // upi, card, web3, emi
  const [error, setError] = useState(null);
  const [amount, setAmount] = useState(initialAmount);
  const [cardData, setCardData] = useState({ number: '', name: '', expiry: '', cvv: '' });
  const [qrScanned, setQrScanned] = useState(false);
  const [selectedEmiMonths, setSelectedEmiMonths] = useState(3);

  const platformFee = 0.05;

  const paymentMethods = [
    { id: 'upi', name: 'UPI / QR', icon: '📱', desc: 'Scan & Pay via GPay, PhonePe' },
    { id: 'card', name: 'Credit / Debit Card', icon: '💳', desc: 'Visa, Mastercard, RuPay' },
    { id: 'web3', name: 'Web3 Wallet', icon: '🦊', desc: 'Pay with Crypto (MetaMask)' },
    { id: 'emi', name: 'EMI / Installments', icon: '📅', desc: 'Pay in 3, 6, or 12 months' }
  ];

  const handlePay = async () => {
    if (method === 'card' && (!cardData.number || !cardData.cvv)) {
        setError("Please enter valid card details.");
        return;
    }
    if (method === 'upi' && !qrScanned) {
        setError("Please scan the QR code first.");
        return;
    }

    setStep('processing');
    setError(null);
    try {
      const idempKey = `chk-${Math.random().toString(36).substring(7)}`;
      
      if (method === 'emi') {
          await axios.post(`${API_BASE}/installments?accountNumber=ZNT-002&totalAmount=${amount}&months=${selectedEmiMonths}`, {}, {
            headers: { 'X-Zenith-Key': 'dev-master-key-changeme' }
          });
      }

      const res = await axios.post(`${API_BASE}/ledger/transfer`, {
        idempotencyKey: idempKey,
        sourceAccountNumber: "ZNT-001",
        destAccountNumber: "ZNT-002",
        amount: parseFloat(amount),
        platformFee: platformFee,
        currency: currency,
        description: `Zenith ${method.toUpperCase()} Payment`
      }, {
        headers: { 'X-Zenith-Key': 'dev-master-key-changeme' }
      });

      if (res.status === 200 || res.status === 201) {
        setStep('success');
        setTimeout(() => onClose(), 3000);
      }
    } catch (err) {
      setError("Payment failed. Please check balance.");
      setStep('pay');
    }
  };

  const selectMethod = (m) => {
    setMethod(m);
    setStep('pay');
    setError(null);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md">
      <motion.div 
        initial={{ opacity: 0, scale: 0.9, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        className="w-full max-w-lg bg-[#111] border border-white/10 rounded-[2.5rem] overflow-hidden shadow-2xl"
      >
        {/* Header */}
        <div className="p-6 border-b border-white/5 flex justify-between items-center bg-[#151515]">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-purple-600 rounded-lg flex items-center justify-center">
              <ShieldCheck size={18} className="text-white" />
            </div>
            <div>
              <span className="block font-black text-xs tracking-widest text-white/50 uppercase">Secured by</span>
              <span className="block font-bold text-sm tracking-tight">ZENITH GATEWAY</span>
            </div>
          </div>
          <button onClick={onClose} className="w-8 h-8 rounded-full bg-white/5 flex items-center justify-center text-white/30 hover:text-white transition">
            <X size={18} />
          </button>
        </div>

        <div className="p-8">
          {error && <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-bold rounded-xl text-center uppercase tracking-widest">{error}</div>}
          <AnimatePresence mode="wait">
            {step === 'select' && (
              <motion.div key="select" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }}>
                <div className="mb-8 text-center">
                  <span className="text-white/40 text-xs font-bold uppercase tracking-[0.2em]">Payable Amount</span>
                  <div className="text-4xl font-black mt-2">${(parseFloat(amount) + platformFee).toFixed(2)}</div>
                </div>
                <div className="grid grid-cols-1 gap-3">
                  {paymentMethods.map(m => (
                    <button 
                      key={m.id}
                      onClick={() => selectMethod(m.id)}
                      className="group p-4 bg-white/5 border border-white/5 rounded-2xl flex items-center gap-4 hover:bg-white/10 hover:border-purple-500/50 transition-all text-left"
                    >
                      <div className="text-2xl w-12 h-12 bg-white/5 rounded-xl flex items-center justify-center group-hover:scale-110 transition">
                        {m.icon}
                      </div>
                      <div className="flex-1">
                        <div className="font-bold text-white/90">{m.name}</div>
                        <div className="text-xs text-white/40">{m.desc}</div>
                      </div>
                      <ArrowRight size={16} className="text-white/20 group-hover:translate-x-1 transition group-hover:text-purple-400" />
                    </button>
                  ))}
                </div>
              </motion.div>
            )}

            {step === 'pay' && (
              <motion.div key="pay" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="text-center">
                <button onClick={() => setStep('select')} className="mb-6 text-xs text-purple-400 font-bold uppercase tracking-widest flex items-center justify-center gap-2 mx-auto hover:text-purple-300">
                  <ArrowRight size={14} className="rotate-180" /> Change Method
                </button>
                
                {method === 'upi' && (
                  <div className="space-y-6">
                    <div className="bg-white p-4 rounded-3xl w-48 h-48 mx-auto flex items-center justify-center shadow-lg shadow-purple-500/10">
                      <img src={`https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=upi://pay?pa=zenith@upi&am=${amount}&tn=ZenithOrder`} alt="UPI QR" />
                    </div>
                    <div className="text-sm font-medium text-white/60">Scan with any UPI App to Pay</div>
                    {!qrScanned ? (
                        <button onClick={() => setQrScanned(true)} className="w-full bg-white/10 border border-white/20 text-white py-4 rounded-2xl font-bold hover:bg-white/20 transition">Simulation: Click to Scan</button>
                    ) : (
                        <button onClick={handlePay} className="w-full bg-purple-600 text-white py-4 rounded-2xl font-bold hover:bg-purple-700 transition flex items-center justify-center gap-2">
                           <CheckCircle2 size={18} /> Confirm Payment
                        </button>
                    )}
                  </div>
                )}

                {method === 'card' && (
                  <div className="space-y-4">
                    <div className="bg-gradient-to-br from-gray-800 to-black p-6 rounded-3xl border border-white/10 text-left relative overflow-hidden h-44 flex flex-col justify-between mb-4">
                      <div className="absolute top-[-20%] right-[-10%] w-40 h-40 bg-purple-500/10 rounded-full blur-3xl"></div>
                      <div className="flex justify-between items-start">
                        <div className="w-10 h-8 bg-yellow-500/20 rounded-md border border-yellow-500/30"></div>
                        <span className="font-bold text-xs text-white/30 italic uppercase">Zenith Platinum</span>
                      </div>
                      <div className="text-lg font-mono tracking-[0.2em] text-white/90">
                        {cardData.number ? cardData.number.replace(/(\d{4})/g, '$1 ').trim() : '•••• •••• •••• ••••'}
                      </div>
                      <div className="flex justify-between items-end">
                        <span className="text-[10px] text-white/40 uppercase font-bold tracking-widest">{cardData.name || 'Your Name'}</span>
                        <span className="text-[10px] text-white/40 font-bold">{cardData.expiry || 'MM / YY'}</span>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-1 gap-3">
                        <input type="text" placeholder="Card Number" maxLength="16" onChange={e => setCardData({...cardData, number: e.target.value})} className="w-full bg-white/5 border border-white/10 rounded-xl py-3 px-4 focus:outline-none focus:border-purple-500 transition text-sm" />
                        <input type="text" placeholder="Cardholder Name" onChange={e => setCardData({...cardData, name: e.target.value})} className="w-full bg-white/5 border border-white/10 rounded-xl py-3 px-4 focus:outline-none focus:border-purple-500 transition text-sm" />
                        <div className="grid grid-cols-2 gap-3">
                            <input type="text" placeholder="MM/YY" maxLength="5" onChange={e => setCardData({...cardData, expiry: e.target.value})} className="w-full bg-white/5 border border-white/10 rounded-xl py-3 px-4 focus:outline-none focus:border-purple-500 transition text-sm" />
                            <input type="password" placeholder="CVV" maxLength="3" onChange={e => setCardData({...cardData, cvv: e.target.value})} className="w-full bg-white/5 border border-white/10 rounded-xl py-3 px-4 focus:outline-none focus:border-purple-500 transition text-sm" />
                        </div>
                    </div>
                    
                    <button onClick={handlePay} className="w-full bg-purple-600 text-white py-4 rounded-2xl font-bold hover:bg-purple-700 transition shadow-lg shadow-purple-500/20 mt-2">Pay Securely</button>
                  </div>
                )}

                {method === 'emi' && (
                  <div className="space-y-4">
                    <div className="text-left font-bold text-xs mb-2 text-white/30 uppercase tracking-widest px-2">Select EMI Plan</div>
                    {[3, 6, 12].map(m => (
                      <button 
                        key={m} 
                        onClick={() => { setSelectedEmiMonths(m); handlePay(); }} 
                        className="w-full p-4 bg-white/5 border border-white/5 rounded-2xl flex justify-between items-center hover:bg-purple-500/10 hover:border-purple-500/30 transition group"
                      >
                        <div className="text-left">
                          <div className="font-bold text-white/90 group-hover:text-purple-400 transition">{m} Months</div>
                          <div className="text-[10px] text-white/40 uppercase font-bold tracking-tighter">Interest free installments</div>
                        </div>
                        <div className="text-right">
                          <div className="font-bold text-purple-400">${(amount / m).toFixed(2)}<span className="text-[10px] text-white/30">/mo</span></div>
                        </div>
                      </button>
                    ))}
                  </div>
                )}

                {method === 'web3' && (
                  <div className="py-8 space-y-6">
                    <div className="w-20 h-20 bg-orange-500/10 rounded-full flex items-center justify-center mx-auto border border-orange-500/20">
                      <span className="text-4xl">🦊</span>
                    </div>
                    <div className="text-sm text-white/60">Pay with USDC / ETH via MetaMask</div>
                    <button onClick={handlePay} className="w-full bg-orange-500 text-white py-4 rounded-2xl font-bold hover:bg-orange-600 transition">Connect & Pay</button>
                  </div>
                )}
              </motion.div>
            )}

            {step === 'processing' && (
              <motion.div key="processing" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="py-12 text-center">
                <Loader2 size={48} className="mx-auto text-purple-500 animate-spin mb-6" />
                <h3 className="text-xl font-bold mb-2">Securing Transaction</h3>
                <p className="text-white/50 text-sm">Validating via Zenith Omni-Channel Mesh...</p>
              </motion.div>
            )}

            {step === 'success' && (
              <motion.div key="success" initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} className="py-12 text-center">
                <div className="w-20 h-20 bg-emerald-500/20 text-emerald-400 rounded-full flex items-center justify-center mx-auto mb-6">
                  <CheckCircle2 size={48} />
                </div>
                <h3 className="text-2xl font-bold mb-2 uppercase tracking-tighter">Transaction Success</h3>
                <p className="text-white/50 text-sm mb-8 font-medium">Order confirmed and ledger entry created.</p>
                <div className="bg-white/5 p-4 rounded-2xl border border-white/5 mb-8 text-left text-xs font-mono text-white/30 space-y-1">
                  <div>TX_ID: {Math.random().toString(36).substring(2, 15).toUpperCase()}</div>
                  <div>METHOD: {method?.toUpperCase()}</div>
                  <div>STATUS: ATOMIC_SETTLED</div>
                </div>
                <button onClick={onClose} className="w-full bg-emerald-500 text-white py-4 rounded-2xl font-bold hover:bg-emerald-600 transition">Return to Store</button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Footer */}
        <div className="p-4 bg-white/5 flex justify-center items-center gap-2 text-[10px] text-white/20 uppercase tracking-[0.3em] font-black border-t border-white/5">
          <ShieldCheck size={12} /> Military Grade 256-bit Encryption
        </div>
      </motion.div>
    </div>
  );
};

export default ZenithCheckout;
