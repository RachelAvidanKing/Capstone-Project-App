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

    def remove_duplicate_trials(self, dry_run=True):
        """
        Scans all participants and removes duplicate trials based on timestamps.
        
        Args:
            dry_run (bool): If True, only prints what would be deleted. 
                            If False, actually deletes the documents.
        """
        print(f"\nSTARTING DUPLICATE SCAN (Dry Run: {dry_run})")
        print("=" * 60)
        
        participants_ref = self.db.collection('participants')
        total_deleted = 0
        
        # 1. Loop through every participant
        for participant in participants_ref.stream():
            p_id = participant.id
            trials_ref = participant.reference.collection('target_trials')
            
            # 2. Fetch all trials for this participant
            all_trials = []
            for doc in trials_ref.stream():
                data = doc.to_dict()
                # We store the ID so we know which one to delete later
                data['__doc_id'] = doc.id 
                all_trials.append(data)
            
            if not all_trials:
                continue

            # 3. Group trials by a unique timestamp
            # using 'goBeepTimestamp' (or 'trialStartTimestamp') as the unique fingerprint
            grouped_trials = defaultdict(list)
            
            for trial in all_trials:
                # Get the unique key. If 'goBeepTimestamp' is missing, try 'trialStartTimestamp'
                unique_key = trial.get('goBeepTimestamp')
                if unique_key is None:
                    unique_key = trial.get('trialStartTimestamp')
                
                # If neither exists,there is no way to safely identify duplicates, so skip
                if unique_key is not None:
                    grouped_trials[unique_key].append(trial)

            # 4. Check for duplicates in the groups
            duplicates_found_for_participant = 0
            
            for timestamp, trials_list in grouped_trials.items():
                # If there is more than 1 trial with this exact timestamp...
                if len(trials_list) > 1:
                    # Sort them to ensure to always keep/delete the same ones (deterministic)
                    # Keep the first one, delete the rest
                    trials_list.sort(key=lambda x: x['__doc_id'])
                    
                    trials_to_keep = trials_list[0]
                    trials_to_delete = trials_list[1:] # All items after the first one
                    
                    for duplicate in trials_to_delete:
                        doc_id = duplicate['__doc_id']
                        
                        if dry_run:
                            print(f"[Would Delete] Participant: {p_id} | Trial ID: {doc_id} | Timestamp: {timestamp}")
                        else:
                            # ACTUAL DELETION
                            trials_ref.document(doc_id).delete()
                            print(f"✓ [DELETED] Participant: {p_id} | Trial ID: {doc_id}")
                            
                        duplicates_found_for_participant += 1
                        total_deleted += 1

            if duplicates_found_for_participant > 0:
                print(f"  → Found {duplicates_found_for_participant} duplicates for participant {p_id}")

        print("=" * 60)
        if dry_run:
            print(f"SCAN COMPLETE. Found {total_deleted} duplicates to delete.")
            print("To actually delete them, change 'dry_run=True' to 'dry_run=False' at the bottom of the script.")
        else:
            print(f"CLEANUP COMPLETE. Successfully deleted {total_deleted} duplicate documents.")

# ==========================================
# MAIN EXECUTION
# ==========================================
if __name__ == "__main__":
    cleaner = FirebaseCleaner()
    
    # ---------------------------------------------------------
    # Run with dry_run=True first to verify!
    # ---------------------------------------------------------
    cleaner.remove_duplicate_trials(dry_run=True)
    
    # After verifying the output, uncomment the line below to actually delete duplicates
    # cleaner.remove_duplicate_trials(dry_run=False)