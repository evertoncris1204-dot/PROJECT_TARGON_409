-- AutoFarm System Database Tables
-- Execute this SQL script to create the necessary tables for the AutoFarm system

-- Table to store AutoFarm settings for each character
CREATE TABLE IF NOT EXISTS `character_autofarm` (
  `char_id` INT(11) NOT NULL,
  `char_name` VARCHAR(35) NOT NULL,
  `radius` INT(11) NOT NULL DEFAULT 1200,
  `short_cut` INT(11) NOT NULL DEFAULT 9,
  `heal_percent` INT(11) NOT NULL DEFAULT 30,
  `buff_protection` TINYINT(1) NOT NULL DEFAULT 0,
  `anti_ks_protection` TINYINT(1) NOT NULL DEFAULT 0,
  `summon_attack` TINYINT(1) NOT NULL DEFAULT 0,
  `summon_skill_percent` INT(11) NOT NULL DEFAULT 0,
  `hp_potion_percent` INT(11) NOT NULL DEFAULT 60,
  `mp_potion_percent` INT(11) NOT NULL DEFAULT 60,
  PRIMARY KEY (`char_id`),
  KEY `char_name` (`char_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Table to track IP addresses for AutoFarm (prevents multiple characters from same IP)
CREATE TABLE IF NOT EXISTS `Auto_Farm_Ip` (
  `char_id` INT(11) NOT NULL,
  `ip` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`char_id`),
  KEY `ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

