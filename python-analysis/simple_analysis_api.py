"""
Simple Analysis API for Kotlin App
Provides clean JSON responses for specific analyses
Run with: python simple_analysis_api.py
"""

from flask import Flask, jsonify, request, send_file
from flask_cors import CORS
from firebase_connector import load_data
import pandas as pd
import numpy as np
from scipy import stats
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns
import io
import base64
from datetime import datetime

app = Flask(__name__)
CORS(app)  # Allow Kotlin app to call this

# Cache data to avoid repeated Firebase calls
cached_data = {'timestamp': None, 'participants': None, 'trials': None}
CACHE_DURATION = 300  # 5 minutes

def get_data():
    """Get data with caching"""
    now = datetime.now().timestamp()
    
    if (cached_data['timestamp'] is None or 
        now - cached_data['timestamp'] > CACHE_DURATION or
        cached_data['trials'] is None):
        
        print("Fetching fresh data from Firebase...")
        p_df, t_df, _ = load_data()
        
        # Clean outliers
        t_df = t_df[(t_df['reactionTime'].notna()) & (t_df['reactionTime'] < 50000)]
        
        cached_data['participants'] = p_df
        cached_data['trials'] = t_df
        cached_data['timestamp'] = now
    
    return cached_data['participants'], cached_data['trials']


@app.route('/api/status', methods=['GET'])
def status():
    """Check if API is running"""
    return jsonify({
        'status': 'online',
        'timestamp': datetime.now().isoformat()
    })


@app.route('/api/summary', methods=['GET'])
def summary():
    """Get quick data summary"""
    try:
        participants_df, trials_df = get_data()
        
        return jsonify({
            'participants': {
                'total': len(participants_df),
                'byGender': participants_df['gender'].value_counts().to_dict(),
                'avgAge': float(participants_df['age'].mean()),
                'withGlasses': int(participants_df['hasGlasses'].sum()),
                'withAttentionDeficit': int(participants_df['hasAttentionDeficit'].sum())
            },
            'trials': {
                'total': len(trials_df),
                'byType': trials_df['trialType'].value_counts().to_dict(),
                'avgReactionTime': float(trials_df['reactionTime'].mean()),
                'avgMovementTime': float(trials_df['movementTime'].mean()),
                'avgPathLength': float(trials_df['pathLength'].mean())
            }
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/trial_comparison', methods=['GET'])
def trial_comparison():
    """Compare the three trial types statistically"""
    try:
        _, trials_df = get_data()
        
        # Get data for each type
        pre_supra = trials_df[trials_df['trialType'] == 'PRE_SUPRA']['reactionTime'].dropna()
        pre_jnd = trials_df[trials_df['trialType'] == 'PRE_JND']['reactionTime'].dropna()
        concurrent = trials_df[trials_df['trialType'] == 'CONCURRENT_SUPRA']['reactionTime'].dropna()
        
        # ANOVA
        f_stat, p_value = stats.f_oneway(pre_supra, pre_jnd, concurrent)
        
        # Pairwise t-tests
        t1, p1 = stats.ttest_ind(pre_supra, pre_jnd)
        t2, p2 = stats.ttest_ind(pre_jnd, concurrent)
        t3, p3 = stats.ttest_ind(pre_supra, concurrent)
        
        return jsonify({
            'anova': {
                'f_statistic': float(f_stat),
                'p_value': float(p_value),
                'significant': bool(p_value < 0.05)
            },
            'means': {
                'PRE_SUPRA': float(pre_supra.mean()),
                'PRE_JND': float(pre_jnd.mean()),
                'CONCURRENT_SUPRA': float(concurrent.mean())
            },
            'pairwise': {
                'PRE_SUPRA_vs_PRE_JND': {'t': float(t1), 'p': float(p1), 'sig': bool(p1 < 0.05)},
                'PRE_JND_vs_CONCURRENT': {'t': float(t2), 'p': float(p2), 'sig': bool(p2 < 0.05)},
                'PRE_SUPRA_vs_CONCURRENT': {'t': float(t3), 'p': float(p3), 'sig': bool(p3 < 0.05)}
            }
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/participant/<participant_id>', methods=['GET'])
def participant_stats(participant_id):
    """Get stats for specific participant"""
    try:
        participants_df, trials_df = get_data()
        
        p_data = participants_df[participants_df['participantId'] == participant_id]
        if p_data.empty:
            return jsonify({'error': 'Participant not found'}), 404
        
        p_trials = trials_df[trials_df['participantId'] == participant_id]
        
        return jsonify({
            'demographics': p_data.iloc[0].to_dict(),
            'trials': {
                'total': len(p_trials),
                'byType': p_trials['trialType'].value_counts().to_dict(),
                'avgReactionTime': float(p_trials['reactionTime'].mean()),
                'avgMovementTime': float(p_trials['movementTime'].mean()),
                'avgPathLength': float(p_trials['pathLength'].mean())
            }
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/plot/<plot_type>', methods=['GET'])
def generate_plot(plot_type):
    """Generate and return plot as base64 image"""
    try:
        _, trials_df = get_data()
        
        fig, ax = plt.subplots(figsize=(10, 6))
        
        if plot_type == 'reaction_time':
            # Box plot of reaction times
            data = [
                trials_df[trials_df['trialType'] == 'PRE_SUPRA']['reactionTime'].dropna(),
                trials_df[trials_df['trialType'] == 'PRE_JND']['reactionTime'].dropna(),
                trials_df[trials_df['trialType'] == 'CONCURRENT_SUPRA']['reactionTime'].dropna()
            ]
            ax.boxplot(data, labels=['PRE_SUPRA', 'PRE_JND', 'CONCURRENT'])
            ax.set_ylabel('Reaction Time (ms)')
            ax.set_title('Reaction Time by Trial Type')
            
        elif plot_type == 'path_efficiency':
            # Path efficiency comparison
            trials_df['efficiency'] = 800 / trials_df['pathLength']
            sns.violinplot(data=trials_df, x='trialType', y='efficiency', ax=ax)
            ax.set_ylabel('Path Efficiency')
            ax.set_title('Path Efficiency by Trial Type')
            
        else:
            return jsonify({'error': 'Unknown plot type'}), 400
        
        # Convert plot to base64 string
        buf = io.BytesIO()
        plt.tight_layout()
        plt.savefig(buf, format='png', dpi=150, bbox_inches='tight')
        buf.seek(0)
        img_base64 = base64.b64encode(buf.read()).decode('utf-8')
        plt.close()
        
        return jsonify({'image': f'data:image/png;base64,{img_base64}'})
        
    except Exception as e:
        plt.close()
        return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    print("\n" + "="*60)
    print("STARTING ANALYSIS API")
    print("="*60)
    print("\nAPI will be available at: http://localhost:5000")
    print("\nEndpoints:")
    print("  GET  /api/status              - Check if API is running")
    print("  GET  /api/summary             - Get data summary")
    print("  GET  /api/trial_comparison    - Statistical comparison")
    print("  GET  /api/participant/<id>    - Get participant stats")
    print("  GET  /api/plot/<type>         - Generate plot (base64)")
    print("\nPress Ctrl+C to stop")
    print("="*60 + "\n")
    
    app.run(host='0.0.0.0', port=5000, debug=True)