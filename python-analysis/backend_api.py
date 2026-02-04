"""
Flask Backend API for Behavioral Analysis Toolkit
==================================================
REST API server that connects Firebase data to the React frontend.

This module provides:
- Database status and connection management
- Data export (raw and processed)
- Full statistical analysis execution
- Velocity plot generation
- Database cleanup operations
- File download management

API runs on: http://localhost:5000
Frontend connects to this server for all data operations.
"""

import tempfile
import zipfile
from flask import Flask, jsonify, request, send_file, after_this_request
from flask_cors import CORS
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend for server use
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
import io
import base64
import json
import os
from datetime import datetime, timezone
from typing import Dict, List, Tuple
import atexit
import shutil

# Import our analysis modules
from firebase_connector import FirebaseConnector
from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
from velocity_plotter import VelocityPlotter

# Initialize Flask app
app = Flask(__name__)
CORS(app)  # Enable CORS for React frontend

# Default Firebase credentials file
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

# Use system temp directory for analysis outputs
TEMP_ANALYSIS_DIR = tempfile.mkdtemp(prefix='analysis_')

# Track created ZIP files for cleanup
_created_zips = []


# ============================================================================
# CLEANUP FUNCTIONS
# ============================================================================

def cleanup_temp_dir():
    """Clean up temporary analysis directory on shutdown."""
    if os.path.exists(TEMP_ANALYSIS_DIR):
        shutil.rmtree(TEMP_ANALYSIS_DIR, ignore_errors=True)
        print(f"🧹 Cleaned up temp directory: {TEMP_ANALYSIS_DIR}")


def cleanup_temp_zips():
    """Clean up any ZIP files created during session."""
    for zip_path in _created_zips:
        if os.path.exists(zip_path):
            try:
                os.remove(zip_path)
                print(f"🧹 Deleted temp ZIP: {zip_path}")
            except Exception as e:
                print(f"⚠️ Could not delete {zip_path}: {e}")


def cleanup_old_zips_on_startup():
    """Clean up any old analysis ZIPs left from previous runs."""
    temp_dir = tempfile.gettempdir()
    count = 0
    for file in os.listdir(temp_dir):
        if file.startswith('analysis_results_') and file.endswith('.zip'):
            try:
                os.remove(os.path.join(temp_dir, file))
                count += 1
            except:
                pass
    if count > 0:
        print(f"🧹 Cleaned up {count} old ZIP file(s) from previous runs")


# Register cleanup functions to run on exit
atexit.register(cleanup_temp_dir)
atexit.register(cleanup_temp_zips)

# Clean up old files on startup
cleanup_old_zips_on_startup()


# ============================================================================
# DATA CACHE
# ============================================================================

# Global data cache to avoid repeated Firebase calls
_cache = {
    'participants_df': None,
    'trials_df': None,
    'last_loaded': None,
    'connector': None
}


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def init_firebase():
    """Initialize Firebase connection if not already done."""
    if _cache['connector'] is None:
        _cache['connector'] = FirebaseConnector(DEFAULT_CREDENTIALS_FILENAME)


def load_data(force_reload=False):
    """
    Load data from Firebase or cache.
    
    Args:
        force_reload (bool): If True, bypass cache and reload from Firebase
        
    Returns:
        tuple: (participants_df, trials_df)
    """
    init_firebase()
    
    if force_reload or _cache['participants_df'] is None:
        participants_df, trials_df = _cache['connector'].fetch_all_data()
        _cache['participants_df'] = participants_df
        _cache['trials_df'] = trials_df
        _cache['last_loaded'] = datetime.now(timezone.utc)
    
    return _cache['participants_df'], _cache['trials_df']


