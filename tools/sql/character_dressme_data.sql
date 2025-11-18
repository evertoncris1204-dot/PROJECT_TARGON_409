-- ============================================================
-- DressMe System - Character DressMe Data Table
-- ============================================================
-- This table stores the DressMe skins data for each character
-- ============================================================

CREATE TABLE IF NOT EXISTS `characters_dressme_data` (
  `obj_Id` INT UNSIGNED NOT NULL DEFAULT 0,
  `armor_skins` VARCHAR(255) DEFAULT '',
  `armor_skin_option` INT UNSIGNED NOT NULL DEFAULT 0,
  `weapon_skins` VARCHAR(255) DEFAULT '',
  `weapon_skin_option` INT UNSIGNED NOT NULL DEFAULT 0,
  `hair_skins` VARCHAR(255) DEFAULT '',
  `hair_skin_option` INT UNSIGNED NOT NULL DEFAULT 0,
  `face_skins` VARCHAR(255) DEFAULT '',
  `face_skin_option` INT UNSIGNED NOT NULL DEFAULT 0,
  `shield_skins` VARCHAR(255) DEFAULT '',
  `shield_skin_option` INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`obj_Id`),
  FOREIGN KEY (`obj_Id`) REFERENCES `characters`(`obj_Id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

