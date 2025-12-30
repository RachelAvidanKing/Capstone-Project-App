import os
import firebase_admin
from firebase_admin import credentials, firestore
from collections import defaultdict

# Default credentials file
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')

class FirebaseCleaner:
    def __init__(self, credentials_path: str = DEFAULT_CREDENTIALS_FILENAME):
        if not firebase_admin._apps:
            cred = credentials.Certificate(credentials_path)
            firebase_admin.initialize_app(cred)
        self.db = firestore.client()
        print("✓ Connected to Firebase for cleanup")


    def remove_incomplete_sets(self, target_count=15, dry_run=True):
        """Removes participant data if trial count is not a multiple of target_count."""
        print(f"\nSTARTING INCOMPLETE SET SCAN (Dry Run: {dry_run})")
        participants_ref = self.db.collection('participants')
        total_deleted = 0
        
        for participant in participants_ref.stream():
            trials_ref = participant.reference.collection('target_trials')
            trials = list(trials_ref.stream())
            count = len(trials)
            
            if count > 0 and count % target_count != 0:
                print(f"  → Found incomplete set: {participant.id} ({count} trials)")
                for doc in trials:
                    if not dry_run:
                        doc.reference.delete()
                    total_deleted += 1
        return total_deleted
    
    def remove_duplicate_trials(self, dry_run=True):
        """Scans and removes duplicate trials based on timestamps."""
        print(f"\nSTARTING DUPLICATE SCAN (Dry Run: {dry_run})")
        participants_ref = self.db.collection('participants')
        total_deleted = 0
        
        for participant in participants_ref.stream():
            p_id = participant.id
            trials_ref = participant.reference.collection('target_trials')
            all_trials = [{'__doc_id': doc.id, **doc.to_dict()} for doc in trials_ref.stream()]
            
            if not all_trials: continue

            grouped_trials = defaultdict(list)
            for trial in all_trials:
                key = trial.get('goBeepTimestamp') or trial.get('trialStartTimestamp')
                if key: grouped_trials[key].append(trial)

            for timestamp, trials_list in grouped_trials.items():
                if len(trials_list) > 1:
                    trials_list.sort(key=lambda x: x['__doc_id'])
                    for duplicate in trials_list[1:]:
                        doc_id = duplicate['__doc_id']
                        if not dry_run:
                            trials_ref.document(doc_id).delete()
                        total_deleted += 1
        return total_deleted
    

# ==========================================
# MAIN EXECUTION
# ==========================================
if __name__ == "__main__":
    cleaner = FirebaseCleaner()
    
    # SAFETY: Keep dry_run=True for the first run!
    IS_DRY_RUN = True 
    
    # Step 1: Remove duplicates first
    cleaner.remove_duplicate_trials(dry_run=IS_DRY_RUN)
    
    # Step 2: Remove incomplete sets (anything not a multiple of 15)
    cleaner.remove_incomplete_sets(target_count=15, dry_run=IS_DRY_RUN)
    
    if IS_DRY_RUN:
        print("\n[NOTICE] This was a DRY RUN. No data was actually deleted.")
        print("To perform actual deletion, set 'IS_DRY_RUN = False' in the script.")