def calculate_advanced_metrics(trials_df: pd.DataFrame) -> pd.DataFrame:
    """
    Calculate advanced metrics for all trials.
    
    Adds computed columns:
    - averageSpeed: Mean velocity during movement
    - speedVariance: Variance in velocity
    - velocityPeaks: Number of velocity peaks (using scipy.signal)
    - jerk: Mean absolute jerk (rate of acceleration change)
    - trialType_mean_RT: Mean RT for this trial type
    
    Args:
        trials_df: DataFrame with trial data
        
    Returns:
        pd.DataFrame: DataFrame with added metric columns
    """
    df = trials_df.copy()
    
    for idx, trial in df.iterrows():
        path = trial.get('movementPath', [])
        
        if not isinstance(path, list) or len(path) < 3:
            continue
        
        # Calculate velocities between each point
        velocities = []
        for i in range(1, len(path)):
            if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                dt = (path[i]['t'] - path[i-1]['t']) / 1000.0  # Convert to seconds
                if dt > 0:
                    dx = path[i]['x'] - path[i-1]['x']
                    dy = path[i]['y'] - path[i-1]['y']
                    dist = np.sqrt(dx**2 + dy**2)
                    velocities.append(dist / dt)
        
        if len(velocities) > 0:
            df.at[idx, 'averageSpeed'] = np.mean(velocities)
            df.at[idx, 'speedVariance'] = np.var(velocities)
            
            # Count velocity peaks
            if len(velocities) > 2:
                from scipy.signal import find_peaks
                peaks, _ = find_peaks(velocities, prominence=50)
                df.at[idx, 'velocityPeaks'] = len(peaks)
            
            # Calculate jerk (rate of change of acceleration)
            if len(velocities) > 3:
                accelerations = np.diff(velocities)
                if len(accelerations) > 1:
                    jerks = np.diff(accelerations)
                    df.at[idx, 'jerk'] = np.mean(np.abs(jerks))
    
    # Add condition mean RT
    df['trialType_mean_RT'] = df.groupby('trialType')['reactionTime'].transform('mean')
    
    return df


def fig_to_base64(fig):
    """
    Convert matplotlib figure to base64 string for JSON transmission.
    
    Args:
        fig: Matplotlib figure object
        
    Returns:
        str: Base64 encoded PNG image
    """
    buf = io.BytesIO()
    fig.savefig(buf, format='png', dpi=150, bbox_inches='tight')
    buf.seek(0)
    img_base64 = base64.b64encode(buf.read()).decode('utf-8')
    plt.close(fig)
    return img_base64


# ============================================================================
# API ENDPOINTS
# ============================================================================

@app.route('/api/status', methods=['GET'])
def get_status():
    """
    Get current system status.
    
    Returns database connection status, data counts, and demographics.
    
    Returns:
        JSON: {
            status: 'success'|'error',
            connected: bool,
            participants_count: int,
            trials_count: int,
            last_updated: str (ISO format),
            demographics: {...}
        }
    """
    try:
        init_firebase()
        participants_df, trials_df = load_data()
        
        # Get gender breakdown
        gender_counts = participants_df['gender'].value_counts().to_dict() if 'gender' in participants_df.columns else {}
        
        return jsonify({
            'status': 'success',
            'connected': True,
            'participants_count': len(participants_df),
            'trials_count': len(trials_df),
            'last_updated': _cache['last_loaded'].isoformat() if _cache['last_loaded'] else None,
            'demographics': {
                'with_adhd': int(participants_df['hasAttentionDeficit'].sum()) if 'hasAttentionDeficit' in participants_df.columns else 0,
                'with_glasses': int(participants_df['hasGlasses'].sum()) if 'hasGlasses' in participants_df.columns else 0,
                'gender_distribution': gender_counts,
                'male_count': gender_counts.get('Male', 0),
                'female_count': gender_counts.get('Female', 0)
            }
        })
    except Exception as e:
        return jsonify({
            'status': 'error',
            'connected': False,
            'message': str(e)
        }), 500


@app.route('/api/reload', methods=['POST'])
def reload_data():
    """
    Force reload data from Firebase (bypass cache).
    
    Returns:
        JSON: {status, message, participants_count, trials_count}
    """
    try:
        participants_df, trials_df = load_data(force_reload=True)
        return jsonify({
            'status': 'success',
            'message': 'Data reloaded successfully',
            'participants_count': len(participants_df),
            'trials_count': len(trials_df)
        })
    except Exception as e:
        return jsonify({
            'status': 'error',
            'message': str(e)
        }), 500


