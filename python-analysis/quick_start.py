"""
Quick Start Script - Command Line Interface
============================================
Interactive command-line interface for the Behavioral Analysis Toolkit.

NOTE: For most users, the web interface is recommended:
  1. Start backend: cd python-analysis && python backend_api.py
  2. Start frontend: cd web-interface && npm start
  3. Open: http://localhost:3000

This script provides a command-line alternative for advanced users or
when running on systems without a web browser.

Features:
- Interactive menu system
- Data viewing and export
- Full statistical analysis
- Velocity plot generation
- Database cleanup
- File management
"""

import os
import shutil
import sys
from datetime import datetime
import pandas as pd

# Default credentials file location
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

# Output directory structure
OUTPUTS_BASE = os.path.join(script_dir, 'analysis_outputs')
FULL_REPORTS_DIR = os.path.join(OUTPUTS_BASE, 'full_reports')
VELOCITY_PLOTS_DIR = os.path.join(OUTPUTS_BASE, 'velocity_plots')

# Data cache (avoid repeated Firebase calls)
_data_cache = {
    'participants': None,
    'trials': None,
    'last_loaded': None
}


# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

def ensure_output_structure():
    """Ensure the output directory structure exists."""
    os.makedirs(FULL_REPORTS_DIR, exist_ok=True)
    os.makedirs(VELOCITY_PLOTS_DIR, exist_ok=True)


def check_requirements():
    """Check if all required packages are installed."""
    required = ['firebase_admin', 'pandas', 'matplotlib', 'seaborn', 'scipy', 'numpy']
    missing = []
    
    for pkg in required:
        try:
            __import__(pkg)
        except ImportError:
            missing.append(pkg)
    
    if missing:
        print(f"❌ Missing packages: {', '.join(missing)}")
        print("Install with: pip install -r requirements.txt")
        return False
    return True


def check_credentials():
    """Check if Firebase credentials file exists."""
    if not os.path.exists(DEFAULT_CREDENTIALS_FILENAME):
        print(f"❌ Firebase credentials not found at: {DEFAULT_CREDENTIALS_FILENAME}")
        print("Please place your serviceAccountKey.json in the python-analysis directory")
        return False
    return True


def load_data_with_cache(force_reload=False):
    """
    Load data from Firebase or cache.
    
    Args:
        force_reload (bool): If True, bypass cache and reload from Firebase
        
    Returns:
        tuple: (participants_df, trials_df)
    """
    from firebase_connector import load_data
    
    if force_reload or _data_cache['participants'] is None:
        print(f"{'🔄 Reloading' if force_reload else '📡 Loading'} data from Firebase...")
        p_df, t_df = load_data(DEFAULT_CREDENTIALS_FILENAME)
        _data_cache.update({
            'participants': p_df, 
            'trials': t_df, 
            'last_loaded': datetime.now()
        })
        print(f"✅ Loaded {len(p_df)} participants, {len(t_df)} trials")
    
    return _data_cache['participants'], _data_cache['trials']


# ============================================================================
# MENU FUNCTIONS
# ============================================================================

def print_menu():
    """Display the main interactive menu."""
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS - COMMAND CENTER")
    print("="*70)
    
    if _data_cache['last_loaded']:
        p_count = len(_data_cache['participants'])
        t_count = len(_data_cache['trials'])
        print(f"📦 Cache: {p_count} participants | {t_count} trials")
    
    print("\n--- DATA MANAGEMENT ---")
    print("1. View Data Summary")
    print("2. Export/Backup Data (CSV)")
    print("3. CLEAN FIRESTORE (Remove duplicates & partial sets)")
    print("4. Refresh Data Cache (Force reload from Firebase)")
    
    print("\n--- ANALYSIS & PLOTTING ---")
    print("5. Run Full Analysis Report")
    print("6. Plot Velocity Profiles")
    
    print("\n--- FILE MANAGEMENT ---")
    print("7. Clean Full Analysis Reports")
    print("8. Clean Velocity Plots")
    print("9. Clean ALL Local Files")
    
    print("\n--- SYSTEM ---")
    print("a. Test Firebase Connection")
    print("b. Help (Jupyter/Advanced)")
    print("0. Exit")
    print("="*70)


