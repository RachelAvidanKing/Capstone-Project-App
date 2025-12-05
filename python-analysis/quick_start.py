"""
Quick Start Script
This is the easiest way to get started with analysis
Just run: python quick_start.py
"""

import os
import sys
from datetime import datetime

# Default credentials file
DEFAULT_CREDENTIALS_FILENAME = 'serviceAccountKey.json'

# Cache for data to avoid re-downloading
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
        
        # Update cache
        _data_cache['participants'] = participants_df
        _data_cache['trials'] = trials_df
        _data_cache['last_loaded'] = datetime.now()
        
        print(f"✅ Loaded {len(participants_df)} participants, {len(trials_df)} trials")
    else:
        print("📦 Using cached data...")
        participants_df = _data_cache['participants']
        trials_df = _data_cache['trials']
    
    return participants_df, trials_df
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

def print_menu():
    """Display menu options"""
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS - QUICK START")
    print("="*70)
    
    # Show cache status
    if _data_cache['last_loaded']:
        cache_time = _data_cache['last_loaded'].strftime('%H:%M:%S')
        p_count = len(_data_cache['participants']) if _data_cache['participants'] is not None else 0
        t_count = len(_data_cache['trials']) if _data_cache['trials'] is not None else 0
        print(f"📦 Data cached: {p_count} participants, {t_count} trials (loaded at {cache_time})")
    
    print("\nWhat would you like to do?\n")
    print("1. Test Firebase connection")
    print("2. View data summary")
    print("3. Run full analysis (generates all plots and reports)")
    print("4. Export data to CSV")
    print("5. Analyze specific participant")
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
        
        if summary['total_participants'] > 0 and 'participants_by_gender' in summary:
            print(f"  Gender distribution: {summary['participants_by_gender']}")
        
        return True
    except Exception as e:
        print(f"\n❌ Connection failed: {e}")
        print("\nTroubleshooting:")
        print(f"  1. Check that '{DEFAULT_CREDENTIALS_FILENAME}' exists in this folder")
        print("  2. Verify you have internet connection")
        print("  3. Ensure Firebase credentials are valid")
        return False

def view_summary():
    """Display quick data summary"""
    print("\n📊 Loading data...")
    try:
        import pandas as pd
        
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
                print(f"   With attention deficit: {participants_df['hasAttentionDeficit'].sum()}")
        
        print(f"\n📊 TRIALS: {len(trials_df)}")
        if not trials_df.empty:
            if 'trialType' in trials_df.columns:
                print(f"   By type: {trials_df['trialType'].value_counts().to_dict()}")
            print(f"\n   Performance metrics:")
            if 'reactionTime' in trials_df.columns:
                rt_data = trials_df['reactionTime'].dropna()
                if len(rt_data) > 0:
                    print(f"   - Avg reaction time: {rt_data.mean():.1f} ms")
            if 'movementTime' in trials_df.columns:
                mt_data = trials_df['movementTime'].dropna()
                if len(mt_data) > 0:
                    print(f"   - Avg movement time: {mt_data.mean():.1f} ms")
            if 'pathLength' in trials_df.columns:
                pl_data = trials_df['pathLength'].dropna()
                if len(pl_data) > 0:
                    print(f"   - Avg path length: {pl_data.mean():.1f} pixels")
        
        return True
    except Exception as e:
        print(f"\n❌ Error loading data: {e}")
        import traceback
        traceback.print_exc()
        return False

