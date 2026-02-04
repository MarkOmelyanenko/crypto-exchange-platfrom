-- Add version column to balance table for optimistic locking
-- This migration is safe to run on existing databases

ALTER TABLE balance 
ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- Update existing rows to have version 0 (if any exist)
UPDATE balance SET version = 0 WHERE version IS NULL;
