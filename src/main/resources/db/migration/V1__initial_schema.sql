CREATE TABLE customers (
                           id BIGINT NOT NULL AUTO_INCREMENT,
                           first_name VARCHAR(50) NOT NULL,
                           last_name VARCHAR(50) NOT NULL,
                           email VARCHAR(150) NOT NULL,

                           CONSTRAINT pk_customers
                               PRIMARY KEY (id),

                           CONSTRAINT uk_customers_email
                               UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE virtual_cards (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               version BIGINT NOT NULL,
                               card_name VARCHAR(50) NOT NULL,
                               card_number VARCHAR(16) NOT NULL,
                               expiry_month INT NOT NULL,
                               expiry_year INT NOT NULL,
                               balance DECIMAL(19, 2) NOT NULL,
                               single_transaction_limit DECIMAL(19, 2) NOT NULL,
                               daily_limit DECIMAL(19, 2) NOT NULL,
                               frozen BIT(1) NOT NULL,
                               customer_id BIGINT NOT NULL,
                               online_transactions_enabled BIT(1) NOT NULL,
                               international_transactions_enabled BIT(1) NOT NULL,

                               CONSTRAINT pk_virtual_cards
                                   PRIMARY KEY (id),

                               CONSTRAINT uk_virtual_cards_card_number
                                   UNIQUE (card_number),

                               CONSTRAINT fk_virtual_cards_customer
                                   FOREIGN KEY (customer_id)
                                       REFERENCES customers (id),

                               INDEX idx_virtual_cards_customer_id (customer_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


CREATE TABLE card_transactions (
                                   id BIGINT NOT NULL AUTO_INCREMENT,
                                   idempotency_key VARCHAR(100) NULL,
                                   type VARCHAR(20) NOT NULL,
                                   status VARCHAR(20) NOT NULL,
                                   decline_reason VARCHAR(50) NULL,
                                   amount DECIMAL(19, 2) NOT NULL,
                                   merchant_name VARCHAR(100) NULL,
                                   created_at DATETIME(6) NOT NULL,
                                   card_id BIGINT NOT NULL,

                                   CONSTRAINT pk_card_transactions
                                       PRIMARY KEY (id),

                                   CONSTRAINT uk_card_transactions_idempotency_key
                                       UNIQUE (idempotency_key),

                                   CONSTRAINT fk_card_transactions_card
                                       FOREIGN KEY (card_id)
                                           REFERENCES virtual_cards (id),

                                   INDEX idx_card_transactions_card_created_at (
        card_id,
        created_at
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;