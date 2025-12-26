import React, { useState, useEffect } from 'react';
import { 
  Activity, Database, Download, BarChart3, TrendingUp, 
  Settings, RefreshCw, CheckCircle, AlertCircle, ChevronDown, ChevronUp 
} from 'lucide-react';

const API_URL = 'http://localhost:5000/api';

const styles = {
  page: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%)',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
  },
  header: {
    background: 'white',
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
    padding: '16px 24px',
  },
  headerContent: {
    maxWidth: '1280px',
    margin: '0 auto',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: '28px',
    fontWeight: 'bold',
    color: '#1F2937',
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    margin: 0,
  },
  subtitle: {
    fontSize: '14px',
    color: '#6B7280',
    marginTop: '4px',
  },
  container: {
    maxWidth: '1280px',
    margin: '0 auto',
    padding: '32px 24px',
  },
  card: {
    background: 'white',
    borderRadius: '12px',
    boxShadow: '0 4px 6px rgba(0,0,0,0.1)',
    padding: '24px',
    marginBottom: '24px',
  },
  cardTitle: {
    fontSize: '20px',
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: '16px',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  grid2: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
    gap: '24px',
  },
  grid4: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    gap: '16px',
  },
  statBox: {
    padding: '16px',
    borderRadius: '8px',
  },
  statLabel: {
    fontSize: '12px',
    color: '#6B7280',
    marginBottom: '4px',
  },
  statValue: {
    fontSize: '32px',
    fontWeight: 'bold',
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
  buttonPrimary: {
    background: '#4F46E5',
    color: 'white',
  },
  buttonSecondary: {
    background: '#F3F4F6',
    color: '#374151',
  },
  buttonDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
  badge: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    fontWeight: '600',
    fontSize: '14px',
  },
  badgeSuccess: {
    color: '#059669',
  },
  badgeError: {
    color: '#DC2626',
  },
  input: {
    width: '100%',
    padding: '10px 12px',
    border: '1px solid #D1D5DB',
    borderRadius: '8px',
    fontSize: '14px',
    marginBottom: '8px',
    boxSizing: 'border-box',
  },
  label: {
    display: 'block',
    fontSize: '14px',
    fontWeight: '500',
    color: '#374151',
    marginBottom: '8px',
  },
  helpText: {
    fontSize: '12px',
    color: '#6B7280',
    marginTop: '4px',
  },
  loading: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
  },
  spinner: {
    border: '4px solid #E5E7EB',
    borderTop: '4px solid #4F46E5',
    borderRadius: '50%',
    width: '48px',
    height: '48px',
    animation: 'spin 1s linear infinite',
    marginBottom: '16px',
  },
  image: {
    width: '100%',
    borderRadius: '8px',
    border: '1px solid #E5E7EB',
  },
};

