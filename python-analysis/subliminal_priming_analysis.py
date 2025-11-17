"""
Specialized Analysis for Subliminal Priming in Reaching Movement Study
Focuses on motor planning differences between PRE_SUPRA, PRE_JND, and CONCURRENT_SUPRA
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from firebase_connector import load_data
import warnings
import os
warnings.filterwarnings('ignore')

class SubliminalPrimingAnalyzer:
    """Analyze subliminal priming effects on motor planning"""
    
    def __init__(self, trials_df, outlier_threshold_ms=50000):
        """
        Initialize with trial data
        
        Args:
            trials_df: DataFrame with trial data
            outlier_threshold_ms: Remove reaction times above this (default 50 seconds)
        """
        self.raw_trials = trials_df.copy()
        
        # Clean data
        print("="*60)
        print("DATA CLEANING")
        print("="*60)
        print(f"Original trials: {len(self.raw_trials)}")
        
        # Remove outliers
        self.trials_df = trials_df[
            (trials_df['reactionTime'].notna()) & 
            (trials_df['reactionTime'] < outlier_threshold_ms)
        ].copy()
        
        removed = len(self.raw_trials) - len(self.trials_df)
        print(f"Removed {removed} outlier trials (reaction time > {outlier_threshold_ms}ms)")
        print(f"Clean trials: {len(self.trials_df)}")
        
        # Calculate additional metrics
        self._calculate_metrics()
    
    def _calculate_metrics(self):
        """Calculate additional motor planning metrics"""
        
        # Path efficiency (ideal distance / actual distance)
        # Assuming targets are at corners, rough estimate of ideal distance
        self.trials_df['pathEfficiency'] = 800 / self.trials_df['pathLength']
        
        # Total response time components
        self.trials_df['planningTime'] = self.trials_df['reactionTime']
        self.trials_df['executionTime'] = self.trials_df['movementTime']
    
    def test_main_hypothesis(self):
        """
        Test the main hypothesis:
        PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA for reaction time and path planning
        """
        print("\n" + "="*60)
        print("MAIN HYPOTHESIS TESTING")
        print("="*60)
        
        # Get data for each condition
        pre_supra = self.trials_df[self.trials_df['trialType'] == 'PRE_SUPRA']
        pre_jnd = self.trials_df[self.trials_df['trialType'] == 'PRE_JND']
        concurrent = self.trials_df[self.trials_df['trialType'] == 'CONCURRENT_SUPRA']
        
        print(f"\nSample sizes:")
        print(f"  PRE_SUPRA: {len(pre_supra)} trials")
        print(f"  PRE_JND: {len(pre_jnd)} trials")
        print(f"  CONCURRENT_SUPRA: {len(concurrent)} trials")
        
        # === REACTION TIME ===
        print("\n" + "-"*60)
        print("1. REACTION TIME (GO beep → first movement)")
        print("-"*60)
        print("Expected: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA")
        
        rt_supra = pre_supra['reactionTime'].dropna()
        rt_jnd = pre_jnd['reactionTime'].dropna()
        rt_concurrent = concurrent['reactionTime'].dropna()
        
        print(f"\nMeans:")
        print(f"  PRE_SUPRA:        {rt_supra.mean():.1f} ms (SD={rt_supra.std():.1f})")
        print(f"  PRE_JND:          {rt_jnd.mean():.1f} ms (SD={rt_jnd.std():.1f})")
        print(f"  CONCURRENT_SUPRA: {rt_concurrent.mean():.1f} ms (SD={rt_concurrent.std():.1f})")
        
        # ANOVA
        f_stat, p_value = stats.f_oneway(rt_supra, rt_jnd, rt_concurrent)
        print(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
        if p_value < 0.05:
            print("  ✓ SIGNIFICANT difference between conditions!")
        else:
            print("  ✗ No significant difference (need more data or effect is small)")
        
        # Pairwise comparisons
        print(f"\nPairwise t-tests:")
        t1, p1 = stats.ttest_ind(rt_supra, rt_jnd)
        print(f"  PRE_SUPRA vs PRE_JND: t={t1:.3f}, p={p1:.4f}")
        
        t2, p2 = stats.ttest_ind(rt_jnd, rt_concurrent)
        print(f"  PRE_JND vs CONCURRENT: t={t2:.3f}, p={p2:.4f}")
        
        t3, p3 = stats.ttest_ind(rt_supra, rt_concurrent)
        print(f"  PRE_SUPRA vs CONCURRENT: t={t3:.3f}, p={p3:.4f}")
        
        # === PATH EFFICIENCY ===
        print("\n" + "-"*60)
        print("2. PATH EFFICIENCY (directness of movement)")
        print("-"*60)
        print("Expected: PRE_SUPRA > PRE_JND > CONCURRENT_SUPRA")
        
        eff_supra = pre_supra['pathEfficiency'].dropna()
        eff_jnd = pre_jnd['pathEfficiency'].dropna()
        eff_concurrent = concurrent['pathEfficiency'].dropna()
        
        print(f"\nMeans (higher = more efficient):")
        print(f"  PRE_SUPRA:        {eff_supra.mean():.3f} (SD={eff_supra.std():.3f})")
        print(f"  PRE_JND:          {eff_jnd.mean():.3f} (SD={eff_jnd.std():.3f})")
        print(f"  CONCURRENT_SUPRA: {eff_concurrent.mean():.3f} (SD={eff_concurrent.std():.3f})")
        
        f_stat, p_value = stats.f_oneway(eff_supra, eff_jnd, eff_concurrent)
        print(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
        if p_value < 0.05:
            print("  ✓ SIGNIFICANT difference in path efficiency!")
        else:
            print("  ✗ No significant difference")
        
        # === MOVEMENT TIME ===
        print("\n" + "-"*60)
        print("3. MOVEMENT TIME (first movement → target)")
        print("-"*60)
        print("Expected: Similar across conditions (execution speed)")
        
        mt_supra = pre_supra['movementTime'].dropna()
        mt_jnd = pre_jnd['movementTime'].dropna()
        mt_concurrent = concurrent['movementTime'].dropna()
        
        print(f"\nMeans:")
        print(f"  PRE_SUPRA:        {mt_supra.mean():.1f} ms (SD={mt_supra.std():.1f})")
        print(f"  PRE_JND:          {mt_jnd.mean():.1f} ms (SD={mt_jnd.std():.1f})")
        print(f"  CONCURRENT_SUPRA: {mt_concurrent.mean():.1f} ms (SD={mt_concurrent.std():.1f})")
        
        f_stat, p_value = stats.f_oneway(mt_supra, mt_jnd, mt_concurrent)
        print(f"\nOne-way ANOVA: F={f_stat:.3f}, p={p_value:.4f}")
        if p_value > 0.05:
            print("  ✓ No significant difference (as expected)")
        else:
            print("  ✗ Unexpected difference in execution time")
    
    def analyze_velocity_profiles(self, sample_size=5):
        """Analyze velocity profiles INCLUDING reaction time (zero velocity period)"""
        print("\n" + "="*60)
        print("VELOCITY PROFILE ANALYSIS (with Reaction Time)")
        print("="*60)
        
        fig, axes = plt.subplots(1, 3, figsize=(18, 5))
        
        for idx, trial_type in enumerate(['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']):
            trials = self.trials_df[self.trials_df['trialType'] == trial_type]
            
            # Get sample trials with good path data
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
                reaction_time = trial['reactionTime']  # Time spent at zero velocity
                
                # Start with reaction time period (zero velocity)
                velocities = [0]  # Start at zero
                times = [0]  # Start at time 0
                
                # Add the end of reaction time (still zero velocity)
                velocities.append(0)
                times.append(reaction_time)
                
                # Now calculate velocities during actual movement
                for i in range(1, len(path)):
                    if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                        dt = (path[i]['t'] - path[i-1]['t']) / 1000.0  # to seconds
                        
                        if dt > 0:
                            dx = path[i]['x'] - path[i-1]['x']
                            dy = path[i]['y'] - path[i-1]['y']
                            dist = np.sqrt(dx**2 + dy**2)
                            velocity = dist / dt
                            
                            # Time relative to GO beep (includes reaction time)
                            time_from_go = reaction_time + (path[i]['t'] - path[0]['t'])
                            
                            velocities.append(velocity)
                            times.append(time_from_go)
                
                if len(velocities) > 2:
                    axes[idx].plot(times, velocities, alpha=0.5, linewidth=1)
            
            axes[idx].axvline(x=self.trials_df[self.trials_df['trialType'] == trial_type]['reactionTime'].mean(), 
                            color='red', linestyle='--', alpha=0.7, label='Avg Reaction Time')
            axes[idx].set_xlabel('Time from GO beep (ms)')
            axes[idx].set_ylabel('Velocity (pixels/ms)')
            axes[idx].set_title(f'{trial_type}\nVelocity Profiles')
            axes[idx].legend()
            axes[idx].grid(True, alpha=0.3)
        
        plt.tight_layout()
        plt.savefig('analysis_outputs/figures/velocity_profiles.png')
        print("  → Saved: velocity_profiles.png")
        print("\nVelocity profile shows:")
        print("  - Flat line at 0 = reaction time (planning)")
        print("  - Bell curve after = movement execution")
        print("  - Better planning → shorter flat period, smoother curve")
        plt.close()
    
    def plot_movement_paths(self):
        """Plot the raw (x,y) coordinates of all trials"""
        print("\n" + "="*60)
        print("PLOTTING (X,Y) MOVEMENT PATHS")
        print("="*60)

        path_output_dir = 'analysis_outputs/figures/movement_paths'
        os.makedirs(path_output_dir, exist_ok=True)
        
        # --- Plot 1: Separated by Trial Type ---
        print("  Generating plot 1: Paths by Trial Type")
        fig, axes = plt.subplots(1, 3, figsize=(18, 6), sharex=True, sharey=True)
        
        for idx, trial_type in enumerate(['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']):
            ax = axes[idx]
            trials_of_type = self.trials_df[self.trials_df['trialType'] == trial_type]
            
            for _, trial in trials_of_type.iterrows():
                path = trial['movementPath']
                
                if not isinstance(path, list) or len(path) < 2:
                    continue
                
                # Extract x and y coordinates
                x = [p['x'] for p in path if isinstance(p, dict) and 'x' in p]
                y = [p['y'] for p in path if isinstance(p, dict) and 'y' in p]
                
                if len(x) > 0:
                    ax.plot(x, y, alpha=0.5, color='blue', linewidth=1)
                    ax.plot(x[0], y[0], 'o', color='green', markersize=2, alpha=0.5) # Start
                    ax.plot(x[-1], y[-1], 'x', color='red', markersize=2, alpha=0.5) # End

            ax.set_title(f"All Paths: {trial_type}")
            ax.set_xlabel("X Coordinate")
            ax.set_ylabel("Y Coordinate")
            ax.invert_yaxis() # Invert y-axis for screen coordinates (0,0 is top-left)
            ax.set_aspect('equal', adjustable='box')
            ax.grid(True, alpha=0.2)

        plt.tight_layout()
        plt.savefig(os.path.join(path_output_dir, 'paths_by_trial_type.png'))
        print(f"  → Saved: {path_output_dir}/paths_by_trial_type.png")
        plt.close()

        # --- Plot 2: Separated by Target, Colored by Trial Type ---
        print("  Generating plot 2: Paths by Target (Colored by Type)")
        
        unique_targets = sorted(self.trials_df['targetIndex'].dropna().unique())
        # Assuming 4 targets for a 2x2 grid
        fig, axes = plt.subplots(2, 2, figsize=(14, 14), sharex=True, sharey=True)
        axes = axes.flatten()
        
        color_map = {
            'PRE_SUPRA': 'green',
            'PRE_JND': 'blue',
            'CONCURRENT_SUPRA': 'red'
        }

        for idx, target_id in enumerate(unique_targets):
            if idx >= len(axes): # Stop if more than 4 targets
                break
            
            ax = axes[idx]
            trials_for_target = self.trials_df[self.trials_df['targetIndex'] == target_id]

            for _, trial in trials_for_target.iterrows():
                path = trial['movementPath']
                trial_type = trial['trialType']
                color = color_map.get(trial_type, 'gray') # Get color
                
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
            
            # Add a legend
            handles = [plt.Line2D([0], [0], color=c, lw=2, label=t) for t, c in color_map.items()]
            ax.legend(handles=handles, title="Trial Type")

        plt.tight_layout()
        plt.savefig(os.path.join(path_output_dir, 'paths_by_target.png'))
        print(f"  → Saved: {path_output_dir}/paths_by_target.png")
        plt.close()

    def analyze_movement_corrections(self):
        """Count direction changes as measure of online corrections"""
        print("\n" + "="*60)
        print("MOVEMENT CORRECTIONS ANALYSIS")
        print("="*60)
        print("Fewer corrections = better planning")
        
        def count_direction_changes(path):
            """Count significant direction changes in movement path"""
            if not isinstance(path, list) or len(path) < 3:
                return None
            
            changes = 0
            prev_angle = None
            
            for i in range(1, len(path) - 1):
                if not all(isinstance(p, dict) for p in [path[i-1], path[i], path[i+1]]):
                    continue
                
                # Calculate movement direction
                dx = path[i+1]['x'] - path[i]['x']
                dy = path[i+1]['y'] - path[i]['y']
                
                if dx == 0 and dy == 0:
                    continue
                
                angle = np.arctan2(dy, dx)
                
                if prev_angle is not None:
                    angle_diff = abs(angle - prev_angle)
                    # Normalize to [0, pi]
                    if angle_diff > np.pi:
                        angle_diff = 2 * np.pi - angle_diff
                    
                    # Count as correction if angle change > 30 degrees
                    if angle_diff > np.pi / 6:
                        changes += 1
                
                prev_angle = angle
            
            return changes
        
        self.trials_df['corrections'] = self.trials_df['movementPath'].apply(
            count_direction_changes
        )
        
        # Compare across conditions
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            corrections = self.trials_df[
                self.trials_df['trialType'] == trial_type
            ]['corrections'].dropna()
            
            print(f"\n{trial_type}:")
            print(f"  Mean corrections: {corrections.mean():.2f} (SD={corrections.std():.2f})")
            print(f"  Median: {corrections.median():.0f}")
    
    def check_target_balance(self):
        """Check if all targets are used equally"""
        print("\n" + "="*60)
        print("TARGET DISTRIBUTION CHECK")
        print("="*60)
        
        target_counts = self.trials_df['targetIndex'].value_counts().sort_index()
        print("\nTarget usage:")
        for target, count in target_counts.items():
            print(f"  Target {target}: {count} trials")
        
        # Chi-square test for uniformity
        chi2, p_value = stats.chisquare(target_counts.values)
        print(f"\nChi-square test for uniform distribution:")
        print(f"  χ² = {chi2:.3f}, p = {p_value:.4f}")
        
        if p_value < 0.05:
            print("  ⚠️  Targets are NOT equally distributed!")
            print("  → Consider balancing in your Kotlin code")
        else:
            print("  ✓ Targets are reasonably balanced")
    
    def create_summary_plots(self):
        """Create key plots for publication/presentation"""
        print("\n" + "="*60)
        print("CREATING SUMMARY FIGURES")
        print("="*60)
        
        fig, axes = plt.subplots(2, 2, figsize=(14, 10))
        
        # Plot 1: Reaction Time comparison
        ax = axes[0, 0]
        data_rt = []
        labels_rt = []
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            rt = self.trials_df[self.trials_df['trialType'] == trial_type]['reactionTime'].dropna()
            data_rt.append(rt)
            labels_rt.append(trial_type.replace('_', '\n'))
        
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
            eff = self.trials_df[self.trials_df['trialType'] == trial_type]['pathEfficiency'].dropna()
            data_eff.append(eff)
        
        bp = ax.boxplot(data_eff, labels=labels_rt, patch_artist=True)
        for patch in bp['boxes']:
            patch.set_facecolor('lightgreen')
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('B. Movement Efficiency by Condition', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3)
        
        # Plot 3: Path Length
        ax = axes[1, 0]
        sns.violinplot(data=self.trials_df, x='trialType', y='pathLength', ax=ax)
        ax.set_xlabel('')
        ax.set_xticklabels(labels_rt)
        ax.set_ylabel('Path Length (pixels)', fontsize=12)
        ax.set_title('C. Path Length Distribution', fontsize=13, fontweight='bold')
        
        # Plot 4: Movement Time
        ax = axes[1, 1]
        sns.violinplot(data=self.trials_df, x='trialType', y='movementTime', ax=ax)
        ax.set_xlabel('')
        ax.set_xticklabels(labels_rt)
        ax.set_ylabel('Movement Time (ms)', fontsize=12)
        ax.set_title('D. Execution Time', fontsize=13, fontweight='bold')
        
        plt.tight_layout()
        plt.savefig('analysis_outputs/figures/summary_comparison.png', dpi=300)
        print("  → Saved: summary_comparison.png")
        plt.close()
    
    def generate_report(self):
        """Generate detailed research report"""
        print("\n" + "="*60)
        print("GENERATING RESEARCH REPORT")
        print("="*60)
        
        with open('analysis_outputs/subliminal_priming_report.txt', 'w') as f:
            f.write("="*80 + "\n")
            f.write("SUBLIMINAL PRIMING IN REACHING MOVEMENT - ANALYSIS REPORT\n")
            f.write("="*80 + "\n\n")
            
            f.write("RESEARCH HYPOTHESIS:\n")
            f.write("-"*80 + "\n")
            f.write("Subliminal visual cues (PRE_JND) facilitate motor planning,\n")
            f.write("falling between conscious cues (PRE_SUPRA) and no advance info (CONCURRENT_SUPRA)\n\n")
            
            f.write("SAMPLE:\n")
            f.write("-"*80 + "\n")
            f.write(f"Total clean trials: {len(self.trials_df)}\n")
            f.write(f"Outliers removed: {len(self.raw_trials) - len(self.trials_df)}\n\n")
            
            for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
                count = len(self.trials_df[self.trials_df['trialType'] == trial_type])
                f.write(f"  {trial_type}: {count} trials\n")
            
            f.write("\n")
            
        print("  → Saved: subliminal_priming_report.txt")


def main():
    """Run specialized analysis"""
    print("\n" + "="*80)
    print("SUBLIMINAL PRIMING ANALYSIS - REACHING MOVEMENT STUDY")
    print("="*80)
    
    # Load data
    participants_df, trials_df, _ = load_data()
    
    if trials_df.empty:
        print("\n❌ No trial data available")
        return
    
    # Run analysis
    analyzer = SubliminalPrimingAnalyzer(trials_df, outlier_threshold_ms=50000)
    
    # Main hypothesis tests
    analyzer.test_main_hypothesis()
    
    # Additional analyses
    analyzer.check_target_balance()
    analyzer.analyze_velocity_profiles()
    analyzer.analyze_movement_corrections()
    
    
    # Create publication-ready plots
    analyzer.create_summary_plots()
    analyzer.plot_movement_paths()
    
    # Generate report
    analyzer.generate_report()
    
    print("\n" + "="*80)
    print("✓ ANALYSIS COMPLETE")
    print("="*80)
    print("\nKey files created:")
    print("  - analysis_outputs/figures/summary_comparison.png")
    print("  - analysis_outputs/figures/velocity_profiles.png")
    print("  - analysis_outputs/subliminal_priming_report.txt")


if __name__ == "__main__":
    main()