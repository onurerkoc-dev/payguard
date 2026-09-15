CREATE TABLE user_accounts (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               email VARCHAR(150) NOT NULL,
                               password_hash VARCHAR(255) NOT NULL,
                               role VARCHAR(20) NOT NULL,
                               enabled BIT(1) NOT NULL,
                               customer_id BIGINT NULL,

                               CONSTRAINT pk_user_accounts
                                   PRIMARY KEY (id),

                               CONSTRAINT uk_user_accounts_email
                                   UNIQUE (email),

                               CONSTRAINT uk_user_accounts_customer
                                   UNIQUE (customer_id),

                               CONSTRAINT fk_user_accounts_customer
                                   FOREIGN KEY (customer_id)
                                       REFERENCES customers (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;