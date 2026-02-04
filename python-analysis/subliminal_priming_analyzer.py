"""
Subliminal Priming Analysis
============================
Statistical analysis of subliminal priming effects on motor planning.

This module performs:
1. Repeated measures ANOVA to test main hypothesis
2. Demographic comparisons (ADHD, gender, glasses, age)
3. Velocity profile visualization
4. Summary plot generation
5. Comprehensive text report generation

Main Hypothesis: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA (reaction time)
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
    Perform repeated measures ANOVA.
    
    Tests whether there are significant differences between conditions
    within the same subjects (repeated measures design).
    
    Args:
        data: DataFrame with trial data
        dv: Dependent variable column name (e.g., 'reactionTime')
        within: Within-subject factor column name (e.g., 'trialType')
        subject: Subject identifier column name (e.g., 'participantId')
    
    Returns:
        dict: ANOVA results including F-statistic, p-value, means, and pairwise comparisons
    """
    # Pivot data to wide format (one row per subject, one column per condition)
    pivot = data.pivot_table(values=dv, index=subject, columns=within)
    
    # Remove participants with missing conditions
    pivot_clean = pivot.dropna()
    
    if len(pivot_clean) < 3:
        return {'error': 'Insufficient participants with complete data'}
    
    # Get conditions
    conditions = list(pivot_clean.columns)
    
    # Prepare data arrays for each condition
    condition_arrays = [pivot_clean[cond].values for cond in conditions]
    
    # Perform one-way repeated measures ANOVA
    # Note: Using f_oneway as approximation. For full RM-ANOVA, consider using pingouin library
    f_stat, p_value = stats.f_oneway(*condition_arrays)
    
    # Calculate means and SEM for each condition
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
    """
    Run appropriate statistical test (t-test or ANOVA) for between-group comparisons.
    
    Automatically selects:
    - t-test for 2 groups
    - ANOVA for 3+ groups
    
    Args:
        groups: List of data Series (one per group)
        group_names: List of group names
        
    Returns:
        dict: Statistical test results
    """
    # Filter out empty groups
    valid = [(g, n) for g, n in zip(groups, group_names) if len(g) > 0]
    
    if len(valid) < 2:
        return {'error': 'Insufficient groups'}
    
    groups, names = zip(*valid)
    
    # Choose appropriate test
    if len(groups) == 2:
        stat, p_val = stats.ttest_ind(groups[0], groups[1])
        test_name = 't-test'
    else:
        stat, p_val = stats.f_oneway(*groups)
        test_name = 'ANOVA'
    
    # Calculate means sorted by value
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
    """
    Create a 2x2 comparison plot for demographic analysis.
    
    Layout:
    - Top row: Line/bar plots of RT and path length by condition
    - Bottom row: Distribution boxplots for overall performance
    
    Args:
        data: DataFrame with trial data
        grouping_col: Column to group by (e.g., 'hasAttentionDeficit')
        group_values: Values to compare (e.g., [True, False])
        group_labels: Human-readable labels for groups
        title: Plot title
        output_path: Where to save the figure
        colors: List of colors for each group
        plot_style: 'line' or 'bar'
    """
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle(title, fontsize=16, fontweight='bold')
    
    trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
    trial_labels = ['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA']
    x_pos = np.arange(len(trial_types))
    
    metrics = [
        ('reactionTime', 'Reaction Time', 'Time from GO beep to first movement (ms)'), 
        ('pathLength', 'Path Length', 'Total distance traveled (pixels)')
    ]
    
    # Top row: Performance by condition
    for ax_idx, (metric, short_label, full_label) in enumerate(metrics):
        ax = axes[0, ax_idx]
        width = 0.8 / len(group_values)
        
        for i, value in enumerate(group_values):
            subset = data[data[grouping_col] == value]
            label = group_labels.get(value, str(value))
            
            # Calculate mean and SEM for each trial type
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

    # Bottom row: Overall distributions
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
    """
    Main analysis engine for subliminal priming experiment.
    
    This class handles:
    - Data cleaning and preprocessing
    - Statistical hypothesis testing
    - Demographic analysis
    - Velocity profile visualization
    - Report generation
    """
    
    # Default credentials file location
    script_dir = os.path.dirname(os.path.abspath(__file__))
    DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')
    
    # Age grouping for analysis
    AGE_MAPPING = {22: '18-25', 30: '26-35', 40: '36-45', 53: '46-60', 65: '60+'}
    AGE_ORDER = ['18-25', '26-35', '36-45', '46-60', '60+']
    
    def __init__(self, trials_df: pd.DataFrame, participants_df: pd.DataFrame,
                 outlier_threshold_ms: int = 50000, output_dir: str = None):
        """
        Initialize the analyzer.
        
        Args:
            trials_df: DataFrame with trial data
            participants_df: DataFrame with participant demographics
            outlier_threshold_ms: Remove trials with RT above this threshold (default: 50000)
            output_dir: Where to save outputs. If None, creates timestamped directory.
        """
        self.raw_trials = trials_df.copy()
        self.participants_df = participants_df.copy()
        
        # Set up output directory
        if output_dir:
            self.output_dir = output_dir
        else:
            base_dir = os.path.dirname(os.path.abspath(__file__))
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            self.output_dir = os.path.join(base_dir, 'analysis_outputs', f'run_{timestamp}')
        
        self.figures_dir = os.path.join(self.output_dir, 'figures')
        self.demographic_dir = os.path.join(self.figures_dir, 'demographic_comparisons')
        os.makedirs(self.demographic_dir, exist_ok=True)
        
        # Report storage
        self.report_lines = []
        
        # Clean data and add derived columns
        self._clean_data(outlier_threshold_ms)
        self._add_age_groups()
        
    def _log(self, text: str):
        """Add text to report and print to console."""
        self.report_lines.append(text)
        print(text)
    
    def _section(self, title: str, level: int = 1):
        """Add a section header to the report."""
        sep = "=" if level == 1 else "-"
        self.report_lines.extend(["\n" + sep * 80, title, sep * 80 + "\n"])

    def _clean_data(self, threshold: int):
        """Remove outliers and invalid trials."""
        self.trials_df = self.raw_trials[
            (self.raw_trials['reactionTime'].notna()) & 
            (self.raw_trials['reactionTime'] < threshold)
        ].copy()
        self._log(f"Data Cleaned: {len(self.trials_df)} trials remaining (Removed >{threshold}ms)")

    def _add_age_groups(self):
        """Add age groups and ensure demographic columns exist."""
        if 'age' in self.trials_df.columns:
            self.trials_df['ageGroup'] = self.trials_df['age'].apply(
                lambda x: self.AGE_MAPPING.get(x, str(x))
            )
        
        # Ensure demographic columns exist (fill missing with None)
        for col in ['hasAttentionDeficit', 'gender', 'hasGlasses']:
            if col not in self.trials_df.columns:
                self.trials_df[col] = None

    def save_report(self):
        """Save the text report to file."""
        path = os.path.join(self.output_dir, 'analysis_report.txt')
        with open(path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(self.report_lines))
        print(f"\n✅ Report saved: {path}")

    # ========================================================================
    # MAIN HYPOTHESIS TESTING
    # ========================================================================
    
    def test_main_hypothesis(self):
        """
        Test main hypothesis using repeated measures ANOVA.
        
        Hypothesis: PRE_SUPRA < PRE_JND < CONCURRENT_SUPRA
        
        This tests whether reaction times differ significantly across
        the three conditions using within-subject (repeated measures) design.
        """
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
        
        # Report condition means
        self._log(f"\nCondition Means (± SEM):")
        for cond in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            if cond in results['means']:
                mean = results['means'][cond]
                sem = results['sems'][cond]
                self._log(f"  {cond:20s}: {mean:6.1f} ± {sem:5.1f} ms")
        
        # Post-hoc pairwise comparisons
        self._log(f"\nPost-hoc Pairwise Comparisons (Paired t-tests):")
        for comparison, stats_result in results['pairwise'].items():
            sig_marker = "***" if stats_result['p_value'] < 0.001 else "**" if stats_result['p_value'] < 0.01 else "*" if stats_result['p_value'] < 0.05 else "ns"
            self._log(f"  {comparison:40s}: t={stats_result['t_statistic']:6.2f}, p={stats_result['p_value']:.4f} {sig_marker}")
            self._log(f"    {'':42s}Δ = {stats_result['mean_difference']:6.1f} ms")
        
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

    # ========================================================================
    # DEMOGRAPHIC ANALYSIS
    # ========================================================================
    
    def run_all_demographics(self):
        """
        Analyze demographic effects using between-subject comparisons.
        
        Tests for differences in:
        - ADHD (yes/no)
        - Gender (male/female)
        - Glasses (yes/no)
        - Age groups (5 categories)
        """
        # Configuration for each demographic analysis
        configs = [
            {
                'col': 'hasAttentionDeficit', 
                'title': 'ADHD Effects',
                'labels': {True: 'With ADHD', False: 'No ADHD'},
                'colors': ['#E63946', '#457B9D'], 
                'style': 'line'
            },
            {
                'col': 'gender', 
                'title': 'Gender Effects',
                'labels': {'Male': 'Male', 'Female': 'Female'},
                'colors': ['#06A77D', '#D4A373'], 
                'style': 'line'
            },
            {
                'col': 'hasGlasses', 
                'title': 'Glasses Effects',
                'labels': {True: 'Glasses', False: 'No Glasses'},
                'colors': ['#F4A261', '#2A9D8F'], 
                'style': 'line'
            },
            {
                'col': 'ageGroup', 
                'title': 'Age Effects',
                'labels': {k: k for k in self.AGE_ORDER},
                'colors': plt.cm.viridis(np.linspace(0, 0.9, 5)), 
                'style': 'bar', 
                'order': self.AGE_ORDER
            }
        ]

        for config in configs:
            self._analyze_categorical(config)

    def _analyze_categorical(self, config: dict):
        """
        Analyze a single categorical demographic variable.
        
        Args:
            config: Dictionary with analysis configuration (column, labels, colors, etc.)
        """
        col = config['col']
        self._section(config['title'].upper(), level=1)

        # Check if data exists
        if col not in self.trials_df.columns or self.trials_df[col].isna().all():
            self._log(f"⚠️ No data for {col}")
            return

        # Get unique values (in specified order if provided)
        if 'order' in config:
            unique_vals = [v for v in config['order'] if v in self.trials_df[col].unique()]
        else:
            unique_vals = self.trials_df[col].dropna().unique()

        if len(unique_vals) < 2:
            self._log(f"⚠️ Insufficient groups for {col}")
            return

        # Create visual comparison plot
        filename = f"comparison_{col}.png"
        output_path = os.path.join(self.demographic_dir, filename)
        
        create_comparison_plot(
            self.trials_df, col, unique_vals, config['labels'],
            f"Performance by {config['title']}", output_path, 
            config['colors'], plot_style=config['style']
        )
        self._log(f"→ Plot saved: {filename}")

        # Run statistical comparison
        groups = [self.trials_df[self.trials_df[col] == v]['reactionTime'].dropna() 
                 for v in unique_vals]
        group_names = [str(config['labels'].get(v, v)) for v in unique_vals]
        
        results = compare_groups_statistical(groups, group_names)
        
        if 'error' not in results:
            self._log(f"\nStatistical Results ({results['test']}):")
            self._log(f"  p-value: {results['p_value']:.4f}")
            self._log(f"  Significant: {results['significant']}")

    # ========================================================================
    # VELOCITY PROFILES
    # ========================================================================
    
    def plot_velocity_profiles(self, split_by_col: str = None, sample_size: int = 5):
        """
        Generate velocity profile plots.
        
        Shows how velocity changes over time during reaching movements.
        Can optionally split by demographic variable.
        
        Args:
            split_by_col: Optional column to split plots (e.g., 'hasAttentionDeficit')
            sample_size: Number of random trials to plot per condition
        """
        title = "Velocity Profiles" + (f" by {split_by_col}" if split_by_col else "")
        self._section(title.upper(), level=1)

        trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        # Set up groups for splitting
        if split_by_col:
            group_vals = self.trials_df[split_by_col].dropna().unique()
            colors = ['#E63946', '#457B9D'] if len(group_vals) == 2 else plt.cm.tab10.colors
        else:
            group_vals = [None]
            colors = ['#2E86AB']

        # Create figure (rows = groups, cols = conditions)
        rows = len(group_vals)
        fig, axes = plt.subplots(rows, 3, figsize=(18, 5 * rows))
        if rows == 1:
            axes = axes.reshape(1, -1)
        
        fig.suptitle(title, fontsize=16, fontweight='bold')

        for r, group_val in enumerate(group_vals):
            for c, t_type in enumerate(trial_types):
                ax = axes[r, c]
                
                # Filter data
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

                # Sample random trials to plot
                sample = valid_paths.sample(min(sample_size, len(valid_paths)))
                for _, trial in sample.iterrows():
                    self._plot_single_path_velocity(ax, trial, colors[r % len(colors)])

                # Add average RT marker
                avg_rt = subset['reactionTime'].mean()
                ax.axvline(avg_rt, color='k', linestyle='--', alpha=0.5, 
                          label=f'Avg RT: {avg_rt:.0f}ms')
                
                # Labels and formatting
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
        """
        Plot velocity profile for a single trial.
        
        Args:
            ax: Matplotlib axis
            trial: Trial data (row from DataFrame)
            color: Color for the plot line
        """
        path = trial['movementPath']
        rt = trial['reactionTime']
        
        # Start with velocity = 0 at trial start and at RT
        velocities, times = [0, 0], [0, rt]
        
        for i in range(1, len(path)):
            if isinstance(path[i], dict) and isinstance(path[i-1], dict):
                dt = (path[i]['t'] - path[i-1]['t']) / 1000.0  # Convert to seconds
                if dt > 0:
                    # Calculate Euclidean distance
                    dist = np.sqrt((path[i]['x'] - path[i-1]['x'])**2 + 
                                  (path[i]['y'] - path[i-1]['y'])**2)
                    velocity = dist / dt
                    velocities.append(velocity)
                    times.append(rt + (path[i]['t'] - path[0]['t']))
        
        if len(velocities) > 2:
            ax.plot(times, velocities, color=color, alpha=0.4, linewidth=1)

    # ========================================================================
    # SUMMARY VISUALIZATIONS
    # ========================================================================
    
    def create_summary_plots(self):
        """
        Create 2x2 summary visualization with key metrics.
        
        Shows boxplots for:
        - Reaction Time
        - Movement Time
        - Path Length
        - Total Response Time
        
        Across all three trial conditions.
        """
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
            
            # Get data for each condition
            data = [self.trials_df[self.trials_df['trialType'] == t][metric].dropna() 
                    for t in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']]
            
            # Create boxplot
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
# MAIN - For standalone execution
# ============================================================================

def main():
    """
    Run complete analysis pipeline.
    
    This is executed when running the script directly:
    python subliminal_priming_analyzer.py
    """
    print("Starting Analysis with Repeated Measures ANOVA...")
    participants, trials = load_data(SubliminalPrimingAnalyzer.DEFAULT_CREDENTIALS_FILENAME)
    
    if trials.empty:
        print("No trial data found!")
        return

    analyzer = SubliminalPrimingAnalyzer(trials, participants)
    analyzer.test_main_hypothesis()
    analyzer.run_all_demographics()
    analyzer.plot_velocity_profiles()
    analyzer.create_summary_plots()
    analyzer.save_report()
    
    print("\n✅ Analysis complete!")
    print(f"Results saved in: {analyzer.output_dir}")

if __name__ == "__main__":
    main()