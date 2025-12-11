"""
Enhanced Subliminal Priming Analysis with Demographic Comparisons
Analyzes how demographics (ADHD, glasses, gender) affect motor planning performance
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats
from firebase_connector import load_data
import warnings
import os
from datetime import datetime
warnings.filterwarnings('ignore')

class SubliminalPrimingAnalyzer:
    """Analyze subliminal priming effects on motor planning"""
    DEFAULT_CREDENTIALS_FILENAME = 'serviceAccountKey.json'

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
        self.demographic_dir = os.path.join(self.figures_dir, 'demographic_comparisons')
        os.makedirs(self.demographic_dir, exist_ok=True)
        
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

        # Add age group column
        self._add_age_group_column()
        
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
        
        self._add_report_section(f"HYPOTHESIS TESTING - {subset_name}", level=2)
        
        # Get data for each condition
        pre_supra = data_subset[data_subset['trialType'] == 'PRE_SUPRA']
        pre_jnd = data_subset[data_subset['trialType'] == 'PRE_JND']
        concurrent = data_subset[data_subset['trialType'] == 'CONCURRENT_SUPRA']
        
        self._add_report_line(f"\nSample sizes:")
        self._add_report_line(f"  PRE_SUPRA: {len(pre_supra)} trials")
        self._add_report_line(f"  PRE_JND: {len(pre_jnd)} trials")
        self._add_report_line(f"  CONCURRENT_SUPRA: {len(concurrent)} trials")
        
        if len(pre_supra) == 0 or len(pre_jnd) == 0 or len(concurrent) == 0:
            self._add_report_line("\n⚠️ Insufficient data for this subset")
            return
        
        # === REACTION TIME ===
        self._add_report_line(f"\n1. REACTION TIME (GO beep → first movement)")
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
        
        # === PATH EFFICIENCY ===
        self._add_report_line(f"\n2. PATH EFFICIENCY (directness of movement)")
        
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
    
    def analyze_demographic_effects(self):
        """Analyze how demographics affect motor planning performance"""
        self._add_report_section("DEMOGRAPHIC EFFECTS ON MOTOR PLANNING", level=1)
        
        demographics = [
            ('hasAttentionDeficit', {True: 'With ADHD', False: 'Without ADHD'}, 'ADHD'),
            ('hasGlasses', {True: 'With Glasses', False: 'Without Glasses'}, 'Glasses'),
            ('gender', {'Male': 'Male', 'Female': 'Female'}, 'Gender')
        ]
        
        for demo_col, value_map, demo_name in demographics:
            if demo_col not in self.trials_df.columns or self.trials_df[demo_col].isna().all():
                self._add_report_line(f"\n⚠️ Skipping {demo_name} - no data available")
                continue
            
            self._analyze_single_demographic(demo_col, value_map, demo_name)
    
    def _analyze_single_demographic(self, demo_col, value_map, demo_name):
        """Analyze effect of a single demographic variable"""
        self._add_report_section(f"EFFECT OF {demo_name.upper()}", level=2)
        
        # Get unique values (excluding NaN)
        unique_values = self.trials_df[demo_col].dropna().unique()
        
        if len(unique_values) < 2:
            self._add_report_line(f"⚠️ Only one group found for {demo_name}, cannot compare")
            return
        
        # Create comparison plots
        fig, axes = plt.subplots(2, 2, figsize=(16, 12))
        fig.suptitle(f'Performance Comparison: {demo_name}', fontsize=16, fontweight='bold')
        
        # Plot 1: Reaction Time by Trial Type and Demographic
        ax = axes[0, 0]
        for value in unique_values:
            subset = self.trials_df[self.trials_df[demo_col] == value]
            label = value_map.get(value, str(value))
            
            rt_means = []
            rt_sems = []
            trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
            
            for tt in trial_types:
                rt_data = subset[subset['trialType'] == tt]['reactionTime'].dropna()
                rt_means.append(rt_data.mean() if len(rt_data) > 0 else 0)
                rt_sems.append(rt_data.sem() if len(rt_data) > 0 else 0)
            
            x_pos = np.arange(len(trial_types))
            ax.errorbar(x_pos, rt_means, yerr=rt_sems, marker='o', label=label, 
                       linewidth=2, markersize=8, capsize=5)
        
        ax.set_xticks(x_pos)
        ax.set_xticklabels(['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA'])
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('A. Reaction Time by Condition', fontsize=13, fontweight='bold')
        ax.legend()
        ax.grid(True, alpha=0.3)
        
        # Plot 2: Path Efficiency by Trial Type and Demographic
        ax = axes[0, 1]
        for value in unique_values:
            subset = self.trials_df[self.trials_df[demo_col] == value]
            label = value_map.get(value, str(value))
            
            eff_means = []
            eff_sems = []
            
            for tt in trial_types:
                eff_data = subset[subset['trialType'] == tt]['pathEfficiency'].dropna()
                eff_means.append(eff_data.mean() if len(eff_data) > 0 else 0)
                eff_sems.append(eff_data.sem() if len(eff_data) > 0 else 0)
            
            x_pos = np.arange(len(trial_types))
            ax.errorbar(x_pos, eff_means, yerr=eff_sems, marker='s', label=label,
                       linewidth=2, markersize=8, capsize=5)
        
        ax.set_xticks(x_pos)
        ax.set_xticklabels(['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA'])
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('B. Path Efficiency by Condition', fontsize=13, fontweight='bold')
        ax.legend()
        ax.grid(True, alpha=0.3)
        
        # Plot 3: Overall Reaction Time Distribution
        ax = axes[1, 0]
        rt_data_by_group = []
        labels = []
        for value in unique_values:
            subset = self.trials_df[self.trials_df[demo_col] == value]
            rt_data_by_group.append(subset['reactionTime'].dropna())
            labels.append(value_map.get(value, str(value)))
        
        bp = ax.boxplot(rt_data_by_group, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], ['lightblue', 'lightcoral']):
            patch.set_facecolor(color)
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('C. Overall Reaction Time Distribution', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 4: Overall Path Efficiency Distribution
        ax = axes[1, 1]
        eff_data_by_group = []
        for value in unique_values:
            subset = self.trials_df[self.trials_df[demo_col] == value]
            eff_data_by_group.append(subset['pathEfficiency'].dropna())
        
        bp = ax.boxplot(eff_data_by_group, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], ['lightgreen', 'lightyellow']):
            patch.set_facecolor(color)
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('D. Overall Path Efficiency Distribution', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        plt.tight_layout()
        filename = os.path.join(self.demographic_dir, f'comparison_{demo_name.lower()}.png')
        plt.savefig(filename, dpi=300)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()
        
        # Statistical comparison in report
        self._add_report_line(f"\nStatistical Comparison:")
        
        for value in unique_values:
            subset = self.trials_df[self.trials_df[demo_col] == value]
            label = value_map.get(value, str(value))
            
            self._add_report_line(f"\n{label} (n={len(subset)} trials):")
            
            for tt in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
                tt_data = subset[subset['trialType'] == tt]
                rt_mean = tt_data['reactionTime'].mean()
                rt_std = tt_data['reactionTime'].std()
                eff_mean = tt_data['pathEfficiency'].mean()
                
                self._add_report_line(f"  {tt}:")
                self._add_report_line(f"    Reaction Time: {rt_mean:.1f} ms (SD={rt_std:.1f})")
                self._add_report_line(f"    Path Efficiency: {eff_mean:.3f}")
        
        # T-test between groups (for binary demographics)
        if len(unique_values) == 2:
            values_list = list(unique_values)
            group1 = self.trials_df[self.trials_df[demo_col] == values_list[0]]
            group2 = self.trials_df[self.trials_df[demo_col] == values_list[1]]
            
            rt1 = group1['reactionTime'].dropna()
            rt2 = group2['reactionTime'].dropna()
            
            if len(rt1) > 0 and len(rt2) > 0:
                t_stat, p_val = stats.ttest_ind(rt1, rt2)
                self._add_report_line(f"\nOverall Reaction Time Comparison:")
                self._add_report_line(f"  {value_map.get(values_list[0], str(values_list[0]))}: {rt1.mean():.1f} ms")
                self._add_report_line(f"  {value_map.get(values_list[1], str(values_list[1]))}: {rt2.mean():.1f} ms")
                self._add_report_line(f"  t-test: t={t_stat:.3f}, p={p_val:.4f}")
                if p_val < 0.05:
                    diff = abs(rt1.mean() - rt2.mean())
                    faster_group = value_map.get(values_list[0] if rt1.mean() < rt2.mean() else values_list[1])
                    self._add_report_line(f"  ✓ SIGNIFICANT: {faster_group} are {diff:.1f}ms faster on average")
                else:
                    self._add_report_line(f"  ✗ No significant difference")
    
    def analyze_by_target(self):
        """Analyze data broken down by target"""
        self._add_report_section("ANALYSIS BY TARGET LOCATION", level=1)
        
        unique_targets = sorted(self.trials_df['targetIndex'].dropna().unique())
        
        if len(unique_targets) == 0:
            self._add_report_line("⚠️ No target data available")
            return
        
        # Create target comparison plot
        fig, axes = plt.subplots(2, 2, figsize=(16, 12))
        fig.suptitle('Performance by Target Location', fontsize=16, fontweight='bold')
        
        # Plot 1: Reaction Time by Target
        ax = axes[0, 0]
        rt_by_target = []
        labels = []
        for target_id in unique_targets:
            subset = self.trials_df[self.trials_df['targetIndex'] == target_id]
            rt_by_target.append(subset['reactionTime'].dropna())
            labels.append(f'Target {int(target_id)}')
        
        bp = ax.boxplot(rt_by_target, labels=labels, patch_artist=True)
        colors = ['lightblue', 'lightgreen', 'lightcoral', 'lightyellow']
        for patch, color in zip(bp['boxes'], colors[:len(bp['boxes'])]):
            patch.set_facecolor(color)
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('A. Reaction Time by Target', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 2: Path Efficiency by Target
        ax = axes[0, 1]
        eff_by_target = []
        for target_id in unique_targets:
            subset = self.trials_df[self.trials_df['targetIndex'] == target_id]
            eff_by_target.append(subset['pathEfficiency'].dropna())
        
        bp = ax.boxplot(eff_by_target, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors[:len(bp['boxes'])]):
            patch.set_facecolor(color)
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('B. Path Efficiency by Target', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 3: Trial Type effects per Target
        ax = axes[1, 0]
        trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        x = np.arange(len(unique_targets))
        width = 0.25
        
        for i, tt in enumerate(trial_types):
            means = []
            for target_id in unique_targets:
                subset = self.trials_df[(self.trials_df['targetIndex'] == target_id) & 
                                       (self.trials_df['trialType'] == tt)]
                means.append(subset['reactionTime'].mean() if len(subset) > 0 else 0)
            
            ax.bar(x + i*width, means, width, label=tt.replace('_', ' '))
        
        ax.set_xlabel('Target', fontsize=12)
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('C. Reaction Time by Target and Condition', fontsize=13, fontweight='bold')
        ax.set_xticks(x + width)
        ax.set_xticklabels([f'T{int(t)}' for t in unique_targets])
        ax.legend()
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 4: Path Length by Target
        ax = axes[1, 1]
        length_by_target = []
        for target_id in unique_targets:
            subset = self.trials_df[self.trials_df['targetIndex'] == target_id]
            length_by_target.append(subset['pathLength'].dropna())
        
        bp = ax.boxplot(length_by_target, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors[:len(bp['boxes'])]):
            patch.set_facecolor(color)
        ax.set_ylabel('Path Length (pixels)', fontsize=12)
        ax.set_title('D. Path Length by Target', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        plt.tight_layout()
        filename = os.path.join(self.figures_dir, 'target_comparison.png')
        plt.savefig(filename, dpi=300)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()
        
        # Report statistics
        self._add_report_line(f"\nTarget Statistics:")
        for target_id in unique_targets:
            subset = self.trials_df[self.trials_df['targetIndex'] == target_id]
            self._add_report_line(f"\nTarget {int(target_id)} (n={len(subset)} trials):")
            self._add_report_line(f"  Reaction Time: {subset['reactionTime'].mean():.1f} ms (SD={subset['reactionTime'].std():.1f})")
            self._add_report_line(f"  Path Efficiency: {subset['pathEfficiency'].mean():.3f}")
            self._add_report_line(f"  Path Length: {subset['pathLength'].mean():.1f} pixels")
        
        # Test for spatial bias
        rt_by_target_for_anova = [self.trials_df[self.trials_df['targetIndex'] == t]['reactionTime'].dropna() 
                                   for t in unique_targets]
        if all(len(x) > 0 for x in rt_by_target_for_anova):
            f_stat, p_val = stats.f_oneway(*rt_by_target_for_anova)
            self._add_report_line(f"\nSpatial Bias Test (ANOVA):")
            self._add_report_line(f"  F={f_stat:.3f}, p={p_val:.4f}")
            if p_val < 0.05:
                self._add_report_line(f"  ✓ SIGNIFICANT spatial bias detected!")
            else:
                self._add_report_line(f"  ✗ No significant spatial bias")
    
    def _map_age_to_range(self, age):
        """Map stored age values to display age ranges"""
        age_mapping = {
            22: '18-25',
            30: '26-35',
            40: '36-45',
            53: '46-60',
            65: '60+'
        }
        return age_mapping.get(age, str(age))

    def _add_age_group_column(self):
        """Add age group column to trials dataframe"""
        if 'age' in self.trials_df.columns:
            self.trials_df['ageGroup'] = self.trials_df['age'].apply(self._map_age_to_range)
            self._add_report_line(f"  Age groups: {self.trials_df['ageGroup'].value_counts().to_dict()}")
        else:
            self._add_report_line(f"  Age data not available")
            self.trials_df['ageGroup'] = None

    def analyze_age_effects(self):
        """Analyze how age groups affect motor planning performance"""
        self._add_report_section("AGE GROUP EFFECTS ON MOTOR PLANNING", level=1)
        
        if 'ageGroup' not in self.trials_df.columns or self.trials_df['ageGroup'].isna().all():
            self._add_report_line("⚠️ No age group data available")
            return
        
        age_groups = self.trials_df['ageGroup'].dropna().unique()
        
        if len(age_groups) < 2:
            self._add_report_line(f"⚠️ Only one age group found, cannot compare")
            return
        
        # Sort age groups in logical order
        age_order = ['18-25', '26-35', '36-45', '46-60', '60+']
        age_groups = [ag for ag in age_order if ag in age_groups]
        
        # Create comparison plots
        fig, axes = plt.subplots(2, 2, figsize=(16, 12))
        fig.suptitle('Performance Comparison: Age Groups', fontsize=16, fontweight='bold')
        
        # Color palette for age groups
        colors = plt.cm.viridis(np.linspace(0, 0.9, len(age_groups)))
        
        # Plot 1: Reaction Time by Trial Type and Age Group
        ax = axes[0, 0]
        trial_types = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        x_pos = np.arange(len(trial_types))
        width = 0.8 / len(age_groups)
        
        for i, age_group in enumerate(age_groups):
            subset = self.trials_df[self.trials_df['ageGroup'] == age_group]
            rt_means = []
            rt_sems = []
            
            for tt in trial_types:
                rt_data = subset[subset['trialType'] == tt]['reactionTime'].dropna()
                rt_means.append(rt_data.mean() if len(rt_data) > 0 else 0)
                rt_sems.append(rt_data.sem() if len(rt_data) > 0 else 0)
            
            offset = (i - len(age_groups)/2 + 0.5) * width
            ax.bar(x_pos + offset, rt_means, width, yerr=rt_sems, 
                label=age_group, color=colors[i], capsize=5)
        
        ax.set_xticks(x_pos)
        ax.set_xticklabels(['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA'])
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('A. Reaction Time by Condition and Age', fontsize=13, fontweight='bold')
        ax.legend()
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 2: Path Efficiency by Trial Type and Age Group
        ax = axes[0, 1]
        for i, age_group in enumerate(age_groups):
            subset = self.trials_df[self.trials_df['ageGroup'] == age_group]
            eff_means = []
            eff_sems = []
            
            for tt in trial_types:
                eff_data = subset[subset['trialType'] == tt]['pathEfficiency'].dropna()
                eff_means.append(eff_data.mean() if len(eff_data) > 0 else 0)
                eff_sems.append(eff_data.sem() if len(eff_data) > 0 else 0)
            
            offset = (i - len(age_groups)/2 + 0.5) * width
            ax.bar(x_pos + offset, eff_means, width, yerr=eff_sems,
                label=age_group, color=colors[i], capsize=5)
        
        ax.set_xticks(x_pos)
        ax.set_xticklabels(['PRE\nSUPRA', 'PRE\nJND', 'CONCURRENT\nSUPRA'])
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('B. Path Efficiency by Condition and Age', fontsize=13, fontweight='bold')
        ax.legend()
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 3: Overall Reaction Time Distribution by Age
        ax = axes[1, 0]
        rt_by_age = []
        labels = []
        for age_group in age_groups:
            subset = self.trials_df[self.trials_df['ageGroup'] == age_group]
            rt_by_age.append(subset['reactionTime'].dropna())
            labels.append(age_group)
        
        bp = ax.boxplot(rt_by_age, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
        ax.set_xlabel('Age Group', fontsize=12)
        ax.set_ylabel('Reaction Time (ms)', fontsize=12)
        ax.set_title('C. Reaction Time Distribution by Age', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        # Plot 4: Overall Path Efficiency Distribution by Age
        ax = axes[1, 1]
        eff_by_age = []
        for age_group in age_groups:
            subset = self.trials_df[self.trials_df['ageGroup'] == age_group]
            eff_by_age.append(subset['pathEfficiency'].dropna())
        
        bp = ax.boxplot(eff_by_age, labels=labels, patch_artist=True)
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
        ax.set_xlabel('Age Group', fontsize=12)
        ax.set_ylabel('Path Efficiency', fontsize=12)
        ax.set_title('D. Path Efficiency Distribution by Age', fontsize=13, fontweight='bold')
        ax.grid(True, alpha=0.3, axis='y')
        
        plt.tight_layout()
        filename = os.path.join(self.demographic_dir, 'comparison_age_groups.png')
        plt.savefig(filename, dpi=300)
        self._add_report_line(f"  → Saved: {filename}")
        plt.close()
        
        # Statistical comparison in report
        self._add_report_line(f"\nStatistical Comparison by Age Group:")
        
        for age_group in age_groups:
            subset = self.trials_df[self.trials_df['ageGroup'] == age_group]
            self._add_report_line(f"\n{age_group} years (n={len(subset)} trials):")
            
            for tt in trial_types:
                tt_data = subset[subset['trialType'] == tt]
                if len(tt_data) > 0:
                    rt_mean = tt_data['reactionTime'].mean()
                    rt_std = tt_data['reactionTime'].std()
                    eff_mean = tt_data['pathEfficiency'].mean()
                    
                    self._add_report_line(f"  {tt}:")
                    self._add_report_line(f"    Reaction Time: {rt_mean:.1f} ms (SD={rt_std:.1f})")
                    self._add_report_line(f"    Path Efficiency: {eff_mean:.3f}")
        
        # ANOVA test across all age groups
        if len(age_groups) >= 2:
            rt_groups = [self.trials_df[self.trials_df['ageGroup'] == ag]['reactionTime'].dropna() 
                        for ag in age_groups]
            
            # Filter out empty groups
            rt_groups = [g for g in rt_groups if len(g) > 0]
            
            if len(rt_groups) >= 2:
                f_stat, p_val = stats.f_oneway(*rt_groups)
                self._add_report_line(f"\nAge Effect on Reaction Time (ANOVA):")
                self._add_report_line(f"  F={f_stat:.3f}, p={p_val:.4f}")
                if p_val < 0.05:
                    self._add_report_line(f"  ✓ SIGNIFICANT age effect detected!")
                    
                    # Find which age groups differ most
                    means = [(ag, self.trials_df[self.trials_df['ageGroup'] == ag]['reactionTime'].mean()) 
                            for ag in age_groups]
                    means.sort(key=lambda x: x[1])
                    fastest = means[0]
                    slowest = means[-1]
                    diff = slowest[1] - fastest[1]
                    self._add_report_line(f"  Fastest: {fastest[0]} ({fastest[1]:.1f} ms)")
                    self._add_report_line(f"  Slowest: {slowest[0]} ({slowest[1]:.1f} ms)")
                    self._add_report_line(f"  Difference: {diff:.1f} ms")
                else:
                    self._add_report_line(f"  ✗ No significant age effect")


    def analyze_velocity_profiles(self, sample_size=5):
        """Analyze velocity profiles INCLUDING reaction time"""
        self._add_report_section("VELOCITY PROFILE ANALYSIS", level=1)
        
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
        self._add_report_section("MOVEMENT PATH VISUALIZATION", level=1)

        path_output_dir = os.path.join(self.figures_dir, 'movement_paths')
        os.makedirs(path_output_dir, exist_ok=True)
        
        # Plot: Separated by Trial Type
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
    
    def create_summary_plots(self):
        """Create overall summary plots"""
        self._add_report_section("CREATING SUMMARY FIGURES", level=1)
        
        fig, axes = plt.subplots(2, 2, figsize=(14, 10))
        
        # Plot 1: Reaction Time comparison
        ax = axes[0, 0]
        data_rt = []
        labels_rt = []
        for trial_type in ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']:
            rt = self.trials_df[self.trials_df['trialType'] == trial_type]['reactionTime'].dropna()
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
            eff = self.trials_df[self.trials_df['trialType'] == trial_type]['pathEfficiency'].dropna()
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
        if len(self.trials_df) > 0:
            sns.violinplot(data=self.trials_df, x='trialType', y='pathLength', ax=ax)
            ax.set_xticklabels(labels_rt)
        ax.set_xlabel('')
        ax.set_ylabel('Path Length (pixels)', fontsize=12)
        ax.set_title('C. Path Length Distribution', fontsize=13, fontweight='bold')
        
        # Plot 4: Movement Time
        ax = axes[1, 1]
        if len(self.trials_df) > 0:
            sns.violinplot(data=self.trials_df, x='trialType', y='movementTime', ax=ax)
            ax.set_xticklabels(labels_rt)
        ax.set_xlabel('')
        ax.set_ylabel('Movement Time (ms)', fontsize=12)
        ax.set_title('D. Execution Time', fontsize=13, fontweight='bold')
        
        plt.tight_layout()
        filename = os.path.join(self.figures_dir, 'summary_comparison_overall.png')
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
    participants_df, trials_df = load_data(SubliminalPrimingAnalyzer.DEFAULT_CREDENTIALS_FILENAME)
    
    if trials_df.empty:
        print("\n❌ No trial data available")
        return
    
    # Run analysis
    analyzer = SubliminalPrimingAnalyzer(trials_df, participants_df, outlier_threshold_ms=50000)
    
    # Overall hypothesis test
    analyzer._add_report_section("MAIN HYPOTHESIS TESTING - ALL DATA", level=1)
    analyzer.test_main_hypothesis()
    
    # DEMOGRAPHIC COMPARISONS
    analyzer.analyze_demographic_effects()

    # AGE GROUP ANALYSIS
    analyzer.analyze_age_effects()
    
    # Target-specific analysis
    analyzer.analyze_by_target()
    
    # Additional visualizations
    analyzer.analyze_velocity_profiles()
    analyzer.plot_movement_paths()
    
    # Create overall summary plots
    analyzer.create_summary_plots()
    
    # Save consolidated report
    analyzer.save_report()
    
    print("\n" + "="*80)
    print("✓ ANALYSIS COMPLETE")
    print("="*80)
    print(f"\nAll outputs saved to: {analyzer.output_dir}")
    print("  - subliminal_priming_report.txt (comprehensive text report)")
    print("  - figures/demographic_comparisons/ (ADHD, glasses, gender effects)")
    print("  - figures/target_comparison.png (spatial effects)")
    print("  - figures/ (all other plots and visualizations)")


if __name__ == "__main__":
    main()