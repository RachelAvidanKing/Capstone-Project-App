"""
Firebase Database Cleaner
==========================
Removes duplicate trials and incomplete trial sets from Firestore.

This module provides tools to clean the Firebase database by:
1. Removing duplicate trials (same timestamp, different document IDs)
2. Removing incomplete trial sets (not multiples of 15 trials per participant)

IMPORTANT: Always run with dry_run=True first to preview changes!
"""

import os
import firebase_admin
from firebase_admin import credentials, firestore
from collections import defaultdict

# Default credentials file location
script_dir = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CREDENTIALS_FILENAME = os.path.join(script_dir, 'serviceAccountKey.json')


class FirebaseCleaner:
    """
    Handles cleanup operations on the Firebase database.
    
    This class provides safe cleanup operations with dry-run mode
    to preview changes before actually deleting data.
    """
    
    def __init__(self, credentials_path: str = DEFAULT_CREDENTIALS_FILENAME):
        """
        Initialize Firebase connection for cleanup operations.
        
        Args:
            credentials_path (str): Path to Firebase service account key
        """
        # Initialize Firebase if not already initialized
        if not firebase_admin._apps:
            cred = credentials.Certificate(credentials_path)
            firebase_admin.initialize_app(cred)
        
        self.db = firestore.client()
        print("✓ Connected to Firebase for cleanup")

    def remove_incomplete_sets(self, target_count: int = 15, dry_run: bool = True) -> int:
        """
        Remove participants whose trial count is not a multiple of target_count.
        
        Each participant should have a complete set of trials (15 in this experiment).
        This method removes all trials for participants with incomplete sets.
        
        Args:
            target_count (int): Expected number of trials per complete set (default: 15)
            dry_run (bool): If True, only report what would be deleted without deleting
            
        Returns:
            int: Number of trials that were (or would be) deleted
        """
        mode_text = "DRY RUN" if dry_run else "ACTUAL CLEANUP"
        print(f"\n{'='*60}")
        print(f"INCOMPLETE SET SCAN ({mode_text})")
        print(f"{'='*60}")
        
        participants_ref = self.db.collection('participants')
        total_deleted = 0
        
        for participant in participants_ref.stream():
            trials_ref = participant.reference.collection('target_trials')
            trials = list(trials_ref.stream())
            count = len(trials)
            
            # Check if trial count is incomplete (not a multiple of target_count)
            if count > 0 and count % target_count != 0:
                print(f"  → Found incomplete set: {participant.id} ({count} trials)")
                
                # Delete all trials for this participant
                for doc in trials:
                    if not dry_run:
                        doc.reference.delete()
                    total_deleted += 1
        
        print(f"{'='*60}")
        print(f"Incomplete Set Scan Complete: Found {total_deleted} trials")
        print(f"{'='*60}")
        
        return total_deleted
    
    def remove_duplicate_trials(self, dry_run: bool = True) -> int:
        """
        Scan and remove duplicate trials based on timestamps.
        
        Duplicates are identified by having the same timestamp (either goBeepTimestamp
        or trialStartTimestamp). When duplicates are found, the first one (by document ID)
        is kept, and the rest are deleted.
        
        Args:
            dry_run (bool): If True, only report what would be deleted without deleting
            
        Returns:
            int: Number of duplicate trials that were (or would be) deleted
        """
        mode_text = "DRY RUN" if dry_run else "ACTUAL CLEANUP"
        print(f"\n{'='*60}")
        print(f"DUPLICATE SCAN ({mode_text})")
        print(f"{'='*60}")
        
        participants_ref = self.db.collection('participants')
        total_deleted = 0
        
        for participant in participants_ref.stream():
            participant_id = participant.id
            trials_ref = participant.reference.collection('target_trials')
            
            # Fetch all trials for this participant
            all_trials = [
                {'__doc_id': doc.id, **doc.to_dict()} 
                for doc in trials_ref.stream()
            ]
            
            if not all_trials:
                continue

            # Group trials by timestamp (use goBeepTimestamp or trialStartTimestamp)
            grouped_trials = defaultdict(list)
            for trial in all_trials:
                # Use goBeepTimestamp as primary key, fall back to trialStartTimestamp
                timestamp_key = trial.get('goBeepTimestamp') or trial.get('trialStartTimestamp')
                if timestamp_key:
                    grouped_trials[timestamp_key].append(trial)

            # Find and remove duplicates
            for timestamp, trials_list in grouped_trials.items():
                if len(trials_list) > 1:
                    # Sort by document ID to keep consistent "first" trial
                    trials_list.sort(key=lambda x: x['__doc_id'])
                    
                    # Delete all except the first one
                    for duplicate in trials_list[1:]:
                        doc_id = duplicate['__doc_id']
                        print(f"  → Duplicate found for {participant_id}: {doc_id}")
                        
                        if not dry_run:
                            trials_ref.document(doc_id).delete()
                        total_deleted += 1
        
        print(f"{'='*60}")
        print(f"Duplicate Scan Complete: Found {total_deleted} duplicates")
        print(f"{'='*60}")
        
        return total_deleted


# ============================================================================
# MAIN - Run cleanup operations
# ============================================================================

if __name__ == "__main__":
    cleaner = FirebaseCleaner()
    
    # SAFETY: Keep dry_run=True for the first run!
    IS_DRY_RUN = True 
    
    print("\n" + "="*60)
    print("FIREBASE DATABASE CLEANUP")
    print("="*60)
    print(f"Mode: {'DRY RUN (no changes will be made)' if IS_DRY_RUN else 'ACTUAL CLEANUP (DESTRUCTIVE)'}")
    print("="*60)
    
    # Step 1: Remove duplicates first
    duplicate_count = cleaner.remove_duplicate_trials(dry_run=IS_DRY_RUN)
    
    # Step 2: Remove incomplete sets (anything not a multiple of 15)
    incomplete_count = cleaner.remove_incomplete_sets(target_count=15, dry_run=IS_DRY_RUN)
    
    # Summary
    print(f"\n{'='*60}")
    print("CLEANUP SUMMARY")
    print(f"{'='*60}")
    print(f"Duplicates found: {duplicate_count}")
    print(f"Incomplete trials found: {incomplete_count}")
    print(f"Total items: {duplicate_count + incomplete_count}")
    
    if IS_DRY_RUN:
        print("\n⚠️  This was a DRY RUN. No data was actually deleted.")
        print("To perform actual deletion, set 'IS_DRY_RUN = False' in the script.")
    else:
        print("\n✓ Cleanup completed successfully!")