@app.route('/api/export/raw', methods=['GET'])
def export_raw_data():
    """
    Export raw data from Firebase (before any processing).
    
    Query params:
        type: 'participants'|'trials'|'both' (default: 'both')
    
    Returns:
        File: CSV or Excel file with raw data
    """
    try:
        data_type = request.args.get('type', 'both')
        participants_df, trials_df = load_data()
        
        timestamp = datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')
        
        if data_type == 'participants':
            # Export only participants as CSV
            output = io.StringIO()
            participants_df.to_csv(output, index=False)
            output.seek(0)
            
            return send_file(
                io.BytesIO(output.getvalue().encode()),
                mimetype='text/csv',
                as_attachment=True,
                download_name=f'raw_participants_{timestamp}.csv'
            )
        
        elif data_type == 'trials':
            # Export trials as CSV
            trials_export = trials_df.copy()
            # Convert movementPath to JSON string for CSV compatibility
            if 'movementPath' in trials_export.columns:
                trials_export['movementPath'] = trials_export['movementPath'].apply(
                    lambda x: json.dumps(x) if isinstance(x, list) else x
                )
            
            output = io.StringIO()
            trials_export.to_csv(output, index=False)
            output.seek(0)
            
            return send_file(
                io.BytesIO(output.getvalue().encode()),
                mimetype='text/csv',
                as_attachment=True,
                download_name=f'raw_trials_{timestamp}.csv'
            )
        
        else:  # 'both' - default legacy behavior
            # Export both as Excel with multiple sheets
            trials_export = trials_df.copy()
            if 'movementPath' in trials_export.columns:
                trials_export['movementPath'] = trials_export['movementPath'].apply(
                    lambda x: json.dumps(x) if isinstance(x, list) else x
                )
            
            output = io.BytesIO()
            with pd.ExcelWriter(output, engine='openpyxl') as writer:
                participants_df.to_excel(writer, sheet_name='Participants', index=False)
                trials_export.to_excel(writer, sheet_name='Trials', index=False)
            
            output.seek(0)
            
            return send_file(
                output,
                mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                as_attachment=True,
                download_name=f'raw_data_{timestamp}.xlsx'
            )
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500


@app.route('/api/export/processed', methods=['GET'])
def export_processed_data():
    """
    Export processed data with all calculated metrics.
    
    Returns CSV with advanced metrics like averageSpeed, jerk, etc.
    
    Returns:
        File: CSV with processed trial data
    """
    try:
        participants_df, trials_df = load_data()
        
        # Calculate all metrics
        processed_df = calculate_advanced_metrics(trials_df)
        
        # Select columns for export
        export_cols = [
            'participantId', 'trialNumber', 'trialType', 'targetIndex',
            'reactionTime', 'movementTime', 'totalResponseTime',
            'pathLength', 'averageSpeed', 'speedVariance',
            'velocityPeaks', 'jerk', 'trialType_mean_RT'
        ]
        
        available_cols = [col for col in export_cols if col in processed_df.columns]
        export_df = processed_df[available_cols]
        
        # Create CSV
        timestamp = datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')
        output = io.StringIO()
        export_df.to_csv(output, index=False)
        output.seek(0)
        
        return send_file(
            io.BytesIO(output.getvalue().encode()),
            mimetype='text/csv',
            as_attachment=True,
            download_name=f'processed_data_{timestamp}.csv'
        )
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500


@app.route('/api/analysis/full', methods=['POST'])
def run_full_analysis():
    """
    Run complete statistical analysis and return plot data.
    
    Executes:
    - Main hypothesis testing (repeated measures ANOVA)
    - All demographic analyses
    - Summary plots
    
    Returns:
        JSON: {
            status: 'success',
            plots: {summary: base64, demographics: {...}},
            report: str (text report),
            output_dir: str (for download)
        }
    """
    try:
        participants_df, trials_df = load_data()
        
        # Create analyzer with temp directory
        analyzer = SubliminalPrimingAnalyzer(
            trials_df, 
            participants_df,
            output_dir=TEMP_ANALYSIS_DIR
        )
        
        # Run analyses
        analyzer.test_main_hypothesis()
        analyzer.run_all_demographics()
        
        # Generate plots
        plots = {}
        
        # Summary plot
        analyzer.create_summary_plots()
        summary_path = os.path.join(analyzer.figures_dir, 'summary.png')
        with open(summary_path, 'rb') as f:
            plots['summary'] = base64.b64encode(f.read()).decode('utf-8')
        
        # Demographic plots
        demographic_plots = {}
        for demo_file in os.listdir(analyzer.demographic_dir):
            if demo_file.endswith('.png'):
                with open(os.path.join(analyzer.demographic_dir, demo_file), 'rb') as f:
                    demo_name = demo_file.replace('comparison_', '').replace('.png', '')
                    demographic_plots[demo_name] = base64.b64encode(f.read()).decode('utf-8')
        
        plots['demographics'] = demographic_plots
        
        # Get report text
        report_text = '\n'.join(analyzer.report_lines)
        analyzer.save_report()
        
        return jsonify({
            'status': 'success',
            'plots': plots,
            'report': report_text,
            'output_dir': analyzer.output_dir  # Frontend uses this to construct download URL
        })
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500


