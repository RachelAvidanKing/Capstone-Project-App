"""
Firebase Data Connector
Handles fetching data from Firestore for analysis
"""

import firebase_admin
from firebase_admin import credentials, firestore
import pandas as pd
import json
import os
from typing import Tuple, Dict
from datetime import datetime

class FirebaseConnector:
    """Manages connection to Firebase and data retrieval"""
    
    def __init__(self, credentials_path: str = 'serviceAccountKey.json'):
        """
        Initialize Firebase connection
        
        Args:
            credentials_path: Path to Firebase service account key JSON
        """
        if not firebase_admin._apps:
            cred = credentials.Certificate(credentials_path)
            firebase_admin.initialize_app(cred)
        
        self.db = firestore.client()
        print(f"✓ Connected to Firebase")
    
    def fetch_participants(self) -> pd.DataFrame:
        """Fetch all participants from Firestore"""
        print("Fetching participants...")
        
        participants_ref = self.db.collection('participants')
        participants_data = []
        
        for doc in participants_ref.stream():
            data = doc.to_dict()
            data['participantId'] = doc.id
            
            # Ensure all expected fields exist
            expected_fields = ['age', 'gender', 'hasGlasses', 'hasAttentionDeficit', 
                             'jndThreshold', 'consentGiven', 'registrationTimestamp']
            for field in expected_fields:
                if field not in data:
                    data[field] = None
            
            participants_data.append(data)
        
        df = pd.DataFrame(participants_data)
        print(f"  → Loaded {len(df)} participants")
        
        # Debug: Print what demographic data we have
        if len(df) > 0:
            print(f"  → Demographics available:")
            if 'hasAttentionDeficit' in df.columns:
                print(f"     hasAttentionDeficit: {df['hasAttentionDeficit'].value_counts().to_dict()}")
            if 'hasGlasses' in df.columns:
                print(f"     hasGlasses: {df['hasGlasses'].value_counts().to_dict()}")
            if 'gender' in df.columns:
                print(f"     gender: {df['gender'].value_counts().to_dict()}")
        
        return df
    
    def fetch_target_trials(self) -> pd.DataFrame:
        """Fetch all target trial results from Firestore (from subcollections)"""
        print("Fetching target trials...")
        
        trials_data = []
        
        try:
            # Get all participants first
            participants_ref = self.db.collection('participants')
            participant_count = 0
            total_trials = 0
            
            for participant_doc in participants_ref.stream():
                participant_count += 1
                participant_id = participant_doc.id
                
                # Access the target_trials subcollection for this participant
                trials_ref = participant_doc.reference.collection('target_trials')
                
                for trial_doc in trials_ref.stream():
                    data = trial_doc.to_dict()
                    data['trialId'] = trial_doc.id
                    data['participantId'] = participant_id
                    
                    # movementPath should already be an array in Firestore
                    if 'movementPath' in data:
                        if isinstance(data['movementPath'], str):
                            try:
                                data['movementPath'] = json.loads(data['movementPath'])
                            except json.JSONDecodeError:
                                data['movementPath'] = []
                    
                    trials_data.append(data)
                    total_trials += 1
            
            print(f"  → Scanned {participant_count} participants")
            print(f"  → Found {total_trials} trials total")
        
        except Exception as e:
            print(f"  ✗ Error fetching trials: {e}")
            import traceback
            traceback.print_exc()
        
        df = pd.DataFrame(trials_data)
        print(f"  → Loaded {len(df)} trials into dataframe")
        return df
    
    def fetch_all_data(self) -> Tuple[pd.DataFrame, pd.DataFrame]:
        """
        Fetch all data and return as tuple
        
        Returns:
            (participants_df, trials_df)
        """
        participants_df = self.fetch_participants()
        trials_df = self.fetch_target_trials()
        
        # Merge participant info into trials
        if not participants_df.empty and not trials_df.empty:
            # Get columns that exist in participants_df
            demographic_cols = ['participantId']
            for col in ['age', 'gender', 'hasGlasses', 'hasAttentionDeficit', 'jndThreshold']:
                if col in participants_df.columns:
                    demographic_cols.append(col)
            
            trials_df = trials_df.merge(
                participants_df[demographic_cols],
                on='participantId',
                how='left',
                suffixes=('', '_participant')
            )
            print("✓ Merged participant data into trials")
            
            # Debug: Check if merge worked
            print(f"  → Trials now have these demographic columns:")
            for col in ['hasAttentionDeficit', 'hasGlasses', 'gender']:
                if col in trials_df.columns:
                    non_null = trials_df[col].notna().sum()
                    print(f"     {col}: {non_null}/{len(trials_df)} non-null values")
        
        return participants_df, trials_df
    
    def save_to_csv(self, participants_df: pd.DataFrame, trials_df: pd.DataFrame, 
                    output_dir: str = 'data_exports'):
        """Save dataframes to CSV for backup/external analysis"""
        os.makedirs(output_dir, exist_ok=True)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
        participants_file = f"{output_dir}/participants_{timestamp}.csv"
        trials_file = f"{output_dir}/trials_{timestamp}.csv"
        
        # For trials, convert movementPath to JSON string for CSV compatibility
        trials_export = trials_df.copy()
        if 'movementPath' in trials_export.columns:
            trials_export['movementPath'] = trials_export['movementPath'].apply(
                lambda x: json.dumps(x) if isinstance(x, list) else x
            )
        
        participants_df.to_csv(participants_file, index=False)
        trials_export.to_csv(trials_file, index=False)
        
        print(f"\n✓ Data exported:")
        print(f"  → {participants_file}")
        print(f"  → {trials_file}")
        
        return participants_file, trials_file
    
    def get_participant_trials(self, participant_id: str) -> pd.DataFrame:
        """Get all trials for a specific participant"""
        participant_ref = self.db.collection('participants').document(participant_id)
        trials_ref = participant_ref.collection('target_trials')
        
        trials_data = []
        for doc in trials_ref.stream():
            data = doc.to_dict()
            data['trialId'] = doc.id
            data['participantId'] = participant_id
            trials_data.append(data)
        
        return pd.DataFrame(trials_data)
    
    def get_trial_summary(self) -> Dict:
        """Get quick summary statistics"""
        participants_df, trials_df = self.fetch_all_data()
        
        summary = {
            'total_participants': len(participants_df),
            'total_trials': len(trials_df),
            'trials_by_type': trials_df['trialType'].value_counts().to_dict() if not trials_df.empty else {},
            'participants_by_gender': participants_df['gender'].value_counts().to_dict() if not participants_df.empty else {},
            'date_range': {
                'earliest_trial': pd.to_datetime(trials_df['trialStartTimestamp'], unit='ms').min() if not trials_df.empty else None,
                'latest_trial': pd.to_datetime(trials_df['trialStartTimestamp'], unit='ms').max() if not trials_df.empty else None,
            }
        }
        
        return summary


