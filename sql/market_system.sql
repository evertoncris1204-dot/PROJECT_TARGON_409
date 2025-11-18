-- Market System Database Tables
-- Execute this SQL script to create the necessary tables for the Market system

-- Table to store market listings
CREATE TABLE IF NOT EXISTS `market_listings` (
  `listing_id` INT(11) NOT NULL AUTO_INCREMENT,
  `seller_id` INT(11) NOT NULL,
  `seller_name` VARCHAR(35) NOT NULL,
  `item_object_id` INT(11) NOT NULL,
  `item_id` INT(11) NOT NULL,
  `item_name` VARCHAR(255) NOT NULL,
  `item_count` BIGINT(20) NOT NULL DEFAULT 1,
  `item_enchant` INT(11) NOT NULL DEFAULT 0,
  `price` BIGINT(20) NOT NULL,
  `created_at` BIGINT(20) NOT NULL,
  `expires_at` BIGINT(20) NOT NULL,
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=Active, 0=Sold/Expired',
  PRIMARY KEY (`listing_id`),
  KEY `seller_id` (`seller_id`),
  KEY `item_id` (`item_id`),
  KEY `status` (`status`),
  KEY `expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Table to store market transactions history
CREATE TABLE IF NOT EXISTS `market_transactions` (
  `transaction_id` INT(11) NOT NULL AUTO_INCREMENT,
  `listing_id` INT(11) NOT NULL,
  `seller_id` INT(11) NOT NULL,
  `seller_name` VARCHAR(35) NOT NULL,
  `buyer_id` INT(11) NOT NULL,
  `buyer_name` VARCHAR(35) NOT NULL,
  `item_id` INT(11) NOT NULL,
  `item_name` VARCHAR(255) NOT NULL,
  `item_count` BIGINT(20) NOT NULL,
  `price` BIGINT(20) NOT NULL,
  `commission` BIGINT(20) NOT NULL DEFAULT 0,
  `transaction_date` BIGINT(20) NOT NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `seller_id` (`seller_id`),
  KEY `buyer_id` (`buyer_id`),
  KEY `transaction_date` (`transaction_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

