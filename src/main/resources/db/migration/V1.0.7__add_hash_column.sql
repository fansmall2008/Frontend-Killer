-- Add hash column to game table
ALTER TABLE game ADD COLUMN IF NOT EXISTS hash VARCHAR(64);