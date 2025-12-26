import React, { useState, useEffect } from 'react';
import { 
  Activity, Database, Download, BarChart3, TrendingUp, 
  Settings, RefreshCw, CheckCircle, AlertCircle, ChevronRight,
  Home, FileText, Zap
} from 'lucide-react';

const API_URL = 'http://localhost:5000/api';

const styles = {
  page: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #EEF2FF 0%, #E0E7FF 100%)',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    display: 'flex',
  },
  sidebar: {
    width: '280px',
    background: 'white',
    boxShadow: '2px 0 8px rgba(0,0,0,0.1)',
    display: 'flex',
    flexDirection: 'column',
    position: 'fixed',
    height: '100vh',
    overflowY: 'auto',
  },
  sidebarHeader: {
    padding: '24px 20px',
    borderBottom: '1px solid #E5E7EB',
  },
  sidebarTitle: {
    fontSize: '18px',
    fontWeight: 'bold',
    color: '#1F2937',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    margin: 0,
  },
  sidebarNav: {
    flex: 1,
    padding: '16px',
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
  navItemActive: {
    background: '#EEF2FF',
    color: '#4F46E5',
  },
  navItemInactive: {
    color: '#6B7280',
  },
  sidebarFooter: {
    padding: '16px',
    borderTop: '1px solid #E5E7EB',
  },
  mainContent: {
    marginLeft: '280px',
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    background: 'white',
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
    padding: '16px 32px',
  },
  headerContent: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: '24px',
    fontWeight: 'bold',
    color: '#1F2937',
    margin: 0,
  },
  subtitle: {
    fontSize: '14px',
    color: '#6B7280',
    marginTop: '4px',
  },
  container: {
    padding: '32px',
    overflowY: 'auto',
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
    gap: '16px',
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
    marginBottom: '16px',
  },
  alert: {
    padding: '12px 16px',
    borderRadius: '8px',
    marginBottom: '16px',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  alertSuccess: {
    background: '#D1FAE5',
    color: '#065F46',
  },
  alertInfo: {
    background: '#DBEAFE',
    color: '#1E40AF',
  },
};

