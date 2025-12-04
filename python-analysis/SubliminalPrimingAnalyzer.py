"""
Enhanced Subliminal Priming Analysis with Timestamped Outputs
Analyzes motor planning with demographic breakdowns and target-specific analysis
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from FirebaseConnector import load_data
import warnings
import os
from datetime import datetime
warnings.filterwarnings('ignore')

class SubliminalPrimingAnalyzer:
    """Analyze subliminal priming effects on motor planning"""
    
    def __init__(self, trials_df, participants_df, outlier_threshold_ms=50000):
        """
        Initialize with trial and participant data
        
        Args:
            trials_df: DataFrame with trial data (already merged with demographics)
            participants_df: DataFrame with participant demographics
            outlier_threshold_ms: Remove reaction times above this (default 50 seconds)
        """
        self.raw_trials = trials_df.copy()
        self.participants_df = participants_df.copy()
        
        # Create timestamped output directory
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.output_dir = f'analysis_outputs/run_{timestamp}'
        self.figures_dir = os.path.join(self.output_dir, 'figures')
        os.makedirs(self.figures_dir, exist_ok=True)
        
        # Store report content
        self.report_lines = []
        
        # Clean data
        self._add_report_section("DATA CLEANING", level=1)
        self._add_report_line(f"Original trials: {len(self.raw_trials)}")
        
        # Remove outliers - KEEP ALL COLUMNS including demographics
        self.trials_df = trials_df[
            (trials_df['reactionTime'].notna()) & 
            (trials_df['reactionTime'] < outlier_threshold_ms)
        ].copy()
        
        removed = len(self.raw_trials) - len(self.trials_df)
        self._add_report_line(f"Removed {removed} outlier trials (reaction time > {outlier_threshold_ms}ms)")
        self._add_report_line(f"Clean trials: {len(self.trials_df)}")
        
        # Check demographic data (already merged by Firebase connector)
        self._check_demographics()
        
        # Calculate additional metrics
        self._calculate_metrics()
    
    def _add_report_section(self, title, level=1):
        """Add a section header to the report"""
        if level == 1:
            self.report_lines.append("\n" + "="*80)
            self.report_lines.append(title)
            self.report_lines.append("="*80 + "\n")
        else:
            self.report_lines.append("\n" + "-"*80)
            self.report_lines.append(title)
            self.report_lines.append("-"*80)
    
    def _add_report_line(self, text):
        """Add a line to the report"""
        self.report_lines.append(text)
        print(text)
    
    def _check_demographics(self):
        """Check that demographic data is present (already merged by Firebase connector)"""
        self._add_report_line(f"\nDemographic breakdown:")
        
        demographic_info = [
            ('hasAttentionDeficit', 'Attention Deficit'),
            ('hasGlasses', 'Glasses'),
            ('gender', 'Gender')
        ]
        
        for col, label in demographic_info:
            if col in self.trials_df.columns:
                counts = self.trials_df[col].value_counts().to_dict()
                null_count = self.trials_df[col].isna().sum()
                self._add_report_line(f"  {label}: {counts}")
                if null_count > 0:
                    self._add_report_line(f"    (Note: {null_count} trials with missing {label} data)")
            else:
                self._add_report_line(f"  {label}: Column not found in data")
                # Add empty column to prevent errors later
                self.trials_df[col] = None
    
    def _calculate_metrics(self):
        """Calculate additional motor planning metrics"""
        self.trials_df['pathEfficiency'] = 800 / self.trials_df['pathLength']
        self.trials_df['planningTime'] = self.trials_df['reactionTime']
        self.trials_df['executionTime'] = self.trials_df['movementTime']
    
    def test_main_hypothesis(self, data_subset=None, subset_name="All Data"):
        """Test main hypothesis for a given data subset"""
        if data_subset is None:
            data_subset = self.trials_df
        
        self._add_report_section(f"MAIN HYPOTHESIS TESTING - {subset_name}", level=1)
        
        # Get data for each condition
        pre_supra = data_subset[data_subset['trialType'] == 'PRE_SUPRA']
        pre_jnd = data_subset[data_subset['trialType'] == 'PRE_JND']
        concurrent = data_subset[data_subset['trialType'] == 'CONCURRENT_SUPRA']
        
        self._add_report_line(f"\nSample sizes:")
        self._add_report_line(f"  PRE_SUPRA: {len(pre_supra)} trials")
        self._add_report_line(f"  PRE_JND: {len(pre_jnd)} trials")
        self._add_report_line(f"  CONCURRENT_SUPRA: {len(concurrent)} trials")
        
        # === REACTION TIME ===
        self._add_report_section("1. REACTION TIME (GO beep → first movement)", level=2)
        self._add_report_line("Expected: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA")
        
        rt_supra = pre_supra['reactionTime'].dropna()
        rt_jnd = pre_jnd['reactionTime'].dropna()
        rt_concurrent = concurrent['reactionTime'].dropna()
        
        self._add_report_line(f"\nMeans:")
        self._add_report_line(f"  PRE_SUPRA:        {rt_supra.mean():.1f} ms (SD={rt_supra.std():.1f})")
        self._add_report_line(f"  PRE_JND:          {rt_jnd.mean():.1f} ms (SD={rt_jnd.std():.1f})")
        self._add_report_line(f"  CONCURRENT_SUPRA: {rt_concurrent.mean():.1f} ms (SD={rt_concurrent.std():.1f})")
        
        # ANOVA
        if len(rt_supra) > 0 and len(rt_jnd) > 0 and len(rt_concurrent) > 0:
            f_stat, p_value = stats.f_oneway(rt_supra, rt_jnd, rt_concurrent)
            self._add_report_line(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
            if p_value < 0.05:
                self._add_report_line("  ✓ SIGNIFICANT difference between conditions!")
            else:
                self._add_report_line("  ✗ No significant difference")
            
            # Pairwise comparisons
            self._add_report_line(f"\nPairwise t-tests:")
            if len(rt_supra) > 0 and len(rt_jnd) > 0:
                t1, p1 = stats.ttest_ind(rt_supra, rt_jnd)
                self._add_report_line(f"  PRE_SUPRA vs PRE_JND: t={t1:.3f}, p={p1:.4f}")
            
            if len(rt_jnd) > 0 and len(rt_concurrent) > 0:
                t2, p2 = stats.ttest_ind(rt_jnd, rt_concurrent)
                self._add_report_line(f"  PRE_JND vs CONCURRENT: t={t2:.3f}, p={p2:.4f}")
            
            if len(rt_supra) > 0 and len(rt_concurrent) > 0:
                t3, p3 = stats.ttest_ind(rt_supra, rt_concurrent)
                self._add_report_line(f"  PRE_SUPRA vs CONCURRENT: t={t3:.3f}, p={p3:.4f}")
        
        # === PATH EFFICIENCY ===
        self._add_report_section("2. PATH EFFICIENCY (directness of movement)", level=2)
        self._add_report_line("Expected: PRE_SUPRA > PRE_JND > CONCURRENT_SUPRA")
        
        eff_supra = pre_supra['pathEfficiency'].dropna()
        eff_jnd = pre_jnd['pathEfficiency'].dropna()
        eff_concurrent = concurrent['pathEfficiency'].dropna()
        
        self._add_report_line(f"\nMeans (higher = more efficient):")
        self._add_report_line(f"  PRE_SUPRA:        {eff_supra.mean():.3f} (SD={eff_supra.std():.3f})")
        self._add_report_line(f"  PRE_JND:          {eff_jnd.mean():.3f} (SD={eff_jnd.std():.3f})")
        self._add_report_line(f"  CONCURRENT_SUPRA: {eff_concurrent.mean():.3f} (SD={eff_concurrent.std():.3f})")
        
        if len(eff_supra) > 0 and len(eff_jnd) > 0 and len(eff_concurrent) > 0:
            f_stat, p_value = stats.f_oneway(eff_supra, eff_jnd, eff_concurrent)
            self._add_report_line(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
            if p_value < 0.05:
                self._add_report_line("  ✓ SIGNIFICANT difference in path efficiency!")
            else:
                self._add_report_line("  ✗ No significant difference")
        
        # === MOVEMENT TIME ===
        self._add_report_section("3. MOVEMENT TIME (first movement → target)", level=2)
        self._add_report_line("Expected: Similar across conditions (execution speed)")
        
        mt_supra = pre_supra['movementTime'].dropna()
        mt_jnd = pre_jnd['movementTime'].dropna()
        mt_concurrent = concurrent['movementTime'].dropna()
        
        self._add_report_line(f"\nMeans:")
        self._add_report_line(f"  PRE_SUPRA:        {mt_supra.mean():.1f} ms (SD={mt_supra.std():.1f})")
        self._add_report_line(f"  PRE_JND:          {mt_jnd.mean():.1f} ms (SD={mt_jnd.std():.1f})")
        self._add_report_line(f"  CONCURRENT_SUPRA: {mt_concurrent.mean():.1f} ms (SD={mt_concurrent.std():.1f})")
        
        if len(mt_supra) > 0 and len(mt_jnd) > 0 and len(mt_concurrent) > 0:
            f_stat, p_value = stats.f_oneway(mt_supra, mt_jnd, mt_concurrent)
            self._add_report_line(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
            if p_value > 0.05:
                self._add_report_line("  ✓ No significant difference (as expected)")
            else:
                self._add_report_line("  ✗ Unexpected difference in execution time")
    
    def analyze_by_demographic(self, demographic_field, values):
        """Analyze data broken down by demographic field"""
        # Check if demographic field exists and has data
        if demographic_field not in self.trials_df.columns:
            self._add_report_section(f"ANALYSIS BY {demographic_field.upper()} - SKIPPED", level=1)
            self._add_report_line(f"No data available for {demographic_field}")
            return
        
        # Check if there's any non-null data
        if self.trials_df[demographic_field].isna().all():
            self._add_report_section(f"ANALYSIS BY {demographic_field.upper()} - SKIPPED", level=1)
            self._add_report_line(f"All values are null for {demographic_field}")
            return
        
        self._add_report_section(f"ANALYSIS BY {demographic_field.upper()}", level=1)
        
        for value in values:
            subset = self.trials_df[self.trials_df[demographic_field] == value]
            if len(subset) > 0:
                self.test_main_hypothesis(subset, f"{demographic_field}={value}")
                self.create_summary_plots(subset, f"{demographic_field}_{value}")
            else:
                self._add_report_line(f"\nNo data for {demographic_field}={value}")
    
    def analyze_by_target(self):
        """Analyze data broken down by target"""
        self._add_report_section("ANALYSIS BY TARGET", level=1)
        
        unique_targets = sorted(self.trials_df['targetIndex'].dropna().unique())
        
        for target_id in unique_targets:
            subset = self.trials_df[self.trials_df['targetIndex'] == target_id]
            if len(subset) > 0:
                self.test_main_hypothesis(subset, f"Target {int(target_id)}")
                self.create_summary_plots(subset, f"target_{int(target_id)}")
    
    def analyze_velocity_profiles(self, sample_size=5):
        """Analyze velocity profiles INCLUDING reaction time"""
        self._add_report_section("VELOCITY PROFILE ANALYSIS (with Reaction Time)", level=1)
        
        fig, axes = plt.subplots(1, 3, figsize=(18, 5))
        
        for idx, trial_type in enumerate(['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']):
            trials = self.trials_df[self.trials_df['trialType'] == trial_type]
            
            valid_trials = trials[
                (trials['movementPath'].apply(lambda x: isinstance(x, list) and len(x) > 5)) &
                (trials['reactionTime'].notna())
            ]
            
            if len(valid_trials) == 0:
                axes[idx].text(0.5, 0.5, 'No valid path data', 
                              ha='center', va='center')
                axes[idx].set_title(trial_type)
                continue
            
            sample = valid_trials.sample(min(sample_size, len(valid_trials)))
            
            for _, trial in sample.iterrows():
                path = trial['movementPath']
                reaction_time = trial['reactionTime']
                
                velocities = [0, 0]
                times = [0, reaction_time]
                
                for i in range(1, len(path)):
                    if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                        dt = (path[i]['t'] - path[i-1]['t']) / 1000.0
                        
                        if dt > 0:
                            dx = path[i]['x'] - path[i-1]['x']
                            dy = path[i]['y'] - path[i-1]['y']
                            dist = np.sqrt(dx**2 + dy**2)
                            velocity = dist / dt
                            
                            time_from_go = reaction_time + (path[i]['t'] - path[0]['t'])
                            
                            velocities.append(velocity)
                            times.append(time_from_go)
                
                if len(velocities) > 2:
                    axes[idx].plot(times, velocities, alpha=0.5, linewidth=1)
            
            avg_rt = self.trials_df[self.trials_df['trialType'] == trial_type]['reactionTime'].mean()
            axes[idx].axvline(x=avg_rt, color='red', linestyle='--', 
                            alpha=0.7, label='Avg Reaction Time')
            axes[idx].set_xlabel('Time from GO beep (ms)')
            axes[idx].set_ylabel('Velocity (pixels/ms)')
            axes[idx].set_title(f'{trial_type}\nVelocity Profiles')
            axes[idx].legend()
            axes[idx].grid(True, alpha=0.3)
        
        plt.tight_layout()
        filename = os.path.join(self.figures_dir, 'velocity_profiles.png')
        plt.savefig(filename)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()
    
    def plot_movement_paths(self):
        """Plot the raw (x,y) coordinates of all trials"""
        self._add_report_section("PLOTTING (X,Y) MOVEMENT PATHS", level=1)

        path_output_dir = os.path.join(self.figures_dir, 'movement_paths')
        os.makedirs(path_output_dir, exist_ok=True)
        
        # Plot 1: Separated by Trial Type
        fig, axes = plt.subplots(1, 3, figsize=(18, 6), sharex=True, sharey=True)
        
        for idx, trial_type in enumerate(['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']):
            ax = axes[idx]
            trials_of_type = self.trials_df[self.trials_df['trialType'] == trial_type]
            
            for _, trial in trials_of_type.iterrows():
                path = trial['movementPath']
                
                if not isinstance(path, list) or len(path) < 2:
                    continue
                
                x = [p['x'] for p in path if isinstance(p, dict) and 'x' in p]
                y = [p['y'] for p in path if isinstance(p, dict) and 'y' in p]
                
                if len(x) > 0:
                    ax.plot(x, y, alpha=0.5, color='blue', linewidth=1)
                    ax.plot(x[0], y[0], 'o', color='green', markersize=2, alpha=0.5)
                    ax.plot(x[-1], y[-1], 'x', color='red', markersize=2, alpha=0.5)

            ax.set_title(f"All Paths: {trial_type}")
            ax.set_xlabel("X Coordinate")
            ax.set_ylabel("Y Coordinate")
            ax.invert_yaxis()
            ax.set_aspect('equal', adjustable='box')
            ax.grid(True, alpha=0.2)

        plt.tight_layout()
        filename = os.path.join(path_output_dir, 'paths_by_trial_type.png')
        plt.savefig(filename)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()

        # Plot 2: Separated by Target, Colored by Trial Type
        unique_targets = sorted(self.trials_df['targetIndex'].dropna().unique())
        n_targets = len(unique_targets)
        
        if n_targets > 0:
            fig, axes = plt.subplots(2, 2, figsize=(14, 14), sharex=True, sharey=True)
            axes = axes.flatten()
            
            color_map = {
                'PRE_SUPRA': 'green',
                'PRE_JND': 'blue',
                'CONCURRENT_SUPRA': 'red'
            }

            for idx, target_id in enumerate(unique_targets[:4]):
                ax = axes[idx]
                trials_for_target = self.trials_df[self.trials_df['targetIndex'] == target_id]

                for _, trial in trials_for_target.iterrows():
                    path = trial['movementPath']
                    trial_type = trial['trialType']
                    color = color_map.get(trial_type, 'gray')
                    
                    if not isinstance(path, list) or len(path) < 2:
                        continue
                    
                    x = [p['x'] for p in path if isinstance(p, dict) and 'x' in p]
                    y = [p['y'] for p in path if isinstance(p, dict) and 'y' in p]
                    
                    if len(x) > 0:
                        ax.plot(x, y, alpha=0.5, color=color, linewidth=2)
                
                ax.set_title(f"Paths to Target {int(target_id)}")
                ax.set_xlabel("X Coordinate")
                ax.set_ylabel("Y Coordinate")
                ax.invert_yaxis()
                ax.set_aspect('equal', adjustable='box')
                ax.grid(True, alpha=0.2)
                
                handles = [plt.Line2D([0], [0], color=c, lw=2, label=t) 
                          for t, c in color_map.items()]
                ax.legend(handles=handles, title="Trial Type")

            plt.tight_layout()
            filename = os.path.join(path_output_dir, 'paths_by_target.png')
            plt.savefig(filename)
            self._add_report_line(f"  → Saved: {filename}")
            plt.close()

    def analyze_movement_corrections(self):
        """Count direction changes as measure of online corrections"""
        self._add_report_section("MOVEMENT CORRECTIONS ANALYSIS", level=1)
        self._add_report_line("Fewer corrections = better planning")
        
        def count_direction_changes(path):
            if not isinstance(path, list) or len(path) < 3:
                return None
            
            changes = 0
            prev_angle = None
            
            for i in range(1, len(path) - 1):
                if not all(isinstance(p, dict) for p in [path[i-1], path[i], path[i+1]]):
                    continue
                
                dx = path[i+1]['x'] - path[i]['x']
                dy = path[i+1]['y'] - path[i]['y']
                
                if dx == 0 and dy == 0:
                    continue
                
                angle = np.arctan2(dy, dx)
                
                if prev_angle is not None:
                    angle_diff = abs(angle - prev_angle)
                    if angle_diff > np.pi:
                        angle_diff = 2 * np.pi - angle_diff
                    
                    if angle_diff > np.pi / 6:
                        changes += 1
                
                prev_angle = angle
            
            return changes
        
        self.trials_df['corrections'] = self.trials_df['movementPath'].apply(
            count_direction_changes
        )
        
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            corrections = self.trials_df[
                self.trials_df['trialType'] == trial_type
            ]['corrections'].dropna()
            
            self._add_report_line(f"\n{trial_type}:")
            self._add_report_line(f"  Mean corrections: {corrections.mean():.2f} (SD={corrections.std():.2f})")
            self._add_report_line(f"  Median: {corrections.median():.0f}")
    
    def create_summary_plots(self, data_subset=None, subset_name="overall"):
        """Create key plots for publication/presentation"""
        if data_subset is None:
            data_subset = self.trials_df
            self._add_report_section("CREATING SUMMARY FIGURES", level=1)
        
        fig, axes = plt.subplots(2, 2, figsize=(14, 10))
        
        # Plot 1: Reaction Time comparison
        ax = axes[0, 0]
        data_rt = []
        labels_rt = []
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            rt = data_subset[data_subset['trialType'] == trial_type]['reactionTime'].dropna()
            if len(rt) > 0:
                data_rt.append(rt)
                labels_rt.append(trial_type.replace('_', '\n'))
        
        if len(data_rt) > 0:
            bp = ax.boxplot(data_rt, labels=labels_rt, patch_artist=True)
            for patch in bp['boxes']:
                patch.set_facecolor('lightblue')
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('A. Planning Time by Condition', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3)
        
        # Plot 2: Path Efficiency
        ax = axes[0, 1]
        data_eff = []
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            eff = data_subset[data_subset['trialType'] == trial_type]['pathEfficiency'].dropna()
            if len(eff) > 0:
                data_eff.append(eff)
        
        if len(data_eff) > 0:
            bp = ax.boxplot(data_eff, labels=labels_rt, patch_artist=True)
            for patch in bp['boxes']:
                patch.set_facecolor('lightgreen')
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('B. Movement Efficiency by Condition', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3)
        
        # Plot 3: Path Length
        ax = axes[1, 0]
        if len(data_subset) > 0:
            sns.violinplot(data=data_subset, x='trialType', y='pathLength', ax=ax)
            ax.set_xticklabels(labels_rt)
        ax.set_xlabel('')
        ax.set_ylabel('Path Length (pixels)', fontsize=12)
        ax.set_title('C. Path Length Distribution', fontsize=13, fontweight='bold')
        
        # Plot 4: Movement Time
        ax = axes[1, 1]
        if len(data_subset) > 0:
            sns.violinplot(data=data_subset, x='trialType', y='movementTime', ax=ax)
            ax.set_xticklabels(labels_rt)
        ax.set_xlabel('')
        ax.set_ylabel('Movement Time (ms)', fontsize=12)
        ax.set_title('D. Execution Time', fontsize=13, fontweight='bold')
        
        plt.tight_layout()
        filename = os.path.join(self.figures_dir, f'summary_comparison_{subset_name}.png')
        plt.savefig(filename, dpi=300)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()
    
    def save_report(self):
        """Save the accumulated report to file"""
        report_path = os.path.join(self.output_dir, 'subliminal_priming_report.txt')
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.report_lines))
        print(f"\n✓ Report saved to: {report_path}")


def main():
    """Run enhanced analysis"""
    print("\n" + "="*80)
    print("SUBLIMINAL PRIMING ANALYSIS - REACHING MOVEMENT STUDY")
    print("="*80)
    
    # Load data
    participants_df, trials_df = load_data()
    
    if trials_df.empty:
        print("\n❌ No trial data available")
        return
    
    # Run analysis
    analyzer = SubliminalPrimingAnalyzer(trials_df, participants_df, outlier_threshold_ms=50000)
    
    # Main hypothesis tests (overall)
    analyzer.test_main_hypothesis()
    
    # Demographic breakdowns
    analyzer.analyze_by_demographic('hasAttentionDeficit', [True, False])
    analyzer.analyze_by_demographic('hasGlasses', [True, False])
    analyzer.analyze_by_demographic('gender', ['Male', 'Female'])
    
    # Target-specific analysis
    analyzer.analyze_by_target()
    
    # Additional analyses
    analyzer.analyze_velocity_profiles()
    analyzer.analyze_movement_corrections()
    
    # Create plots
    analyzer.create_summary_plots()
    analyzer.plot_movement_paths()
    
    # Save consolidated report
    analyzer.save_report()
    
    print("\n" + "="*80)
    print("✓ ANALYSIS COMPLETE")
    print("="*80)
    print(f"\nAll outputs saved to: {analyzer.output_dir}")
    print("  - subliminal_priming_report.txt (comprehensive text report)")
    print("  - figures/ (all plots and visualizations)")


if __name__ == "__main__":
    main()