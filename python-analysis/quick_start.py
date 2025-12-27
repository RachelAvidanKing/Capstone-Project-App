"""
Quick Start Script - Command Line Interface

NOTE: For most userss, the web interface is recommended:
  1. Start backend: cd python-analysis && python backend_api.py
  2. Start frontend: cd web-interface && npm start
  3. Open: http://localhost:3000

This script provides a command-line alternative for advanced users.
"""

import os
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

def check_requirements():
    """Check if all required packages are installed"""
    required = ['firebase_admin', 'pandas', 'matplotlib', 'seaborn', 'scipy', 'numpy']
    missing = []
    
    for package in required:
        try:
            __import__(package)
        except ImportError:
            missing.append(package)
    
    if missing:
        print("❌ Missing required packages:")
        for pkg in missing:
            print(f"   - {pkg}")
        print("\nPlease run: pip install -r requirements.txt")
        return False
    
    return True

def check_credentials():
    """Check if Firebase credentials exist"""
    if not os.path.exists(DEFAULT_CREDENTIALS_FILENAME):
        print("❌ Firebase credentials not found!")
        print(f"\nLooking for: {DEFAULT_CREDENTIALS_FILENAME}")
        print("\nPlease:")
        print("1. Go to Firebase Console → Project Settings → Service Accounts")
        print("2. Click 'Generate new private key'")
        print(f"3. Save the file as '{DEFAULT_CREDENTIALS_FILENAME}' in this folder")
        return False
    
    return True

def load_data_with_cache(force_reload=False):
    """Load data from Firebase or cache"""
    from firebase_connector import load_data
    
    if force_reload or _data_cache['participants'] is None:
        if force_reload:
            print("🔄 Reloading data from Firebase...")
        else:
            print("📡 Loading data from Firebase...")
        
        participants_df, trials_df = load_data(DEFAULT_CREDENTIALS_FILENAME)
        
        _data_cache['participants'] = participants_df
        _data_cache['trials'] = trials_df
        _data_cache['last_loaded'] = datetime.now()
        
        print(f"✅ Loaded {len(participants_df)} participants, {len(trials_df)} trials")
    else:
        print("📦 Using cached data...")
        participants_df = _data_cache['participants']
        trials_df = _data_cache['trials']
    
    return participants_df, trials_df

def print_menu():
    """Display menu options"""
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS - ENHANCED VERSION")
    print("="*70)
    
    if _data_cache['last_loaded']:
        cache_time = _data_cache['last_loaded'].strftime('%H:%M:%S')
        p_count = len(_data_cache['participants']) if _data_cache['participants'] is not None else 0
        t_count = len(_data_cache['trials']) if _data_cache['trials'] is not None else 0
        print(f"📦 Data cached: {p_count} participants, {t_count} trials (loaded at {cache_time})")
    
    print("\nWhat would you like to do?\n")
    print("1. Test Firebase connection")
    print("2. View data summary")
    print("3. Run full analysis (generates all plots and reports)")
    print("4. Export data to CSV (raw or processed)")
    print("5. Plot all velocities")
    print("6. Clean old analysis outputs")
    print("7. Help - Learn about Jupyter notebooks")
    print("8. Reload data from Firebase (refresh cache)")
    print("0. Exit")
    print("\n" + "="*70)

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
    print("\n📊 Loading data...")
    try:
        participants_df, trials_df = load_data_with_cache()
        
        print("\n" + "="*70)
        print("DATA SUMMARY")
        print("="*70)
        
        print(f"\n👥 PARTICIPANTS: {len(participants_df)}")
        if not participants_df.empty:
            if 'gender' in participants_df.columns:
                print(f"   Gender: {participants_df['gender'].value_counts().to_dict()}")
            if 'age' in participants_df.columns:
                age_data = participants_df['age'].dropna()
                if len(age_data) > 0:
                    print(f"   Age range: {age_data.min():.0f}-{age_data.max():.0f} years")
            if 'hasGlasses' in participants_df.columns:
                print(f"   With glasses: {participants_df['hasGlasses'].sum()}")
            if 'hasAttentionDeficit' in participants_df.columns:
                print(f"   With ADHD: {participants_df['hasAttentionDeficit'].sum()}")
        
        print(f"\n📊 TRIALS: {len(trials_df)}")
        if not trials_df.empty:
            if 'trialType' in trials_df.columns:
                print(f"   By type: {trials_df['trialType'].value_counts().to_dict()}")
            print(f"\n   Performance metrics:")
            if 'reactionTime' in trials_df.columns:
                rt_data = trials_df['reactionTime'].dropna()
                if len(rt_data) > 0:
                    print(f"   - Avg reaction time: {rt_data.mean():.1f} ms")
        
        return True
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

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

