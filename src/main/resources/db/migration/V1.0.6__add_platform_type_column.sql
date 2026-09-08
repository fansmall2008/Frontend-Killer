-- Add platform_type column to game table
ALTER TABLE game ADD COLUMN IF NOT EXISTS platform_type VARCHAR(50);