def run_analysis():
    """Run full analysis"""
    print("\n🔬 Running complete analysis...")
    print("This may take a minute...")
    
    try:
        from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
        
        # Load data (using cache if available)
        participants_df, trials_df = load_data_with_cache()
        
        if trials_df.empty:
            print("\n⚠️  No trial data found. Cannot run analysis.")
            return False
        
        print(f"✓ Using {len(participants_df)} participants, {len(trials_df)} trials")
        
        # Create analyzer - NOTE: Order changed to match new constructor
        analyzer = SubliminalPrimingAnalyzer(trials_df, participants_df)
        
        # Run all analyses
        print("\n📊 Running main hypothesis tests...")
        analyzer.test_main_hypothesis()
        
        print("\n👥 Analyzing demographic effects...")
        analyzer.analyze_demographic_effects()
        
        print("\n🎯 Analyzing target effects...")
        analyzer.analyze_by_target()
        
        print("\n📈 Generating velocity profiles...")
        analyzer.analyze_velocity_profiles()
        
        print("\n🗺️  Plotting movement paths...")
        analyzer.plot_movement_paths()
        
        print("\n📊 Creating summary plots...")
        analyzer.create_summary_plots()
        
        print("\n💾 Saving report...")
        analyzer.save_report()
        
        print("\n" + "="*70)
        print("✅ ANALYSIS COMPLETE!")
        print("="*70)
        print(f"\nResults saved in:")
        print(f"  📁 {analyzer.output_dir}/")
        print(f"     ├─ subliminal_priming_report.txt")
        print(f"     └─ figures/")
        print(f"        ├─ demographic_comparisons/")
        print(f"        │  ├─ comparison_adhd.png")
        print(f"        │  ├─ comparison_glasses.png")
        print(f"        │  └─ comparison_gender.png")
        print(f"        ├─ movement_paths/")
        print(f"        │  └─ paths_by_trial_type.png")
        print(f"        ├─ summary_comparison_overall.png")
        print(f"        ├─ target_comparison.png")
        print(f"        └─ velocity_profiles.png")
        
        return True
    except Exception as e:
        print(f"\n❌ Analysis failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def export_data():
    """Export data to CSV"""
    print("\n💾 Exporting data to CSV...")
    
    try:
        from firebase_connector import FirebaseConnector
        
        connector = FirebaseConnector(DEFAULT_CREDENTIALS_FILENAME)
        participants_df, trials_df = connector.fetch_all_data()
        
        if participants_df.empty and trials_df.empty:
            print("\n⚠️  No data to export")
            return False
        
        files = connector.save_to_csv(participants_df, trials_df)
        
        print("\n✅ Data exported successfully!")
        print("\nFiles created:")
        print(f"  📄 {files[0]}")
        print(f"  📄 {files[1]}")
        print("\nYou can now open these files in Excel, SPSS, or any spreadsheet software.")
        
        return True
    except Exception as e:
        print(f"\n❌ Export failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def analyze_participant():
    """Analyze specific participant"""
    print("\n👤 Loading participants...")
    
    try:
        # Use cached data
        participants_df, trials_df = load_data_with_cache()
        
        if participants_df.empty:
            print("\n⚠️  No participants found")
            return False
        
        print("\nAvailable participants:")
        for idx, row in enumerate(participants_df.head(10).itertuples(), 1):
            pid = row.participantId
            p_trials = len(trials_df[trials_df['participantId'] == pid])
            age = getattr(row, 'age', 'N/A')
            gender = getattr(row, 'gender', 'N/A')
            print(f"  {idx}. {pid} ({p_trials} trials, Age: {age}, Gender: {gender})")
        
        if len(participants_df) > 10:
            print(f"  ... and {len(participants_df) - 10} more")
        
        choice = input("\nEnter participant ID or number (1-{}): ".format(len(participants_df))).strip()
        
        # Determine participant_id
        participant_id = None
        
        # Try as number first
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(participants_df):
                participant_id = participants_df.iloc[idx]['participantId']
            else:
                print(f"❌ Number out of range (1-{len(participants_df)})")
                return False
        except ValueError:
            # Not a number, treat as participant ID
            participant_id = choice
        
        # Get participant data
        p_data = participants_df[participants_df['participantId'] == participant_id]
        if p_data.empty:
            print(f"\n❌ Participant {participant_id} not found")
            return False
        
        p_trials = trials_df[trials_df['participantId'] == participant_id]
        
        print(f"\n" + "="*70)
        print(f"PARTICIPANT: {participant_id}")
        print("="*70)
        
        p_info = p_data.iloc[0]
        print(f"\nDemographics:")
        print(f"  Age: {p_info.get('age', 'N/A')}")
        print(f"  Gender: {p_info.get('gender', 'N/A')}")
        print(f"  Glasses: {p_info.get('hasGlasses', 'N/A')}")
        print(f"  Attention deficit: {p_info.get('hasAttentionDeficit', 'N/A')}")
        if p_info.get('jndThreshold'):
            print(f"  JND threshold: {p_info['jndThreshold']}")
        
        print(f"\nPerformance:")
        print(f"  Total trials: {len(p_trials)}")
        if not p_trials.empty:
            if 'trialType' in p_trials.columns:
                print(f"  Trial types: {p_trials['trialType'].value_counts().to_dict()}")
            if 'reactionTime' in p_trials.columns:
                rt_data = p_trials['reactionTime'].dropna()
                if len(rt_data) > 0:
                    print(f"  Avg reaction time: {rt_data.mean():.1f} ms")
            if 'movementTime' in p_trials.columns:
                mt_data = p_trials['movementTime'].dropna()
                if len(mt_data) > 0:
                    print(f"  Avg movement time: {mt_data.mean():.1f} ms")
        
        # Save participant report
        filename = f"participant_{participant_id}_report.txt"
        with open(filename, 'w') as f:
            f.write(f"Participant Report: {participant_id}\n")
            f.write(f"Generated: {datetime.now()}\n\n")
            f.write(f"Demographics:\n")
            f.write(f"  Age: {p_info.get('age', 'N/A')}\n")
            f.write(f"  Gender: {p_info.get('gender', 'N/A')}\n")
            f.write(f"  Glasses: {p_info.get('hasGlasses', 'N/A')}\n")
            f.write(f"  Attention deficit: {p_info.get('hasAttentionDeficit', 'N/A')}\n\n")
            f.write(f"Trial Summary:\n")
            if not p_trials.empty:
                cols_to_export = ['trialNumber', 'trialType', 'reactionTime', 'movementTime']
                available_cols = [col for col in cols_to_export if col in p_trials.columns]
                if available_cols:
                    f.write(p_trials[available_cols].to_string())
                else:
                    f.write("No trial data available\n")
        
        print(f"\n✅ Report saved to: {filename}")
        
        # Export trials to CSV
        csv_file = f"participant_{participant_id}_trials.csv"
        p_trials.to_csv(csv_file, index=False)
        print(f"✅ Trials exported to: {csv_file}")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def clean_old_outputs():
    """Clean old analysis outputs to free up space"""
    print("\n🧹 Cleaning old analysis outputs...")
    
    try:
        import shutil
        
        if not os.path.exists('analysis_outputs'):
            print("No analysis_outputs folder found - nothing to clean!")
            return True
        
        # List all runs
        runs = [d for d in os.listdir('analysis_outputs') 
                if os.path.isdir(os.path.join('analysis_outputs', d)) and d.startswith('run_')]
        
        if not runs:
            print("No analysis runs found!")
            return True
        
        runs.sort(reverse=True)  # Most recent first
        
        print(f"\nFound {len(runs)} analysis runs:")
        for idx, run in enumerate(runs[:10], 1):
            size = get_folder_size(os.path.join('analysis_outputs', run))
            print(f"  {idx}. {run} ({size:.1f} MB)")
        
        if len(runs) > 10:
            print(f"  ... and {len(runs) - 10} more")
        
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
            total_freed = 0
            for run in to_delete:
                run_path = os.path.join('analysis_outputs', run)
                size = get_folder_size(run_path)
                shutil.rmtree(run_path)
                total_freed += size
                print(f"  ✓ Deleted {run}")
            
            print(f"\n✅ Freed {total_freed:.1f} MB of space")
            return True
        else:
            print("Cancelled")
            return False
            
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def get_folder_size(folder_path):
    """Get folder size in MB"""
    total_size = 0
    for dirpath, dirnames, filenames in os.walk(folder_path):
        for filename in filenames:
            filepath = os.path.join(dirpath, filename)
            if os.path.exists(filepath):
                total_size += os.path.getsize(filepath)
    return total_size / (1024 * 1024)  # Convert to MB

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

WHY USE IT?
  • Great for exploring data interactively
  • You can modify code and re-run without starting over
  • Perfect for learning and experimentation
  
HOW TO GET STARTED:

1. Install Jupyter (if you haven't already):
   pip install jupyter

2. Start Jupyter in this folder:
   jupyter notebook

3. This will open in your web browser automatically

4. Create a new notebook (click "New" → "Python 3")

5. Try this simple example in the first cell:

   from firebase_connector import load_data
   participants, trials = load_data('serviceAccountKey.json')
   print(f"Loaded {len(participants)} participants")
   
6. Press Shift+Enter to run the cell

7. Create more cells to explore your data:

   # See trial types
   trials['trialType'].value_counts()
   
   # Plot reaction times
   import matplotlib.pyplot as plt
   trials['reactionTime'].hist()
   plt.show()

TIPS:
  • Press Shift+Enter to run a cell
  • Press ESC then A to add a cell above
  • Press ESC then B to add a cell below
  • Save often with Ctrl+S

LEARNING RESOURCES:
  • https://jupyter.org/try
  • Built-in help: Press H in Jupyter for keyboard shortcuts

If you're happy with the automated analysis (option 3), you don't need
Jupyter at all! It's just an alternative way to explore data.
""")
    
    choice = input("\nWould you like to start Jupyter now? (yes/no): ").strip().lower()
    
    if choice == 'yes':
        try:
            import subprocess
            print("\n🚀 Starting Jupyter Notebook...")
            print("A browser window will open shortly.")
            print("Press Ctrl+C in this terminal to stop Jupyter when done.\n")
            subprocess.run(['jupyter', 'notebook'])
        except FileNotFoundError:
            print("\n❌ Jupyter not installed!")
            print("Install it with: pip install jupyter")
        except Exception as e:
            print(f"\n❌ Error starting Jupyter: {e}")

def reload_data():
    """Force reload data from Firebase"""
    print("\n🔄 Reloading data from Firebase...")
    try:
        load_data_with_cache(force_reload=True)
        print("\n✅ Data refreshed successfully!")
        return True
    except Exception as e:
        print(f"\n❌ Reload failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Main program loop"""
    print("\n" + "="*70)
    print("REACHING MOVEMENT ANALYSIS TOOL")
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
            print("\n👋 Goodbye!")
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
            analyze_participant()
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
        print("\n\n👋 Interrupted by user. Goodbye!")
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()