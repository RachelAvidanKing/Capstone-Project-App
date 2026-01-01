import React, { useState, useEffect } from 'react';
import { 
  Activity, Database, Download, BarChart3, TrendingUp, 
  Settings, RefreshCw, CheckCircle, ShieldCheck, AlertTriangle, 
  AlertCircle, ChevronRight, Home, FileText, Zap, Trash2,
  Moon, Sun, Package
} from 'lucide-react';

// Use 127.0.0.1 instead of localhost to prevent "Failed to Fetch" on some systems
const API_URL = 'http://127.0.0.1:5000/api';

// --- THEME DEFINITIONS ---
const lightTheme = {
  page: '#EEF2FF',
  pageGradient: 'linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%)',
  sidebar: '#FFFFFF',
  sidebarBorder: '#E5E7EB',
  card: '#FFFFFF',
  text: '#1F2937',
  textSecondary: '#6B7280',
  textTertiary: '#9CA3AF',
  primary: '#4F46E5',
  primaryHover: '#4338CA',
  secondary: '#F3F4F6',
  secondaryHover: '#E5E7EB',
  success: '#059669',
  successBg: '#D1FAE5',
  error: '#DC2626',
  errorBg: '#FEE2E2',
  info: '#2563EB',
  infoBg: '#DBEAFE',
  border: '#E5E7EB',
  shadow: '0 4px 6px rgba(0,0,0,0.1)',
  warning: '#F59E0B',
  warningBg: '#FEF3C7',
};

const darkTheme = {
  page: '#0F172A',
  pageGradient: 'linear-gradient(135deg, #0F172A 0%, #1E293B 100%)',
  sidebar: '#1E293B',
  sidebarBorder: '#334155',
  card: '#1E293B',
  text: '#F1F5F9',
  textSecondary: '#CBD5E1',
  textTertiary: '#94A3B8',
  primary: '#6366F1',
  primaryHover: '#4F46E5',
  secondary: '#334155',
  secondaryHover: '#475569',
  success: '#10B981',
  successBg: '#064E3B',
  error: '#EF4444',
  errorBg: '#7F1D1D',
  info: '#3B82F6',
  infoBg: '#1E3A8A',
  border: '#334155',
  shadow: '0 4px 6px rgba(0,0,0,0.3)',
  warning: '#F59E0B',
  warningBg: '#78350F',
};

