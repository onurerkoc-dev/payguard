ALTER TABLE card_transactions
    ADD COLUMN online_transaction BIT(1) NULL
        AFTER merchant_name,

    ADD COLUMN international_transaction BIT(1) NULL
        AFTER online_transaction,

    ADD COLUMN balance_after_transaction DECIMAL(19, 2) NULL
        AFTER international_transaction;