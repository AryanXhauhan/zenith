import { useState, useEffect } from 'react';
import { 
  LayoutDashboard, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Settings, 
  History,
  Calendar,
  ShieldCheck,
  TrendingUp,
  Wallet,
  Globe,
  Key,
  Copy,
  RefreshCw,
  ShoppingBag
} from 'lucide-react';
import axios from 'axios';
import ZenithCheckout from './Checkout';
import { motion, AnimatePresence } from 'framer-motion';

const API_BASE = 'http://localhost:8080/api/v1';
// In production, this would be fetched from a secure session or .env
const MASTER_KEY = 'dev-master-key-changeme'; 

const Dashboard = () => {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [currency, setCurrency] = useState({ code: 'USD', symbol: '$', rate: 1 });
  const [balance, setBalance] = useState(0);
  const [stats, setStats] = useState({ successRate: 0, totalVolume: 0 });
  const [transactions, setTransactions] = useState([]);
  const [emiPlans, setEmiPlans] = useState([]);
  const [apiKey, setApiKey] = useState(null);
  const [walletAddress, setWalletAddress] = useState(null);
  const [, setLoading] = useState(true);
  const [showCheckout, setShowCheckout] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const currencies = [
    { code: 'USD', symbol: '$', rate: 1 },
    { code: 'INR', symbol: '₹', rate: 83.20 },
    { code: 'EUR', symbol: '€', rate: 0.92 },
    { code: 'GBP', symbol: '£', rate: 0.79 }
  ];


  const formatValue = (val) => {
    return (val * currency.rate).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  useEffect(() => {
    const fetchAllData = async () => {
      try {
        setIsRefreshing(true);
        const accRes = await axios.get(`${API_BASE}/accounts/ZNT-002`, {
          headers: { 'X-Zenith-Key': MASTER_KEY }
        });
        setBalance(accRes.data.balance);

        const statsRes = await axios.get(`${API_BASE}/ledger/stats`, {
          headers: { 'X-Zenith-Key': MASTER_KEY }
        });
        setStats(statsRes.data);

        const historyRes = await axios.get(`${API_BASE}/ledger/history/ZNT-002`, {
          headers: { 'X-Zenith-Key': MASTER_KEY }
        });

        const emiRes = await axios.get(`${API_BASE}/installments`, {
          headers: { 'X-Zenith-Key': MASTER_KEY }
        });
        setEmiPlans(emiRes.data);

        const mappedHistory = historyRes.data.map(tx => ({
          id: tx.id,
          type: tx.description.includes('Fee') ? 'fee' : 'credit',
          amount: tx.amount,
          desc: tx.description,
          date: new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }));

        setTransactions(mappedHistory);
      } catch {
        console.error("Dashboard Sync Failed");
      } finally {
        setLoading(false);
        setTimeout(() => setIsRefreshing(false), 500);
      }
    };

    const checkWallet = async () => {
      if (window.ethereum) {
        const accounts = await window.ethereum.request({ method: 'eth_accounts' });
        if (accounts.length > 0) setWalletAddress(accounts[0]);
      }
    };

    const init = async () => {
      await fetchAllData();
      await checkWallet();
    };
    init();

    const interval = setInterval(fetchAllData, 10000); // Polling every 10s for production
    return () => clearInterval(interval);
  }, []);

  const connectWallet = async () => {
    if (window.ethereum) {
      try {
        const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' });
        setWalletAddress(accounts[0]);
      } catch (err) {
        console.error("Wallet connection failed", err);
      }
    } else {
      alert("MetaMask not found. Please install it to use real Web3 features.");
    }
  };

  const generateNewKey = async () => {
    try {
      const res = await axios.post(`${API_BASE}/auth/keys`, 
        { label: "Production Key" },
        { headers: { 'X-Zenith-Key': MASTER_KEY } }
      );
      setApiKey(res.data.key);
    } catch (err) {
      console.error("Key generation failed", err);
    }
  };

  return (
    <div className="min-h-screen bg-[#050505] text-white font-sans selection:bg-purple-500/30 overflow-x-hidden">
      {/* Sidebar */}
      <aside className="fixed left-0 top-0 h-full w-64 bg-[#0a0a0a] border-r border-white/5 p-6 hidden lg:block z-20">
        <div className="flex items-center gap-3 mb-10">
          <div className="w-8 h-8 bg-gradient-to-br from-purple-500 to-blue-500 rounded-lg flex items-center justify-center">
            <ShieldCheck size={20} className="text-white" />
          </div>
          <span className="text-xl font-bold tracking-tight">ZENITH</span>
        </div>

        <nav className="space-y-2">
          <NavItem icon={<LayoutDashboard size={20}/>} label="Dashboard" active={activeTab === 'dashboard'} onClick={() => setActiveTab('dashboard')} />
          <NavItem icon={<Globe size={20}/>} label="Web3 Engine" active={activeTab === 'web3'} onClick={() => setActiveTab('web3')} />
          <NavItem icon={<History size={20}/>} label="Settlements" active={activeTab === 'settlements'} onClick={() => setActiveTab('settlements')} />
          <NavItem icon={<Calendar size={20}/>} label="EMI Manager" active={activeTab === 'emi'} onClick={() => setActiveTab('emi')} />
          <NavItem icon={<Key size={20}/>} label="Developer Mode" active={activeTab === 'developer'} onClick={() => setActiveTab('developer')} />
          <NavItem icon={<Settings size={20}/>} label="Settings" active={activeTab === 'settings'} onClick={() => setActiveTab('settings')} />
        </nav>

        {/* Wallet Status Indicator */}
        <div className="absolute bottom-6 left-6 right-6 p-4 bg-white/5 rounded-2xl border border-white/5 cursor-pointer hover:bg-white/10 transition" onClick={connectWallet}>
          <div className="flex items-center justify-between mb-2">
            <span className="text-[10px] text-white/30 uppercase font-bold tracking-widest">Web3 Status</span>
            <div className={`w-2 h-2 rounded-full ${walletAddress ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'}`} />
          </div>
          <div className="text-xs font-mono text-white/70 truncate">
            {walletAddress ? `Connected: ${walletAddress.substring(0, 6)}...${walletAddress.slice(-4)}` : 'Connect Wallet'}
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="lg:ml-64 p-4 md:p-8">
        <header className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-4">
            <div>
              <h1 className="text-2xl font-bold capitalize">{activeTab}</h1>
              <p className="text-white/50 text-sm flex items-center gap-2">
                Live Zenith Console {isRefreshing && <RefreshCw size={12} className="animate-spin text-purple-400" />}
              </p>
            </div>
          </div>
          <div className="flex gap-3 items-center">
            <div className="bg-white/5 border border-white/10 rounded-full px-3 py-1 flex items-center gap-2 mr-4">
              <span className="text-[10px] font-bold text-white/30 uppercase tracking-widest">Global Engine</span>
              <select 
                value={currency.code}
                onChange={(e) => setCurrency(currencies.find(c => c.code === e.target.value))}
                className="bg-transparent border-none text-xs font-bold text-purple-400 focus:outline-none cursor-pointer"
              >
                {currencies.map(c => <option key={c.code} value={c.code} className="bg-[#111]">{c.code} ({c.symbol})</option>)}
              </select>
            </div>
            <button 
              onClick={() => setShowCheckout(true)}
              className="bg-purple-600 text-white px-4 py-2 rounded-full font-bold hover:bg-purple-500 transition flex items-center gap-2 shadow-lg shadow-purple-500/20"
            >
              <ShoppingBag size={18} /> Launch Checkout
            </button>
          </div>
        </header>

        <AnimatePresence mode="wait">
          {activeTab === 'dashboard' && (
            <motion.div key="dash" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
              {/* Stats Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                <StatCard title="Total Balance" value={`${currency.symbol}${formatValue(balance)}`} icon={<Wallet className="text-purple-400" />} trend="+12.5%" />
                <StatCard title="Settlement Volume" value={`${currency.symbol}${formatValue(stats.totalVolume)}`} icon={<TrendingUp className="text-blue-400" />} trend="+5.2%" />
                <StatCard title="Success Rate" value={`${stats.successRate}%`} icon={<ShieldCheck className="text-emerald-400" />} trend="Live" />
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="lg:col-span-2">
                  <TransactionsTable transactions={transactions} currency={currency} formatValue={formatValue} />
                </div>
                <div className="space-y-6">
                  <SystemHealth />
                </div>
              </div>
            </motion.div>
          )}

          {activeTab === 'web3' && (
            <motion.div key="web3" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
               <div className="bg-[#0a0a0a] border border-white/5 p-8 rounded-[2rem] mb-8">
                 <h3 className="text-xl font-bold mb-4">Web3 Settlement Bridge</h3>
                 <p className="text-white/50 mb-6">Connect your wallet to enable atomic off-ramps and blockchain settlements.</p>
                 {!walletAddress ? (
                   <button onClick={connectWallet} className="bg-purple-600 px-6 py-3 rounded-2xl font-bold hover:bg-purple-500 transition">Connect Web3 Wallet</button>
                 ) : (
                   <div className="p-6 bg-white/5 rounded-2xl border border-white/10">
                     <div className="text-sm text-white/30 mb-2 uppercase font-bold">Connected Wallet</div>
                     <div className="text-lg font-mono mb-4 text-purple-400">{walletAddress}</div>
                     <div className="grid grid-cols-3 gap-4">
                       <div className="bg-white/5 p-4 rounded-xl border border-white/5">
                         <div className="text-[10px] uppercase font-bold text-white/30 mb-1">Network</div>
                         <div className="font-bold flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-emerald-500" /> Ethereum</div>
                       </div>
                       <div className="bg-white/5 p-4 rounded-xl border border-white/5">
                         <div className="text-[10px] uppercase font-bold text-white/30 mb-1">Bridge Status</div>
                         <div className="text-emerald-400 font-bold">ACTIVE</div>
                       </div>
                       <div className="bg-white/5 p-4 rounded-xl border border-white/5">
                         <div className="text-[10px] uppercase font-bold text-white/30 mb-1">Gas Estimation</div>
                         <div className="text-white font-bold font-mono">12 Gwei</div>
                       </div>
                     </div>
                   </div>
                 )}
               </div>
            </motion.div>
          )}

          {activeTab === 'emi' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {emiPlans.length > 0 ? emiPlans.map(plan => (
                <div key={plan.id} className="bg-white/5 border border-white/5 p-6 rounded-[2rem] hover:bg-white/10 transition group">
                  <div className="flex justify-between items-start mb-6">
                    <div className="w-12 h-12 bg-purple-500/10 rounded-2xl flex items-center justify-center text-purple-400 group-hover:scale-110 transition">
                      <Calendar size={24} />
                    </div>
                    <span className="px-3 py-1 bg-emerald-500/10 text-emerald-400 rounded-full text-[10px] font-bold uppercase tracking-widest border border-emerald-500/20">
                      {plan.status}
                    </span>
                  </div>
                  <div className="mb-6">
                    <div className="text-white/40 text-xs font-bold uppercase tracking-widest mb-1">Account: {plan.accountNumber}</div>
                    <div className="text-2xl font-black">${plan.totalAmount.toFixed(2)}</div>
                  </div>
                  <div className="space-y-4">
                    <div className="flex justify-between text-xs font-bold">
                      <span className="text-white/40 uppercase tracking-widest">Progress</span>
                      <span>{plan.paidInstallments} / {plan.totalInstallments} Months</span>
                    </div>
                    <div className="h-2 bg-white/5 rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-purple-500 transition-all duration-1000" 
                        style={{ width: `${(plan.paidInstallments / plan.totalInstallments) * 100}%` }}
                      ></div>
                    </div>
                    <div className="flex justify-between items-end pt-4 border-t border-white/5">
                      <div>
                        <div className="text-[10px] text-white/30 uppercase font-bold tracking-widest">Next Due</div>
                        <div className="text-sm font-bold text-white/70">{new Date(plan.nextDueDate).toLocaleDateString()}</div>
                      </div>
                      <div className="text-right">
                        <div className="text-[10px] text-white/30 uppercase font-bold tracking-widest">Monthly</div>
                        <div className="text-lg font-black text-purple-400">${plan.installmentAmount.toFixed(2)}</div>
                      </div>
                    </div>
                  </div>
                </div>
              )) : (
                <div className="col-span-full py-20 text-center bg-white/5 rounded-[2rem] border border-dashed border-white/10">
                  <Calendar size={48} className="mx-auto text-white/10 mb-4" />
                  <div className="text-white/30 font-bold uppercase tracking-widest">No Active EMI Plans Found</div>
                </div>
              )}
            </div>
          </div>
        )}
        
        {activeTab === 'settlements' && (
            <motion.div key="settle" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
              <TransactionsTable transactions={transactions} currency={currency} formatValue={formatValue} full />
            </motion.div>
          )}

          {activeTab === 'developer' && (
            <motion.div key="dev" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
              <div className="bg-gradient-to-br from-[#0a0a0a] to-[#111] border border-white/5 p-8 rounded-[2rem] shadow-xl relative overflow-hidden max-w-2xl">
                <div className="absolute -top-12 -right-12 w-32 h-32 bg-purple-500/10 blur-3xl rounded-full" />
                <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                  <Key size={18} className="text-purple-400" /> API Access Keys
                </h3>
                <p className="text-white/50 text-sm mb-6">Use these keys to integrate the Zenith Settlement Engine into your external applications.</p>
                
                <AnimatePresence mode="wait">
                  {!apiKey ? (
                    <motion.button key="gen" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={generateNewKey} className="w-full bg-white text-black py-4 rounded-2xl font-bold text-sm hover:bg-white/90 transition shadow-lg">
                      Generate Production Key
                    </motion.button>
                  ) : (
                    <motion.div key="key" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="p-6 bg-white/5 rounded-2xl border border-purple-500/30">
                      <div className="text-[10px] text-purple-400 uppercase font-bold mb-2">Live API Key</div>
                      <div className="flex items-center justify-between gap-4">
                        <code className="text-sm font-mono truncate bg-black/40 px-3 py-2 rounded-lg flex-1">{apiKey}</code>
                        <button onClick={() => navigator.clipboard.writeText(apiKey)} className="p-2 bg-white/5 rounded-xl hover:bg-white/10 transition">
                          <Copy size={20} />
                        </button>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </motion.div>
          )}

          {activeTab === 'settings' && (
            <motion.div key="set" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }}>
              <div className="p-8 bg-[#0a0a0a] border border-white/5 rounded-[2rem] max-w-2xl">
                <h3 className="text-xl font-bold mb-6">Merchant Configuration</h3>
                <div className="space-y-6">
                  <div className="space-y-2">
                    <label className="text-xs uppercase font-bold text-white/30 tracking-widest">Merchant Name</label>
                    <input disabled type="text" value="Zenith Merchant ZNT-002" className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white/70" />
                  </div>
                  <div className="space-y-2">
                    <label className="text-xs uppercase font-bold text-white/30 tracking-widest">Base Currency</label>
                    <div className="flex items-center gap-4">
                      <select 
                        value={currency.code}
                        onChange={(e) => setCurrency(currencies.find(c => c.code === e.target.value))}
                        className="flex-1 bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white appearance-none focus:outline-none focus:border-purple-500 transition"
                      >
                        {currencies.map(c => <option key={c.code} value={c.code} className="bg-[#111]">{c.code} - {c.symbol}</option>)}
                      </select>
                      <div className="px-4 py-3 bg-purple-500/10 text-purple-400 rounded-xl font-bold border border-purple-500/20">
                        {currency.code} Engine Active
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </main>

      {/* Checkout Modal */}
      {showCheckout && (
        <ZenithCheckout 
          amount={250.00} 
          currency={currency.code} 
          onClose={() => setShowCheckout(false)} 
        />
      )}
    </div>
  );
};

const NavItem = ({ icon, label, active = false, onClick }) => (
  <div onClick={onClick} className={`flex items-center gap-3 px-4 py-3 rounded-xl cursor-pointer transition ${active ? 'bg-white/10 text-white' : 'text-white/40 hover:text-white hover:bg-white/5'}`}>
    {icon}
    <span className="font-medium">{label}</span>
  </div>
);

const TransactionsTable = ({ transactions, currency, formatValue, full = false }) => (
  <div className="bg-[#0a0a0a] border border-white/5 rounded-3xl overflow-hidden shadow-sm">
    <div className="p-6 border-b border-white/5 flex justify-between items-center">
      <h3 className="font-semibold text-lg">{full ? 'All Settlements' : 'Recent Settlements'}</h3>
      {!full && <button className="text-sm text-purple-400 hover:text-purple-300 transition">View all</button>}
    </div>
    <div className="overflow-x-auto">
      <table className="w-full text-left">
        <thead>
          <tr className="text-white/30 text-[10px] uppercase tracking-wider">
            <th className="px-6 py-4 font-bold">Transaction</th>
            <th className="px-6 py-4 font-bold">Status</th>
            <th className="px-6 py-4 font-bold">Time</th>
            <th className="px-6 py-4 font-bold text-right">Amount</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/5">
          {transactions.map((tx) => (
            <tr key={tx.id} className="hover:bg-white/[0.02] transition">
              <td className="px-6 py-4">
                <div className="flex items-center gap-3">
                  <div className={`p-2 rounded-full ${tx.type === 'fee' ? 'bg-purple-500/10 text-purple-400' : 'bg-emerald-500/10 text-emerald-400'}`}>
                    {tx.type === 'fee' ? <ArrowUpRight size={16}/> : <ArrowDownLeft size={16}/>}
                  </div>
                  <span className="font-medium">{tx.desc}</span>
                </div>
              </td>
              <td className="px-6 py-4">
                <span className="px-2 py-1 bg-emerald-500/10 text-emerald-400 text-[10px] font-bold rounded-full uppercase tracking-tighter">Completed</span>
              </td>
              <td className="px-6 py-4 text-white/50 text-sm">{tx.date}</td>
              <td className={`px-6 py-4 text-right font-mono font-bold ${tx.type === 'fee' ? 'text-purple-400' : 'text-emerald-400'}`}>
                {tx.type === 'fee' ? '-' : '+'}{currency.symbol}{formatValue(tx.amount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  </div>
);

const SystemHealth = () => (
  <div className="p-8 bg-white/5 rounded-[2rem] border border-white/5">
    <div className="flex items-center gap-3 mb-4 text-emerald-400">
      <ShieldCheck size={20} />
      <span className="font-bold text-sm">System Health: Optimal</span>
    </div>
    <div className="space-y-4">
      <HealthBar label="Postgres Engine" progress={100} />
      <HealthBar label="Redis Rate Limiter" progress={100} />
      <HealthBar label="Web3 Bridge" progress={85} />
    </div>
  </div>
);

const StatCard = ({ title, value, icon, trend }) => (
  <div className="bg-[#0a0a0a] border border-white/5 p-6 rounded-3xl relative overflow-hidden group hover:border-white/10 transition shadow-sm">
    <div className="absolute top-0 right-0 w-32 h-32 bg-purple-500/5 blur-[50px] group-hover:bg-purple-500/10 transition" />
    <div className="flex justify-between items-start mb-4">
      <div className="p-3 bg-white/5 rounded-2xl">
        {icon}
      </div>
      <span className={`text-[10px] font-black px-2 py-1 rounded-full uppercase tracking-tighter ${trend === 'Live' ? 'bg-purple-500/20 text-purple-400 animate-pulse' : 'bg-emerald-500/10 text-emerald-400'}`}>
        {trend}
      </span>
    </div>
    <div className="text-white/50 text-[10px] uppercase font-bold tracking-widest mb-1">{title}</div>
    <div className="text-3xl font-black tracking-tighter">{value}</div>
  </div>
);

const HealthBar = ({ label, progress }) => (
  <div className="space-y-1">
    <div className="flex justify-between text-[10px] uppercase font-bold text-white/30">
      <span>{label}</span>
      <span>{progress}%</span>
    </div>
    <div className="h-1 bg-white/5 rounded-full overflow-hidden">
      <div className="h-full bg-gradient-to-r from-emerald-500 to-emerald-400" style={{ width: `${progress}%` }} />
    </div>
  </div>
);

export default Dashboard;