def export_data():
    """Export data with specific user choices"""
    print("\n💾 DATA EXPORT")
    print("1. Raw Data (Participants & Trials from Firebase)")
    print("2. Processed Data (Calculated Metrics: Jerk, Peaks, etc.)")
    choice = input("Select export type (1-2): ").strip()

    participants_df, trials_df = load_data_with_cache()
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')

    if choice == '1':
        # Raw Export logic
        filename = f"raw_data_{timestamp}.xlsx"
        with pd.ExcelWriter(filename) as writer:
            participants_df.to_excel(writer, sheet_name='Participants', index=False)
            trials_df.to_excel(writer, sheet_name='Trials', index=False)
        print(f"✅ Raw data saved to {filename}")
    
    elif choice == '2':
        # Processed Export logic
        from backend_api import calculate_advanced_metrics
        processed_df = calculate_advanced_metrics(trials_df)
        filename = f"processed_metrics_{timestamp}.csv"
        processed_df.to_csv(filename, index=False)
        print(f"✅ Processed metrics saved to {filename}")

    else:
        print("❌ Invalid choice")
        return False

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
        print("  Recommended: 10000 ms (10 seconds)")
        print("  Default: 10000 ms")
        
        time_cap_input = input("\nEnter time cap in ms (press Enter for 10000): ").strip()
        
        try:
            time_cap = int(time_cap_input) if time_cap_input else 10000
        except ValueError:
            print("Invalid input, using default 10000 ms")
            time_cap = 10000
        
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
        
        print("\n✅ Velocity plots complete!")
        print(f"📁 Saved to: {plotter.output_dir}/")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def clean_old_outputs():
    """Clean old analysis outputs"""
    print("\n🧹 Cleaning old analysis outputs...")
    
    try:
        import shutil
        
        if not os.path.exists('analysis_outputs'):
            print("No analysis_outputs folder found - nothing to clean!")
            return True
        
        runs = [d for d in os.listdir('analysis_outputs') 
                if os.path.isdir(os.path.join('analysis_outputs', d)) and d.startswith('run_')]
        
        if not runs:
            print("No analysis runs found!")
            return True
        
        runs.sort(reverse=True)
        
        print(f"\nFound {len(runs)} analysis runs")
        print("\nOptions:")
        print("  1. Keep only the most recent run")
        print("  2. Keep the 3 most recent runs")
        print("  3. Delete all runs")
        print("  0. Cancel")
        
        choice = input("\nYour choice: ").strip()
        
        if choice == '0':
            print("Cancelled")
            return True
        elif choice == '1':
            to_delete = runs[1:]
        elif choice == '2':
            to_delete = runs[3:]
        elif choice == '3':
            to_delete = runs
        else:
            print("Invalid choice")
            return False
        
        if not to_delete:
            print("Nothing to delete!")
            return True
        
        confirm = input(f"\n⚠️  Delete {len(to_delete)} runs? (yes/no): ").strip().lower()
        
        if confirm == 'yes':
            for run in to_delete:
                run_path = os.path.join('analysis_outputs', run)
                shutil.rmtree(run_path)
                print(f"  ✓ Deleted {run}")
            
            print(f"\n✅ Deleted {len(to_delete)} runs")
            return True
        else:
            print("Cancelled")
            return False
            
    except Exception as e:
        print(f"\n❌ Error: {e}")
        return False

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
    """Main program loop"""
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS TOOL - ENHANCED")
    print("="*70)
    
    # Check requirements
    print("\n🔍 Checking system requirements...")
    if not check_requirements():
        return
    print("✅ All packages installed")
    
    if not check_credentials():
        return
    print("✅ Firebase credentials found")
    
    # Main loop
    while True:
        print_menu()
        
        choice = input("\nEnter your choice (0-8): ").strip()
        
        if choice == '0':
            print("\nGoodbye!")
            break
        elif choice == '1':
            test_connection()
        elif choice == '2':
            view_summary()
        elif choice == '3':
            run_analysis()
        elif choice == '4':
            export_data()
        elif choice == '5':
            plot_all_velocities()
        elif choice == '6':
            clean_old_outputs()
        elif choice == '7':
            show_jupyter_help()
        elif choice == '8':
            reload_data()
        else:
            print("\n❌ Invalid choice. Please try again.")
        
        input("\nPress Enter to continue...")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nInterrupted by user. Goodbye!")
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()