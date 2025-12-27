"""
Advanced Velocity Profile Plotter
Handles plotting all velocities with averages and time/velocity caps
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from typing import List, Dict, Optional, Tuple
import os


class VelocityPlotter:
    """Creates comprehensive velocity profile visualizations"""
    
    def __init__(self, trials_df: pd.DataFrame, output_dir: str = 'analysis_outputs/velocities'):
        """
        Initialize velocity plotter
        
        Args:
            trials_df: DataFrame with all trial data
            output_dir: Where to save plots
        """
        self.trials_df = trials_df.copy()
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
    
    def plot_all_velocities(self, 
                           time_cap_ms: int = 10000,
                           velocity_cap_px_s: int = 5000,
                           split_by_col: Optional[str] = None,
                           conditions: List[str] = None):
        """
        Plot ALL velocity profiles with average overlay
        
        Args:
            time_cap_ms: Maximum time to display (prevents time outliers)
            velocity_cap_px_s: Maximum velocity to display (prevents velocity outliers)
            split_by_col: Optional column to split plots (e.g., 'hasAttentionDeficit')
            conditions: List of trial types to plot (default: all three)
        """
        if conditions is None:
            conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        print(f"\n{'='*70}")
        print(f"PLOTTING ALL VELOCITY PROFILES")
        print(f"{'='*70}")
        print(f"Time cap: {time_cap_ms} ms")
        print(f"Velocity cap: {velocity_cap_px_s} px/s")
        print(f"Split by: {split_by_col if split_by_col else 'None'}")
        
        if split_by_col:
            self._plot_split_velocities(conditions, time_cap_ms, velocity_cap_px_s, split_by_col)
        else:
            self._plot_unified_velocities(conditions, time_cap_ms, velocity_cap_px_s)
    
    def _plot_unified_velocities(self, conditions: List[str], time_cap_ms: int, velocity_cap: int):
        """Plot all velocities in one figure (3 subplots for 3 conditions)"""
        
        fig, axes = plt.subplots(1, 3, figsize=(20, 6))
        fig.suptitle(f'All Velocity Profiles (Time Cap: {time_cap_ms}ms, Velocity Cap: {velocity_cap}px/s)', 
                     fontsize=16, fontweight='bold')
        
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        
        for idx, condition in enumerate(conditions):
            ax = axes[idx]
            
            print(f"\n{condition}:")
            cond_data = self.trials_df[self.trials_df['trialType'] == condition]
            print(f"  Total trials: {len(cond_data)}")
            
            # Storage for average calculation
            all_velocities_at_time = {}
            valid_count = 0
            
            # Plot each trial
            for _, trial in cond_data.iterrows():
                path = trial.get('movementPath', [])
                
                if not isinstance(path, list) or len(path) < 3:
                    continue
                
                velocities, times = self._extract_velocity_profile(path, time_cap_ms, velocity_cap)
                
                if len(velocities) > 0:
                    # Plot individual trial (THIN, very transparent)
                    ax.plot(times, velocities, color=colors[idx], alpha=0.6, linewidth=0.2)
                    valid_count += 1
                    
                    # Store for averaging
                    for t, v in zip(times, velocities):
                        if t not in all_velocities_at_time:
                            all_velocities_at_time[t] = []
                        all_velocities_at_time[t].append(v)
            
            print(f"  Valid trials plotted: {valid_count}")
            
            # Calculate and plot average (MEDIUM thickness - not too thick!)
            if all_velocities_at_time:
                avg_times = sorted(all_velocities_at_time.keys())
                avg_velocities = [np.mean(all_velocities_at_time[t]) for t in avg_times]
                
                ax.plot(avg_times, avg_velocities, color='black', linewidth=2.5, 
                       label=f'Average (n={valid_count})', zorder=10)
                
                print(f"  Average profile calculated from {len(avg_times)} time points")
            
            # Add reaction time marker
            mean_rt = cond_data['reactionTime'].mean()
            ax.axvline(mean_rt, color='red', linestyle='--', linewidth=2.5, 
                      label=f'Mean RT: {mean_rt:.0f}ms', zorder=5)
            
            # Formatting
            ax.set_xlim(0, time_cap_ms)
            ax.set_ylim(0, velocity_cap)
            ax.set_xlabel('Time from Trial Start (ms)', fontsize=12, fontweight='bold')
            if idx == 0:
                ax.set_ylabel('Velocity (pixels/second)', fontsize=12, fontweight='bold')
            ax.set_title(condition.replace('_', ' '), fontsize=13, fontweight='bold')
            ax.legend(loc='upper right', fontsize=10, framealpha=0.9)
            ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        filename = f'all_velocities_tcap{time_cap_ms}_vcap{velocity_cap}.png'
        filepath = os.path.join(self.output_dir, filename)
        plt.savefig(filepath, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"\n✅ Saved: {filepath}")
    
    def _plot_split_velocities(self, conditions: List[str], time_cap_ms: int, 
                               velocity_cap: int, split_col: str):
        """Plot velocities split by a demographic variable"""
        
        # Get unique values for split
        split_values = self.trials_df[split_col].dropna().unique()
        
        if len(split_values) < 2:
            print(f"⚠️ Insufficient groups in {split_col} for split plotting")
            return
        
        # Create figure with rows for each group
        n_rows = len(split_values)
        fig, axes = plt.subplots(n_rows, 3, figsize=(20, 5 * n_rows))
        
        if n_rows == 1:
            axes = axes.reshape(1, -1)
        
        fig.suptitle(f'All Velocity Profiles Split by {split_col} (Time: {time_cap_ms}ms, Vel: {velocity_cap}px/s)', 
                     fontsize=16, fontweight='bold')
        
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        group_colors = ['#E63946', '#457B9D'] if len(split_values) == 2 else plt.cm.tab10.colors
        
        for row_idx, split_val in enumerate(split_values):
            print(f"\n{split_col} = {split_val}:")
            
            for col_idx, condition in enumerate(conditions):
                ax = axes[row_idx, col_idx]
                
                # Filter data
                mask = (self.trials_df['trialType'] == condition) & (self.trials_df[split_col] == split_val)
                cond_data = self.trials_df[mask]
                
                print(f"  {condition}: {len(cond_data)} trials")
                
                all_velocities_at_time = {}
                valid_count = 0
                
                # Plot each trial (THIN, transparent)
                for _, trial in cond_data.iterrows():
                    path = trial.get('movementPath', [])
                    
                    if not isinstance(path, list) or len(path) < 3:
                        continue
                    
                    velocities, times = self._extract_velocity_profile(path, time_cap_ms, velocity_cap)
                    
                    if len(velocities) > 0:
                        ax.plot(times, velocities, 
                               color=group_colors[row_idx % len(group_colors)], 
                               alpha=0.08, linewidth=0.3)
                        valid_count += 1
                        
                        for t, v in zip(times, velocities):
                            if t not in all_velocities_at_time:
                                all_velocities_at_time[t] = []
                            all_velocities_at_time[t].append(v)
                
                # Average line (THICK)
                if all_velocities_at_time:
                    avg_times = sorted(all_velocities_at_time.keys())
                    avg_velocities = [np.mean(all_velocities_at_time[t]) for t in avg_times]
                    
                    ax.plot(avg_times, avg_velocities, color='black', linewidth=4, 
                           label=f'Avg (n={valid_count})', zorder=10)
                
                # Mean RT marker
                if len(cond_data) > 0:
                    mean_rt = cond_data['reactionTime'].mean()
                    ax.axvline(mean_rt, color='red', linestyle='--', linewidth=2.5, 
                              label=f'RT: {mean_rt:.0f}ms', zorder=5)
                
                # Formatting
                ax.set_xlim(0, time_cap_ms)
                ax.set_ylim(0, velocity_cap)
                
                if row_idx == n_rows - 1:
                    ax.set_xlabel('Time from Trial Start (ms)', fontsize=11, fontweight='bold')
                if col_idx == 0:
                    ax.set_ylabel(f'{split_col}={split_val}\nVelocity (px/s)', 
                                 fontsize=11, fontweight='bold')
                
                if row_idx == 0:
                    ax.set_title(condition.replace('_', ' '), fontsize=12, fontweight='bold')
                
                ax.legend(loc='upper right', fontsize=9, framealpha=0.9)
                ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        filename = f'all_velocities_split_{split_col}_tcap{time_cap_ms}_vcap{velocity_cap}.png'
        filepath = os.path.join(self.output_dir, filename)
        plt.savefig(filepath, dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"\n✅ Saved: {filepath}")
    
    def _extract_velocity_profile(self, path: List[Dict], time_cap_ms: int, 
                                   velocity_cap: int) -> Tuple[List[float], List[float]]:
        """
        Extract velocity and time arrays from movement path
        
        Args:
            path: List of position dictionaries with 'x', 'y', 't' keys
            time_cap_ms: Maximum time to include
            velocity_cap: Maximum velocity to include (filters outliers)
            
        Returns:
            (velocities, times) - both as lists, filtered by caps
        """
        velocities = []
        times = []
        
        if not isinstance(path, list) or len(path) < 2:
            return velocities, times
        
        # Get start time
        start_time = path[0].get('t', 0)
        
        for i in range(1, len(path)):
            if not (isinstance(path[i], dict) and isinstance(path[i-1], dict)):
                continue
            
            # Calculate time relative to start
            current_time = path[i].get('t', 0) - start_time
            
            # Apply time cap
            if current_time > time_cap_ms:
                break
            
            # Calculate velocity
            dt = (path[i]['t'] - path[i-1]['t']) / 1000.0  # Convert to seconds
            
            if dt > 0:
                dx = path[i]['x'] - path[i-1]['x']
                dy = path[i]['y'] - path[i-1]['y']
                distance = np.sqrt(dx**2 + dy**2)
                velocity = distance / dt
                
                # Apply velocity cap (filter outliers)
                if velocity <= velocity_cap:
                    velocities.append(velocity)
                    times.append(current_time)
        
        return velocities, times
    
    def create_velocity_comparison_matrix(self, time_cap_ms: int = 10000, 
                                         velocity_cap: int = 5000):
        """
        Create a comprehensive comparison matrix showing:
        - All three conditions
        - Statistical comparison between conditions
        - Distribution summaries
        """
        print(f"\n{'='*70}")
        print("CREATING VELOCITY COMPARISON MATRIX")
        print(f"{'='*70}")
        
        conditions = ['PRE_SUPRA', 'PRE_JND', 'CONCURRENT_SUPRA']
        
        fig = plt.figure(figsize=(20, 12))
        gs = fig.add_gridspec(3, 3, hspace=0.3, wspace=0.3)
        
        fig.suptitle(f'Comprehensive Velocity Analysis (Time: {time_cap_ms}ms, Velocity: {velocity_cap}px/s)', 
                     fontsize=16, fontweight='bold')
        
        colors = ['#2E86AB', '#A23B72', '#F18F01']
        
        # Row 1: Individual velocity plots
        for idx, condition in enumerate(conditions):
            ax = fig.add_subplot(gs[0, idx])
            
            cond_data = self.trials_df[self.trials_df['trialType'] == condition]
            all_velocities_at_time = {}
            
            for _, trial in cond_data.iterrows():
                path = trial.get('movementPath', [])
                velocities, times = self._extract_velocity_profile(path, time_cap_ms, velocity_cap)
                
                if len(velocities) > 0:
                    ax.plot(times, velocities, color=colors[idx], alpha=0.08, linewidth=0.3)
                    
                    for t, v in zip(times, velocities):
                        if t not in all_velocities_at_time:
                            all_velocities_at_time[t] = []
                        all_velocities_at_time[t].append(v)
            
            # Average (THICK)
            if all_velocities_at_time:
                avg_times = sorted(all_velocities_at_time.keys())
                avg_velocities = [np.mean(all_velocities_at_time[t]) for t in avg_times]
                ax.plot(avg_times, avg_velocities, color='black', linewidth=4, label='Average')
            
            ax.set_ylim(0, velocity_cap)
            ax.set_title(condition.replace('_', ' '), fontweight='bold')
            ax.set_xlabel('Time from Trial Start (ms)', fontweight='bold')
            if idx == 0:
                ax.set_ylabel('Velocity (px/s)', fontweight='bold')
            ax.legend()
            ax.grid(alpha=0.3)
        
        # Row 2: Peak velocity distribution
        ax_peaks = fig.add_subplot(gs[1, :])
        
        peak_data = []
        for condition in conditions:
            cond_data = self.trials_df[self.trials_df['trialType'] == condition]
            peaks = []
            
            for _, trial in cond_data.iterrows():
                path = trial.get('movementPath', [])
                velocities, _ = self._extract_velocity_profile(path, time_cap_ms, velocity_cap)
                
                if len(velocities) > 0:
                    peaks.append(max(velocities))
            
            peak_data.append(peaks)
        
        bp = ax_peaks.boxplot(peak_data, labels=[c.replace('_', '\n') for c in conditions], 
                              patch_artist=True)
        
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)
        
        ax_peaks.set_ylabel('Peak Velocity (px/s)', fontsize=12, fontweight='bold')
        ax_peaks.set_title('Peak Velocity Distribution by Condition', fontsize=13, fontweight='bold')
        ax_peaks.grid(axis='y', alpha=0.3)
        
        # Row 3: Velocity statistics table
        ax_table = fig.add_subplot(gs[2, :])
        ax_table.axis('off')
        
        # Calculate statistics
        stats_data = []
        for condition in conditions:
            cond_data = self.trials_df[self.trials_df['trialType'] == condition]
            all_vels = []
            
            for _, trial in cond_data.iterrows():
                path = trial.get('movementPath', [])
                velocities, _ = self._extract_velocity_profile(path, time_cap_ms, velocity_cap)
                all_vels.extend(velocities)
            
            if len(all_vels) > 0:
                stats_data.append([
                    condition.replace('_', ' '),
                    f"{np.mean(all_vels):.1f}",
                    f"{np.std(all_vels):.1f}",
                    f"{np.median(all_vels):.1f}",
                    f"{np.max(all_vels):.1f}",
                    f"{len(cond_data)}"
                ])
        
        table = ax_table.table(cellText=stats_data,
                              colLabels=['Condition', 'Mean Vel', 'SD', 'Median', 'Max', 'N Trials'],
                              cellLoc='center',
                              loc='center',
                              bbox=[0.1, 0.2, 0.8, 0.6])
        
        table.auto_set_font_size(False)
        table.set_fontsize(11)
        table.scale(1, 2)
        
        # Style table
        for i in range(len(stats_data) + 1):
            for j in range(6):
                cell = table[(i, j)]
                if i == 0:
                    cell.set_facecolor('#E0E0E0')
                    cell.set_text_props(weight='bold')
                else:
                    cell.set_facecolor(['#E8F4F8', '#FCE4EC', '#FFF3E0'][i-1])
        
        plt.savefig(os.path.join(self.output_dir, 
                   f'velocity_comparison_matrix_tcap{time_cap_ms}_vcap{velocity_cap}.png'), 
                   dpi=300, bbox_inches='tight')
        plt.close()
        
        print(f"✅ Saved: velocity_comparison_matrix_tcap{time_cap_ms}_vcap{velocity_cap}.png")