# Convenience function for quick data loading
def load_data(credentials_path: str = 'serviceAccountKey.json') -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Quick function to load all data
    
    Returns:
        (participants_df, trials_df)
    """
    connector = FirebaseConnector(credentials_path)
    return connector.fetch_all_data()


if __name__ == "__main__":
    # Test the connector
    print("=" * 60)
    print("FIREBASE DATA CONNECTOR TEST")
    print("=" * 60)
    
    try:
        connector = FirebaseConnector()
        
        # Fetch and display summary
        summary = connector.get_trial_summary()
        print("\n" + "=" * 60)
        print("DATA SUMMARY")
        print("=" * 60)
        print(f"Total Participants: {summary['total_participants']}")
        print(f"Total Trials: {summary['total_trials']}")
        print(f"\nTrials by Type:")
        for trial_type, count in summary['trials_by_type'].items():
            print(f"  {trial_type}: {count}")
        print(f"\nParticipants by Gender:")
        for gender, count in summary['participants_by_gender'].items():
            print(f"  {gender}: {count}")
        
        # Export data
        participants_df, trials_df = connector.fetch_all_data()
        if not participants_df.empty:
            connector.save_to_csv(participants_df, trials_df)
        
        print("\n✓ Connection test successful!")
        
    except Exception as e:
        print(f"\n✗ Error: {e}")
        print("\nMake sure:")
        print("  1. serviceAccountKey.json is in the same directory")
        print("  2. You have internet connection")
        print("  3. Firebase credentials are valid")
        import traceback
        traceback.print_exc()