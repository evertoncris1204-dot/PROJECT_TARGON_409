-- Ranking System Tables
-- This script creates tables for the ranking system with weekly/monthly rewards

-- Table to store ranking data and track rewards
CREATE TABLE IF NOT EXISTS `ranking_data` (
  `player_id` INT NOT NULL,
  `player_name` VARCHAR(35) NOT NULL,
  `pvp_kills` INT NOT NULL DEFAULT 0,
  `pk_kills` INT NOT NULL DEFAULT 0,
  `level` TINYINT NOT NULL DEFAULT 1,
  `pve_kills` BIGINT NOT NULL DEFAULT 0,
  `pve_exp` BIGINT NOT NULL DEFAULT 0,
  `last_update` BIGINT NOT NULL,
  `weekly_pvp_rank` INT DEFAULT 0,
  `weekly_pk_rank` INT DEFAULT 0,
  `weekly_level_rank` INT DEFAULT 0,
  `weekly_pve_rank` INT DEFAULT 0,
  `monthly_pvp_rank` INT DEFAULT 0,
  `monthly_pk_rank` INT DEFAULT 0,
  `monthly_level_rank` INT DEFAULT 0,
  `monthly_pve_rank` INT DEFAULT 0,
  `weekly_reward_claimed` TINYINT NOT NULL DEFAULT 0,
  `monthly_reward_claimed` TINYINT NOT NULL DEFAULT 0,
  `last_weekly_reset` BIGINT NOT NULL DEFAULT 0,
  `last_monthly_reset` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`player_id`),
  KEY `pvp_kills` (`pvp_kills`),
  KEY `pk_kills` (`pk_kills`),
  KEY `level` (`level`),
  KEY `pve_kills` (`pve_kills`),
  KEY `pve_exp` (`pve_exp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Table to store reward configurations
CREATE TABLE IF NOT EXISTS `ranking_rewards` (
  `reward_id` INT NOT NULL AUTO_INCREMENT,
  `reward_type` VARCHAR(20) NOT NULL, -- 'weekly' or 'monthly'
  `ranking_type` VARCHAR(20) NOT NULL, -- 'pvp', 'pk', 'level', 'pve'
  `rank_position` INT NOT NULL, -- 1, 2, 3, etc.
  `item_id` INT NOT NULL,
  `item_count` BIGINT NOT NULL DEFAULT 1,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`reward_id`),
  KEY `reward_type` (`reward_type`, `ranking_type`, `rank_position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Insert default weekly rewards (top 3 for each category)
-- PvP Weekly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('weekly', 'pvp', 1, 57, 1000000), -- 1M Adena
('weekly', 'pvp', 2, 57, 500000),  -- 500K Adena
('weekly', 'pvp', 3, 57, 250000);  -- 250K Adena

-- PK Weekly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('weekly', 'pk', 1, 57, 500000),   -- 500K Adena
('weekly', 'pk', 2, 57, 250000),   -- 250K Adena
('weekly', 'pk', 3, 57, 100000);   -- 100K Adena

-- Level Weekly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('weekly', 'level', 1, 57, 750000), -- 750K Adena
('weekly', 'level', 2, 57, 400000),  -- 400K Adena
('weekly', 'level', 3, 57, 200000);  -- 200K Adena

-- PvE Weekly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('weekly', 'pve', 1, 57, 800000),  -- 800K Adena
('weekly', 'pve', 2, 57, 400000),  -- 400K Adena
('weekly', 'pve', 3, 57, 200000);  -- 200K Adena

-- Insert default monthly rewards (top 5 for each category)
-- PvP Monthly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('monthly', 'pvp', 1, 57, 10000000), -- 10M Adena
('monthly', 'pvp', 2, 57, 5000000),  -- 5M Adena
('monthly', 'pvp', 3, 57, 2500000),  -- 2.5M Adena
('monthly', 'pvp', 4, 57, 1000000),  -- 1M Adena
('monthly', 'pvp', 5, 57, 500000);   -- 500K Adena

-- PK Monthly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('monthly', 'pk', 1, 57, 5000000),   -- 5M Adena
('monthly', 'pk', 2, 57, 2500000),   -- 2.5M Adena
('monthly', 'pk', 3, 57, 1000000),   -- 1M Adena
('monthly', 'pk', 4, 57, 500000),    -- 500K Adena
('monthly', 'pk', 5, 57, 250000);    -- 250K Adena

-- Level Monthly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('monthly', 'level', 1, 57, 7500000), -- 7.5M Adena
('monthly', 'level', 2, 57, 4000000),  -- 4M Adena
('monthly', 'level', 3, 57, 2000000),  -- 2M Adena
('monthly', 'level', 4, 57, 1000000),  -- 1M Adena
('monthly', 'level', 5, 57, 500000);   -- 500K Adena

-- PvE Monthly Rewards
INSERT INTO `ranking_rewards` (`reward_type`, `ranking_type`, `rank_position`, `item_id`, `item_count`) VALUES
('monthly', 'pve', 1, 57, 8000000),  -- 8M Adena
('monthly', 'pve', 2, 57, 4000000),  -- 4M Adena
('monthly', 'pve', 3, 57, 2000000),  -- 2M Adena
('monthly', 'pve', 4, 57, 1000000),  -- 1M Adena
('monthly', 'pve', 5, 57, 500000);   -- 500K Adena

