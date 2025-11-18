-- Custom Spawnlist System
-- Table to store custom spawns created by admins

DROP TABLE IF EXISTS `custom_spawnlist`;
CREATE TABLE `custom_spawnlist` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `npc_id` INT NOT NULL,
  `npc_name` VARCHAR(100) NOT NULL,
  `x` INT NOT NULL,
  `y` INT NOT NULL,
  `z` INT NOT NULL,
  `heading` INT NOT NULL DEFAULT 0,
  `respawn_delay` INT NOT NULL DEFAULT 60,
  `respawn_random` INT NOT NULL DEFAULT 0,
  `loc_id` INT NOT NULL DEFAULT 0,
  `period_of_day` VARCHAR(10) NOT NULL DEFAULT 'ALL',
  `created_by` VARCHAR(35) NOT NULL DEFAULT 'System',
  `created_at` BIGINT NOT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `npc_id` (`npc_id`),
  KEY `enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

