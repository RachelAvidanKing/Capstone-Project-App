"""
Quick Start Script
This is the easiest way to get started with analysis
Just run: python quick_start.py
"""

import os
import sys
from datetime import datetime

def check_requirements():
    """Check if all required packages are installed"""
    required = ['firebase_admin', 'pandas', 'matplotlib', 'seaborn', 'scipy']
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
    if not os.path.exists('serviceAccountKey.json'):
        print("❌ Firebase credentials not found!")
        print("\nPlease:")
        print("1. Go to Firebase Console → Project Settings → Service Accounts")
        print("2. Click 'Generate new private key'")
        print("3. Save the file as 'serviceAccountKey.json' in this folder")
        return False
    
    return True

def print_menu():
    """Display menu options"""
    print("\n" + "="*60)
    print("REACHING MOVEMENT ANALYSIS - QUICK START")
    print("="*60)
    print("\nWhat would you like to do?\n")
    print("1. Test Firebase connection")
    print("2. View data summary")
    print("3. Run full analysis (generates all plots and reports)")
    print("4. Export data to CSV")
    print("5. Open interactive analysis (Jupyter)")
    print("6. Analyze specific participant")
    print("0. Exit")
    print("\n" + "="*60)

def test_connection():
    """Test Firebase connection"""
    print("\n🔄 Testing Firebase connection...")
    try:
        from firebase_connector import FirebaseConnector
        connector = FirebaseConnector()
        summary = connector.get_trial_summary()
        
        print("\n✓ Connection successful!")
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
    print("\n🔄 Loading data...")
    try:
        from firebase_connector import load_data
        import pandas as pd
        
        participants_df, trials_df, _ = load_data()
        
        print("\n" + "="*60)
        print("DATA SUMMARY")
        print("="*60)
        
        print(f"\n📊 PARTICIPANTS: {len(participants_df)}")
        if not participants_df.empty:
            print(f"   Gender: {participants_df['gender'].value_counts().to_dict()}")
            print(f"   Age range: {participants_df['age'].min()}-{participants_df['age'].max()} years")
            print(f"   With glasses: {participants_df['hasGlasses'].sum()}")
            print(f"   With attention deficit: {participants_df['hasAttentionDeficit'].sum()}")
        
        print(f"\n📊 TRIALS: {len(trials_df)}")
        if not trials_df.empty:
            print(f"   By type: {trials_df['trialType'].value_counts().to_dict()}")
            print(f"\n   Performance metrics:")
            print(f"   - Avg reaction time: {trials_df['reactionTime'].mean():.1f} ms")
            print(f"   - Avg movement time: {trials_df['movementTime'].mean():.1f} ms")
            print(f"   - Avg path length: {trials_df['pathLength'].mean():.1f} pixels")
        
        return True
    except Exception as e:
        print(f"\n❌ Error loading data: {e}")
        return False

def run_analysis():
    """Run full analysis"""
    print("\n🔄 Running complete analysis...")
    print("This may take a minute...")
    DEFAULT_CREDENTIALS_FILENAME = 'serviceAccountKey.json'
    try:
        from firebase_connector import load_data
        from subliminal_priming_analyzer import SubliminalPrimingAnalyzer
        
        participants_df, trials_df, _ = load_data(DEFAULT_CREDENTIALS_FILENAME)
        
        if trials_df.empty:
            print("\n⚠️  No trial data found. Cannot run analysis.")
            return False
        
        analyzer = SubliminalPrimingAnalyzer(participants_df, trials_df)
        analyzer.run_full_analysis()
        
        print("\n✓ Analysis complete!")
        print(f"\nResults saved in:")
        print(f"  📁 analysis_outputs/")
        print(f"  📁 analysis_outputs/figures/")
        
        return True
    except Exception as e:
        print(f"\n❌ Analysis failed: {e}")
        import traceback
        traceback.print_exc()
        return False

def export_data():
    """Export data to CSV"""
    print("\n🔄 Exporting data to CSV...")
    
    try:
        from firebase_connector import FirebaseConnector
        
        connector = FirebaseConnector()
        participants_df, trials_df, _ = connector.fetch_all_data()
        
        if participants_df.empty and trials_df.empty:
            print("\n⚠️  No data to export")
            return False
        
        files = connector.save_to_csv(participants_df, trials_df)
        
        print("\n✓ Data exported successfully!")
        print("You can now open these files in Excel, SPSS, or any spreadsheet software.")
        
        return True
    except Exception as e:
        print(f"\n❌ Export failed: {e}")
        return False