def test_connection():
    """Test Firebase connection."""
    print("\n📡 Testing Firebase connection...")
    try:
        from firebase_connector import FirebaseConnector
        connector = FirebaseConnector(DEFAULT_CREDENTIALS_FILENAME)
        summary = connector.get_trial_summary()
        
        print("\n✅ Connection successful!")
        print(f"\nData available:")
        print(f"  Participants: {summary['total_participants']}")
        print(f"  Total trials: {summary['total_trials']}")
        
        if summary['trials_by_type']:
            print(f"  Trial types:")
            for trial_type, count in summary['trials_by_type'].items():
                print(f"    - {trial_type}: {count}")
        
        return True
    except Exception as e:
        print(f"\n❌ Connection failed: {e}")
        print("\nMake sure:")
        print("  1. serviceAccountKey.json is in the same directory")
        print("  2. You have internet connection")
        print("  3. Firebase credentials are valid")
        return False


def view_summary():
    """Display quick data summary."""
    p_df, t_df = load_data_with_cache()
    
    print("\n" + "="*70)
    print("DATA SUMMARY")
    print("="*70)
    print(f"👥 Participants: {len(p_df)}")
    
    if not p_df.empty and 'gender' in p_df.columns:
        gender_counts = p_df['gender'].value_counts().to_dict()
        print(f"   Gender distribution: {gender_counts}")
    
    print(f"📊 Total Trials: {len(t_df)}")
    
    if not t_df.empty and 'trialType' in t_df.columns:
        trial_counts = t_df['trialType'].value_counts().to_dict()
        print(f"   Trial distribution: {trial_counts}")


def run_analysis():
    """Run full statistical analysis."""
    print("\n📬 Running complete analysis...")
    ensure_output_structure()
    
    try:
        from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
        
        participants_df, trials_df = load_data_with_cache()
        
        if trials_df.empty:
            print("\n⚠️  No trial data found.")
            return False
        
        # Create timestamped directory within full_reports
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        run_dir = os.path.join(FULL_REPORTS_DIR, f'run_{timestamp}')
        os.makedirs(run_dir, exist_ok=True)
        
        # Initialize analyzer
        analyzer = SubliminalPrimingAnalyzer(
            trials_df, 
            participants_df, 
            output_dir=run_dir
        )
        
        print("\n📊 Running main hypothesis tests...")
        analyzer.test_main_hypothesis()
        
        print("\n👥 Analyzing demographic effects...")
        analyzer.run_all_demographics()
        
        print("\n📈 Generating velocity profiles...")
        analyzer.plot_velocity_profiles()
        
        # Ask about splitting by demographic
        print("\nWould you like velocity profiles split by a demographic?")
        print("  1. ADHD")
        print("  2. Glasses")
        print("  3. Gender")
        print("  0. Skip")
        
        choice = input("\nYour choice: ").strip()
        
        split_col = {
            '1': 'hasAttentionDeficit',
            '2': 'hasGlasses',
            '3': 'gender'
        }.get(choice)
        
        if split_col:
            analyzer.plot_velocity_profiles(split_by_col=split_col)
        
        print("\n📊 Creating summary plots...")
        analyzer.create_summary_plots()
        
        print("\n💾 Saving report...")
        analyzer.save_report()
        
        print("\n" + "="*70)
        print("✅ ANALYSIS COMPLETE!")
        print("="*70)
        print(f"\n📁 Results saved in: {analyzer.output_dir}/")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Analysis failed: {e}")
        import traceback
        traceback.print_exc()
        return False


def clean_firestore_database():
    """Safety-first cleanup: Dry Run → Report → Confirm Deletion."""
    print("\n" + "="*70)
    print("FIRESTORE DATABASE CLEANUP")
    print("="*70)
    
    # Offer backup first
    if input("\nWould you like to backup data to CSV first? (y/n): ").lower() == 'y':
        export_data()

    try:
        from firebase_cleaner import FirebaseCleaner
        cleaner = FirebaseCleaner(DEFAULT_CREDENTIALS_FILENAME)

        print("\n--- STEP 1: DRY RUN (No data will be deleted) ---")
        dup_count = cleaner.remove_duplicate_trials(dry_run=True)
        inc_count = cleaner.remove_incomplete_sets(target_count=15, dry_run=True)

        # Check if database is clean
        if dup_count == 0 and inc_count == 0:
            print("\n✨ Database is clean! No duplicates or incomplete sets found.")
            return

        # Confirm deletion
        total_issues = dup_count + inc_count
        print("\n" + "!"*30)
        print(f"Found {total_issues} issues ({dup_count} duplicates, {inc_count} incomplete)")
        confirm = input(f"Proceed with ACTUAL deletion? (type 'yes'): ").strip().lower()
        print("!"*30)

        if confirm == 'yes':
            print("\n--- STEP 2: ACTUAL CLEANUP ---")
            cleaner.remove_duplicate_trials(dry_run=False)
            cleaner.remove_incomplete_sets(target_count=15, dry_run=False)
            print("\n✅ Database cleaned. Refreshing local cache...")
            load_data_with_cache(force_reload=True)
        else:
            print("\nCleanup cancelled.")

    except Exception as e:
        print(f"❌ Cleanup failed: {e}")
        import traceback
        traceback.print_exc()