export default function BehavioralAnalysisToolkit() {
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState('overview');
  const [analysisResults, setAnalysisResults] = useState(null);
  const [velocityPlots, setVelocityPlots] = useState(null);
  
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
        alert('✅ Data reloaded successfully!');
      }
    } catch (error) {
      alert('❌ Error reloading data: ' + error.message);
    }
    setLoading(false);
  };

  const downloadRawData = async (dataType) => {
    try {
      const endpoint = dataType === 'participants' ? 'participants' : 'trials';
      const response = await fetch(`${API_URL}/export/raw?type=${endpoint}`);
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `raw_${dataType}_${Date.now()}.${dataType === 'participants' ? 'csv' : 'xlsx'}`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      alert('❌ Error downloading raw data: ' + error.message);
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
      alert('❌ Error downloading processed data: ' + error.message);
    }
  };

  const runFullAnalysis = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/analysis/full`, { method: 'POST' });
      const data = await response.json();
      if (data.status === 'success') {
        setAnalysisResults(data);
        setCurrentPage('analysis-results');
        alert('✅ Analysis complete! Results are displayed below.');
      } else {
        alert('❌ Analysis failed: ' + data.message);
      }
    } catch (error) {
      alert('❌ Error running analysis: ' + error.message);
    }
    setLoading(false);
  };

  const generateVelocityPlots = async () => {
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/plots/velocity`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(velocitySettings)
      });
      const data = await response.json();
      if (data.status === 'success') {
        setVelocityPlots(data.plots);
        setCurrentPage('velocity-results');
        alert('✅ Velocity plots generated!');
      } else {
        alert('❌ Failed to generate velocity plots: ' + data.message);
      }
    } catch (error) {
      alert('❌ Error generating velocity plots: ' + error.message);
    }
    setLoading(false);
  };

  const testFirebase = async () => {
    try {
      const response = await fetch(`${API_URL}/test/firebase`);
      const data = await response.json();
      if (data.connected) {
        alert('✅ Firebase connection successful!\n\nSummary:\n' + JSON.stringify(data.summary, null, 2));
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
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
        button:hover:not(:disabled) {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
      `}</style>

      {/* Sidebar */}
      <div style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h1 style={styles.sidebarTitle}>
            <Activity color="#4F46E5" size={24} />
            Analysis Toolkit
          </h1>
          <p style={{ fontSize: '12px', color: '#6B7280', margin: '4px 0 0 0' }}>
            Reaching Movement Research
          </p>
        </div>

        <nav style={styles.sidebarNav}>
          <button
            onClick={() => setCurrentPage('overview')}
            style={{
              ...styles.navItem,
              ...(currentPage === 'overview' ? styles.navItemActive : styles.navItemInactive)
            }}
          >
            <Home size={20} />
            Overview
          </button>

          <button
            onClick={() => setCurrentPage('analysis')}
            style={{
              ...styles.navItem,
              ...(currentPage === 'analysis' || currentPage === 'analysis-results' ? styles.navItemActive : styles.navItemInactive)
            }}
          >
            <BarChart3 size={20} />
            Full Analysis
          </button>

          <button
            onClick={() => setCurrentPage('velocity')}
            style={{
              ...styles.navItem,
              ...(currentPage === 'velocity' || currentPage === 'velocity-results' ? styles.navItemActive : styles.navItemInactive)
            }}
          >
            <TrendingUp size={20} />
            Velocity Analysis
          </button>

          <button
            onClick={() => setCurrentPage('export')}
            style={{
              ...styles.navItem,
              ...(currentPage === 'export' ? styles.navItemActive : styles.navItemInactive)
            }}
          >
            <Download size={20} />
            Export Data
          </button>

          <button
            onClick={() => setCurrentPage('settings')}
            style={{
              ...styles.navItem,
              ...(currentPage === 'settings' ? styles.navItemActive : styles.navItemInactive)
            }}
          >
            <Settings size={20} />
            Settings
          </button>
        </nav>

        <div style={styles.sidebarFooter}>
          <button
            onClick={reloadData}
            disabled={loading}
            style={{
              ...styles.button,
              ...styles.buttonPrimary,
              ...(loading ? styles.buttonDisabled : {})
            }}
          >
            <RefreshCw size={18} style={loading ? { animation: 'spin 1s linear infinite' } : {}} />
            Reload Data
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div style={styles.mainContent}>
        <header style={styles.header}>
          <div style={styles.headerContent}>
            <div>
              <h2 style={styles.title}>
                {currentPage === 'overview' && 'Overview'}
                {currentPage === 'analysis' && 'Run Full Analysis'}
                {currentPage === 'analysis-results' && 'Analysis Results'}
                {currentPage === 'velocity' && 'Velocity Analysis Configuration'}
                {currentPage === 'velocity-results' && 'Velocity Analysis Results'}
                {currentPage === 'export' && 'Export Data'}
                {currentPage === 'settings' && 'Settings'}
              </h2>
            </div>
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
        </header>

        <div style={styles.container}>
          {/* Overview Page */}
          {currentPage === 'overview' && (
            <>
              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <Database size={24} color="#4F46E5" />
                  Dataset Status
                </h3>
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

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>Quick Actions</h3>
                <div style={styles.grid2}>
                  <button
                    onClick={() => setCurrentPage('analysis')}
                    style={{...styles.button, ...styles.buttonPrimary}}
                  >
                    <BarChart3 size={20} />
                    Run Full Analysis
                    <ChevronRight size={18} />
                  </button>
                  <button
                    onClick={() => setCurrentPage('velocity')}
                    style={{...styles.button, background: '#7C3AED', color: 'white'}}
                  >
                    <TrendingUp size={20} />
                    Velocity Analysis
                    <ChevronRight size={18} />
                  </button>
                </div>
              </div>
            </>
          )}

          {/* Full Analysis Page */}
          {currentPage === 'analysis' && (
            <div style={styles.card}>
              <h3 style={styles.cardTitle}>
                <BarChart3 size={24} color="#4F46E5" />
                Run Complete Statistical Analysis
              </h3>
              <p style={{ color: '#6B7280', marginBottom: '16px' }}>
                This will generate all plots, run statistical tests (repeated measures ANOVA), and create comprehensive reports.
                Analysis results will be displayed on this page - no automatic downloads.
              </p>
              <div style={{...styles.alert, ...styles.alertInfo}}>
                <AlertCircle size={20} />
                <span>Processing may take 1-2 minutes depending on dataset size.</span>
              </div>
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
          )}

          {/* Analysis Results Page */}
          {currentPage === 'analysis-results' && analysisResults && (
            <>
              <div style={{...styles.alert, ...styles.alertSuccess}}>
                <CheckCircle size={20} />
                <span>Analysis completed successfully! Scroll to view all results.</span>
              </div>

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>Performance Summary</h3>
                {analysisResults.plots?.summary && (
                  <img 
                    src={`data:image/png;base64,${analysisResults.plots.summary}`}
                    alt="Summary"
                    style={styles.image}
                  />
                )}
              </div>

              {analysisResults.plots?.demographics && Object.keys(analysisResults.plots.demographics).length > 0 && (
                <div style={styles.card}>
                  <h3 style={styles.cardTitle}>Demographic Comparisons</h3>
                  <div style={styles.grid2}>
                    {Object.entries(analysisResults.plots.demographics).map(([name, plot]) => (
                      <div key={name}>
                        <h4 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '8px', textTransform: 'capitalize' }}>
                          {name.replace('_', ' ')}
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
            </>
          )}

          {/* Velocity Configuration Page */}
          {currentPage === 'velocity' && (
            <div style={styles.card}>
              <h3 style={styles.cardTitle}>
                <TrendingUp size={24} color="#7C3AED" />
                Velocity Plot Configuration
              </h3>
              
              <div>
                <label style={styles.label}>Time Cap (milliseconds)</label>
                <input
                  type="number"
                  value={velocitySettings.time_cap}
                  onChange={(e) => setVelocitySettings({...velocitySettings, time_cap: parseInt(e.target.value)})}
                  style={styles.input}
                  placeholder="10000"
                />
                <p style={styles.helpText}>Maximum time to display on X-axis (prevents outliers from stretching the graph)</p>
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
                  marginTop: '24px',
                  ...(loading ? styles.buttonDisabled : {})
                }}
              >
                {loading ? 'Generating...' : 'Generate Velocity Plots'}
              </button>
            </div>
          )}

          {/* Velocity Results Page */}
          {currentPage === 'velocity-results' && velocityPlots && (
            <>
              <div style={{...styles.alert, ...styles.alertSuccess}}>
                <CheckCircle size={20} />
                <span>Velocity plots generated successfully!</span>
              </div>

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>Velocity Profiles</h3>
                {Object.entries(velocityPlots).map(([name, plot]) => (
                  <div key={name}>
                    <h4 style={{ fontSize: '16px', fontWeight: '600', marginBottom: '12px', textTransform: 'capitalize' }}>
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
            </>
          )}

          {/* Export Page */}
          {currentPage === 'export' && (
            <>
              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <Download size={22} color="#4F46E5" />
                  Raw Data Export
                </h3>
                <p style={{ color: '#6B7280', marginBottom: '16px' }}>
                  Export unprocessed data directly from Firebase. Choose what to download:
                </p>
                <div style={styles.grid2}>
                  <button
                    onClick={() => downloadRawData('participants')}
                    disabled={loading || !status?.connected}
                    style={{
                      ...styles.button,
                      ...styles.buttonSecondary,
                      ...(loading || !status?.connected ? styles.buttonDisabled : {})
                    }}
                  >
                    <Download size={18} />
                    Participants Data (CSV)
                  </button>
                  <button
                    onClick={() => downloadRawData('trials')}
                    disabled={loading || !status?.connected}
                    style={{
                      ...styles.button,
                      ...styles.buttonSecondary,
                      ...(loading || !status?.connected ? styles.buttonDisabled : {})
                    }}
                  >
                    <Download size={18} />
                    Trials Data (Excel)
                  </button>
                </div>
              </div>

              <div style={styles.card}>
                <h3 style={styles.cardTitle}>
                  <FileText size={22} color="#4F46E5" />
                  Processed Data Export
                </h3>
                <p style={{ color: '#6B7280', marginBottom: '16px' }}>
                  Export data with calculated metrics (speed, jerk, peaks, variance, etc.)
                </p>
                <button
                  onClick={downloadProcessedData}
                  disabled={loading || !status?.connected}
                  style={{
                    ...styles.button,
                    ...styles.buttonPrimary,
                    ...(loading || !status?.connected ? styles.buttonDisabled : {})
                  }}
                >
                  <Download size={18} />
                  Download Processed Data (CSV)
                </button>
                <p style={{ ...styles.helpText, marginTop: '12px' }}>
                  Includes: reactionTime, movementTime, pathLength, averageSpeed, speedVariance, velocityPeaks, jerk, and more
                </p>
              </div>
            </>
          )}

          {/* Settings Page */}
          {currentPage === 'settings' && (
            <div style={styles.card}>
              <h3 style={styles.cardTitle}>
                <Settings size={22} color="#4F46E5" />
                Advanced Settings
              </h3>
              
              <button
                onClick={testFirebase}
                style={{ ...styles.button, ...styles.buttonSecondary, marginBottom: '12px' }}
              >
                <Zap size={18} />
                Test Firebase Connection
              </button>

              <button
                onClick={fetchStatus}
                style={{ ...styles.button, ...styles.buttonSecondary, marginBottom: '12px' }}
              >
                <RefreshCw size={18} />
                Refresh Status
              </button>

              <div style={{ 
                fontSize: '12px', 
                color: '#6B7280', 
                background: '#F9FAFB', 
                padding: '16px', 
                borderRadius: '8px',
                marginTop: '24px'
              }}>
                <p style={{ fontWeight: '600', marginBottom: '8px' }}>System Information</p>
                <p>Backend API: {API_URL}</p>
                <p>Frontend Version: 1.0.0</p>
                <p>Status: {status?.connected ? 'Connected' : 'Disconnected'}</p>
                {status?.last_updated && (
                  <p>Last Data Update: {new Date(status.last_updated).toLocaleString()}</p>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}