@app.route('/api/analysis/download', methods=['POST'])
def download_analysis_package():
    """
    Download all analysis results as a ZIP file.
    
    Request body:
        {output_dir: str} - Directory containing analysis outputs
    
    Returns:
        File: ZIP archive with all analysis files
    """
    try:
        data = request.json
        output_dir = data.get('output_dir') 
        
        if not output_dir:
            return jsonify({'status': 'error', 'message': 'No directory provided'}), 400

        abs_output_dir = os.path.abspath(output_dir)
        
        if not os.path.exists(abs_output_dir):
            return jsonify({'status': 'error', 'message': f'Path not found: {abs_output_dir}'}), 404
        
        # Create ZIP file with timestamp
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        temp_dir = tempfile.gettempdir()
        zip_filename = f'analysis_results_{timestamp}.zip'
        zip_path = os.path.join(temp_dir, zip_filename)

        # Build the ZIP
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            file_count = 0
            for root, dirs, files in os.walk(abs_output_dir):
                for file in files:
                    file_path = os.path.join(root, file)
                    arcname = os.path.relpath(file_path, abs_output_dir)
                    zipf.write(file_path, arcname)
                    file_count += 1
        
        if file_count == 0:
            return jsonify({'status': 'error', 'message': 'Target directory was empty'}), 400
        
        # Track this ZIP for cleanup
        _created_zips.append(zip_path)
        
        # Schedule deletion after file is sent
        @after_this_request
        def remove_file(response):
            """Delete ZIP after it's been sent to client."""
            try:
                import threading
                def delayed_delete():
                    import time
                    time.sleep(2)  # Wait 2 seconds to ensure file is sent
                    if os.path.exists(zip_path):
                        os.remove(zip_path)
                        print(f"🧹 Deleted ZIP after download: {zip_path}")
                        if zip_path in _created_zips:
                            _created_zips.remove(zip_path)
                
                thread = threading.Thread(target=delayed_delete)
                thread.daemon = True
                thread.start()
            except Exception as e:
                print(f"⚠️ Could not delete ZIP: {e}")
            return response
        
        return send_file(
            zip_path,
            mimetype='application/zip',
            as_attachment=True,
            download_name=zip_filename
        )
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500


@app.route('/api/plots/velocity', methods=['POST'])
def create_velocity_plots():
    """
    Create custom velocity plots with user-specified parameters.
    
    Request body:
        {
            time_cap: int (default 5500),
            velocity_cap: int (default 5000),
            split_by: str|null (demographic column),
            include_overlay: bool (default True)
        }
    
    Returns:
        JSON: {
            status: 'success',
            plots: {plot_name: base64_image, ...},
            output_dir: str (for download)
        }
    """
    try:
        data = request.json
        time_cap = data.get('time_cap', 5500)
        velocity_cap = data.get('velocity_cap', 5000)
        split_by = data.get('split_by', None)
        include_overlay = data.get('include_overlay', True)
        
        participants_df, trials_df = load_data()
        
        # Create unique temporary directory for this velocity analysis
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        velocity_dir = tempfile.mkdtemp(prefix=f'velocity_{timestamp}_')
        
        # Track for cleanup
        _created_zips.append(velocity_dir)
        
        plotter = VelocityPlotter(trials_df, output_dir=velocity_dir)
        
        # Generate unified plot
        plotter.plot_all_velocities(
            time_cap_ms=time_cap,
            velocity_cap_px_s=velocity_cap,
            split_by_col=split_by
        )
        
        # Generate overlay plot if requested
        if include_overlay:
            plotter.plot_overlay_all_conditions(
                time_cap_ms=time_cap,
                velocity_cap=velocity_cap
            )
        
        # Always generate comparison matrix
        plotter.create_velocity_comparison_matrix(
            time_cap_ms=time_cap,
            velocity_cap=velocity_cap
        )
        
        # Find generated files and encode as base64
        plots = {}
        for file in os.listdir(velocity_dir):
            if file.endswith('.png'):
                with open(os.path.join(velocity_dir, file), 'rb') as f:
                    plot_name = file.replace('.png', '')
                    plots[plot_name] = base64.b64encode(f.read()).decode('utf-8')
        
        return jsonify({
            'status': 'success',
            'plots': plots,
            'output_dir': velocity_dir  # For ZIP download
        })
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500        