def export_data():
    """Export data with user selection."""
    print("\n💾 DATA EXPORT / BACKUP")
    print("1. Raw Data (Recommended for Backup)")
    print("2. Processed Data (Advanced Metrics)")
    choice = input("Select export type (1-2): ").strip()

    p_df, t_df = load_data_with_cache()
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    export_dir = os.path.join(script_dir, 'data_exports')
    os.makedirs(export_dir, exist_ok=True)

    if choice == '1':
        # Raw data export
        p_file = os.path.join(export_dir, f"backup_participants_{timestamp}.csv")
        t_file = os.path.join(export_dir, f"backup_trials_{timestamp}.csv")
        
        p_df.to_csv(p_file, index=False)
        
        # Format movementPath for CSV compatibility
        t_export = t_df.copy()
        if 'movementPath' in t_export.columns:
            import json
            t_export['movementPath'] = t_export['movementPath'].apply(
                lambda x: json.dumps(x) if isinstance(x, list) else x
            )
        t_export.to_csv(t_file, index=False)
        
        print(f"✅ Backup saved to: {export_dir}")
        print(f"  - {os.path.basename(p_file)}")
        print(f"  - {os.path.basename(t_file)}")
        
    elif choice == '2':
        # Processed data with metrics
        from backend_api import calculate_advanced_metrics
        processed_df = calculate_advanced_metrics(t_df)
        proc_file = os.path.join(export_dir, f"processed_{timestamp}.csv")
        processed_df.to_csv(proc_file, index=False)
        print(f"✅ Processed metrics exported to: {os.path.basename(proc_file)}")


def plot_all_velocities():
    """Plot velocity profiles with user configuration."""
    print("\n📈 PLOT ALL VELOCITIES")
    print("="*70)
    ensure_output_structure()
    
    try:
        from velocity_plotter import VelocityPlotter
        
        participants_df, trials_df = load_data_with_cache()
        
        if trials_df.empty:
            print("\n⚠️  No trial data found")
            return False
        
        # Get time cap
        print("\nSet time cap (milliseconds) to filter time outliers:")
        print("  Recommended: 5500 ms")
        time_cap_input = input("Enter time cap in ms (press Enter for 5500): ").strip()
        
        try:
            time_cap = int(time_cap_input) if time_cap_input else 5500
        except ValueError:
            print("Invalid input, using default 5500 ms")
            time_cap = 5500
        
        # Get velocity cap
        print("\nSet velocity cap (pixels/second) to filter velocity outliers:")
        print("  Recommended: 5000 px/s")
        vel_cap_input = input("Enter velocity cap in px/s (press Enter for 5000): ").strip()
        
        try:
            vel_cap = int(vel_cap_input) if vel_cap_input else 5000
        except ValueError:
            print("Invalid input, using default 5000 px/s")
            vel_cap = 5000
        
        # Ask about splitting
        print("\nWould you like to split by demographic?")
        print("  1. ADHD")
        print("  2. Glasses")
        print("  3. Gender")
        print("  0. No split (show all together)")
        
        choice = input("\nYour choice: ").strip()
        
        split_col = {
            '1': 'hasAttentionDeficit',
            '2': 'hasGlasses',
            '3': 'gender',
            '0': None
        }.get(choice, None)
        
        # Create timestamped directory within velocity_plots
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        velocity_run_dir = os.path.join(VELOCITY_PLOTS_DIR, f'velocity_{timestamp}')
        os.makedirs(velocity_run_dir, exist_ok=True)
        
        # Create plotter
        plotter = VelocityPlotter(trials_df, output_dir=velocity_run_dir)
        plotter.plot_all_velocities(
            time_cap_ms=time_cap, 
            velocity_cap_px_s=vel_cap, 
            split_by_col=split_col
        )
        
        # Ask about comparison matrix
        print("\nWould you like a comprehensive comparison matrix? (y/n): ")
        if input().strip().lower() == 'y':
            plotter.create_velocity_comparison_matrix(
                time_cap_ms=time_cap, 
                velocity_cap=vel_cap
            )
        
        # Ask about overlay plot
        print("\nWould you like an overlay plot with all conditions together? (y/n): ")
        if input().strip().lower() == 'y':
            plotter.plot_overlay_all_conditions(
                time_cap_ms=time_cap, 
                velocity_cap=vel_cap
            )
        
        print("\n✅ Velocity plots complete!")
        print(f"📁 Saved to: {plotter.output_dir}/")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False


