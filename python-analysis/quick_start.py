"""
Quick Start Script - Command Line Interface

NOTE: For most users, the web interface is recommended:
  1. Start backend: cd python-analysis && python backend_api.py
  2. Start frontend: cd web-interface && npm start
  3. Open: http://localhost:3000

This script provides a command-line alternative for advanced users.
"""

import os
import shutil
import sys
from datetime import datetime
import pandas as pd

# Default credentials file
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

# Cache for data
_data_cache = {
    'participants': None,
    'trials': None,
    'last_loaded': None
}

# --- UTILITY FUNCTIONS ---

def check_requirements():
    """Check if all required packages are installed"""
    required = ['firebase_admin', 'pandas', 'matplotlib', 'seaborn', 'scipy', 'numpy']
    missing = [pkg for pkg in required if not __import_util(pkg)]
    
    if missing:
        print(f"❌ Missing: {', '.join(missing)}. Run: pip install -r requirements.txt")
        return False
    return True

def __import_util(pkg):
    try:
        __import__(pkg)
        return True
    except ImportError:
        return False

def check_credentials():
    """Check if Firebase credentials exist"""
    if not os.path.exists(DEFAULT_CREDENTIALS_FILENAME):
        print(f"❌ Firebase credentials not found at: {DEFAULT_CREDENTIALS_FILENAME}")
        return False
    return True

def load_data_with_cache(force_reload=False):
    """Load data from Firebase or cache"""
    from firebase_connector import load_data
    
    if force_reload or _data_cache['participants'] is None:
        print(f"{'🔄 Reloading' if force_reload else '📡 Loading'} data from Firebase...")
        p_df, t_df = load_data(DEFAULT_CREDENTIALS_FILENAME)
        _data_cache.update({'participants': p_df, 'trials': t_df, 'last_loaded': datetime.now()})
        print(f"✅ Loaded {len(p_df)} participants, {len(t_df)} trials")
    
    return _data_cache['participants'], _data_cache['trials']

# --- MENU ACTIONS ---

def print_menu():
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS - COMMAND CENTER")
    print("="*70)
    if _data_cache['last_loaded']:
        print(f"📦 Cache: {len(_data_cache['participants'])} pts | {len(_data_cache['trials'])} trials")
    
    print("\n--- DATA MANAGEMENT ---")
    print("1. View Data Summary")
    print("2. Export/Backup Data (CSV)")
    print("3. CLEAN FIRESTORE (Remove duplicates & partial sets)")
    print("4. Refresh Data Cache (Force reload from Firebase)")
    
    print("\n--- ANALYSIS & PLOTTING ---")
    print("5. Run Full Analysis Report")
    print("6. Plot Velocity Profiles")
    print("7. Manage Local Files (Clean 'analysis_outputs' folder)")
    
    print("\n--- SYSTEM ---")
    print("8. Test Firebase Connection")
    print("9. Help (Jupyter/Advanced)")
    print("0. Exit")
    print("="*70)

def test_connection():
    """Test Firebase connection"""
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
        return False

def view_summary():
    """Display quick data summary"""
    p_df, t_df = load_data_with_cache()
    print("\n" + "="*70 + "\nDATA SUMMARY\n" + "="*70)
    print(f"👥 Participants: {len(p_df)}")
    if not p_df.empty and 'gender' in p_df.columns:
        print(f"   Demographics: {p_df['gender'].value_counts().to_dict()}")
    print(f"📊 Total Trials: {len(t_df)}")
    if not t_df.empty and 'trialType' in t_df.columns:
        print(f"   Trial Split: {t_df['trialType'].value_counts().to_dict()}")

def run_analysis():
    """Run full analysis"""
    print("\n🔬 Running complete analysis...")
    
    try:
        from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
        
        participants_df, trials_df = load_data_with_cache()
        
        if trials_df.empty:
            print("\n⚠️  No trial data found.")
            return False
        
        analyzer = SubliminalPrimingAnalyzer(trials_df, participants_df)
        
        print("\n📊 Running main hypothesis tests...")
        analyzer.test_main_hypothesis()
        
        print("\n👥 Analyzing demographic effects...")
        analyzer.run_all_demographics()
        
        print("\n📈 Generating velocity profiles...")
        analyzer.plot_velocity_profiles()
        
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
    """Safety-first cleanup: Dry Run -> Report -> Confirm Deletion"""
    print("\n" + "="*70 + "\nFIRESTORE DATABASE CLEANUP\n" + "="*70)
    
    if input("Would you like to backup data to CSV first? (y/n): ").lower() == 'y':
        export_data()

    try:
        from firebase_cleaner import FirebaseCleaner
        cleaner = FirebaseCleaner(DEFAULT_CREDENTIALS_FILENAME)

        print("\n--- STEP 1: DRY RUN (No data will be deleted) ---")
        dup_count = cleaner.remove_duplicate_trials(dry_run=True)
        inc_count = cleaner.remove_incomplete_sets(target_count=15, dry_run=True)

        # CHECK IF CLEAN:
        if dup_count == 0 and inc_count == 0:
            print("\n✨ Database is clean! No duplicates or incomplete sets found.")
            return

        # 2. Human Confirmation
        print("\n" + "!"*30)
        confirm = input(f"Found {dup_count + inc_count} issues. Proceed with ACTUAL deletion? (type 'yes'): ").strip().lower()
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

