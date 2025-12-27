"""
Flask Backend API for Behavioral Analysis Toolkit
Handles Firebase connections, data processing, and analysis
"""

import tempfile
from flask import Flask, jsonify, request, send_file
from flask_cors import CORS
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
import io
import base64
import json
import os
from datetime import datetime
from typing import Dict, List, Tuple

# Import existing modules
from firebase_connector import FirebaseConnector
from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
from velocity_plotter import VelocityPlotter

app = Flask(__name__)
CORS(app)  # Enable CORS for React frontend

# Default credentials file
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

# Global data cache
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
    """Initialize Firebase connection if not already done"""
    if _cache['connector'] is None:
        _cache['connector'] = FirebaseConnector(DEFAULT_CREDENTIALS_FILENAME)

def load_data(force_reload=False):
    """Load data from Firebase or cache"""
    init_firebase()
    
    if force_reload or _cache['participants_df'] is None:
        participants_df, trials_df = _cache['connector'].fetch_all_data()
        _cache['participants_df'] = participants_df
        _cache['trials_df'] = trials_df
        _cache['last_loaded'] = datetime.now()
    
    return _cache['participants_df'], _cache['trials_df']

def calculate_advanced_metrics(trials_df: pd.DataFrame) -> pd.DataFrame:
    """Calculate advanced metrics for all trials"""
    df = trials_df.copy()
    
    for idx, trial in df.iterrows():
        path = trial.get('movementPath', [])
        
        if not isinstance(path, list) or len(path) < 3:
            continue
        
        # Calculate velocities
        velocities = []
        for i in range(1, len(path)):
            if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                dt = (path[i]['t'] - path[i-1]['t']) / 1000.0
                if dt > 0:
                    dx = path[i]['x'] - path[i-1]['x']
                    dy = path[i]['y'] - path[i-1]['y']
                    dist = np.sqrt(dx**2 + dy**2)
                    velocities.append(dist / dt)
        
        if len(velocities) > 0:
            df.at[idx, 'averageSpeed'] = np.mean(velocities)
            df.at[idx, 'speedVariance'] = np.var(velocities)
            
            # Count peaks
            if len(velocities) > 2:
                from scipy.signal import find_peaks
                peaks, _ = find_peaks(velocities, prominence=50)
                df.at[idx, 'velocityPeaks'] = len(peaks)
            
            # Calculate jerk
            if len(velocities) > 3:
                accelerations = np.diff(velocities)
                if len(accelerations) > 1:
                    jerks = np.diff(accelerations)
                    df.at[idx, 'jerk'] = np.mean(np.abs(jerks))
    
    # Add condition mean RT
    df['trialType_mean_RT'] = df.groupby('trialType')['reactionTime'].transform('mean')
    
    return df

def fig_to_base64(fig):
    """Convert matplotlib figure to base64 string"""
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
    """Get current system status"""
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
    """Force reload data from Firebase"""
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
    """Export raw data from Firebase (before any processing)"""
    try:
        data_type = request.args.get('type', 'both')  # 'participants', 'trials', or 'both'
        participants_df, trials_df = load_data()
        
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
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
            # Export only trials as Excel
            trials_export = trials_df.copy()
            if 'movementPath' in trials_export.columns:
                trials_export['movementPath'] = trials_export['movementPath'].apply(
                    lambda x: json.dumps(x) if isinstance(x, list) else x
                )
            
            output = io.BytesIO()
            trials_export.to_excel(output, engine='openpyxl', index=False)
            output.seek(0)
            
            return send_file(
                output,
                mimetype='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                as_attachment=True,
                download_name=f'raw_trials_{timestamp}.xlsx'
            )
        
        else:  # 'both' - default legacy behavior
            # Prepare trials for export (convert movementPath to JSON string)
            trials_export = trials_df.copy()
            if 'movementPath' in trials_export.columns:
                trials_export['movementPath'] = trials_export['movementPath'].apply(
                    lambda x: json.dumps(x) if isinstance(x, list) else x
                )
            
            # Create Excel with both sheets
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
    """Export processed data with all calculated metrics"""
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
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
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
    """Run complete analysis and return plot data"""
    try:
        participants_df, trials_df = load_data()
        
        # Create analyzer
        analyzer = SubliminalPrimingAnalyzer(trials_df, participants_df)
        
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
            'output_dir': analyzer.output_dir  # Frontend can use this to construct download URL
        })
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500

