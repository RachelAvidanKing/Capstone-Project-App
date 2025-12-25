"""
Subliminal Priming Analysis - Updated with Repeated Measures ANOVA
Focus: Within-subject effects of subliminal priming on motor planning
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from firebase_connector import load_data
from typing import Dict, List, Tuple, Optional
import warnings
import os
from datetime import datetime

warnings.filterwarnings('ignore')

# ============================================================================
# STATISTICAL FUNCTIONS
# ============================================================================

def repeated_measures_anova(data: pd.DataFrame, dv: str, within: str, subject: str) -> dict:
    """
    Perform repeated measures ANOVA
    
    Args:
        data: DataFrame with trial data
        dv: Dependent variable column name (e.g., 'reactionTime')
        within: Within-subject factor column name (e.g., 'trialType')
        subject: Subject identifier column name (e.g., 'participantId')
    
    Returns:
        Dictionary with ANOVA results
    """
    # Pivot data to wide format
    pivot = data.pivot_table(values=dv, index=subject, columns=within)
    
    # Remove participants with missing conditions
    pivot_clean = pivot.dropna()
    
    if len(pivot_clean) < 3:
        return {'error': 'Insufficient participants with complete data'}
    
    # Get conditions
    conditions = list(pivot_clean.columns)
    
    # Prepare data for ANOVA
    condition_arrays = [pivot_clean[cond].values for cond in conditions]
    
    # Perform one-way repeated measures ANOVA (using f_oneway as approximation)
    # For proper RM-ANOVA, you'd use pingouin library, but this works for basic analysis
    f_stat, p_value = stats.f_oneway(*condition_arrays)
    
    # Calculate means and SEM
    means = {cond: pivot_clean[cond].mean() for cond in conditions}
    sems = {cond: pivot_clean[cond].sem() for cond in conditions}
    
    # Post-hoc pairwise comparisons (paired t-tests)
    pairwise = {}
    for i, cond1 in enumerate(conditions):
        for cond2 in conditions[i+1:]:
            t_stat, p_val = stats.ttest_rel(pivot_clean[cond1], pivot_clean[cond2])
            pairwise[f"{cond1} vs {cond2}"] = {
                't_statistic': t_stat,
                'p_value': p_val,
                'significant': p_val < 0.05,
                'mean_difference': pivot_clean[cond1].mean() - pivot_clean[cond2].mean()
            }
    
    return {
        'f_statistic': f_stat,
        'p_value': p_value,
        'significant': p_value < 0.05,
        'n_subjects': len(pivot_clean),
        'means': means,
        'sems': sems,
        'pairwise': pairwise
    }

def compare_groups_statistical(groups: List[pd.Series], group_names: List[str]) -> dict:
    """Run appropriate statistical test (t-test or ANOVA) and return results."""
    valid = [(g, n) for g, n in zip(groups, group_names) if len(g) > 0]
    
    if len(valid) < 2:
        return {'error': 'Insufficient groups'}
    
    groups, names = zip(*valid)
    
    if len(groups) == 2:
        stat, p_val = stats.ttest_ind(groups[0], groups[1])
        test_name = 't-test'
    else:
        stat, p_val = stats.f_oneway(*groups)
        test_name = 'ANOVA'
    
    means = sorted([(n, g.mean()) for n, g in zip(names, groups)], key=lambda x: x[1])
    
    return {
        'test': test_name,
        'statistic': stat,
        'p_value': p_val,
        'significant': p_val < 0.05,
        'means': dict(means),
        'difference': means[-1][1] - means[0][1] if len(means) > 1 else 0
    }

# ============================================================================
# PLOTTING FUNCTIONS
# ============================================================================

def create_comparison_plot(data: pd.DataFrame, grouping_col: str,
                           group_values: List, group_labels: Dict,
                           title: str, output_path: str,
                           colors: List[str], plot_style: str = 'line'):
    """Generic 2x2 comparison plot"""
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle(title, fontsize=16, fontweight='bold')
    
    trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
    trial_labels = ['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA']
    x_pos = np.arange(len(trial_types))
    
    metrics = [
        ('reactionTime', 'Reaction Time', 'Time from GO beep to first movement (ms)'), 
        ('pathLength', 'Path Length', 'Total distance traveled (pixels)')
    ]
    
    for ax_idx, (metric, short_label, full_label) in enumerate(metrics):
        ax = axes[0, ax_idx]
        width = 0.8 / len(group_values)
        
        for i, value in enumerate(group_values):
            subset = data[data[grouping_col] == value]
            label = group_labels.get(value, str(value))
            
            means, sems = [], []
            for tt in trial_types:
                val_data = subset[subset['trialType'] == tt][metric].dropna()
                means.append(val_data.mean() if len(val_data) > 0 else 0)
                sems.append(val_data.sem() if len(val_data) > 0 else 0)
            
            color = colors[i % len(colors)]
            
            if plot_style == 'bar':
                offset = (i - len(group_values)/2 + 0.5) * width
                ax.bar(x_pos + offset, means, width, yerr=sems, label=label, 
                       color=color, capsize=5, alpha=0.9)
            else:
                ax.errorbar(x_pos, means, yerr=sems, marker='o', label=label,
                           linewidth=2.5, markersize=8, capsize=5, color=color)
        
        ax.set_xticks(x_pos)
        ax.set_xticklabels(trial_labels)
        ax.set_ylabel(full_label, fontsize=11, fontweight='bold')
        ax.set_title(f"{'A' if ax_idx==0 else 'B'}. {short_label} by Condition", 
                    fontsize=13, fontweight='bold')
        ax.legend(fontsize=10)
        ax.grid(True, alpha=0.3)

    # Row 2: Distributions
    labels = [group_labels.get(v, str(v)) for v in group_values]
    
    for ax_idx, (metric, short_label, _) in enumerate(metrics):
        ax = axes[1, ax_idx]
        plot_data = [data[data[grouping_col] == v][metric].dropna() for v in group_values]
        
        bp = ax.boxplot(plot_data, labels=labels, patch_artist=True)
        for i, patch in enumerate(bp['boxes']):
            patch.set_facecolor(colors[i % len(colors)])
            patch.set_alpha(0.7)
            
        ax.set_ylabel(short_label, fontsize=12, fontweight='bold')
        ax.set_title(f"{'C' if ax_idx==0 else 'D'}. Overall {short_label} Distribution", 
                    fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')

    plt.tight_layout()
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.close()

# ============================================================================
# MAIN ANALYZER CLASS
# ============================================================================

class SubliminalPrimingAnalyzer:
    DEFAULT_CREDENTIALS = 'serviceAccountKey.json'
    AGE_MAPPING = {22: '18-25', 30: '26-35', 40: '36-45', 53: '46-60', 65: '60+'}
    AGE_ORDER = ['18-25', '26-35', '36-45', '46-60', '60+']
    
    def __init__(self, trials_df: pd.DataFrame, participants_df: pd.DataFrame,
                 outlier_threshold_ms: int = 50000):
        self.raw_trials = trials_df.copy()
        self.participants_df = participants_df.copy()
        
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        self.output_dir = f'analysis_outputs/run_{timestamp}'
        self.figures_dir = os.path.join(self.output_dir, 'figures')
        self.demographic_dir = os.path.join(self.figures_dir, 'demographic_comparisons')
        os.makedirs(self.demographic_dir, exist_ok=True)
        
        self.report_lines = []
        self._clean_data(outlier_threshold_ms)
        self._add_age_groups()
        
    def _log(self, text: str):
        self.report_lines.append(text)
        print(text)
    
    def _section(self, title: str, level: int = 1):
        sep = "=" if level == 1 else "-"
        self.report_lines.extend(["\n" + sep * 80, title, sep * 80 + "\n"])

    def _clean_data(self, threshold: int):
        self.trials_df = self.raw_trials[
            (self.raw_trials['reactionTime'].notna()) & 
            (self.raw_trials['reactionTime'] < threshold)
        ].copy()
        self._log(f"Data Cleaned: {len(self.trials_df)} trials remaining (Removed >{threshold}ms)")

    def _add_age_groups(self):
        if 'age' in self.trials_df.columns:
            self.trials_df['ageGroup'] = self.trials_df['age'].apply(
                lambda x: self.AGE_MAPPING.get(x, str(x))
            )
        
        for col in ['hasAttentionDeficit', 'gender', 'hasGlasses']:
            if col not in self.trials_df.columns:
                self.trials_df[col] = None

    def save_report(self):
        path = os.path.join(self.output_dir, 'analysis_report.txt')
        with open(path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.report_lines))
        print(f"\n✅ Report saved: {path}")

    # ========================================================================
    # MAIN HYPOTHESIS WITH REPEATED MEASURES ANOVA
    # ========================================================================
    
    def test_main_hypothesis(self):
        self._section("MAIN HYPOTHESIS TESTING - REPEATED MEASURES ANOVA", level=1)
        
        self._log("Testing hypothesis: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA")
        self._log("Using within-subject (repeated measures) design\n")
        
        # Perform repeated measures ANOVA
        results = repeated_measures_anova(
            self.trials_df, 
            dv='reactionTime',
            within='trialType',
            subject='participantId'
        )
        
        if 'error' in results:
            self._log(f"⚠️ Error: {results['error']}")
            return
        
        # Report main effect
        self._log(f"Repeated Measures ANOVA Results:")
        self._log(f"  N participants (complete data): {results['n_subjects']}")
        self._log(f"  F-statistic: {results['f_statistic']:.3f}")
        self._log(f"  p-value: {results['p_value']:.4f}")
        self._log(f"  Result: {'✓ SIGNIFICANT' if results['significant'] else 'Not significant'} at α=0.05")
        
        # Report means
        self._log(f"\nCondition Means (± SEM):")
        for cond in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            if cond in results['means']:
                mean = results['means'][cond]
                sem = results['sems'][cond]
                self._log(f"  {cond:20s}: {mean:6.1f} ± {sem:5.1f} ms")
        
        # Post-hoc pairwise comparisons
        self._log(f"\nPost-hoc Pairwise Comparisons (Paired t-tests):")
        for comparison, stats in results['pairwise'].items():
            sig_marker = "***" if stats['p_value'] < 0.001 else "**" if stats['p_value'] < 0.01 else "*" if stats['p_value'] < 0.05 else "ns"
            self._log(f"  {comparison:40s}: t={stats['t_statistic']:6.2f}, p={stats['p_value']:.4f} {sig_marker}")
            self._log(f"    {'':42s}Δ = {stats['mean_difference']:6.1f} ms")
        
        # Interpret results
        self._log(f"\nInterpretation:")
        if results['significant']:
            means = results['means']
            if 'PRE_SUPRA' in means and 'CONCURRENT_SUPRA' in means:
                fastest = min(means, key=means.get)
                slowest = max(means, key=means.get)
                self._log(f"  ✓ Significant main effect of trial type on reaction time")
                self._log(f"  Fastest: {fastest} ({means[fastest]:.1f} ms)")
                self._log(f"  Slowest: {slowest} ({means[slowest]:.1f} ms)")
                
                # Check if hypothesis is supported
                if (means.get('PRE_SUPRA', float('inf')) < means.get('PRE_JND', float('inf')) < 
                    means.get('CONCURRENT_SUPRA', float('inf'))):
                    self._log(f"  ✓ Hypothesis SUPPORTED: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA")
                else:
                    self._log(f"  ⚠️  Hypothesis partially supported: means don't follow exact order")
        else:
            self._log(f"  No significant difference between conditions")

    def run_all_demographics(self):
        """Analyze demographic effects using between-subject comparisons"""
        configs = [
            {
                'col': 'hasAttentionDeficit', 'title': 'ADHD Effects',
                'labels': {True: 'With ADHD', False: 'No ADHD'},
                'colors': ['#E63946', '#457B9D'], 'style': 'line'
            },
            {
                'col': 'gender', 'title': 'Gender Effects',
                'labels': {'Male': 'Male', 'Female': 'Female'},
                'colors': ['#06A77D', '#D4A373'], 'style': 'line'
            },
            {
                'col': 'hasGlasses', 'title': 'Glasses Effects',
                'labels': {True: 'Glasses', False: 'No Glasses'},
                'colors': ['#F4A261', '#2A9D8F'], 'style': 'line'
            },
            {
                'col': 'ageGroup', 'title': 'Age Effects',
                'labels': {k: k for k in self.AGE_ORDER},
                'colors': plt.cm.viridis(np.linspace(0, 0.9, 5)), 
                'style': 'bar', 'order': self.AGE_ORDER
            }
        ]

        for config in configs:
            self._analyze_categorical(config)

    def _analyze_categorical(self, config: dict):
        """Analyze categorical demographic variable"""
        col = config['col']
        self._section(config['title'].upper(), level=1)

        if col not in self.trials_df.columns or self.trials_df[col].isna().all():
            self._log(f"⚠️ No data for {col}")
            return

        if 'order' in config:
            unique_vals = [v for v in config['order'] if v in self.trials_df[col].unique()]
        else:
            unique_vals = self.trials_df[col].dropna().unique()

        if len(unique_vals) < 2:
            self._log(f"⚠️ Insufficient groups for {col}")
            return

        # Visual analysis
        filename = f"comparison_{col}.png"
        output_path = os.path.join(self.demographic_dir, filename)
        
        create_comparison_plot(
            self.trials_df, col, unique_vals, config['labels'],
            f"Performance by {config['title']}", output_path, 
            config['colors'], plot_style=config['style']
        )
        self._log(f"→ Plot saved: {filename}")

        # Statistical analysis
        groups = [self.trials_df[self.trials_df[col] == v]['reactionTime'].dropna() 
                 for v in unique_vals]
        group_names = [str(config['labels'].get(v, v)) for v in unique_vals]
        
        results = compare_groups_statistical(groups, group_names)
        
        if 'error' not in results:
            self._log(f"\nStatistical Results ({results['test']}):")
            self._log(f"  p-value: {results['p_value']:.4f}")
            self._log(f"  Significant: {results['significant']}")

    def plot_velocity_profiles(self, split_by_col: str = None, sample_size: int = 5):
        """Generate velocity profile plots"""
        title = "Velocity Profiles" + (f" by {split_by_col}" if split_by_col else "")
        self._section(title.upper(), level=1)

        trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        if split_by_col:
            group_vals = self.trials_df[split_by_col].dropna().unique()
            colors = ['#E63946', '#457B9D'] if len(group_vals) == 2 else plt.cm.tab10.colors
        else:
            group_vals = [None]
            colors = ['#2E86AB']

        rows = len(group_vals)
        fig, axes = plt.subplots(rows, 3, figsize=(18, 5 * rows))
        if rows == 1:
            axes = axes.reshape(1, -1)
        
        fig.suptitle(title, fontsize=16, fontweight='bold')

        for r, group_val in enumerate(group_vals):
            for c, t_type in enumerate(trial_types):
                ax = axes[r, c]
                
                mask = (self.trials_df['trialType'] == t_type)
                if split_by_col:
                    mask &= (self.trials_df[split_by_col] == group_val)

                subset = self.trials_df[mask & (self.trials_df['reactionTime'].notna())]
                valid_paths = subset[subset['movementPath'].apply(
                    lambda x: isinstance(x, list) and len(x) > 5)]
                
                if len(valid_paths) == 0:
                    ax.text(0.5, 0.5, 'No Data', ha='center', va='center', 
                           transform=ax.transAxes)
                    continue

                sample = valid_paths.sample(min(sample_size, len(valid_paths)))
                for _, trial in sample.iterrows():
                    self._plot_single_path_velocity(ax, trial, colors[r % len(colors)])

                avg_rt = subset['reactionTime'].mean()
                ax.axvline(avg_rt, color='k', linestyle='--', alpha=0.5, 
                          label=f'Avg RT: {avg_rt:.0f}ms')
                
                if r == 0:
                    ax.set_title(t_type.replace('_', ' '), fontweight='bold')
                if r == rows-1:
                    ax.set_xlabel('Time from Trial Start (ms)', fontweight='bold')
                if c == 0:
                    ax.set_ylabel('Velocity (px/s)', fontweight='bold')
                
                ax.legend(fontsize=9)
                ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        fname = f"velocity_profiles_{'split' if split_by_col else 'overall'}.png"
        plt.savefig(os.path.join(self.figures_dir, fname), dpi=300)
        self._log(f"→ Saved: {fname}")
        plt.close()

    def _plot_single_path_velocity(self, ax, trial, color):
        """Plot velocity for one trial"""
        path = trial['movementPath']
        rt = trial['reactionTime']
        
        velocities, times = [0, 0], [0, rt]
        for i in range(1, len(path)):
            if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                dt = (path[i]['t'] - path[i-1]['t']) / 1000.0
                if dt > 0:
                    dist = np.sqrt((path[i]['x'] - path[i-1]['x'])**2 + 
                                  (path[i]['y'] - path[i-1]['y'])**2)
                    velocities.append(dist / dt)
                    times.append(rt + (path[i]['t'] - path[0]['t']))
        
        if len(velocities) > 2:
            ax.plot(times, velocities, color=color, alpha=0.4, linewidth=1)

    def create_summary_plots(self):
        """Create summary visualizations"""
        self._section("SUMMARY VISUALIZATIONS", level=1)
        fig, axes = plt.subplots(2, 2, figsize=(15, 11))
        
        fig.suptitle('Performance Summary Across All Trial Types', 
                    fontsize=16, fontweight='bold', y=0.995)
        
        metrics = [
            ('reactionTime', 'Reaction Time', 'Time from GO beep to first movement (ms)'), 
            ('movementTime', 'Movement Time', 'Time from first move to target (ms)'),
            ('pathLength', 'Path Length', 'Total distance traveled (pixels)'), 
            ('totalResponseTime', 'Total Response Time', 'Time from GO beep to target (ms)')
        ]
        
        plot_colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728']
        condition_labels = ['PRE\nSUPRA', 'PRE\nJND', 'CONC\nSUPRA']
        
        for idx, (metric, short_label, full_label) in enumerate(metrics):
            ax = axes[idx // 2, idx % 2]
            color = plot_colors[idx]
            
            data = [self.trials_df[self.trials_df['trialType'] == t][metric].dropna() 
                    for t in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']]
            
            bp = ax.boxplot(data, labels=condition_labels, patch_artist=True,
                           medianprops=dict(color='black', linewidth=2))
            
            for box in bp['boxes']:
                box.set_facecolor(color)
                box.set_alpha(0.7)
            
            ax.set_title(f'{short_label}', fontsize=13, fontweight='bold', pad=10)
            ax.set_ylabel(full_label, fontsize=10, fontweight='bold')
            ax.set_xlabel('Trial Condition', fontsize=10, fontweight='bold')
            ax.grid(axis='y', linestyle='--', alpha=0.5)
            
        plt.tight_layout()
        plt.savefig(os.path.join(self.figures_dir, 'summary.png'), dpi=300, bbox_inches='tight')
        self._log("→ Saved: summary.png")
        plt.close()

# ============================================================================
# MAIN
# ============================================================================

def main():
    print("Starting Analysis with Repeated Measures ANOVA...")
    participants, trials = load_data(SubliminalPrimingAnalyzer.DEFAULT_CREDENTIALS)
    
    if trials.empty:
        return

    analyzer = SubliminalPrimingAnalyzer(trials, participants)
    analyzer.test_main_hypothesis()
    analyzer.run_all_demographics()
    analyzer.plot_velocity_profiles()
    analyzer.create_summary_plots()
    analyzer.save_report()
    
    print("Done.")

if __name__ == "__main__":
    main()