def export_data():
    """Export data with specific user choices"""
    print("\n💾 DATA EXPORT / BACKUP")
    print("1. Raw Data (Recommended for Backup)")
    print("2. Processed Data (Advanced Metrics)")
    choice = input("Select export type (1-2): ").strip()

    p_df, t_df = load_data_with_cache()
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    export_dir = os.path.join(script_dir, 'data_exports')
    os.makedirs(export_dir, exist_ok=True)

    if choice == '1':
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
    elif choice == '2':
        from backend_api import calculate_advanced_metrics
        processed_df = calculate_advanced_metrics(t_df)
        processed_df.to_csv(os.path.join(export_dir, f"processed_{timestamp}.csv"), index=False)
        print("✅ Processed metrics exported.")

def plot_all_velocities():
    """Plot all velocity profiles with time cap"""
    print("\n📈 PLOT ALL VELOCITIES")
    print("="*70)
    
    try:
        from velocity_plotter import VelocityPlotter
        
        participants_df, trials_df = load_data_with_cache()
        
        if trials_df.empty:
            print("\n⚠️  No trial data found")
            return False
        
        # Get time cap
        print("\nSet time cap (milliseconds) to prevent time outliers:")
        print("  Recommended: 5500 ms")
        print("  Default: 5500 ms")
        
        time_cap_input = input("\nEnter time cap in ms (press Enter for 5500): ").strip()
        
        try:
            time_cap = int(time_cap_input) if time_cap_input else 5500
        except ValueError:
            print("Invalid input, using default 5500 ms")
            time_cap = 5500
        
        # Get velocity cap
        print("\nSet velocity cap (pixels/second) to prevent velocity outliers:")
        print("  Recommended: 5000 px/s")
        print("  Default: 5000 px/s")
        
        vel_cap_input = input("\nEnter velocity cap in px/s (press Enter for 5000): ").strip()
        
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
        
        # Create plotter and generate plots
        plotter = VelocityPlotter(trials_df)
        plotter.plot_all_velocities(time_cap_ms=time_cap, velocity_cap_px_s=vel_cap, 
                                    split_by_col=split_col)
        
        # Also create comparison matrix
        print("\nWould you like a comprehensive comparison matrix? (yes/no): ")
        if input().strip().lower() == 'yes':
            plotter.create_velocity_comparison_matrix(time_cap_ms=time_cap, 
                                                     velocity_cap=vel_cap)
        
        # Create overlay plot
        print("\nWould you like an overlay plot with all conditions together? (yes/no): ")
        if input().strip().lower() == 'yes':
            plotter.plot_overlay_all_conditions(time_cap_ms=time_cap, 
                                               velocity_cap=vel_cap)
        
        print("\n✅ Velocity plots complete!")
        print(f"📁 Saved to: {plotter.output_dir}/")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def clean_old_outputs():
    """Clean old analysis outputs using absolute paths"""
    outputs_dir = os.path.join(script_dir, 'analysis_outputs')
    print(f"\n🧹 Cleaning old analysis outputs at: {outputs_dir}")
    
    try:
        if not os.path.exists(outputs_dir):
            print("No analysis_outputs folder found - nothing to clean!")
            return True
        
        runs = [d for d in os.listdir(outputs_dir) 
                if os.path.isdir(os.path.join(outputs_dir, d)) and d.startswith('run_')]
        
        if not runs:
            print("No analysis runs found inside the folder!")
            return True
        
        runs.sort(reverse=True)
        print(f"\nFound {len(runs)} analysis runs")
        print("\nOptions:")
        print("  1. Keep only the most recent run")
        print("  2. Keep the 3 most recent runs")
        print("  3. Delete all runs")
        print("  0. Cancel")
        
        choice = input("\nChoice: ").strip()
        if choice == '1': to_delete = runs[1:]
        elif choice == '2': to_delete = runs[3:]
        elif choice == '3': to_delete = runs
        else: return True
        
        if to_delete and input(f"Delete {len(to_delete)} runs? (y/n): ").lower() == 'y':
            for run in to_delete:
                shutil.rmtree(os.path.join(outputs_dir, run))
                print(f"  ✓ Deleted {run}")
            print("✅ Cleaned.")
    except Exception as e:
        print(f"❌ Error: {e}")

def show_jupyter_help():
    """Show help about Jupyter notebooks"""
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
   from single_participant_analyzer import SingleParticipantAnalyzer
   
   participants, trials = load_data('serviceAccountKey.json')
   print(f"Loaded {len(participants)} participants")
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
    """Force reload data from Firebase"""
    print("\n🔄 Reloading data from Firebase...")
    try:
        load_data_with_cache(force_reload=True)
        print("\n✅ Data refreshed successfully!")
        return True
    except Exception as e:
        print(f"\n❌ Reload failed: {e}")
        return False

def main():
    if not (check_requirements() and check_credentials()): return
    
    # Mapping the menu options directly to the functions in this script
    actions = {
        '1': view_summary,
        '2': export_data,
        '3': clean_firestore_database,
        '4': lambda: load_data_with_cache(force_reload=True), # Refresh
        '5': run_analysis,
        '6': plot_all_velocities,
        '7': clean_old_outputs,
        '8': test_connection,
        '9': show_jupyter_help
    }

    while True:
        print_menu()
        choice = input("\nSelect an option: ").strip()
        if choice == '0': break
        if choice in actions:
            actions[choice]() # Calls the actual function
        else:
            print("❌ Invalid choice.")
        input("\nPress Enter to continue...")

if __name__ == "__main__":
    main()