export default function BehavioralAnalysisToolkit() {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [analysisResults, setAnalysisResults] = useState(null);
  const [velocityPlots, setVelocityPlots] = useState(null);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');
  
  const [velocitySettings, setVelocitySettings] = useState({
    time_cap: 10000,
    velocity_cap: 5000,
    split_by: null
  });

  useEffect(() => {
    fetchStatus();
  }, []);

  const fetchStatus = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/status`);
      const data = await response.json();
      setStatus(data);
    } catch (error) {
      console.error('Error fetching status:', error);
      setStatus({ connected: false, error: error.message });
    }
    setLoading(false);
  };

  const reloadData = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/reload`, { method: 'POST' });
      const data = await response.json();
      if (data.status === 'success') {
        await fetchStatus();
        alert('Data reloaded successfully!');
      }
    } catch (error) {
      alert('Error reloading data: ' + error.message);
    }
    setLoading(false);
  };

  const downloadRawData = async () => {
    try {
      const response = await fetch(`${API_URL}/export/raw`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `raw_data_${Date.now()}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      alert('Error downloading raw data: ' + error.message);
    }
  };

  const downloadProcessedData = async () => {
    try {
      const response = await fetch(`${API_URL}/export/processed`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `processed_data_${Date.now()}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      alert('Error downloading processed data: ' + error.message);
    }
  };

  const runFullAnalysis = async () => {
    setLoading(true);
    setActiveTab('analysis');
    try {
      const response = await fetch(`${API_URL}/analysis/full`, { method: 'POST' });
      const data = await response.json();
      if (data.status === 'success') {
        setAnalysisResults(data);
        alert('Analysis complete!');
      } else {
        alert('Analysis failed: ' + data.message);
      }
    } catch (error) {
      alert('Error running analysis: ' + error.message);
    }
    setLoading(false);
  };

  const generateVelocityPlots = async () => {
    setLoading(true);
    setActiveTab('velocity');
    try {
      const response = await fetch(`${API_URL}/plots/velocity`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(velocitySettings)
      });
      const data = await response.json();
      if (data.status === 'success') {
        setVelocityPlots(data.plots);
      } else {
        alert('Failed to generate velocity plots: ' + data.message);
      }
    } catch (error) {
      alert('Error generating velocity plots: ' + error.message);
    }
    setLoading(false);
  };

  const testFirebase = async () => {
    try {
      const response = await fetch(`${API_URL}/test/firebase`);
      const data = await response.json();
      if (data.connected) {
        alert('✅ Firebase connection successful!\n\n' + JSON.stringify(data.summary, null, 2));
      } else {
        alert('❌ Firebase connection failed:\n' + data.message);
      }
    } catch (error) {
      alert('❌ Error testing Firebase: ' + error.message);
    }
  };

  if (loading && !status) {
    return (
      <div style={styles.loading}>
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
        <div style={styles.spinner}></div>
        <p style={{ color: '#6B7280', fontSize: '18px' }}>Loading...</p>
      </div>
    );
  }

  return (
    <div style={styles.page}>
      {/* Header */}
      <header style={styles.header}>
        <div style={styles.headerContent}>
          <div>
            <h1 style={styles.title}>
              <Activity color="#4F46E5" size={32} />
              Behavioral Analysis Toolkit
            </h1>
            <p style={styles.subtitle}>
              Reaching Movement & Subliminal Priming Analysis
            </p>
          </div>
          <button
            onClick={reloadData}
            disabled={loading}
            style={{
              ...styles.button,
              ...styles.buttonPrimary,
              ...(loading ? styles.buttonDisabled : {}),
              width: 'auto'
            }}
          >
            <RefreshCw size={18} style={loading ? { animation: 'spin 1s linear infinite' } : {}} />
            Reload Data
          </button>
        </div>
      </header>

      <div style={styles.container}>
        {/* Status Card */}
        <div style={styles.card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2 style={styles.cardTitle}>
              <Database size={24} color="#4F46E5" />
              Dataset Status
            </h2>
            {status?.connected ? (
              <div style={{...styles.badge, ...styles.badgeSuccess}}>
                <CheckCircle size={20} />
                Connected
              </div>
            ) : (
              <div style={{...styles.badge, ...styles.badgeError}}>
                <AlertCircle size={20} />
                Disconnected
              </div>
            )}
          </div>

          {status?.connected && (
            <div style={styles.grid4}>
              <div style={{...styles.statBox, background: '#EEF2FF'}}>
                <p style={styles.statLabel}>Participants</p>
                <p style={{...styles.statValue, color: '#4F46E5'}}>{status.participants_count}</p>
              </div>
              <div style={{...styles.statBox, background: '#F3E8FF'}}>
                <p style={styles.statLabel}>Total Trials</p>
                <p style={{...styles.statValue, color: '#7C3AED'}}>{status.trials_count}</p>
              </div>
              <div style={{...styles.statBox, background: '#DBEAFE'}}>
                <p style={styles.statLabel}>With ADHD</p>
                <p style={{...styles.statValue, color: '#2563EB'}}>{status.demographics?.with_adhd || 0}</p>
              </div>
              <div style={{...styles.statBox, background: '#D1FAE5'}}>
                <p style={styles.statLabel}>Last Updated</p>
                <p style={{fontSize: '14px', fontWeight: '600', color: '#059669'}}>
                  {status.last_updated ? new Date(status.last_updated).toLocaleString() : 'N/A'}
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Main Actions */}
        <div style={styles.grid2}>
          <div style={styles.card}>
            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{...styles.statBox, background: '#EEF2FF', padding: '16px', borderRadius: '8px', flexShrink: 0}}>
                <BarChart3 size={32} color="#4F46E5" />
              </div>
              <div style={{ flex: 1 }}>
                <h3 style={{ fontSize: '18px', fontWeight: 'bold', marginBottom: '8px' }}>Run Full Analysis</h3>
                <p style={{ fontSize: '14px', color: '#6B7280', marginBottom: '16px' }}>
                  Generate all plots, statistical tests, and comprehensive reports
                </p>
                <button
                  onClick={runFullAnalysis}
                  disabled={loading || !status?.connected}
                  style={{
                    ...styles.button,
                    ...styles.buttonPrimary,
                    ...(loading || !status?.connected ? styles.buttonDisabled : {})
                  }}
                >
                  {loading ? 'Analyzing...' : 'Start Analysis'}
                </button>
              </div>
            </div>
          </div>

          <div style={styles.card}>
            <div style={{ display: 'flex', gap: '16px' }}>
              <div style={{...styles.statBox, background: '#F3E8FF', padding: '16px', borderRadius: '8px', flexShrink: 0}}>
                <TrendingUp size={32} color="#7C3AED" />
              </div>
              <div style={{ flex: 1 }}>
                <h3 style={{ fontSize: '18px', fontWeight: 'bold', marginBottom: '8px' }}>Velocity Analysis</h3>
                <p style={{ fontSize: '14px', color: '#6B7280', marginBottom: '16px' }}>
                  Create custom velocity profile plots with adjustable parameters
                </p>
                <button
                  onClick={() => setActiveTab('velocity-config')}
                  disabled={loading || !status?.connected}
                  style={{
                    ...styles.button,
                    background: '#7C3AED',
                    color: 'white',
                    ...(loading || !status?.connected ? styles.buttonDisabled : {})
                  }}
                >
                  Configure Velocity Plots
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Data Export */}
        <div style={styles.card}>
          <h3 style={styles.cardTitle}>
            <Download size={22} color="#4F46E5" />
            Export Data
          </h3>
          <div style={styles.grid2}>
            <button
              onClick={downloadRawData}
              disabled={loading || !status?.connected}
              style={{
                ...styles.button,
                ...styles.buttonSecondary,
                ...(loading || !status?.connected ? styles.buttonDisabled : {})
              }}
            >
              <Download size={18} />
              Download Raw Data (Excel)
            </button>
            <button
              onClick={downloadProcessedData}
              disabled={loading || !status?.connected}
              style={{
                ...styles.button,
                ...styles.buttonSecondary,
                ...(loading || !status?.connected ? styles.buttonDisabled : {})
              }}
            >
              <Download size={18} />
              Download Processed Data (CSV)
            </button>
          </div>
          <p style={{ ...styles.helpText, marginTop: '12px' }}>
            Raw data includes all Firebase data unchanged. Processed data includes calculated metrics.
          </p>
        </div>

        {/* Velocity Configuration */}
        {activeTab === 'velocity-config' && (
          <div style={styles.card}>
            <h3 style={styles.cardTitle}>Velocity Plot Settings</h3>
            
            <div>
              <label style={styles.label}>Time Cap (milliseconds)</label>
              <input
                type="number"
                value={velocitySettings.time_cap}
                onChange={(e) => setVelocitySettings({...velocitySettings, time_cap: parseInt(e.target.value)})}
                style={styles.input}
                placeholder="10000"
              />
              <p style={styles.helpText}>Maximum time to display on X-axis (prevents outliers)</p>
            </div>

            <div style={{ marginTop: '16px' }}>
              <label style={styles.label}>Velocity Cap (pixels/second)</label>
              <input
                type="number"
                value={velocitySettings.velocity_cap}
                onChange={(e) => setVelocitySettings({...velocitySettings, velocity_cap: parseInt(e.target.value)})}
                style={styles.input}
                placeholder="5000"
              />
              <p style={styles.helpText}>Maximum velocity to display on Y-axis (filters calculation errors)</p>
            </div>

            <div style={{ marginTop: '16px' }}>
              <label style={styles.label}>Split By Demographic</label>
              <select
                value={velocitySettings.split_by || ''}
                onChange={(e) => setVelocitySettings({...velocitySettings, split_by: e.target.value || null})}
                style={styles.input}
              >
                <option value="">None (All Together)</option>
                <option value="hasAttentionDeficit">ADHD</option>
                <option value="gender">Gender</option>
                <option value="hasGlasses">Glasses</option>
              </select>
            </div>

            <button
              onClick={generateVelocityPlots}
              disabled={loading}
              style={{
                ...styles.button,
                background: '#7C3AED',
                color: 'white',
                marginTop: '16px',
                ...(loading ? styles.buttonDisabled : {})
              }}
            >
              {loading ? 'Generating...' : 'Generate Velocity Plots'}
            </button>
          </div>
        )}

        {/* Analysis Results */}
        {activeTab === 'analysis' && analysisResults && (
          <div style={styles.card}>
            <h3 style={styles.cardTitle}>Analysis Results</h3>
            
            {analysisResults.plots?.summary && (
              <div style={{ marginBottom: '24px' }}>
                <h4 style={{ fontWeight: '600', marginBottom: '12px' }}>Performance Summary</h4>
                <img 
                  src={`data:image/png;base64,${analysisResults.plots.summary}`}
                  alt="Summary"
                  style={styles.image}
                />
              </div>
            )}

            {analysisResults.plots?.demographics && Object.keys(analysisResults.plots.demographics).length > 0 && (
              <div>
                <h4 style={{ fontWeight: '600', marginBottom: '12px' }}>Demographic Comparisons</h4>
                <div style={styles.grid2}>
                  {Object.entries(analysisResults.plots.demographics).map(([name, plot]) => (
                    <div key={name} style={{ border: '1px solid #E5E7EB', borderRadius: '8px', padding: '8px' }}>
                      <p style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px', textTransform: 'capitalize' }}>
                        {name.replace('_', ' ')}
                      </p>
                      <img 
                        src={`data:image/png;base64,${plot}`}
                        alt={name}
                        style={styles.image}
                      />
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Velocity Plots */}
        {activeTab === 'velocity' && velocityPlots && (
          <div style={styles.card}>
            <h3 style={styles.cardTitle}>Velocity Profiles</h3>
            <div>
              {Object.entries(velocityPlots).map(([name, plot]) => (
                <div key={name} style={{ marginBottom: '24px' }}>
                  <h4 style={{ fontWeight: '600', marginBottom: '12px', textTransform: 'capitalize' }}>
                    {name.replace(/_/g, ' ')}
                  </h4>
                  <img 
                    src={`data:image/png;base64,${plot}`}
                    alt={name}
                    style={styles.image}
                  />
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Advanced Settings */}
        <div style={styles.card}>
          <button
            onClick={() => setShowAdvanced(!showAdvanced)}
            style={{
              ...styles.button,
              ...styles.buttonSecondary,
              justifyContent: 'space-between'
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Settings size={22} />
              Advanced Settings
            </span>
            {showAdvanced ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
          </button>

          {showAdvanced && (
            <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid #E5E7EB' }}>
              <button
                onClick={testFirebase}
                style={{ ...styles.button, ...styles.buttonSecondary, marginBottom: '8px' }}
              >
                Test Firebase Connection
              </button>
              <button
                onClick={fetchStatus}
                style={{ ...styles.button, ...styles.buttonSecondary, marginBottom: '8px' }}
              >
                Refresh Status
              </button>
              <div style={{ 
                fontSize: '12px', 
                color: '#6B7280', 
                background: '#F9FAFB', 
                padding: '12px', 
                borderRadius: '8px',
                marginTop: '8px'
              }}>
                <p style={{ fontWeight: '600', marginBottom: '4px' }}>System Info:</p>
                <p>Backend URL: {API_URL}</p>
                <p>Frontend Version: 1.0.0</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}