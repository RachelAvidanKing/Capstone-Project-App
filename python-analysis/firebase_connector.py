"""
Firebase Data Connector
=======================
Handles fetching data from Firestore for analysis.

This module provides the FirebaseConnector class which manages:
- Connection to Firebase/Firestore
- Fetching participant demographics
- Fetching trial data from subcollections
- Merging participant data into trials
- Exporting data to CSV for backup/external analysis
"""

import firebase_admin
from firebase_admin import credentials, firestore
import pandas as pd
import json
import os
from typing import Tuple, Dict
from datetime import datetime


class FirebaseConnector:
    """
    Manages connection to Firebase and data retrieval.
    
    This class handles all interactions with the Firestore database,
    including fetching participants, trials, and exporting data.
    """
    
    # Default credentials file location (same directory as script)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

    def __init__(self, credentials_path=None):
        """
        Initialize Firebase connection.
        
        Args:
            credentials_path (str, optional): Path to Firebase service account key JSON.
                                             If None, uses DEFAULT_CREDENTIALS_FILENAME.
        """
        # Use provided path or default
        if credentials_path is None:
            credentials_path = self.DEFAULT_CREDENTIALS_FILENAME
            print(f"Using default credentials: {credentials_path}")

        # Initialize Firebase app if not already initialized
        if not firebase_admin._apps:
            cred = credentials.Certificate(credentials_path)
            firebase_admin.initialize_app(cred)
        
        self.db = firestore.client()
        print(f"✓ Connected to Firebase")
    
    def fetch_participants(self) -> pd.DataFrame:
        """
        Fetch all participants from Firestore.
        
        Returns:
            pd.DataFrame: DataFrame with participant demographics including:
                         participantId, age, gender, hasGlasses, hasAttentionDeficit,
                         jndThreshold, consentGiven, registrationTimestamp
        """
        print("Fetching participants...")
        
        participants_ref = self.db.collection('participants')
        participants_data = []
        
        # Fetch all participant documents
        for doc in participants_ref.stream():
            data = doc.to_dict()
            data['participantId'] = doc.id
            
            # Ensure all expected fields exist (fill missing with None)
            expected_fields = [
                'age', 'gender', 'hasGlasses', 'hasAttentionDeficit', 
                'jndThreshold', 'consentGiven', 'registrationTimestamp'
            ]
            for field in expected_fields:
                if field not in data:
                    data[field] = None
            
            participants_data.append(data)
        
        df = pd.DataFrame(participants_data)
        print(f"  → Loaded {len(df)} participants")
        
        # Print demographic summary if data exists
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
        """
        Fetch all target trial results from Firestore subcollections.
        
        Each participant has a subcollection called 'target_trials' containing
        their trial data. This method fetches all trials from all participants.
        
        Returns:
            pd.DataFrame: DataFrame with all trial data including:
                         trialId, participantId, trialType, reactionTime, movementPath, etc.
        """
        print("Fetching target trials...")
        
        trials_data = []
        
        try:
            # Get all participants
            participants_ref = self.db.collection('participants')
            participant_count = 0
            total_trials = 0
            
            # For each participant, fetch their trials subcollection
            for participant_doc in participants_ref.stream():
                participant_count += 1
                participant_id = participant_doc.id
                
                # Access the target_trials subcollection
                trials_ref = participant_doc.reference.collection('target_trials')
                
                for trial_doc in trials_ref.stream():
                    data = trial_doc.to_dict()
                    data['trialId'] = trial_doc.id
                    data['participantId'] = participant_id
                    
                    # Handle movementPath - should be an array, but convert if string
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
        Fetch all data and merge participant demographics into trials.
        
        This is the main method to use for getting data. It fetches both
        participants and trials, then merges demographic data into the trials
        dataframe so each trial has its participant's demographics attached.
        
        Returns:
            tuple: (participants_df, trials_df) where trials_df includes
                   merged participant demographics
        """
        participants_df = self.fetch_participants()
        trials_df = self.fetch_target_trials()
        
        # Merge participant info into trials
        if not participants_df.empty and not trials_df.empty:
            # Get demographic columns that exist in participants_df
            demographic_cols = ['participantId']
            for col in ['age', 'gender', 'hasGlasses', 'hasAttentionDeficit', 'jndThreshold']:
                if col in participants_df.columns:
                    demographic_cols.append(col)
            
            # Perform left merge to add demographics to trials
            trials_df = trials_df.merge(
                participants_df[demographic_cols],
                on='participantId',
                how='left',
                suffixes=('', '_participant')  # Avoid column name conflicts
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
                    output_dir: str = 'python-analysis/data_exports'):
        """
        Save dataframes to CSV for backup or external analysis.
        
        Args:
            participants_df (pd.DataFrame): Participant data
            trials_df (pd.DataFrame): Trial data
            output_dir (str): Directory to save CSV files
            
        Returns:
            tuple: (participants_file_path, trials_file_path)
        """
        # Create output directories
        os.makedirs(output_dir, exist_ok=True)
        participants_dir = os.path.join(output_dir, 'participants')
        trials_dir = os.path.join(output_dir, 'trials')
        os.makedirs(participants_dir, exist_ok=True)
        os.makedirs(trials_dir, exist_ok=True)
        
        # Generate timestamped filenames
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        participants_file = os.path.join(participants_dir, f"participants_{timestamp}.csv")
        trials_file = os.path.join(trials_dir, f"trials_{timestamp}.csv")
        
        # Prepare trials for export (convert movementPath to JSON string for CSV)
        trials_export = trials_df.copy()
        if 'movementPath' in trials_export.columns:
            trials_export['movementPath'] = trials_export['movementPath'].apply(
                lambda x: json.dumps(x) if isinstance(x, list) else x
            )
        
        # Save to CSV
        participants_df.to_csv(participants_file, index=False)
        trials_export.to_csv(trials_file, index=False)
        
        print(f"\n✓ Data exported:")
        print(f"  → {participants_file}")
        print(f"  → {trials_file}")
        
        return participants_file, trials_file
    
    def get_participant_trials(self, participant_id: str) -> pd.DataFrame:
        """
        Get all trials for a specific participant.
        
        Args:
            participant_id (str): The participant's ID
            
        Returns:
            pd.DataFrame: All trials for this participant
        """
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
        """
        Get quick summary statistics of the database.
        
        Returns:
            dict: Summary containing counts, distributions, and date ranges
        """
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
def load_data(credentials_filename: str) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Quick function to load all data with one call.
    
    Args:
        credentials_filename (str): Name of credentials file (e.g., 'serviceAccountKey.json')
        
    Returns:
        tuple: (participants_df, trials_df) with merged demographic data
    """
    current_dir = os.path.dirname(os.path.abspath(__file__))
    credentials_path = os.path.join(current_dir, credentials_filename)

    connector = FirebaseConnector(credentials_path)
    return connector.fetch_all_data()


# ============================================================================
# MAIN - Test the connector
# ============================================================================

if __name__ == "__main__":
    print("=" * 60)
    print("FIREBASE DATA CONNECTOR TEST")
    print("=" * 60)
    
    try:
        connector = FirebaseConnector()
        
        participants_df, trials_df = connector.fetch_all_data()
        
        print("\n" + "=" * 60)
        print("DATA SUMMARY")
        print("=" * 60)
        print(f"Total Participants: {len(participants_df)}")
        print(f"Total Trials: {len(trials_df)}")
        
        if not trials_df.empty:
            print(f"\nTrials by Type:")
            for trial_type, count in trials_df['trialType'].value_counts().items():
                print(f"  {trial_type}: {count}")
        
        if not participants_df.empty:
            print(f"\nParticipants by Gender:")
            for gender, count in participants_df['gender'].value_counts().items():
                print(f"  {gender}: {count}")
        
        # Export data
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