def clean_full_reports():
    """Clean full analysis report directories."""
    print(f"\n🧹 CLEAN FULL ANALYSIS REPORTS")
    print(f"Location: {FULL_REPORTS_DIR}")
    print("="*70)
    
    try:
        if not os.path.exists(FULL_REPORTS_DIR):
            print("No full_reports folder found - nothing to clean!")
            return True
        
        # Get all run directories
        runs = [d for d in os.listdir(FULL_REPORTS_DIR) 
                if os.path.isdir(os.path.join(FULL_REPORTS_DIR, d)) and d.startswith('run_')]
        
        if not runs:
            print("No analysis runs found!")
            return True
        
        runs.sort(reverse=True)  # Most recent first
        print(f"\nFound {len(runs)} full analysis reports:")
        for run in runs[:5]:  # Show first 5
            print(f"  - {run}")
        if len(runs) > 5:
            print(f"  ... and {len(runs) - 5} more")
        
        print("\nOptions:")
        print("  1. Keep only the most recent run")
        print("  2. Keep the 3 most recent runs")
        print("  3. Delete all runs")
        print("  0. Cancel")
        
        choice = input("\nChoice: ").strip()
        
        if choice == '1':
            to_delete = runs[1:]
        elif choice == '2':
            to_delete = runs[3:]
        elif choice == '3':
            to_delete = runs
        else:
            print("Cancelled.")
            return True
        
        if to_delete and input(f"\nDelete {len(to_delete)} runs? (y/n): ").lower() == 'y':
            for run in to_delete:
                shutil.rmtree(os.path.join(FULL_REPORTS_DIR, run))
                print(f"  ✓ Deleted {run}")
            print(f"\n✅ Cleaned {len(to_delete)} analysis reports.")
        else:
            print("Cancelled.")
            
    except Exception as e:
        print(f"❌ Error: {e}")


def clean_velocity_plots():
    """Clean velocity plot directories."""
    print(f"\n🧹 CLEAN VELOCITY PLOTS")
    print(f"Location: {VELOCITY_PLOTS_DIR}")
    print("="*70)
    
    try:
        if not os.path.exists(VELOCITY_PLOTS_DIR):
            print("No velocity_plots folder found - nothing to clean!")
            return True
        
        # Get all velocity run directories
        runs = [d for d in os.listdir(VELOCITY_PLOTS_DIR) 
                if os.path.isdir(os.path.join(VELOCITY_PLOTS_DIR, d)) and d.startswith('velocity_')]
        
        # Get loose files
        files = [f for f in os.listdir(VELOCITY_PLOTS_DIR) 
                 if os.path.isfile(os.path.join(VELOCITY_PLOTS_DIR, f))]
        
        if not runs and not files:
            print("No velocity plots found!")
            return True
        
        print(f"\nFound:")
        if runs:
            runs.sort(reverse=True)
            print(f"  - {len(runs)} velocity plot runs:")
            for run in runs[:5]:  # Show first 5
                print(f"    • {run}")
            if len(runs) > 5:
                print(f"    ... and {len(runs) - 5} more")
        if files:
            print(f"  - {len(files)} loose files")
        
        print("\nOptions:")
        print("  1. Keep only the most recent velocity run")
        print("  2. Keep the 3 most recent velocity runs")
        print("  3. Delete all velocity plots")
        print("  0. Cancel")
        
        choice = input("\nChoice: ").strip()
        
        to_delete_dirs = []
        to_delete_files = []
        
        if choice == '1':
            to_delete_dirs = runs[1:] if runs else []
            to_delete_files = files
        elif choice == '2':
            to_delete_dirs = runs[3:] if runs else []
            to_delete_files = files
        elif choice == '3':
            to_delete_dirs = runs
            to_delete_files = files
        else:
            print("Cancelled.")
            return True
        
        total_to_delete = len(to_delete_dirs) + len(to_delete_files)
        
        if total_to_delete == 0:
            print("Nothing to delete.")
            return True
        
        if input(f"\nDelete {len(to_delete_dirs)} directories and {len(to_delete_files)} files? (y/n): ").lower() == 'y':
            for run in to_delete_dirs:
                shutil.rmtree(os.path.join(VELOCITY_PLOTS_DIR, run))
                print(f"  ✓ Deleted: {run}")
            for f in to_delete_files:
                os.remove(os.path.join(VELOCITY_PLOTS_DIR, f))
                print(f"  ✓ Deleted: {f}")
            print(f"\n✅ Cleaned {total_to_delete} items.")
        else:
            print("Cancelled.")
            
    except Exception as e:
        print(f"❌ Error: {e}")