def open_jupyter():
    """Open Jupyter notebook"""
    print("\n🔄 Starting Jupyter notebook...")
    
    try:
        import jupyter
        os.system('jupyter notebook')
    except ImportError:
        print("\n❌ Jupyter not installed")
        print("Install it with: pip install jupyter")
        print("Then run: jupyter notebook")
        return False

def analyze_participant():
    """Analyze specific participant"""
    print("\n🔄 Loading participants...")
    
    try:
        from firebase_connector import FirebaseConnector
        
        connector = FirebaseConnector()
        participants_df, trials_df, _ = connector.fetch_all_data()
        
        if participants_df.empty:
            print("\n⚠️  No participants found")
            return False
        
        print("\nAvailable participants:")
        for idx, pid in enumerate(participants_df['participantId'].head(10), 1):
            p_trials = len(trials_df[trials_df['participantId'] == pid])
            print(f"  {idx}. {pid} ({p_trials} trials)")
        
        if len(participants_df) > 10:
            print(f"  ... and {len(participants_df) - 10} more")
        
        choice = input("\nEnter participant ID (or number): ").strip()
        
        # Check if they entered a number
        try:
            idx = int(choice) - 1
            if 0 <= idx < len(participants_df):
                participant_id = participants_df.iloc[idx]['participantId']
            else:
                print("Invalid number")
                return False
        except ValueError:
            participant_id = choice
        
        # Get participant data
        p_data = participants_df[participants_df['participantId'] == participant_id]
        if p_data.empty:
            print(f"\n❌ Participant {participant_id} not found")
            return False
        
        p_trials = trials_df[trials_df['participantId'] == participant_id]
        
        print(f"\n" + "="*60)
        print(f"PARTICIPANT: {participant_id}")
        print("="*60)
        
        p_info = p_data.iloc[0]
        print(f"\nDemographics:")
        print(f"  Age: {p_info['age']}")
        print(f"  Gender: {p_info['gender']}")
        print(f"  Glasses: {p_info['hasGlasses']}")
        print(f"  Attention deficit: {p_info['hasAttentionDeficit']}")
        if p_info.get('jndThreshold'):
            print(f"  JND threshold: {p_info['jndThreshold']}")
        
        print(f"\nPerformance:")
        print(f"  Total trials: {len(p_trials)}")
        if not p_trials.empty:
            print(f"  Trial types: {p_trials['trialType'].value_counts().to_dict()}")
            print(f"  Avg reaction time: {p_trials['reactionTime'].mean():.1f} ms")
            print(f"  Avg movement time: {p_trials['movementTime'].mean():.1f} ms")
        
        # Save participant report
        filename = f"participant_{participant_id}_report.txt"
        with open(filename, 'w') as f:
            f.write(f"Participant Report: {participant_id}\n")
            f.write(f"Generated: {datetime.now()}\n\n")
            f.write(f"Demographics:\n")
            f.write(f"  Age: {p_info['age']}\n")
            f.write(f"  Gender: {p_info['gender']}\n")
            f.write(f"  Glasses: {p_info['hasGlasses']}\n")
            f.write(f"  Attention deficit: {p_info['hasAttentionDeficit']}\n\n")
            f.write(f"Trial Summary:\n")
            f.write(p_trials[['trialNumber', 'trialType', 'reactionTime', 'movementTime']].to_string())
        
        print(f"\n✓ Report saved to: {filename}")
        
        # Export trials to CSV
        csv_file = f"participant_{participant_id}_trials.csv"
        p_trials.to_csv(csv_file, index=False)
        print(f"✓ Trials exported to: {csv_file}")
        
        return True
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Main program loop"""
    print("\n" + "="*60)
    print("REACHING MOVEMENT ANALYSIS TOOL")
    print("="*60)
    
    # Check requirements
    print("\n🔍 Checking system requirements...")
    if not check_requirements():
        return
    print("✓ All packages installed")
    
    if not check_credentials():
        return
    print("✓ Firebase credentials found")
    
    # Main loop
    while True:
        print_menu()
        
        choice = input("\nEnter your choice (0-6): ").strip()
        
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
            open_jupyter()
        elif choice == '6':
            analyze_participant()
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