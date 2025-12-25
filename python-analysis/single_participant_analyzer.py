"""
Single Participant Analysis Module
Provides detailed analysis for individual participants including:
- Paired t-tests between conditions
- Comprehensive metrics (jerk, speed variance, velocity peaks)
- Individual visualizations
- Detailed data export
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from typing import Dict, List, Tuple
import os
from datetime import datetime


class SingleParticipantAnalyzer:
    """Analyzes individual participant performance in detail"""
    
    def __init__(self, participant_id: str, trials_df: pd.DataFrame, 
                 participant_info: pd.Series, output_dir: str = None):
        """
        Initialize analyzer for a single participant
        
        Args:
            participant_id: The participant's ID
            trials_df: DataFrame with all trials for this participant
            participant_info: Series with participant demographics
            output_dir: Where to save outputs (auto-created if None)
        """
        self.participant_id = participant_id
        self.trials_df = trials_df.copy()
        self.participant_info = participant_info
        
        # Create output directory
        if output_dir is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            output_dir = f'data_exports/participant_reports/{participant_id}_{timestamp}'
        
        self.output_dir = output_dir
        self.figures_dir = os.path.join(output_dir, 'figures')
        os.makedirs(self.figures_dir, exist_ok=True)
        
        self.report_lines = []
        self._calculate_advanced_metrics()
    
    def _log(self, text: str):
        """Add line to report and print"""
        self.report_lines.append(text)
        print(text)
    
    def _section(self, title: str):
        """Add section header to report"""
        sep = "=" * 70
        self.report_lines.extend(["\n" + sep, title, sep + "\n"])
    
    def _calculate_advanced_metrics(self):
        """Calculate advanced movement metrics for each trial"""
        
        for idx, trial in self.trials_df.iterrows():
            path = trial.get('movementPath', [])
            
            if not isinstance(path, list) or len(path) < 3:
                continue
            
            # Calculate velocity profile
            velocities = self._calculate_velocities(path)
            
            # Speed variance
            self.trials_df.at[idx, 'speedVariance'] = np.var(velocities) if len(velocities) > 0 else 0
            
            # Number of velocity peaks (local maxima)
            self.trials_df.at[idx, 'velocityPeaks'] = self._count_peaks(velocities)
            
            # Jerk (rate of change of acceleration)
            self.trials_df.at[idx, 'jerk'] = self._calculate_jerk(path)
            
            # Average speed
            self.trials_df.at[idx, 'averageSpeed'] = np.mean(velocities) if len(velocities) > 0 else 0
    
    def _calculate_velocities(self, path: List[Dict]) -> np.ndarray:
        """Calculate velocity at each point in the path"""
        velocities = []
        
        for i in range(1, len(path)):
            if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                dt = (path[i]['t'] - path[i-1]['t']) / 1000.0  # Convert to seconds
                
                if dt > 0:
                    dx = path[i]['x'] - path[i-1]['x']
                    dy = path[i]['y'] - path[i-1]['y']
                    dist = np.sqrt(dx**2 + dy**2)
                    velocities.append(dist / dt)
        
        return np.array(velocities)
    
    def _count_peaks(self, velocities: np.ndarray, prominence: float = 50) -> int:
        """Count number of peaks in velocity profile"""
        if len(velocities) < 3:
            return 0
        
        from scipy.signal import find_peaks
        peaks, _ = find_peaks(velocities, prominence=prominence)
        return len(peaks)
    
    def _calculate_jerk(self, path: List[Dict]) -> float:
        """Calculate jerk (third derivative of position)"""
        if len(path) < 4:
            return 0
        
        # Calculate acceleration
        accelerations = []
        for i in range(2, len(path)):
            if all(isinstance(path[j], dict) for j in range(i-1, i+1)):
                dt1 = (path[i-1]['t'] - path[i-2]['t']) / 1000.0
                dt2 = (path[i]['t'] - path[i-1]['t']) / 1000.0
                
                if dt1 > 0 and dt2 > 0:
                    v1 = np.sqrt((path[i-1]['x'] - path[i-2]['x'])**2 + 
                                (path[i-1]['y'] - path[i-2]['y'])**2) / dt1
                    v2 = np.sqrt((path[i]['x'] - path[i-1]['x'])**2 + 
                                (path[i]['y'] - path[i-1]['y'])**2) / dt2
                    
                    acceleration = (v2 - v1) / dt2
                    accelerations.append(acceleration)
        
        # Jerk is rate of change of acceleration
        if len(accelerations) < 2:
            return 0
        
        jerks = np.diff(accelerations)
        return np.mean(np.abs(jerks))
    
    def run_paired_tests(self) -> Dict[str, Dict]:
        """
        Run paired t-tests comparing conditions for this participant
        
        Returns:
            Dictionary with test results for each comparison
        """
        self._section("PAIRED T-TEST ANALYSIS")
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        comparisons = [
            ('PRE_SUPRA', 'PRE_JND'),
            ('PRE_SUPRA', 'CONCURRENT_SUPRA'),
            ('PRE_JND', 'CONCURRENT_SUPRA')
        ]
        
        results = {}
        
        for cond1, cond2 in comparisons:
            self._log(f"\n{cond1} vs {cond2}:")
            
            data1 = self.trials_df[self.trials_df['trialType'] == cond1]['reactionTime'].dropna()
            data2 = self.trials_df[self.trials_df['trialType'] == cond2]['reactionTime'].dropna()
            
            if len(data1) < 2 or len(data2) < 2:
                self._log("  ⚠️ Insufficient data for comparison")
                continue
            
            # Paired t-test (assumes same number of trials per condition)
            # If unequal, we'll use independent t-test
            if len(data1) == len(data2):
                t_stat, p_val = stats.ttest_rel(data1, data2)
                test_type = "Paired t-test"
            else:
                t_stat, p_val = stats.ttest_ind(data1, data2)
                test_type = "Independent t-test"
            
            mean_diff = data1.mean() - data2.mean()
            
            results[f"{cond1}_vs_{cond2}"] = {
                'test_type': test_type,
                't_statistic': t_stat,
                'p_value': p_val,
                'mean_difference': mean_diff,
                'significant': p_val < 0.05,
                'mean1': data1.mean(),
                'mean2': data2.mean()
            }
            
            self._log(f"  Test: {test_type}")
            self._log(f"  t-statistic: {t_stat:.3f}")
            self._log(f"  p-value: {p_val:.4f}")
            self._log(f"  Mean {cond1}: {data1.mean():.1f} ms")
            self._log(f"  Mean {cond2}: {data2.mean():.1f} ms")
            self._log(f"  Difference: {mean_diff:.1f} ms")
            self._log(f"  Result: {'Significant' if p_val < 0.05 else 'Not significant'} at α=0.05")
        
        return results
    
    def export_detailed_data(self) -> str:
        """
        Export comprehensive trial data to CSV
        
        Returns:
            Path to exported file
        """
        self._section("DATA EXPORT")
        
        # Select columns for export
        export_cols = [
            'trialNumber', 'trialType', 'targetIndex',
            'reactionTime', 'movementTime', 'totalResponseTime',
            'pathLength', 'averageSpeed', 'speedVariance',
            'velocityPeaks', 'jerk'
        ]
        
        # Only include columns that exist
        available_cols = [col for col in export_cols if col in self.trials_df.columns]
        
        export_df = self.trials_df[available_cols].copy()
        
        # Add condition-specific statistics
        export_df['trialType_mean_RT'] = export_df.groupby('trialType')['reactionTime'].transform('mean')
        
        # Sort by trial number
        export_df = export_df.sort_values('trialNumber')
        
        # Export
        filepath = os.path.join(self.output_dir, f'{self.participant_id}_detailed_data.csv')
        export_df.to_csv(filepath, index=False)
        
        self._log(f"✅ Detailed data exported to: {filepath}")
        self._log(f"   Columns: {len(available_cols)}")
        self._log(f"   Rows: {len(export_df)}")
        
        return filepath
    
    def create_individual_plots(self):
        """Create comprehensive visualizations for this participant"""
        self._section("GENERATING VISUALIZATIONS")
        
        # 1. Performance by condition
        self._plot_performance_by_condition()
        
        # 2. Velocity profiles
        self._plot_velocity_profiles()
        
        # 3. Movement paths
        self._plot_movement_paths()
        
        # 4. Advanced metrics comparison
        self._plot_advanced_metrics()
        
        self._log(f"✅ All plots saved to: {self.figures_dir}")
    
    def _plot_performance_by_condition(self):
        """Plot reaction time and movement metrics by condition"""
        fig, axes = plt.subplots(2, 2, figsize=(14, 10))
        fig.suptitle(f'Performance by Condition - Participant {self.participant_id}', 
                     fontsize=14, fontweight='bold')
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        
        metrics = [
            ('reactionTime', 'Reaction Time', 'Time from GO beep to first movement (ms)'),
            ('movementTime', 'Movement Time', 'Time from first move to target (ms)'),
            ('pathLength', 'Path Length', 'Total distance traveled (pixels)'),
            ('averageSpeed', 'Average Speed', 'Mean movement speed (px/s)')
        ]
        
        for idx, (metric, short_label, full_label) in enumerate(metrics):
            ax = axes[idx // 2, idx % 2]
            
            data = [self.trials_df[self.trials_df['trialType'] == cond][metric].dropna() 
                    for cond in conditions]
            means = [d.mean() if len(d) > 0 else 0 for d in data]
            stds = [d.std() if len(d) > 0 else 0 for d in data]
            
            x_pos = np.arange(len(conditions))
            bars = ax.bar(x_pos, means, yerr=stds, capsize=5, color=colors, alpha=0.7)
            
            # Add individual points
            for i, cond in enumerate(conditions):
                points = self.trials_df[self.trials_df['trialType'] == cond][metric].dropna()
                ax.scatter([i] * len(points), points, color='black', alpha=0.4, s=30)
            
            ax.set_xticks(x_pos)
            ax.set_xticklabels(['PRE\nSUPRA', 'PRE\nJND', 'CONC\nSUPRA'])
            ax.set_ylabel(full_label, fontweight='bold')
            ax.set_title(short_label, fontweight='bold')
            ax.set_xlabel('Trial Condition', fontweight='bold')
            ax.grid(axis='y', alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.figures_dir, 'performance_by_condition.png'), dpi=300)
        plt.close()
        
        self._log("  → performance_by_condition.png")
    
    def _plot_velocity_profiles(self):
        """Plot velocity profiles for all trials"""
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        fig, axes = plt.subplots(1, 3, figsize=(18, 5))
        fig.suptitle(f'Velocity Profiles - Participant {self.participant_id}', 
                     fontsize=14, fontweight='bold')
        
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        
        for idx, cond in enumerate(conditions):
            ax = axes[idx]
            cond_trials = self.trials_df[self.trials_df['trialType'] == cond]
            
            for _, trial in cond_trials.iterrows():
                path = trial.get('movementPath', [])
                if isinstance(path, list) and len(path) > 2:
                    velocities = self._calculate_velocities(path)
                    times = [path[i]['t'] - path[0]['t'] for i in range(1, len(path)) 
                            if isinstance(path[i], dict)][:len(velocities)]
                    
                    if len(times) == len(velocities):
                        ax.plot(times, velocities, color=colors[idx], alpha=0.5, linewidth=1)
            
            # Add mean line
            ax.axvline(cond_trials['reactionTime'].mean(), color='black', 
                      linestyle='--', label=f'Mean RT: {cond_trials["reactionTime"].mean():.0f}ms')
            
            ax.set_title(cond.replace('_', ' '))
            ax.set_xlabel('Time (ms)')
            if idx == 0:
                ax.set_ylabel('Velocity (px/s)')
            ax.legend()
            ax.grid(alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.figures_dir, 'velocity_profiles.png'), dpi=300)
        plt.close()
        
        self._log("  → velocity_profiles.png")
    
    def _plot_movement_paths(self):
        """Plot movement paths for each condition"""
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        fig, axes = plt.subplots(1, 3, figsize=(18, 6))
        fig.suptitle(f'Movement Paths - Participant {self.participant_id}', 
                     fontsize=14, fontweight='bold')
        
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        
        for idx, cond in enumerate(conditions):
            ax = axes[idx]
            cond_trials = self.trials_df[self.trials_df['trialType'] == cond]
            
            for _, trial in cond_trials.iterrows():
                path = trial.get('movementPath', [])
                if isinstance(path, list) and len(path) > 2:
                    x_coords = [p['x'] for p in path if isinstance(p, dict)]
                    y_coords = [p['y'] for p in path if isinstance(p, dict)]
                    
                    if len(x_coords) > 0:
                        ax.plot(x_coords, y_coords, color=colors[idx], alpha=0.5, linewidth=1.5)
            
            ax.set_title(cond.replace('_', ' '))
            ax.set_xlabel('X Position')
            if idx == 0:
                ax.set_ylabel('Y Position')
            ax.set_aspect('equal')
            ax.grid(alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.figures_dir, 'movement_paths.png'), dpi=300)
        plt.close()
        
        self._log("  → movement_paths.png")
    
    def _plot_advanced_metrics(self):
        """Plot advanced metrics (jerk, variance, peaks)"""
        fig, axes = plt.subplots(1, 3, figsize=(15, 5))
        fig.suptitle(f'Advanced Movement Metrics - Participant {self.participant_id}', 
                     fontsize=14, fontweight='bold')
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        metrics = [
            ('speedVariance', 'Speed Variance', 'Variance in movement speed\n(lower = smoother)'),
            ('velocityPeaks', 'Velocity Peaks', 'Number of local maxima\n(fewer = better planning)'),
            ('jerk', 'Jerk', 'Movement smoothness\n(lower = smoother)')
        ]
        
        for idx, (metric, short_label, full_label) in enumerate(metrics):
            ax = axes[idx]
            
            data = [self.trials_df[self.trials_df['trialType'] == cond][metric].dropna() 
                    for cond in conditions]
            
            bp = ax.boxplot(data, labels=['PRE\nSUPRA', 'PRE\nJND', 'CONC\nSUPRA'], 
                           patch_artist=True)
            
            for patch in bp['boxes']:
                patch.set_facecolor('#7FB3D5')
                patch.set_alpha(0.7)
            
            ax.set_ylabel(full_label, fontsize=10, fontweight='bold')
            ax.set_xlabel('Trial Condition', fontweight='bold')
            ax.set_title(short_label, fontweight='bold')
            ax.grid(axis='y', alpha=0.3)
        
        plt.tight_layout()
        plt.savefig(os.path.join(self.figures_dir, 'advanced_metrics.png'), dpi=300)
        plt.close()
        
        self._log("  → advanced_metrics.png")
    
    def generate_report(self):
        """Generate comprehensive text report"""
        self._section(f"PARTICIPANT ANALYSIS REPORT: {self.participant_id}")
        
        # Demographics
        self._log("DEMOGRAPHICS:")
        self._log(f"  Age: {self.participant_info.get('age', 'N/A')}")
        self._log(f"  Gender: {self.participant_info.get('gender', 'N/A')}")
        self._log(f"  Glasses: {self.participant_info.get('hasGlasses', 'N/A')}")
        self._log(f"  ADHD: {self.participant_info.get('hasAttentionDeficit', 'N/A')}")
        self._log(f"  JND Threshold: {self.participant_info.get('jndThreshold', 'N/A')}")
        
        # Performance summary
        self._section("PERFORMANCE SUMMARY")
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        for cond in conditions:
            cond_data = self.trials_df[self.trials_df['trialType'] == cond]
            
            self._log(f"\n{cond}:")
            self._log(f"  Trials: {len(cond_data)}")
            self._log(f"  Mean RT: {cond_data['reactionTime'].mean():.1f} ms (SD: {cond_data['reactionTime'].std():.1f})")
            self._log(f"  Mean Movement Time: {cond_data['movementTime'].mean():.1f} ms")
            self._log(f"  Mean Path Length: {cond_data['pathLength'].mean():.1f} px")
            self._log(f"  Mean Speed: {cond_data['averageSpeed'].mean():.1f} px/s")
            self._log(f"  Mean Speed Variance: {cond_data['speedVariance'].mean():.1f}")
            self._log(f"  Mean Velocity Peaks: {cond_data['velocityPeaks'].mean():.2f}")
            self._log(f"  Mean Jerk: {cond_data['jerk'].mean():.2f}")
        
        # Run statistical tests
        self.run_paired_tests()
        
        # Save report
        report_path = os.path.join(self.output_dir, f'{self.participant_id}_analysis_report.txt')
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.report_lines))
        
        self._log(f"\n✅ Report saved to: {report_path}")
        
        return report_path
    
    def run_full_analysis(self):
        """Run complete analysis pipeline"""
        print(f"\n{'='*70}")
        print(f"ANALYZING PARTICIPANT: {self.participant_id}")
        print(f"{'='*70}\n")
        
        # Generate report
        self.generate_report()
        
        # Create plots
        self.create_individual_plots()
        
        # Export data
        self.export_detailed_data()
        
        print(f"\n{'='*70}")
        print(f"✅ ANALYSIS COMPLETE")
        print(f"{'='*70}")
        print(f"📁 All files saved to: {self.output_dir}")
        print(f"   ├── {self.participant_id}_analysis_report.txt")
        print(f"   ├── {self.participant_id}_detailed_data.csv")
        print(f"   └── figures/")
        print(f"       ├── performance_by_condition.png")
        print(f"       ├── velocity_profiles.png")
        print(f"       ├── movement_paths.png")
        print(f"       └── advanced_metrics.png")