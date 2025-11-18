-- ================================================
-- Tournament System Database Tables
-- ================================================

-- Table for character memo alternative (used by Tournament system)
CREATE TABLE IF NOT EXISTS `character_memo_alt` (
  `obj_id` int(11) NOT NULL DEFAULT 0,
  `name` varchar(255) NOT NULL DEFAULT '0',
  `value` text NOT NULL,
  `expire_time` bigint(20) NOT NULL DEFAULT 0,
  UNIQUE KEY `prim` (`obj_id`,`name`),
  KEY `obj_id` (`obj_id`),
  KEY `name` (`name`),
  KEY `value` (`value`(333)),
  KEY `expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- Table for tournament player statistics
CREATE TABLE IF NOT EXISTS `tournament_player_data` (
  `obj_id` int(11) DEFAULT NULL,
  `fight_type` varchar(45) DEFAULT '',
  `fights_done` int(11) DEFAULT NULL,
  `victories` int(11) DEFAULT NULL,
  `defeats` int(11) DEFAULT NULL,
  `ties` int(11) DEFAULT NULL,
  `kills` int(11) DEFAULT NULL,
  `damage` int(11) DEFAULT NULL,
  `wdt` varchar(11) DEFAULT '',
  `dpf` varchar(11) DEFAULT '',
  KEY `obj_id` (`obj_id`),
  KEY `fight_type` (`fight_type`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