@app.route('/api/stats/hypothesis', methods=['GET'])
def get_hypothesis_stats():
    """
    Get main hypothesis test statistics (for advanced users).
    
    Returns detailed ANOVA statistics without generating plots.
    
    Returns:
        JSON: {
            status: 'success',
            conditions: {condition_name: {mean, std, n, sem}},
            anova: {f_statistic, p_value, significant, n_participants}
        }
    """
    try:
        participants_df, trials_df = load_data()
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        # Get data for each condition
        condition_data = {}
        for cond in conditions:
            data = trials_df[trials_df['trialType'] == cond]['reactionTime'].dropna()
            condition_data[cond] = {
                'mean': float(data.mean()),
                'std': float(data.std()),
                'n': int(len(data)),
                'sem': float(data.sem())
            }
        
        # Repeated measures ANOVA
        grouped = trials_df.groupby(['participantId', 'trialType'])['reactionTime'].mean().reset_index()
        pivot = grouped.pivot(index='participantId', columns='trialType', values='reactionTime')
        pivot_clean = pivot.dropna()
        
        if len(pivot_clean) > 0:
            data_for_anova = [pivot_clean[cond].values for cond in conditions if cond in pivot_clean.columns]
            f_stat, p_value = stats.f_oneway(*data_for_anova)
            
            return jsonify({
                'status': 'success',
                'conditions': condition_data,
                'anova': {
                    'f_statistic': float(f_stat),
                    'p_value': float(p_value),
                    'significant': p_value < 0.05,
                    'n_participants': len(pivot_clean)
                }
            })
        else:
            return jsonify({
                'status': 'error',
                'message': 'Insufficient data for repeated measures ANOVA'
            }), 400
            
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500


@app.route('/api/test-connection', methods=['GET'])
def test_firebase_connection():
    """
    Test Firebase connection (simple ping).
    
    Returns:
        JSON: {status, connected, summary}
    """
    try:
        init_firebase()
        summary = _cache['connector'].get_trial_summary()
        
        return jsonify({
            'status': 'success',
            'connected': True,
            'summary': summary
        })
    except Exception as e:
        return jsonify({
            'status': 'error',
            'connected': False,
            'message': str(e)
        }), 500


@app.route('/api/clean/database', methods=['POST'])
def clean_database():
    """
    Run database cleanup (remove duplicates and incomplete sets).
    
    Request body:
        {dry_run: bool (default True)}
    
    Returns:
        JSON: {
            status: 'success',
            dry_run: bool,
            summary: {duplicates_found, incomplete_trials_found, total_actions}
        }
    """
    try:
        from firebase_cleaner import FirebaseCleaner
        data = request.json
        dry_run = data.get('dry_run', True)
        
        cleaner = FirebaseCleaner(DEFAULT_CREDENTIALS_FILENAME)
        
        # Run cleanup operations
        dup_count = cleaner.remove_duplicate_trials(dry_run=dry_run)
        inc_count = cleaner.remove_incomplete_sets(target_count=15, dry_run=dry_run)
        
        return jsonify({
            'status': 'success',
            'dry_run': dry_run,
            'summary': {
                'duplicates_found': dup_count,
                'incomplete_trials_found': inc_count,
                'total_actions': dup_count + inc_count
            }
        })
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500    
    

# ============================================================================
# MAIN - Start the Flask server
# ============================================================================

if __name__ == '__main__':
    print("="*70)
    print("BEHAVIORAL ANALYSIS TOOLKIT - Backend Server")
    print("="*70)
    print("\nStarting Flask server...")
    print("Backend available at: http://localhost:5000")
    print("Frontend should connect to this address")
    print("\nPress Ctrl+C to stop the server\n")
    
    app.run(debug=True, host='0.0.0.0', port=5000)