export default function BehavioralAnalysisToolkit() {
  // --- STATE ---
  const [isDark, setIsDark] = useState(false);
  const theme = isDark ? darkTheme : lightTheme;
  
  const [status, setStatus] = useState(null);
  const [currentPage, setCurrentPage] = useState('overview');
  const [analysisResults, setAnalysisResults] = useState(null);
  const [velocityResults, setVelocityResults] = useState(null);
  
  const [cleanupResult, setCleanupResult] = useState(null); 
  const [isProcessing, setIsProcessing] = useState(false);

  const [velocitySettings, setVelocitySettings] = useState({
    time_cap: 5500,
    velocity_cap: 5000,
    split_by: '',
    include_overlay: true
  });

  // --- INITIALIZATION ---
  useEffect(() => {
    fetchStatus();
    const interval = setInterval(() => {
      fetchStatus();
    }, 30000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const savedTheme = window.localStorage.getItem('theme');
    if (savedTheme === 'dark') setIsDark(true);
  }, []);

  useEffect(() => {
    window.localStorage.setItem('theme', isDark ? 'dark' : 'light');
  }, [isDark]);

  // --- STYLES ---
  const styles = {
    page: {
      minHeight: '100vh',
      background: theme.pageGradient,
      fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      display: 'flex',
      transition: 'background 0.3s',
    },
    sidebar: {
      width: '280px',
      background: theme.sidebar,
      boxShadow: theme.shadow,
      display: 'flex',
      flexDirection: 'column',
      position: 'fixed',
      height: '100vh',
      overflowY: 'auto',
      borderRight: `1px solid ${theme.sidebarBorder}`,
      transition: 'all 0.3s',
    },
    sidebarHeader: {
      padding: '24px 20px',
      borderBottom: `1px solid ${theme.sidebarBorder}`,
    },
    sidebarTitle: {
      fontSize: '18px',
      fontWeight: 'bold',
      color: theme.text,
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      margin: 0,
    },
    navItem: {
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
      padding: '12px 16px',
      marginBottom: '8px',
      borderRadius: '8px',
      cursor: 'pointer',
      fontSize: '14px',
      fontWeight: '500',
      transition: 'all 0.2s',
      border: 'none',
      width: '100%',
      textAlign: 'left',
      background: 'transparent',
    },
    mainContent: {
      marginLeft: '280px',
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
    },
    header: {
      background: theme.sidebar,
      boxShadow: theme.shadow,
      padding: '16px 32px',
      borderBottom: `1px solid ${theme.border}`,
    },
    card: {
      background: theme.card,
      borderRadius: '12px',
      boxShadow: theme.shadow,
      padding: '24px',
      marginBottom: '24px',
      border: `1px solid ${theme.border}`,
      transition: 'all 0.3s',
    },
    button: {
      padding: '12px 24px',
      borderRadius: '8px',
      fontWeight: '600',
      cursor: 'pointer',
      border: 'none',
      fontSize: '14px',
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      justifyContent: 'center',
      transition: 'all 0.2s',
      width: '100%',
    },
    input: {
      width: '100%',
      padding: '10px 12px',
      border: `1px solid ${theme.border}`,
      borderRadius: '8px',
      fontSize: '14px',
      marginBottom: '8px',
      boxSizing: 'border-box',
      background: theme.sidebar,
      color: theme.text,
    },
    cardTitle: {
      fontSize: '18px',
      fontWeight: 'bold',
      color: theme.text,
      marginBottom: '16px',
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
    },
    statBox: {
      padding: '16px',
      borderRadius: '12px',
      background: theme.secondary,
      border: `1px solid ${theme.border}`
    }
  };

  // --- API FUNCTIONS ---

  const fetchStatus = async () => {
    try {
      const response = await fetch(`${API_URL}/status`);
      const data = await response.json();
      setStatus(data);
    } catch (error) {
      console.error('Error:', error);
      setStatus({ connected: false, error: error.message });
    }
  };

  const testFirebase = async () => {
    setIsProcessing(true);
    await fetchStatus();
    if(status?.connected) {
        alert("✅ Connection Successful");
    } else {
        alert("❌ Connection Failed");
    }
    setIsProcessing(false);
  };

  const reloadData = async () => {
    setIsProcessing(true);
    try {
      const response = await fetch(`${API_URL}/reload`, { method: 'POST' });
      const data = await response.json();
      if (data.status === 'success') {
        await fetchStatus();
        window.alert('✅ Data reloaded successfully!');
      }
    } catch (error) {
      window.alert('❌ Error: ' + error.message);
    }
    setIsProcessing(false);
  };

  const downloadAnalysisPackage = async () => {
    if (!analysisResults?.output_dir) return;
    try {
      const response = await fetch(`${API_URL}/analysis/download`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ output_dir: analysisResults.output_dir })
      });
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
      a.download = `analysis_results_${timestamp}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      window.alert('❌ Error downloading: ' + error.message);
    }
  };

  const downloadVelocityPackage = async () => {
    if (!velocityResults?.output_dir) return;
    try {
      const response = await fetch(`${API_URL}/analysis/download`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ output_dir: velocityResults.output_dir })
      });
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
      a.download = `velocity_results_${timestamp}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      window.alert('❌ Error downloading: ' + error.message);
    }
  };

  const runFullAnalysis = async () => {
    setIsProcessing(true);
    try {
      const response = await fetch(`${API_URL}/analysis/full`, { method: 'POST' });
      if (!response.ok) throw new Error('Backend not responding');
      
      const data = await response.json();
      if (data.status === 'success') {
        setAnalysisResults(data);
        setCurrentPage('analysis-results');
        window.alert('✅ Analysis complete!');
      }
    } catch (error) {
      setStatus(prev => ({ ...prev, connected: false }));
      window.alert('❌ Cannot connect to backend: ' + error.message);
    }
    setIsProcessing(false);
  };

  const generateVelocityPlots = async () => {
    setIsProcessing(true);
    try {
      const response = await fetch(`${API_URL}/plots/velocity`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(velocitySettings)
      });
      const data = await response.json();
      if (data.status === 'success') {
        setVelocityResults(data);
        setCurrentPage('velocity-results');
        window.alert('✅ Velocity plots generated!');
      }
    } catch (error) {
      window.alert('❌ Error: ' + error.message);
    }
    setIsProcessing(false);
  };

  const handleDatabaseCleanup = async (dryRun = true) => {
    if (!dryRun) {
      const confirm = window.confirm("⚠️ WARNING: This will permanently DELETE documents from your Firebase database. Are you absolutely sure?");
      if (!confirm) return;
    }

    setIsProcessing(true);
    try {
      const response = await fetch(`${API_URL}/clean/database`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dry_run: dryRun, target_count: 15 })
      });
      
      const data = await response.json();
      if (data.status === 'success') {
        setCleanupResult(data);
        if (!dryRun) {
          fetchStatus();
        }
      } else {
        alert("Cleanup Error: " + data.message);
      }
    } catch (err) {
      console.error("Failed to connect to backend:", err);
      alert("❌ Error: Failed to fetch from backend. Is the server running?");
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div style={styles.page}>
      <style>{`
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); }}
        .animate-spin { animation: spin 1s linear infinite; }
        button:hover:not(:disabled) { transform: translateY(-1px); }
      `}</style>

      {/* Sidebar */}
      <div style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h1 style={styles.sidebarTitle}>
            <Activity color={theme.primary} size={24} />
            Analysis Toolkit
          </h1>
          <p style={{ fontSize: '12px', color: theme.textSecondary, margin: '4px 0 0 0' }}>
            Reaching Movement Research
          </p>
        </div>

        <nav style={{ flex: 1, padding: '16px' }}>
          {[
            { id: 'overview', icon: Home, label: 'Overview' },
            { id: 'analysis', icon: BarChart3, label: 'Full Analysis' },
            { id: 'velocity', icon: TrendingUp, label: 'Velocity Analysis' },
            { id: 'export', icon: Download, label: 'Export Data' },
            { id: 'settings', icon: Settings, label: 'Settings' },
          ].map(nav => (
            <button
              key={nav.id}
              onClick={() => setCurrentPage(nav.id)}
              style={{
                ...styles.navItem,
                background: (currentPage === nav.id || currentPage.startsWith(nav.id)) ? theme.secondary : 'transparent',
                color: (currentPage === nav.id || currentPage.startsWith(nav.id)) ? theme.primary : theme.textSecondary,
              }}
            >
              <nav.icon size={20} />
              {nav.label}
            </button>
          ))}
        </nav>

        <div style={{ padding: '16px', borderTop: `1px solid ${theme.sidebarBorder}` }}>
          <button
            onClick={() => setIsDark(!isDark)}
            style={{
              ...styles.button,
              background: theme.secondary,
              color: theme.text,
              marginBottom: '12px',
            }}
          >
            {isDark ? <Sun size={18} /> : <Moon size={18} />}
            {isDark ? 'Light Mode' : 'Dark Mode'}
          </button>
          <button
            onClick={reloadData}
            disabled={isProcessing}
            style={{
              ...styles.button,
              background: theme.primary,
              color: 'white',
              opacity: isProcessing ? 0.5 : 1,
            }}
          >
            <RefreshCw size={18} className={isProcessing ? 'animate-spin' : ''} />
            Reload Data
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div style={styles.mainContent}>
        <header style={styles.header}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ fontSize: '24px', fontWeight: 'bold', color: theme.text, margin: 0 }}>
              {currentPage === 'overview' && 'Overview'}
              {currentPage === 'analysis' && 'Run Full Analysis'}
              {currentPage === 'analysis-results' && 'Analysis Results'}
              {currentPage === 'velocity' && 'Velocity Configuration'}
              {currentPage === 'velocity-results' && 'Velocity Results'}
              {currentPage === 'export' && 'Export Data'}
              {currentPage === 'settings' && 'Settings'}
            </h2>
            {status?.connected ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: theme.success, fontWeight: '600' }}>
                <CheckCircle size={20} /> Connected
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: theme.error, fontWeight: '600' }}>
                <AlertCircle size={20} /> Disconnected
              </div>
            )}
          </div>
        </header>

        <div style={{ padding: '32px', overflowY: 'auto' }}>
          {/* Overview Page */}
          {currentPage === 'overview' && status?.connected && (
            <>
              <div style={styles.card}>
                <h3 style={{ fontSize: '20px', fontWeight: 'bold', color: theme.text, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Database size={24} color={theme.primary} />
                  Dataset Status
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
                  {[
                    { label: 'Participants', value: status.participants_count, color: theme.primary, bg: isDark ? '#312E81' : '#EEF2FF' },
                    { label: 'Total Trials', value: status.trials_count, color: '#7C3AED', bg: isDark ? '#581C87' : '#F3E8FF' },
                    { label: 'Male', value: status.demographics?.male_count || 0, color: '#06A77D', bg: isDark ? '#064E3B' : '#D1FAE5' },
                    { label: 'Female', value: status.demographics?.female_count || 0, color: '#D4A373', bg: isDark ? '#78350F' : '#FED7AA' },
                    { label: 'With ADHD', value: status.demographics?.with_adhd || 0, color: '#2563EB', bg: isDark ? '#1E3A8A' : '#DBEAFE' },
                  ].map((stat, i) => (
                    <div key={i} style={{ padding: '16px', borderRadius: '8px', background: stat.bg }}>
                      <p style={{ fontSize: '12px', color: theme.textSecondary, marginBottom: '4px' }}>{stat.label}</p>
                      <p style={{ fontSize: '32px', fontWeight: 'bold', color: stat.color }}>{stat.value}</p>
                    </div>
                  ))}
                </div>
              </div>

              <div style={styles.card}>
                <h3 style={{ fontSize: '18px', fontWeight: 'bold', color: theme.text, marginBottom: '16px' }}>Quick Actions</h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '16px' }}>
                  <button onClick={() => setCurrentPage('analysis')} style={{ ...styles.button, background: theme.primary, color: 'white' }}>
                    <BarChart3 size={20} /> Run Full Analysis <ChevronRight size={18} />
                  </button>
                  <button onClick={() => setCurrentPage('velocity')} style={{ ...styles.button, background: '#7C3AED', color: 'white' }}>
                    <TrendingUp size={20} /> Velocity Analysis <ChevronRight size={18} />
                  </button>
                </div>
              </div>
            </>
          )}

          {/* Analysis Page */}
          {currentPage === 'analysis' && (
            <div style={styles.card}>
              <h3 style={{ fontSize: '20px', fontWeight: 'bold', color: theme.text, marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <BarChart3 size={24} color={theme.primary} />
                Run Complete Statistical Analysis
              </h3>
              <p style={{ color: theme.textSecondary, marginBottom: '16px' }}>
                Generate all plots, run repeated measures ANOVA, and create comprehensive reports.
              </p>
              <button onClick={runFullAnalysis} disabled={isProcessing || !status?.connected} style={{ ...styles.button, background: theme.primary, color: 'white', opacity: (isProcessing || !status?.connected) ? 0.5 : 1 }}>
                {isProcessing ? 'Analyzing...' : 'Start Analysis'}
              </button>
            </div>
          )}

          {/* Analysis Results */}
          {currentPage === 'analysis-results' && analysisResults && (
            <>
              <div style={{ ...styles.card, background: theme.successBg, color: theme.success, padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <CheckCircle size={20} />
                Analysis completed! Scroll to view all results.
              </div>
              
              <div style={styles.card}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '18px', fontWeight: 'bold', color: theme.text, margin: 0 }}>Analysis Results</h3>
                  <button onClick={downloadAnalysisPackage} style={{ ...styles.button, width: 'auto', background: theme.primary, color: 'white' }}>
                    <Package size={18} /> Download All (ZIP)
                  </button>
                </div>
                
                {analysisResults.report && (
                  <div style={{ background: theme.secondary, padding: '16px', borderRadius: '8px', marginBottom: '16px' }}>
                    <h4 style={{ fontSize: '14px', fontWeight: '600', color: theme.text, marginBottom: '8px' }}>Statistical Report</h4>
                    <pre style={{ fontSize: '12px', color: theme.textSecondary, whiteSpace: 'pre-wrap', fontFamily: 'monospace', margin: 0 }}>
                      {analysisResults.report}
                    </pre>
                  </div>
                )}
                
                {analysisResults.plots?.summary && (
                  <div style={{ marginBottom: '24px' }}>
                    <h4 style={{ fontSize: '16px', fontWeight: '600', color: theme.text, marginBottom: '12px' }}>Performance Summary</h4>
                    <img src={`data:image/png;base64,${analysisResults.plots.summary}`} alt="Summary" style={{ width: '100%', borderRadius: '8px', border: `1px solid ${theme.border}` }} />
                  </div>
                )}

                {analysisResults.plots?.demographics && Object.keys(analysisResults.plots.demographics).length > 0 && (
                  <div>
                    <h4 style={{ fontSize: '16px', fontWeight: '600', color: theme.text, marginBottom: '12px' }}>Demographic Comparisons</h4>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '16px' }}>
                      {Object.entries(analysisResults.plots.demographics).map(([name, plot]) => (
                        <div key={name}>
                          <p style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px', color: theme.text, textTransform: 'capitalize' }}>
                            {name.replace('_', ' ')}
                          </p>
                          <img src={`data:image/png;base64,${plot}`} alt={name} style={{ width: '100%', borderRadius: '8px', border: `1px solid ${theme.border}` }} />
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </>
          )}

          {/* Velocity Config */}
          {currentPage === 'velocity' && (
            <div style={styles.card}>
              <h3 style={{ fontSize: '20px', fontWeight: 'bold', color: theme.text, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <TrendingUp size={24} color="#7C3AED" />
                Velocity Plot Configuration
              </h3>
              
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '14px', fontWeight: '500', color: theme.text, marginBottom: '8px' }}>
                  Time Cap (ms)
                </label>
                <input type="number" value={velocitySettings.time_cap} onChange={(e) => setVelocitySettings({...velocitySettings, time_cap: parseInt(e.target.value)})} style={styles.input} />
                <p style={{ fontSize: '12px', color: theme.textSecondary, marginTop: '4px' }}>Max time on X-axis (default: 5500ms)</p>
              </div>

              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '14px', fontWeight: '500', color: theme.text, marginBottom: '8px' }}>
                  Velocity Cap (px/s)
                </label>
                <input type="number" value={velocitySettings.velocity_cap} onChange={(e) => setVelocitySettings({...velocitySettings, velocity_cap: parseInt(e.target.value)})} style={styles.input} />
                <p style={{ fontSize: '12px', color: theme.textSecondary, marginTop: '4px' }}>Max velocity on Y-axis (default: 5000px/s)</p>
              </div>

              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '14px', fontWeight: '500', color: theme.text, marginBottom: '8px' }}>
                  Split By Demographic
                </label>
                <select value={velocitySettings.split_by || ''} onChange={(e) => setVelocitySettings({...velocitySettings, split_by: e.target.value || null})} style={styles.input}>
                  <option value="">None</option>
                  <option value="hasAttentionDeficit">ADHD</option>
                  <option value="gender">Gender</option>
                  <option value="hasGlasses">Glasses</option>
                </select>
                <p style={{ fontSize: '12px', color: theme.textSecondary, marginTop: '4px' }}>Generate separate plots for demographic groups</p>
              </div>

              <div style={{ marginBottom: '24px' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}>
                  <input type="checkbox" checked={velocitySettings.include_overlay} onChange={(e) => setVelocitySettings({...velocitySettings, include_overlay: e.target.checked})} />
                  <span style={{ fontSize: '14px', color: theme.text }}>Include overlay plot with all conditions</span>
                </label>
              </div>

              <button onClick={generateVelocityPlots} disabled={isProcessing} style={{ ...styles.button, background: '#7C3AED', color: 'white', opacity: isProcessing ? 0.5 : 1 }}>
                {isProcessing ? 'Generating...' : 'Generate All Velocity Plots'}
              </button>
            </div>
          )}

          {/* Velocity Results */}
          {currentPage === 'velocity-results' && velocityResults && (
            <>
              <div style={{ ...styles.card, background: theme.successBg, color: theme.success, padding: '12px 16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <CheckCircle size={20} />
                Velocity plots generated successfully!
              </div>

              <div style={styles.card}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                  <h3 style={{ fontSize: '18px', fontWeight: 'bold', color: theme.text, margin: 0 }}>Velocity Analysis Results</h3>
                  <button onClick={downloadVelocityPackage} style={{ ...styles.button, width: 'auto', background: '#7C3AED', color: 'white' }}>
                    <Package size={18} /> Download All (ZIP)
                  </button>
                </div>
                
                {velocityResults.plots && Object.entries(velocityResults.plots).map(([name, plot]) => (
                  <div key={name} style={{ marginBottom: '32px' }}>
                    <h4 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '12px', color: theme.text, textTransform: 'capitalize' }}>
                      {name.replace(/_/g, ' ').replace(/tcap\d+/, '').replace(/vcap\d+/, '')}
                    </h4>
                    <img src={`data:image/png;base64,${plot}`} alt={name} style={{ width: '100%', borderRadius: '8px', border: `1px solid ${theme.border}`, marginBottom: '8px' }} />
                  </div>
                ))}
              </div>
            </>
          )}

          {/* Export Page */}
          {currentPage === 'export' && (
            <>
              <div style={styles.card}>
                <h3 style={{ fontSize: '18px', fontWeight: 'bold', color: theme.text, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Download size={22} color={theme.primary} />
                  Raw Data Export
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: '16px' }}>
                  <button onClick={() => fetch(`${API_URL}/export/raw?type=participants`)
                    .then(r => r.blob())
                    .then(b => { 
                      const url = URL.createObjectURL(b); 
                      const a = document.createElement('a'); 
                      a.href = url; 
                      a.download = `participants_${Date.now()}.csv`; 
                      a.click(); 
                    })} 
                    style={{ ...styles.button, background: theme.secondary, color: theme.text }}>
                    <Download size={18} /> Participants (CSV)
                  </button>
                  <button onClick={() => fetch(`${API_URL}/export/raw?type=trials`)
                    .then(r => r.blob())
                    .then(b => { 
                      const url = URL.createObjectURL(b); 
                      const a = document.createElement('a'); 
                      a.href = url; 
                      a.download = `trials_${Date.now()}.csv`; 
                      a.click(); 
                    })} 
                    style={{ ...styles.button, background: theme.secondary, color: theme.text }}>
                    <Download size={18} /> Trials (CSV)
                  </button>
                </div>
              </div>

              <div style={styles.card}>
                <h3 style={{ fontSize: '18px', fontWeight: 'bold', color: theme.text, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <FileText size={22} color={theme.primary} />
                  Processed Data
                </h3>
                <button onClick={() => fetch(`${API_URL}/export/processed`)
                  .then(r => r.blob())
                  .then(b => { 
                    const url = URL.createObjectURL(b); 
                    const a = document.createElement('a'); 
                    a.href = url; 
                    a.download = `processed_${Date.now()}.csv`; 
                    a.click(); 
                  })} 
                  style={{ ...styles.button, background: theme.primary, color: 'white' }}>
                  <Download size={18} /> Download Processed Data (CSV)
                </button>
              </div>
            </>
          )}

          {/* Settings Page */}
          {currentPage === 'settings' && (
            <>
              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <Settings size={22} color={theme.primary} />
                  Database Management
                </h3>
                
                <button
                  onClick={testFirebase}
                  disabled={isProcessing}
                  style={{ 
                    ...styles.button, 
                    background: theme.secondary, 
                    color: theme.text, 
                    marginBottom: '12px',
                    opacity: isProcessing ? 0.5 : 1
                  }}
                >
                  <Zap size={18} />
                  Test Firebase Connection
                </button>

                <button
                  onClick={fetchStatus}
                  disabled={isProcessing}
                  style={{ 
                    ...styles.button, 
                    background: theme.secondary, 
                    color: theme.text,
                    opacity: isProcessing ? 0.5 : 1
                  }}
                >
                  <RefreshCw size={18} />
                  Refresh Database Status
                </button>
              </div>

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <Trash2 size={22} color={theme.warning} />
                  Database Cleaner
                </h3>
                
                <div style={{ 
                  background: theme.warningBg, 
                  border: `1px solid ${theme.warning}`,
                  borderRadius: '8px',
                  padding: '12px',
                  marginBottom: '16px'
                }}>
                  <p style={{ fontSize: '14px', color: theme.text, margin: 0 }}>
                    ⚠️ <strong>Caution:</strong> The cleanup operation permanently removes trials from Firebase. Always run a dry run scan first!
                  </p>
                </div>

                {cleanupResult && (
                  <div style={{ 
                    background: theme.infoBg,
                    borderRadius: '8px',
                    padding: '16px',
                    marginBottom: '16px'
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                        {cleanupResult.summary.total_actions > 0 ?
                        <AlertTriangle size={24} color={theme.error} /> : <ShieldCheck size={24} color={theme.success} />}
                        <h4 style={{ color: theme.text, margin: 0 }}>
                        {cleanupResult.dry_run ? "Scan Results (No Changes Made)" : "Cleanup Results (Success)"}
                        </h4>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div style={styles.statBox}>
                        <span style={{ fontSize: '12px', color: theme.textSecondary }}>Duplicates Found</span>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: theme.text }}>{cleanupResult.summary.duplicates_found}</div>
                        </div>
                        <div style={styles.statBox}>
                        <span style={{ fontSize: '12px', color: theme.textSecondary }}>Incomplete Sets</span>
                        <div style={{ fontSize: '20px', fontWeight: '700', color: theme.text }}>{cleanupResult.summary.incomplete_trials_found}</div>
                        </div>
                    </div>
                  </div>
                )}

                <div style={{ display: 'grid', gap: '12px' }}>
                  <button
                    onClick={() => handleDatabaseCleanup(true)}
                    disabled={isProcessing}
                    style={{ 
                      ...styles.button, 
                      background: theme.info, 
                      color: 'white',
                      opacity: isProcessing ? 0.5 : 1
                    }}
                  >
                    <Zap size={18} />
                    {isProcessing ? 'Scanning...' : 'Scan for Duplicates or Incomplete Sets (Dry Run)'}
                  </button>

                  <button
                    onClick={() => handleDatabaseCleanup(false)}
                    disabled={isProcessing}
                    style={{ 
                      ...styles.button, 
                      background: theme.error, 
                      color: 'white',
                      opacity: isProcessing ? 0.5 : 1
                    }}
                  >
                    <Trash2 size={18} />
                    {isProcessing ? 'Removing...' : 'Remove Trials (PERMANENT)'}
                  </button>
                </div>

                <p style={{ fontSize: '12px', color: theme.textSecondary, marginTop: '12px' }}>
                  Dry run mode will show you which duplicates or incomplete sets would be removed without making any changes to the database.
                </p>
              </div>

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <Activity size={22} color={theme.primary} />
                  System Information
                </h3>
                
                <div style={{ 
                  fontSize: '13px', 
                  color: theme.textSecondary, 
                  background: theme.secondary, 
                  padding: '16px', 
                  borderRadius: '8px'
                }}>
                  <div style={{ marginBottom: '8px' }}>
                    <span style={{ fontWeight: '600', color: theme.text }}>Backend API:</span>{' '}
                    <code style={{ background: theme.card, padding: '2px 6px', borderRadius: '4px' }}>{API_URL}</code>
                  </div>
                  <div style={{ marginBottom: '8px' }}>
                    <span style={{ fontWeight: '600', color: theme.text }}>Frontend Version:</span> 1.0.0
                  </div>
                  <div style={{ marginBottom: '8px' }}>
                    <span style={{ fontWeight: '600', color: theme.text }}>Connection Status:</span>{' '}
                    <span style={{ color: status?.connected ? theme.success : theme.error }}>
                      {status?.connected ? '✓ Connected' : '✗ Disconnected'}
                    </span>
                  </div>
                  {status?.last_updated && (
                    <div>
                      <span style={{ fontWeight: '600', color: theme.text }}>Last Data Update:</span>{' '}
                      {new Date(status.last_updated).toLocaleString()}
                    </div>
                  )}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}