@app.route('/api/analysis/download', methods=['POST'])
def download_analysis_package():
    """Download all analysis results as a zip file"""
    try:
        data = request.json
        # The analyzer provides this path during the /api/analysis/full call
        output_dir = data.get('output_dir') 
        
        if not output_dir:
            return jsonify({'status': 'error', 'message': 'No directory provided'}), 400

        # Convert to absolute path so Python knows exactly where to look
        abs_output_dir = os.path.abspath(output_dir)
        
        if not os.path.exists(abs_output_dir):
            return jsonify({'status': 'error', 'message': f'Path not found: {abs_output_dir}'}), 404
        
        # Create zip file
        import zipfile
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        temp_dir = tempfile.gettempdir()
        zip_filename = f'analysis_results_{timestamp}.zip'
        zip_path = os.path.join(temp_dir, zip_filename)

        # Build the ZIP
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zipf:
            file_count = 0
            for root, dirs, files in os.walk(abs_output_dir):
                for file in files:
                    file_path = os.path.join(root, file)
                    # This ensures the ZIP doesn't contain your whole C:\Users\ path
                    arcname = os.path.relpath(file_path, abs_output_dir)
                    zipf.write(file_path, arcname)
                    file_count += 1
        
        if file_count == 0:
            return jsonify({'status': 'error', 'message': 'Target directory was empty'}), 400
        
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
    """Create custom velocity plots"""
    try:
        data = request.json
        time_cap = data.get('time_cap', 10000)
        velocity_cap = data.get('velocity_cap', 5000)
        split_by = data.get('split_by', None)
        include_matrix = data.get('include_matrix', False)
        
        participants_df, trials_df = load_data()
        
        # Create a unique output directory for this request
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_dir = f'python-analysis/analysis_outputs/velocity_{timestamp}'
        os.makedirs(output_dir, exist_ok=True)
        
        plotter = VelocityPlotter(trials_df, output_dir=output_dir)
        
        # Generate unified plot
        plotter.plot_all_velocities(
            time_cap_ms=time_cap,
            velocity_cap_px_s=velocity_cap,
            split_by_col=split_by
        )
        
        # Generate matrix if requested
        if include_matrix:
            plotter.create_velocity_comparison_matrix(
                time_cap_ms=time_cap,
                velocity_cap=velocity_cap
            )
        
        # Find generated files and encode
        plots = {}
        for file in os.listdir(output_dir):
            if file.endswith('.png'):
                with open(os.path.join(output_dir, file), 'rb') as f:
                    plots[file.replace('.png', '')] = base64.b64encode(f.read()).decode('utf-8')
        
        return jsonify({
            'status': 'success',
            'plots': plots
        })
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500

@app.route('/api/stats/hypothesis', methods=['GET'])
def get_hypothesis_stats():
    """Get main hypothesis test statistics"""
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
        # Group by participant and condition
        grouped = trials_df.groupby(['participantId', 'trialType'])['reactionTime'].mean().reset_index()
        
        # Pivot for repeated measures
        pivot = grouped.pivot(index='participantId', columns='trialType', values='reactionTime')
        
        # Remove participants with missing conditions
        pivot_clean = pivot.dropna()
        
        if len(pivot_clean) > 0:
            # Perform repeated measures ANOVA
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
    """Test Firebase connection"""
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

@app.route('/api/clean/duplicates', methods=['POST'])
def clean_duplicate_trials():
    """Clean duplicate trials from Firebase"""
    try:
        from firebase_cleaner import FirebaseCleaner
        
        data = request.json
        dry_run = data.get('dry_run', True)  # Default to dry run for safety
        
        cleaner = FirebaseCleaner(DEFAULT_CREDENTIALS_FILENAME)
        
        # Capture output
        import io
        import sys
        
        old_stdout = sys.stdout
        sys.stdout = captured_output = io.StringIO()
        
        cleaner.remove_duplicate_trials(dry_run=dry_run)
        
        # Get output
        output = captured_output.getvalue()
        sys.stdout = old_stdout
        
        # Parse output for duplicate count (simple approach)
        import re
        match = re.search(r'(\d+) duplicates?', output)
        duplicate_count = int(match.group(1)) if match else 0
        
        return jsonify({
            'status': 'success',
            'dry_run': dry_run,
            'duplicates_found': duplicate_count,
            'message': 'Dry run complete' if dry_run else 'Duplicates removed',
            'details': output
        })
        
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500

# ============================================================================
# MAIN
# ============================================================================

if __name__ == '__main__':
    print("="*70)
    print("BEHAVIORAL ANALYSIS TOOLKIT - Backend Server")
    print("="*70)
    print("\nStarting Flask server...")
    print("Backend will be available at: http://localhost:5000")
    print("Frontend should connect to this address")
    print("\nPress Ctrl+C to stop the server\n")
    
    app.run(debug=True, host='0.0.0.0', port=5000)