def clean_all_files():
    """Clean both analysis outputs and velocity plots."""
    print("\n🧹 CLEAN ALL LOCAL FILES")
    print("="*70)
    print("\nThis will clean:")
    print("  - Full analysis reports")
    print("  - Velocity plots")
    print("\nData exports and Firebase data will NOT be affected.")
    
    if input("\nProceed? (y/n): ").lower() != 'y':
        print("Cancelled.")
        return
    
    print("\n--- Cleaning Full Analysis Reports ---")
    clean_full_reports()
    
    print("\n--- Cleaning Velocity Plots ---")
    clean_velocity_plots()
    
    print("\n✅ All local files cleaned!")


def show_jupyter_help():
    """Show help about Jupyter notebooks."""
    print("\n" + "="*70)
    print("JUPYTER NOTEBOOK - WHAT IS IT?")
    print("="*70)
    
    print("""
Jupyter Notebook is an interactive coding environment where you can:
  • Run Python code in small chunks (cells)
  • See results immediately with plots and tables
  • Mix code, explanations, and visualizations
  • Experiment with data analysis step-by-step

HOW TO GET STARTED:

1. Install Jupyter:
   pip install jupyter

2. Start Jupyter in this folder:
   jupyter notebook

3. This will open in your web browser

4. Create a new notebook and try:

   from firebase_connector import load_data
   
   participants, trials = load_data('serviceAccountKey.json')
   print(f"Loaded {len(participants)} participants")
   
   # You can now explore the data interactively!
""")
    
    choice = input("\nWould you like to start Jupyter now? (yes/no): ").strip().lower()
    
    if choice == 'yes':
        try:
            import subprocess
            print("\n🚀 Starting Jupyter Notebook...")
            subprocess.run(['jupyter', 'notebook'])
        except:
            print("\n❌ Jupyter not installed! Install with: pip install jupyter")


def reload_data():
    """Force reload data from Firebase."""
    print("\n🔄 Reloading data from Firebase...")
    try:
        load_data_with_cache(force_reload=True)
        print("\n✅ Data refreshed successfully!")
        return True
    except Exception as e:
        print(f"\n❌ Reload failed: {e}")
        return False


# ============================================================================
# MAIN LOOP
# ============================================================================

def main():
    """Main program loop."""
    # Check prerequisites
    if not (check_requirements() and check_credentials()):
        return
    
    ensure_output_structure()
    
    # Action mapping
    actions = {
        '1': view_summary,
        '2': export_data,
        '3': clean_firestore_database,
        '4': reload_data,
        '5': run_analysis,
        '6': plot_all_velocities,
        '7': clean_full_reports,
        '8': clean_velocity_plots,
        '9': clean_all_files,
        'a': test_connection,
        'b': show_jupyter_help
    }

    # Main loop
    while True:
        print_menu()
        choice = input("\nSelect an option: ").strip().lower()
        
        if choice == '0':
            print("\nGoodbye!")
            break
        
        if choice in actions:
            print()  # Add spacing
            actions[choice]()
        else:
            print("❌ Invalid choice. Please try again.")
        
        input("\nPress Enter to continue...")


if __name__ == "__main__":
    main()